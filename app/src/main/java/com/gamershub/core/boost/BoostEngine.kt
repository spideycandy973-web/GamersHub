package com.gamershub.core.boost

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import com.gamershub.core.monitor.HardwareMonitor
import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.shizuku.ShizukuManager
import com.gamershub.core.shizuku.ShizukuManager.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A concrete set of changes the boost engine applies to the system. */
data class BoostPlan(
    val performanceMode: Boolean = true,
    /** 0 = leave as-is */
    val refreshRateHz: Int = 120,
    /** 1f = physical resolution, 0.85f = -15% render resolution for extra FPS */
    val resolutionScale: Float = 1.0f,
    val killBackground: Boolean = true,
    val blockNotifications: Boolean = true,
    /** Best-effort GPU/HWUI tweaks (animation scales, HWUI props) */
    val speedTweaks: Boolean = true
)

sealed interface BoostState {
    data object Idle : BoostState
    data class Active(
        val plan: BoostPlan,
        val gamePackage: String?,
        val appliedAt: Long = System.currentTimeMillis(),
        val softThrottled: Boolean = false
    ) : BoostState
}

/**
 * Applies and rolls back system-wide performance changes through the
 * Shizuku shell. Everything here works on unrooted devices running Shizuku
 * (adb / wireless debugging identity).
 *
 * What it changes:
 *  - `cmd power set-fixed-performance-mode-enabled` - sustained perf mode
 *  - `cmd thermalservice override-status`           - fake "cool" status to apps
 *  - `settings put system peak_refresh_rate`        - HRR lock (90/120/144 Hz)
 *  - `wm size` / `wm density`                       - render-resolution scaling
 *  - `am kill-all`                                   - frees background RAM
 *  - `settings put global zen_mode`                  - gaming DND
 *  - animation scale reduction                       - snappier UI
 */
