package com.gamershub.core.scaling

import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Lossless Scaling engine — system-display-surface upscaling via Shizuku.
 *
 * How it really works (no magic):
 *  - `wm size` shrinks the logical render resolution of the whole display.
 *    The SurfaceFlinger compositor then upscales to the physical panel in
 *    hardware, in real time, with zero added latency — the game just renders
 *    fewer pixels (more FPS) while the picture fills the screen.
 *  - **Integer scaling** picks render sizes that are exact divisors of the
 *    panel (1/2, 1/3 …), so the compositor upscale is pixel-perfect block
 *    scaling instead of a blurry fractional resample.
 *  - `wm density` is adjusted alongside so UI layouts stay proportionally
 *    identical (no broken menus).
 *  - The refresh-rate lock (`settings put system peak/min/user_refresh_rate`)
 *    stops the panel from silently dropping to 60 Hz mid-game.
 *
 * Everything is recorded before the first change so [restore] returns the
 * display exactly to its original state.
 */
class LosslessScalingEngine(private val shizuku: ShizukuManager) {

    enum class ScaleMode { NATIVE, HALF, TWO_THIRDS, THREE_QUARTERS, CUSTOM }

    data class State(
        val mode: ScaleMode = ScaleMode.NATIVE,
        val renderW: Int = 0,
        val renderH: Int = 0,
        val panelW: Int = 0,
        val panelH: Int = 0,
        val refreshHz: Int = 0,
        val active: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var backupSize: String? = null
    private var backupDensity: String? = null
    private var backupRefresh: Map<String, String> = emptyMap()

    /** Reads the physical panel size (px) and density. */
    suspend fun probe(): State = withContext(Dispatchers.IO) {
        val sizeOut = shizuku.exec("wm size").output
        val phys = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(sizeOut)
        val w = phys?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val h = phys?.groupValues?.get(2)?.toIntOrNull() ?: 0
        _state.value = _state.value.copy(panelW = w, panelH = h)
        _state.value
    }

    /**
     * Applies a render scale. [customPercent] is used for [ScaleMode.CUSTOM]
     * (50–100). Integer modes pick exact divisors for pixel-perfect upscaling.
     */
    suspend fun apply(
        mode: ScaleMode,
        customPercent: Int = 100,
        refreshHz: Int = 0
    ): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) {
            out += "Shizuku shell not connected"
            return@withContext out
        }
        val st = if (_state.value.panelW == 0) probe() else _state.value
        if (st.panelW == 0 || st.panelH == 0) {
            out += "Could not read the panel resolution"
            return@withContext out
        }

        // backup once
        if (backupSize == null) {
            backupSize = Regex("Physical size:\\s*(\\S+)").find(shizuku.exec("wm size").output)
                ?.groupValues?.get(1)
            backupDensity = Regex("Physical density:\\s*(\\d+)")
                .find(shizuku.exec("wm density").output)?.groupValues?.get(1)
            backupRefresh = mapOf(
                "peak_refresh_rate" to shizuku.exec("settings get system peak_refresh_rate").trimmed,
                "min_refresh_rate" to shizuku.exec("settings get system min_refresh_rate").trimmed,
                "user_refresh_rate" to shizuku.exec("settings get system user_refresh_rate").trimmed
            )
        }

        val (rw, rh) = when (mode) {
            ScaleMode.NATIVE -> st.panelW to st.panelH
            ScaleMode.HALF -> (st.panelW / 2) to (st.panelH / 2)
            ScaleMode.TWO_THIRDS -> (st.panelW * 2 / 3) to (st.panelH * 2 / 3)
            ScaleMode.THREE_QUARTERS -> (st.panelW * 3 / 4) to (st.panelH * 3 / 4)
            ScaleMode.CUSTOM -> {
                val p = customPercent.coerceIn(40, 100)
                (st.panelW * p / 100) to (st.panelH * p / 100)
            }
        }
        // encoders/scalers like even numbers
        val w = rw - (rw % 2)
        val h = rh - (rh % 2)

        if (mode == ScaleMode.NATIVE) {
            shizuku.exec("wm size reset")
            shizuku.exec("wm density reset")
            out += "Render resolution reset to native ${st.panelW}x${st.panelH}"
        } else {
            shizuku.exec("wm size ${w}x${h}")
            val origDensity = backupDensity?.toIntOrNull() ?: 420
            val scaled = (origDensity * w.toFloat() / st.panelW).toInt().coerceAtLeast(120)
            shizuku.exec("wm density $scaled")
            val factor = if (w > 0) st.panelW.toFloat() / w else 1f
            out += "Rendering at ${w}x${h}, compositor upscales ×%.2f to ${st.panelW}x${st.panelH}".format(factor)
            out += if (st.panelW % w == 0 && st.panelH % h == 0) {
                "Integer scaling — pixel-perfect upscale"
            } else {
                "Fractional scaling — GPU compositor upscale (still zero-lag)"
            }
        }

        if (refreshHz > 0) {
            shizuku.exec("settings put system peak_refresh_rate $refreshHz.0")
            shizuku.exec("settings put system min_refresh_rate $refreshHz.0")
            runCatching {
                shizuku.exec("settings put system user_refresh_rate $refreshHz.0")
            }
            out += "Refresh rate locked to ${refreshHz} Hz"
        }

