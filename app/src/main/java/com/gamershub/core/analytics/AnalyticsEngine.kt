package com.gamershub.core.analytics

import android.content.Context
import com.gamershub.core.monitor.HardwareMonitor
import com.gamershub.core.record.RecorderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * In-app real-time analytics: keeps a rolling hardware history buffer for charts
 * and records every capture/stream session to disk (JSONL) for the dashboard.
 */
class AnalyticsEngine(
    private val context: Context,
    private val monitor: HardwareMonitor
) {
    @Serializable
    data class StatsSample(
        val ts: Long,
        val fps: Float,
        val cpuPct: Float,
        val cpuTempC: Float,
        val gpuTempC: Float,
        val batteryTempC: Float,
        val memPct: Float,
        val foreground: String
    )

    @Serializable
    data class SessionRecord(
        val startedAt: Long,
        val endedAt: Long,
        val mode: String,
        val maxFps: Float,
        val avgFps: Float,
        val maxBatteryTempC: Float,
        val maxCpuTempC: Float,
        val samples: Int
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val buffer = ArrayDeque<StatsSample>(HISTORY_CAPACITY + 16)
    private val _history = MutableStateFlow<List<StatsSample>>(emptyList())
    val history: StateFlow<List<StatsSample>> = _history.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()

    // Active capture session aggregates
    @Volatile private var sessionStart: Long = 0
    @Volatile private var sessionMode: String = ""
    private var fpsSum = 0.0
    private var fpsCount = 0
    private var maxFps = 0f
    private var maxBatt = -1f
    private var maxCpuT = -1f

    init {
        loadSessions()
        scope.launch {
            monitor.stats.collect { s ->
                if (s.timestamp == 0L) return@collect
                val sample = StatsSample(
                    ts = s.timestamp,
                    fps = s.fps,
                    cpuPct = s.cpuUsagePct,
                    cpuTempC = s.cpuTempC,
                    gpuTempC = s.gpuTempC,
                    batteryTempC = s.batteryTempC,
                    memPct = if (s.memTotalMb > 0) s.memUsedMb * 100f / s.memTotalMb else 0f,
                    foreground = s.foregroundPackage
                )
                while (buffer.size >= HISTORY_CAPACITY) buffer.removeFirst()
                buffer.addLast(sample)

                if (sessionStart > 0) {
                    if (s.fps >= 0) {
                        fpsSum += s.fps; fpsCount++
                        if (s.fps > maxFps) maxFps = s.fps
                    }
                    if (s.batteryTempC > maxBatt) maxBatt = s.batteryTempC
                    if (s.cpuTempC > maxCpuT) maxCpuT = s.cpuTempC
                }
            }
        }
        // Publish history snapshots ~1/s
        scope.launch {
            while (true) {
                _history.value = buffer.toList()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun onSessionStarted(config: RecorderConfig) {
        sessionStart = System.currentTimeMillis()
        sessionMode = config.mode.name
        fpsSum = 0.0; fpsCount = 0; maxFps = 0f; maxBatt = -1f; maxCpuT = -1f
    }

    fun onSessionEnded() {
        if (sessionStart == 0L) return
        val rec = SessionRecord(
            startedAt = sessionStart,
            endedAt = System.currentTimeMillis(),
            mode = sessionMode,
            maxFps = maxFps,
            avgFps = if (fpsCount > 0) (fpsSum / fpsCount).toFloat() else -1f,
            maxBatteryTempC = maxBatt,
            maxCpuTempC = maxCpuT,
            samples = fpsCount
        )
        sessionStart = 0
        val updated = (_sessions.value + rec).takeLast(100)
        _sessions.value = updated
        scope.launch { appendSession(rec) }
    }

    private val sessionsFile: File
        get() = File(context.filesDir, "analytics/sessions.jsonl")

    private fun loadSessions() {
        scope.launch {
            runCatching {
                val file = sessionsFile
                if (!file.exists()) return@runCatching
                val list = file.readLines().takeLast(100).mapNotNull { line ->
                    runCatching { json.decodeFromString<SessionRecord>(line) }.getOrNull()
                }
                _sessions.value = list
            }
        }
    }

    private fun appendSession(rec: SessionRecord) {
        runCatching {
            sessionsFile.parentFile?.mkdirs()
            sessionsFile.appendText(json.encodeToString(rec) + "\n")
        }
    }

    companion object {
        const val HISTORY_CAPACITY = 600 // 10 minutes @ 1 Hz
    }
}