class BoostEngine(
    private val context: Context,
    private val shizuku: ShizukuManager,
    private val settings: SettingsRepository,
    private val monitor: HardwareMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BoostState>(BoostState.Idle)
    val state: StateFlow<BoostState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** Original values so we can restore exactly. */
    private var original: Backup? = null

    private data class Backup(
        val peakRefresh: String?, val minRefresh: String?,
        val zenMode: String?, val animWindow: String?,
        val animTransition: String?, val animAnimator: String?,
        val physicalSize: String?
    )

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(line: String) {
        val stamped = "[${timeFmt.format(Date())}] $line"
        _log.value = (_log.value + stamped).takeLast(200)
    }

    // ------------------------------------------------------------ apply

    /** Applies [plan]; if [gamePackage] is set, this boost is bound to that game. */
    suspend fun applyBoost(plan: BoostPlan, gamePackage: String? = null): Boolean {
        if (!shizuku.canUseShell) {
            log("Shizuku shell not ready - boost NOT applied")
            return false
        }
        if (original == null) original = captureBackup()

        log("Applying boost ${gamePackage ?: "(global)"}: $plan")

        if (plan.performanceMode) {
            run("cmd power set-fixed-performance-mode-enabled true")
        }
        // Fake "device is cool" so games/streaming apps don't self-throttle
        run("cmd thermalservice override-status 0")

        if (plan.refreshRateHz > 0) {
            run("settings put system peak_refresh_rate ${plan.refreshRateHz}.0")
            run("settings put system min_refresh_rate ${plan.refreshRateHz}.0")
        }

        if (plan.resolutionScale != 1.0f) {
            val phys = physicalSize()
            if (phys != null) {
                val w = (phys.first * plan.resolutionScale).toInt()
                val h = (phys.second * plan.resolutionScale).toInt()
                run("wm size ${w}x$h")
            }
        }

        if (plan.killBackground) run("am kill-all")

        if (plan.blockNotifications) run("settings put global zen_mode 1")

        if (plan.speedTweaks) {
            run("settings put global window_animation_scale 0.5")
            run("settings put global transition_animation_scale 0.5")
            run("settings put global animator_duration_scale 0.5")
            // Best effort: HWUI renderer hint (silently fails when not writable)
            run("setprop debug.hwui.renderer skiavk", quiet = true)
        }

        _state.value = BoostState.Active(plan, gamePackage)
        keepScreenOn(true)
        return true
    }

    /** Alias used by the floating widget quick toggle. */
    suspend fun toggleBoost(defaultPlan: BoostPlan): Boolean {
        return when (_state.value) {
            is BoostState.Active -> {
                restoreBoost()
                false
            }
            BoostState.Idle -> applyBoost(defaultPlan)
        }
    }

    // ------------------------------------------------------------ restore

    suspend fun restoreBoost() {
        if (!shizuku.canUseShell) return
        val backup = original ?: return
        log("Restoring system state")

        run("cmd power set-fixed-performance-mode-enabled false")
        run("cmd thermalservice reset")
        backup.peakRefresh?.let { run("settings put system peak_refresh_rate $it") }
        backup.minRefresh?.let { run("settings put system min_refresh_rate $it") }
        backup.zenMode?.let { run("settings put global zen_mode $it") }
        backup.animWindow?.let { run("settings put global window_animation_scale $it") }
        backup.animTransition?.let { run("settings put global transition_animation_scale $it") }
        backup.animAnimator?.let { run("settings put global animator_duration_scale $it") }
        if (backup.physicalSize != null) run("wm size reset")

        original = null
        _state.value = BoostState.Idle
        keepScreenOn(false)
    }

    // ------------------------------------------------- soft thermal throttle
    /** Called by the thermal governor in [attachThermalGovernor]. */
    private suspend fun softThrottle(on: Boolean) {
        val active = _state.value as? BoostState.Active ?: return
        if (active.softThrottled == on) return
        if (on) {
            run("settings put system min_refresh_rate 60.0", quiet = true)
            log("Governor: temp above target, capped min refresh at 60 Hz (protecting hardware)")
        } else {
            val hz = active.plan.refreshRateHz
            if (hz > 0) run("settings put system min_refresh_rate ${hz}.0", quiet = true)
            log("Governor: cooled down, full boost restored")
        }
        _state.value = active.copy(softThrottled = on)
    }

    /**
     * Watches temperature while boost is active. When battery skin temp exceeds the
     * profile's target, the governor eases off (prevents real kernel throttling
     * spikes by lowering sustained load before the SoC hits its limit).
     */
    fun attachThermalGovernor(thermal: ThermalManager) {
        scope.launch {
            combine(monitor.stats, thermal.activeProfile) { stats, profile -> stats to profile }
                .collect { (stats, profile) ->
                    val active = _state.value as? BoostState.Active ?: return@collect
                    val target = thermal.targetTempFor(profile, stats.foregroundPackage)
                    val temp = stats.batteryTempC
                    if (temp <= 0f) return@collect
                    if (temp > target + 0.5f) softThrottle(true)
                    else if (temp < target - 1.5f) softThrottle(false)
                }
        }
    }

    // ------------------------------------------------------------ helpers

    private suspend fun run(cmd: String, quiet: Boolean = false): ShellResult {
        val res = shizuku.exec(cmd)
        if (!quiet) {
            val status = if (res.ok) "ok" else "fail(${res.code})"
            val first = res.trimmed.lineSequence().firstOrNull()?.take(80).orEmpty()
            log("$ $cmd -> $status ${if (first.isNotBlank()) "«$first»" else ""}")
        }
        return res
    }

    private suspend fun captureBackup(): Backup {
        suspend fun get(scopeNs: String, key: String): String? =
            shizuku.exec("settings get $scopeNs $key").let {
                if (it.ok && it.trimmed.isNotEmpty() && it.trimmed != "null") it.trimmed else null
            }
        val size = shizuku.exec("wm size").let {
            Regex("Physical size:\\s*(\\d+x\\d+)").find(it.output)?.groupValues?.get(1)
        }
        return Backup(
            peakRefresh = get("system", "peak_refresh_rate"),
            minRefresh = get("system", "min_refresh_rate"),
            zenMode = get("global", "zen_mode"),
            animWindow = get("global", "window_animation_scale"),
            animTransition = get("global", "transition_animation_scale"),
            animAnimator = get("global", "animator_duration_scale"),
            physicalSize = size
        )
    }

    private suspend fun physicalSize(): Pair<Int, Int>? {
        val res = shizuku.exec("wm size")
        val m = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(res.output) ?: return null
        return (m.groupValues[1].toIntOrNull() ?: return null) to
                (m.groupValues[2].toIntOrNull() ?: return null)
    }

    /** "Keep screen on while gaming" - implemented as a window flag on our own activity
     *  plus an extended screen-off timeout through the shell when available. */
    private fun keepScreenOn(on: Boolean) {
        scope.launch {
            val keep = settings.keepScreenOn.first()
            if (on && keep) run("settings put system screen_off_timeout 1800000", quiet = true)
            else if (!on) run("settings put system screen_off_timeout 60000", quiet = true)
        }
        // Also flag our UI window when it exists.
        context.sendBroadcast(Intent(ACTION_KEEP_SCREEN_ON).putExtra(EXTRA_ON, on))
    }

    /** Default plan assembled from user settings. */
    suspend fun defaultPlan(): BoostPlan {
        return BoostPlan(
            performanceMode = true,
            refreshRateHz = settings.defaultRefresh.first(),
            resolutionScale = settings.defaultResolutionScale.first(),
            killBackground = true,
            blockNotifications = true,
            speedTweaks = true
        )
    }

    companion object {
        const val ACTION_KEEP_SCREEN_ON = "com.gamershub.action.KEEP_SCREEN_ON"
        const val EXTRA_ON = "on"
    }
}