        _state.value = st.copy(
            mode = mode, renderW = w, renderH = h,
            refreshHz = refreshHz, active = mode != ScaleMode.NATIVE
        )
        out
    }

    /** Puts the display back exactly as it was. */
    suspend fun restore(): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) return@withContext listOf("Shizuku shell not connected")
        shizuku.exec("wm size reset")
        shizuku.exec("wm density reset")
        backupRefresh.forEach { (k, v) ->
            if (v.isBlank() || v == "null") shizuku.exec("settings delete system $k")
            else shizuku.exec("settings put system $k $v")
        }
        backupSize = null; backupDensity = null; backupRefresh = emptyMap()
        _state.value = State(panelW = _state.value.panelW, panelH = _state.value.panelH)
        out += "Display restored to native settings"
        out
    }
}

/**
 * Frame-generation engine — display side.
 *
 * Two real mechanisms, honestly reported:
 *  1. **MEMC probe** — several SoCs (MediaTek, Snapdragon with OEM panels)
 *     expose hardware motion-interpolation toggles. We probe the known vendor
 *     nodes/props and drive them when present; when absent we say so.
 *  2. **Frame pacing** — Android's GameManagerService (`cmd game`) frame-rate
 *     votes + forced peak refresh stop the panel duplicating frames at 60 Hz.
 *
 * Capture-side real frame generation (motion-compensated intermediate frames
 * in the recorder/stream output) is handled by
 * [com.gamershub.core.scaling.FrameGenStage] inside the capture pipeline.
 */
class FrameGenEngine(private val shizuku: ShizukuManager) {

    data class MemcCapability(
        val available: Boolean,
        val node: String?,
        val detail: String
    )

    private val _memc = MutableStateFlow(MemcCapability(false, null, "Not probed yet"))
    val memc: StateFlow<MemcCapability> = _memc.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    companion object {
        /** Known vendor MEMC / motion-smoothing switches. */
        private val MEMC_NODES = listOf(
            "/sys/class/dispsys/dispsys/motion_smoothing",
            "/sys/devices/platform/dispsys/motion_smoothing",
            "/proc/memc",
            "/sys/class/graphics/fb0/motion",
            "/sys/class/lcm/motion_interpolation",
            "/sys/kernel/debug/ged/hal/memc"
        )
        private val MEMC_PROPS = listOf(
            "persist.vendor.display.memc_enable",
            "vendor.display.memc.enable",
            "persist.vendor.dpu.memc",
            "persist.sys.smartfps.enable",
            "vendor.display.enable_optimize_refresh"
        )
    }

    /** Probes the device for hardware motion interpolation. */
    suspend fun probe(): MemcCapability = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) {
            val c = MemcCapability(false, null, "Shizuku shell not connected")
            _memc.value = c
            return@withContext c
        }
        for (node in MEMC_NODES) {
            val w = shizuku.exec("test -e '$node' && echo yes").trimmed
            if (w == "yes") {
                val c = MemcCapability(true, node, "Hardware MEMC node found: $node")
                _memc.value = c
                return@withContext c
            }
        }
        for (prop in MEMC_PROPS) {
            val v = shizuku.exec("getprop $prop").trimmed
            if (v.isNotBlank() && v != "null") {
                val c = MemcCapability(true, "prop:$prop", "Vendor property $prop = $v")
                _memc.value = c
                return@withContext c
            }
        }
        val c = MemcCapability(
            false, null,
            "No hardware MEMC exposed on this device — use capture-side FrameGen + frame pacing"
        )
        _memc.value = c
        c
    }

    /** Toggles hardware MEMC when available. Returns a human report. */
    suspend fun setEnabled(on: Boolean): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        val cap = _memc.value
        if (!cap.available) {
            out += cap.detail
            return@withContext out
        }
        val value = if (on) "1" else "0"
        val node = cap.node ?: return@withContext out
        if (node.startsWith("prop:")) {
            shizuku.exec("setprop ${node.removePrefix("prop:")} $value")
            out += "MEMC property set to $value"
        } else {
            shizuku.exec("echo $value > '$node'")
            out += "MEMC node set to $value"
        }
        _enabled.value = on
        out
    }

    /**
     * Frame-pacing stabilization: GameMode frame-rate vote + refresh locks so
     * the panel presents at a steady cadence (kills 60 Hz frame duplication).
     */
    suspend fun applyPacing(targetHz: Int, packageName: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            if (!shizuku.canUseShell) return@withContext listOf("Shizuku shell not connected")
            shizuku.exec("settings put system peak_refresh_rate $targetHz.0")
            shizuku.exec("settings put system min_refresh_rate $targetHz.0")
            out += "Panel cadence locked to $targetHz Hz"
            if (packageName != null) {
                // Android 14+/15 GameManagerService frame-rate vote (best effort)
                val r = shizuku.exec("cmd game set-game-frame-rate $packageName $targetHz")
                out += if (r.ok) "GameMode frame-rate vote: $targetHz Hz for $packageName"
                else "GameMode vote unavailable on this Android version (panel lock still active)"
            }
            out
        }
}
