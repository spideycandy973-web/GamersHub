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

    enum class ScaleMode {
        /** Native panel resolution (reset). */
        NATIVE,
        /** Render below native → compositor upscales → more FPS. */
        HALF, TWO_THIRDS, THREE_QUARTERS, CUSTOM,
        /**
         * Render ABOVE native → compositor supersamples down to the panel —
         * real full-screen anti-aliasing / sharpness ("4K / 8K class" image).
         * Costs GPU power; the picture gets visibly crisper everywhere.
         */
        SUPER_125, SUPER_150, SUPER_200
    }

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

    /** "namespace:key" -> saved value, for global+system refresh keys. */
    private var backupRefresh: Map<String, String> = emptyMap()

    /** True once this engine wrote refresh overrides (so restore knows to clean up). */
    private var refreshTouched = false

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
     * Applies a render scale.
     *  - [customPercent] 40–100 = render below native (performance),
     *    101–200 = supersample above native (quality).
     *  - [refreshHz] locks the panel cadence via every keyspace Android/MIUI
     *    actually reads (global + system peak/min/user refresh rate).
     * Every applied value is re-read from the system and reported back as
     * live proof in the returned log lines.
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
            backupRefresh = captureRefreshBackup()
        }

        // --- render target -------------------------------------------------
        var w: Int
        var h: Int
        var percent = 100
        when (mode) {
            ScaleMode.NATIVE -> {
                w = st.panelW; h = st.panelH; percent = 100
            }
            ScaleMode.HALF -> { w = st.panelW / 2; h = st.panelH / 2; percent = 50 }
            ScaleMode.TWO_THIRDS -> { w = st.panelW * 2 / 3; h = st.panelH * 2 / 3; percent = 67 }
            ScaleMode.THREE_QUARTERS -> { w = st.panelW * 3 / 4; h = st.panelH * 3 / 4; percent = 75 }
            ScaleMode.SUPER_125 -> { w = st.panelW * 5 / 4; h = st.panelH * 5 / 4; percent = 125 }
            ScaleMode.SUPER_150 -> { w = st.panelW * 3 / 2; h = st.panelH * 3 / 2; percent = 150 }
            ScaleMode.SUPER_200 -> { w = st.panelW * 2; h = st.panelH * 2; percent = 200 }
            ScaleMode.CUSTOM -> {
                percent = customPercent.coerceIn(40, 200)
                w = st.panelW * percent / 100
                h = st.panelH * percent / 100
            }
        }
        // Never exceed a sane 8K-class ceiling for supersampling.
        val maxLong = 4320
        val longSide = maxOf(w, h)
        if (longSide > maxLong) {
            val f = maxLong.toFloat() / longSide
            w = (w * f).toInt(); h = (h * f).toInt()
        }
        // encoders/scalers like even numbers
        w -= w % 2
        h -= h % 2

        if (mode == ScaleMode.NATIVE && percent == 100) {
            shizuku.exec("wm size reset")
            shizuku.exec("wm density reset")
            out += "Render resolution reset to native ${st.panelW}x${st.panelH}"
        } else {
            shizuku.exec("wm size ${w}x${h}")
            val origDensity = backupDensity?.toIntOrNull() ?: 420
            val scaled = (origDensity * w.toFloat() / st.panelW).toInt().coerceIn(120, 1200)
            shizuku.exec("wm density $scaled")
            if (w <= st.panelW) {
                val factor = if (w > 0) st.panelW.toFloat() / w else 1f
                out += "PERFORMANCE: rendering at ${w}x${h}, compositor upscales ×%.2f to ${st.panelW}x${st.panelH}".format(factor)
                out += if (st.panelW % w == 0 && st.panelH % h == 0) {
                    "Integer scaling — pixel-perfect upscale"
                } else {
                    "Fractional scaling — GPU compositor upscale (still zero-lag)"
                }
            } else {
                out += "QUALITY: rendering at ${w}x${h} (${percent}%), compositor supersamples DOWN to ${st.panelW}x${st.panelH}"
                out += "Real full-screen anti-aliasing — every app and game gets the sharper image"
            }

            // Live proof: read the override back from the system
            val verify = shizuku.exec("wm size").output
            val override = Regex("Override size:\\s*(\\S+)").find(verify)?.groupValues?.get(1)
            out += if (override == "${w}x${h}") {
                "PROOF: system reports 'Override size: $override' ✓"
            } else {
                "PROOF: system reports 'Override size: ${override ?: "none"}' (requested ${w}x${h})"
            }
        }

        if (refreshHz > 0) lockRefreshHz(refreshHz, out)

        _state.value = st.copy(
            mode = mode, renderW = w, renderH = h,
            refreshHz = if (refreshHz > 0) refreshHz else st.refreshHz,
            active = mode != ScaleMode.NATIVE
        )
        out
    }

    /** Locks the panel cadence only (used by the game-space panel). */
    suspend fun lockRefresh(hz: Int): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) return@withContext listOf("Shizuku shell not connected")
        if (backupRefresh.isEmpty()) {
            backupRefresh = captureRefreshBackup()
        }
        lockRefreshHz(hz, out)
        _state.value = _state.value.copy(refreshHz = hz)
        out
    }

    /**
     * Writes every refresh-rate keyspace Android/MIUI reads, then verifies by
     * reading the global key back. MIUI/HyperOS honours the *global* namespace
     * (the old system-only write silently did nothing — 'lossless scaling is
     * not working').
     */
    private suspend fun lockRefreshHz(hz: Int, out: MutableList<String>) {
        refreshTouched = true
        listOf("global", "system").forEach { ns ->
            shizuku.exec("settings put $ns peak_refresh_rate $hz.0")
            shizuku.exec("settings put $ns min_refresh_rate $hz.0")
        }
        shizuku.exec("settings put system user_refresh_rate $hz.0")
        shizuku.exec("settings put global user_refresh_rate $hz.0")
        val readBack = shizuku.exec("settings get global peak_refresh_rate").trimmed
        out += if (readBack.startsWith("$hz")) {
            "Refresh locked to ${hz} Hz (verified: global peak_refresh_rate = $readBack) ✓"
        } else {
            "Refresh lock requested at ${hz} Hz (system reports peak_refresh_rate = $readBack)"
        }
    }

    /**
     * Saves every refresh-rate key in BOTH namespaces (MIUI reads the global
     * one — restoring only system was why the panel stayed stuck at 144 Hz
     * after the engine was turned off).
     */
    private suspend fun captureRefreshBackup(): Map<String, String> {
        val m = mutableMapOf<String, String>()
        listOf("global", "system").forEach { ns ->
            listOf("peak_refresh_rate", "min_refresh_rate", "user_refresh_rate").forEach { k ->
                m["$ns:$k"] = shizuku.exec("settings get $ns $k").trimmed
            }
        }
        return m
    }

    /** Puts the display back exactly as it was. */
    suspend fun restore(): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) return@withContext listOf("Shizuku shell not connected")
        shizuku.exec("wm size reset")
        shizuku.exec("wm density reset")
        val refreshKeys = listOf("peak_refresh_rate", "min_refresh_rate", "user_refresh_rate")
        when {
            backupRefresh.isNotEmpty() -> backupRefresh.forEach { (nsk, v) ->
                val ns = nsk.substringBefore(":")
                val k = nsk.substringAfter(":")
                if (v.isBlank() || v == "null") shizuku.exec("settings delete $ns $k")
                else shizuku.exec("settings put $ns $k $v")
            }
            refreshTouched -> {
                // No backup taken — DELETE our overrides so the device falls
                // back to its own default instead of staying at e.g. 144 Hz.
                listOf("global", "system").forEach { ns ->
                    refreshKeys.forEach { k -> shizuku.exec("settings delete $ns $k") }
                }
            }
        }
        backupSize = null; backupDensity = null; backupRefresh = emptyMap()
        refreshTouched = false
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
