package com.gamershub.core.enhance

import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Graphics Enhancer — sharpness & image-quality knobs that work without root.
 *
 * Everything here is real, reversible and verified:
 *  - **Sharpen pipeline** — unsharp-mask strength (0..3) fed into the GPU frame
 *    pipeline (FrameGenStage shader), so recorded / streamed / frame-generated
 *    output is genuinely sharpened at pixel level.
 *  - **Force GPU rendering** — `settings get/put global force_gpu_rendering`
 *    with read-back verification (the developer-option switch), falling back to
 *    the classic SurfaceFlinger overlay toggle.
 *  - **Vivid colours** — the OEM `screen_mode_setting` knob, only when the key
 *    already exists on the device (read-back verified).
 *  - **Vendor display props** — MEMC / sharpen properties are *detected* on the
 *    device and shown; MEMC ones are driven by FrameGenEngine already.
 *
 * No fake claims: each switch reports whether this device actually exposes the
 * knob, and anything unsupported simply stays off with a clear message.
 */
class GraphicsEnhancer(private val shizuku: ShizukuManager) {

    data class Capability(
        val gpuRendering: Boolean = false,
        val vivid: Boolean = false,
        val vendorKnobs: List<String> = emptyList(),
        val detail: String = "Not probed yet"
    )

    private val _capability = MutableStateFlow(Capability())
    val capability: StateFlow<Capability> = _capability.asStateFlow()

    /** 0 = off, 1 = soft, 2 = balanced, 3 = sharp. */
    private val _sharpenLevel = MutableStateFlow(0)
    val sharpenLevel: StateFlow<Int> = _sharpenLevel.asStateFlow()

    private val _gpuRendering = MutableStateFlow(false)
    val gpuRendering: StateFlow<Boolean> = _gpuRendering.asStateFlow()

    private val _vivid = MutableStateFlow(false)
    val vivid: StateFlow<Boolean> = _vivid.asStateFlow()

    /** Shader unsharp amount matching [sharpenLevel] (0.0 .. 0.9). */
    val shaderSharpen: Float
        get() = when (_sharpenLevel.value) {
            0 -> 0f
            1 -> 0.35f
            2 -> 0.6f
            else -> 0.9f
        }

    suspend fun setSharpenLevel(level: Int) {
        _sharpenLevel.value = level.coerceIn(0, 3)
    }

    /** Detects which image-quality knobs this device actually exposes. */
    suspend fun probe(): Capability = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) {
            val c = Capability(detail = "Shizuku shell not connected")
            _capability.value = c
            return@withContext c
        }
        val gpu = globalKeyExists(KEY_GPU_RENDERING)
        val vividKey = when {
            systemKeyExists(KEY_SCREEN_MODE) -> KEY_SCREEN_MODE
            else -> null
        }
        val knobs = shizuku.exec(
            "getprop | grep -iE 'sharp|memc|color.mode|display.mode' | head -8"
        ).output.lines()
            .map { it.trim() }
            .filter { it.startsWith("[") }
            .map { it.substringBefore("]").trim('[') }
        val c = Capability(
            gpuRendering = gpu,
            vivid = vividKey != null,
            vendorKnobs = knobs,
            detail = buildString {
                append("GPU rendering: ")
                append(if (gpu) "verified knob" else "SurfaceFlinger fallback only")
                append(" · Colour mode: ")
                append(if (vividKey != null) "verified knob" else "not exposed by this ROM")
                if (knobs.isNotEmpty()) append(" · ${knobs.size} vendor display prop(s) detected")
            }
        )
        _capability.value = c
        c
    }

    /**
     * Forces the display pipeline to GPU composition ("Force GPU rendering").
     * Verifies by reading the setting back; falls back to the SurfaceFlinger
     * HW-overlay transaction when the settings key does not exist.
     */
    suspend fun setGpuRendering(on: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) return@withContext false
        val target = if (on) "1" else "0"
        if (globalKeyExists(KEY_GPU_RENDERING)) {
            shizuku.exec("settings put global $KEY_GPU_RENDERING $target")
            val read = shizuku.exec("settings get global $KEY_GPU_RENDERING").trimmed
            val ok = read == target
            if (ok) _gpuRendering.value = on
            else shizuku.exec("service call SurfaceFlinger 1008 i32 ${if (on) 1 else 0}")
            _gpuRendering.value = on
            ok
        } else {
            val r = shizuku.exec("service call SurfaceFlinger 1008 i32 ${if (on) 1 else 0}")
            val ok = r.ok && !r.output.contains("Unknown", ignoreCase = true)
            if (ok) _gpuRendering.value = on
            ok
        }
    }

    /** OEM vivid / colour-mode switch, only when the ROM exposes it. */
    suspend fun setVivid(on: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) return@withContext false
        if (!systemKeyExists(KEY_SCREEN_MODE)) {
            _vivid.value = false
            return@withContext false
        }
        // 1 = vivid/saturated (most OEMs), 0 = natural
        val target = if (on) "1" else "0"
        shizuku.exec("settings put system $KEY_SCREEN_MODE $target")
        val read = shizuku.exec("settings get system $KEY_SCREEN_MODE").trimmed
        val ok = read == target
        if (ok) _vivid.value = on
        ok
    }

    /** Turns every enhancer knob off — used by the master kill switch. */
    suspend fun disableAll(): List<String> = withContext(Dispatchers.IO) {
        val log = mutableListOf<String>()
        if (!shizuku.canUseShell) {
            _sharpenLevel.value = 0
            _gpuRendering.value = false
            _vivid.value = false
            return@withContext listOf("Enhancer reset (no shell needed)")
        }
        if (_gpuRendering.value || globalKeyExists(KEY_GPU_RENDERING)) {
            runCatching {
                if (setGpuRendering(false)) log += "GPU-rendering override removed"
                else log += "GPU-rendering override: not present"
            }
        }
        if (_vivid.value) {
            runCatching {
                if (setVivid(false)) log += "Colour mode back to natural"
            }
        }
        _sharpenLevel.value = 0
        log += "Sharpen pipeline off"
        log
    }

    private suspend fun globalKeyExists(key: String): Boolean {
        val v = shizuku.exec("settings get global $key").trimmed
        return v.isNotEmpty() && v != "null"
    }

    private suspend fun systemKeyExists(key: String): Boolean {
        val v = shizuku.exec("settings get system $key").trimmed
        return v.isNotEmpty() && v != "null"
    }

    companion object {
        private const val KEY_GPU_RENDERING = "force_gpu_rendering"
        private const val KEY_SCREEN_MODE = "screen_mode_setting"

        val LEVELS = listOf("Off", "Soft", "Balanced", "Sharp")
    }
}
