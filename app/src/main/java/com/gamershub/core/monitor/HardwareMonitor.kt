package com.gamershub.core.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.view.Display
import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Real-time hardware telemetry.
 *
 * - CPU usage / frequency / thermal zones are read directly (world-readable sysfs).
 * - Live FPS of the *foreground game* is computed from `dumpsys gfxinfo` deltas
 *   through the Shizuku shell (works on unrooted devices).
 * - Battery temperature comes from the sticky battery broadcast (no permission).
 */
class HardwareMonitor(
    private val context: Context,
    private val shizuku: ShizukuManager
) {
    data class HardwareStats(
        val cpuUsagePct: Float = -1f,
        val cpuFreqMhz: Int = 0,
        val cpuCoreCount: Int = Runtime.getRuntime().availableProcessors(),
        val gpuModel: String = "",
        val gpuTempC: Float = -1f,
        val cpuTempC: Float = -1f,
        val batteryTempC: Float = -1f,
        val memUsedMb: Long = 0,
        val memTotalMb: Long = 0,
        /** FPS of the current foreground app (-1 when unknown). */
        val fps: Float = -1f,
        val foregroundPackage: String = "",
        val refreshRateHz: Int = 60,
        /** Thermal status 0..6 (THM_STATUS_*), -1 when unknown. */
        val thermalStatus: Int = -1,
        val timestamp: Long = System.currentTimeMillis()
    )

    /** Runs the sampling loops — cancelled by [stop] while the master switch is OFF. */
    private var scope = com.gamershub.core.util.SafeScopes.default("monitor")

    private val _stats = MutableStateFlow(HardwareStats())
    val stats: StateFlow<HardwareStats> = _stats.asStateFlow()

    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L
    private var lastFrames = -1L
    private var lastFrameNs = -1L
    private var gpuModelCache: String? = null
    @Volatile private var running = false

    /**
     * Stops all sampling loops (master switch OFF). The next [start] gets a
     * fresh scope — SafeScopes' cancelled scope can't be reused.
     */
    fun stop() {
        running = false
        runCatching { scope.cancel() }
        scope = com.gamershub.core.util.SafeScopes.default("monitor")
    }

    fun start() {
        if (running) return
        running = true

        // Fast sensors (all readable without special privileges)
        scope.launch {
            while (isActive) {
                try {
                    val prev = _stats.value
                    _stats.value = prev.copy(
                        cpuUsagePct = readCpuUsage(),
                        cpuFreqMhz = readCpuMaxFreqMhz(),
                        cpuTempC = readZoneTemp(listOf("soc", "cpu", "ap", "big", "mtktsAP")),
                        gpuTempC = readZoneTemp(listOf("gpu")),
                        batteryTempC = readBatteryTempC(),
                        memUsedMb = memoryInfo().first,
                        memTotalMb = memoryInfo().second,
                        refreshRateHz = currentRefreshRate(),
                        timestamp = System.currentTimeMillis()
                    )
                } catch (t: Throwable) {
                    // one bad read must never kill telemetry or the app
                    android.util.Log.w("GamersHub", "monitor tick failed", t)
                }
                delay(1000)
            }
        }

        // Slow privileged path: foreground app + fps + thermal status.
        // NOTE: these shell probes are the expensive ones — `dumpsys window`
        // dumps every window in the system and used to run every 2.5s, which
        // made the WHOLE PHONE lag. The cadence is now 5s, foreground detection
        // uses the far cheaper `dumpsys activity activities`, thermal status is
        // re-read only every third tick (it barely changes).
        var slowTick = 0
        scope.launch {
            while (isActive) {
                try {
                    if (shizuku.canUseShell) {
                        val fg = readForegroundPackage()
                        if (fg.isNotBlank()) {
                            val fps = readForegroundFps(fg)
                            _stats.value = _stats.value.copy(foregroundPackage = fg, fps = fps)
                        }
                        if (slowTick % 3 == 0) {
                            val ts = readThermalStatus()
                            if (ts >= 0) _stats.value = _stats.value.copy(thermalStatus = ts)
                        }
                        if (gpuModelCache == null) {
                            gpuModelCache = readGpuModel()
                            _stats.value = _stats.value.copy(gpuModel = gpuModelCache.orEmpty())
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("GamersHub", "privileged monitor tick failed", t)
                }
                slowTick++
                delay(5000)
            }
        }
    }

    // ------------------------------------------------------------------ CPU
    private fun readCpuUsage(): Float {
        return try {
            val parts = File("/proc/stat").bufferedReader().use { it.readLine() }
                .split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle = parts[3] + parts.getOrElse(4) { 0L }
            val total = parts.sum()
            val dTotal = total - lastCpuTotal
            val dIdle = idle - lastCpuIdle
            lastCpuTotal = total
            lastCpuIdle = idle
            if (dTotal <= 0) _stats.value.cpuUsagePct
            else ((dTotal - dIdle).toFloat() / dTotal.toFloat() * 100f).coerceIn(0f, 100f)
        } catch (_: Throwable) {
            -1f
        }
    }

    private fun readCpuMaxFreqMhz(): Int {
        return try {
            val base = File("/sys/devices/system/cpu")
            var maxKhz = 0
            base.listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.forEach { cpu ->
                val f = File(cpu, "cpufreq/scaling_cur_freq")
                if (f.canRead()) {
                    val khz = f.readText().trim().toIntOrNull() ?: 0
                    if (khz > maxKhz) maxKhz = khz
                }
            }
            maxKhz / 1000
        } catch (_: Throwable) {
            0
        }
    }

    // --------------------------------------------------------------- Thermals
    /** Searches thermal zones for one whose `type` contains any of [keywords]. */
    private fun readZoneTemp(keywords: List<String>): Float {
        return try {
            val dir = File("/sys/class/thermal") ?: return -1f
            val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return -1f
            // First pass: match by type name
            for (zone in zones) {
                val typeFile = File(zone, "type")
                if (!typeFile.canRead()) continue
                val type = typeFile.readText().trim().lowercase()
                if (keywords.any { type.contains(it.lowercase()) }) {
                    tempFrom(File(zone, "temp"))?.let { return it }
                }
            }
            -1f
        } catch (_: Throwable) {
            -1f
        }
    }

    /** Highest temperature across all readable zones (used as "device temp"). */
    fun hottestZoneTemp(): Float {
        return try {
            var hottest = -1f
            File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.forEach { zone ->
                    tempFrom(File(zone, "temp"))?.let { if (it > hottest) hottest = it }
                }
            hottest
        } catch (_: Throwable) {
            -1f
        }
    }

    private fun tempFrom(file: File): Float? {
        if (!file.canRead()) return null
        val raw = file.readText().trim().toFloatOrNull() ?: return null
        // Zone temps are reported either in millidegree-C or degree-C.
        return when {
            raw > 1000f -> raw / 1000f
            raw > 200f -> raw / 10f
            else -> raw
        }
    }

    private fun readBatteryTempC(): Float {
        return try {
            val intent: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val t = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1000) ?: -1000
            if (t == -1000) -1f else t / 10f
        } catch (_: Throwable) {
            -1f
        }
    }

    // --------------------------------------------------------------- Memory
    private fun memoryInfo(): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val used = (mi.totalMem - mi.availMem) / (1024 * 1024)
            val total = mi.totalMem / (1024 * 1024)
            used to total
        } catch (_: Throwable) {
            0L to 0L
        }
    }

    // --------------------------------------------------------------- Display
    private fun currentRefreshRate(): Int {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            display?.refreshRate?.toInt() ?: 60
        } catch (_: Throwable) {
            60
        }
    }

    // -------------------------------------------- Privileged (Shizuku) reads
    private suspend fun readForegroundPackage(): String {
        return try {
            // `dumpsys window` dumps EVERY window in the system (megabytes) and
            // was the main source of whole-phone lag. `dumpsys activity
            // activities` piped through grep is a tiny fraction of the cost.
            val res = shizuku.exec(
                "dumpsys activity activities | grep -m1 -E 'topResumedActivity|ResumedActivity'"
            )
            if (!res.ok) return ""
            // topResumedActivity=ActivityRecord{... u0 com.game.pkg/.MainActivity}
            // NOTE: this pattern must stay valid for ICU regex — Android's
            // java.util.regex is ICU-backed on many devices (MIUI etc.) and
            // throws PatternSyntaxException for patterns the OpenJDK engine
            // tolerates (e.g. a stray '}'), which used to crash the app.
            val regex = Regex("([a-zA-Z0-9_.]+)/[a-zA-Z0-9_$.]+")
            regex.find(res.output)?.groupValues?.getOrNull(1) ?: ""
        } catch (t: Throwable) {
            ""
        }
    }

    /**
     * Computes FPS of [pkg] from `dumpsys gfxinfo` frame counters.
     * Returns -1 when unavailable (app not rendering / not found).
     */
    private suspend fun readForegroundFps(pkg: String): Float {
        val res = shizuku.exec("dumpsys gfxinfo $pkg")
        if (!res.ok) return -1f
        val frames = Regex("Total frames rendered:\\s*(\\d+)").find(res.output)
            ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return -1f
        val since = Regex("Stats since:\\s*(\\d+)").find(res.output)
            ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return -1f

        val prevFrames = lastFrames
        val prevSince = lastFrameNs
        lastFrames = frames
        lastFrameNs = since
        if (prevFrames < 0 || since <= prevSince || frames < prevFrames) return -1f
        val dtSec = (since - prevSince) / 1_000_000_000f
        if (dtSec <= 0f) return -1f
        return ((frames - prevFrames) / dtSec).coerceIn(0f, 240f)
    }

    private suspend fun readThermalStatus(): Int {
        val res = shizuku.exec("dumpsys thermalservice | grep -i 'mStatus' | head -n 1")
        if (!res.ok) return -1
        return Regex("mStatus=(\\d+)").find(res.output)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: -1
    }

    private suspend fun readGpuModel(): String {
        if (Build.SUPPORTED_ABIS.isEmpty()) return ""
        // Cheap props first — `dumpsys SurfaceFlinger` dumps every layer in the
        // system, so only fall back to it when the props are empty.
        val cheap = shizuku.exec(
            "getprop | grep -m2 -iE 'ro.hardware.egl|ro.board.platform'"
        ).output.substringAfter("]").trim().trim('[', ' ')
        if (cheap.isNotBlank()) return cheap.take(64)
        val res = shizuku.exec("dumpsys SurfaceFlinger | grep -i 'GLES:' | head -n 1")
        if (!res.ok) return ""
        // GLES: Qualcomm, Adreno (TM) 740, ...
        return res.output.substringAfter("GLES:").trim().take(64)
    }
}
