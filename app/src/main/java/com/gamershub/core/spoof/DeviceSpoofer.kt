package com.gamershub.core.spoof

import android.content.Context
import android.os.Build
import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Flagship gaming phone identity used to unlock max-graphics options in games. */
data class SpoofPreset(
    val id: String,
    val displayName: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val gpu: String,
    val displayW: Int,
    val displayH: Int,
    val displayDensity: Int,
    val maxRefreshHz: Int
)

enum class SpoofCapability {
    UNKNOWN,
    /** Full model spoof + display tuning (Magisk resetprop available). */
    FULL_ROOT,
    /** Display/refresh spoof only (Shizuku shell cannot rewrite ro.* build props). */
    DISPLAY_ONLY,
    UNAVAILABLE
}

/**
 * Device spoofing engine.
 *
 * Reality check (honest engineering):
 *  - `ro.product.*` / `ro.build.fingerprint` are read-only properties; changing them
 *    requires init reconfiguration - possible only as root (Magisk `resetprop`).
 *  - With Shizuku (shell UID) we CAN spoof what genuinely affects games:
 *    display resolution/density (`wm size`/`wm density`) and refresh-rate locks,
 *    which is what unlocks 90/120 FPS modes on many titles.
 *  - FULL_ROOT additionally rewrites the model identity so games detect a
 *    flagship gaming phone.
 */
class DeviceSpoofer(
    private val context: Context,
    private val shizuku: ShizukuManager,
    private val settings: SettingsRepository
) {
    private val _capability = MutableStateFlow(SpoofCapability.UNKNOWN)
    val capability: StateFlow<SpoofCapability> = _capability.asStateFlow()

    private val _appliedPreset = MutableStateFlow<SpoofPreset?>(null)
    val appliedPreset: StateFlow<SpoofPreset?> = _appliedPreset.asStateFlow()

    /** Original display settings captured before spoofing. */
    private var backupSize: String? = null
    private var backupDensity: String? = null
    private var backupProps: Map<String, String> = emptyMap()

    suspend fun detect() = withContext(Dispatchers.IO) {
        _capability.value = when {
            !shizuku.canUseShell -> SpoofCapability.UNAVAILABLE
            hasResetprop() -> SpoofCapability.FULL_ROOT
            else -> SpoofCapability.DISPLAY_ONLY
        }
    }

    private suspend fun hasResetprop(): Boolean =
        shizuku.exec("which resetprop").ok

    suspend fun apply(preset: SpoofPreset): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) {
            out += "Shizuku shell not ready"
            return@withContext out
        }
        detect()

        // Backup display state once
        if (backupSize == null) {
            backupSize = shizuku.exec("wm size").output
                .let { Regex("Physical size:\\s*(\\S+)").find(it)?.groupValues?.get(1) }
            backupDensity = shizuku.exec("wm density").output
                .let { Regex("Physical density:\\s*(\\d+)").find(it)?.groupValues?.get(1) }
        }

        // 1. Display spoof - works for every Shizuku user
        shizuku.exec("wm size ${preset.displayW}x${preset.displayH}")
        shizuku.exec("wm density ${preset.displayDensity}")
        shizuku.exec("settings put system peak_refresh_rate ${preset.maxRefreshHz}.0")
        shizuku.exec("settings put system min_refresh_rate ${preset.maxRefreshHz}.0")
        out += "Display set to ${preset.displayW}x${preset.displayH}@${preset.maxRefreshHz}Hz (density ${preset.displayDensity})"

        // 2. Full identity spoof when root power is available
        if (_capability.value == SpoofCapability.FULL_ROOT) {
            val props = mapOf(
                "ro.product.manufacturer" to preset.manufacturer,
                "ro.product.brand" to preset.brand,
                "ro.product.model" to preset.model,
                "ro.product.device" to preset.device,
                "ro.product.name" to preset.product,
            )
            if (backupProps.isEmpty()) {
                backupProps = props.keys.associateWith { key ->
                    shizuku.exec("getprop $key").trimmed
                }
            }
            props.forEach { (k, v) -> shizuku.exec("resetprop $k \"$v\"") }
            out += "Device identity spoofed to ${preset.displayName} (resetprop)"
        } else {
            out += "Model identity: SKIPPED (needs root / Sui - current: display tuning only)"
        }

        _appliedPreset.value = preset
        settings.setSpoofPreset(preset.id)
        settings.setSpoofApplied(true)
        out
    }

    suspend fun remove(): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) return@withContext listOf("Shizuku shell not ready")

        backupSize?.let { shizuku.exec("wm size reset") }
        backupDensity?.let { shizuku.exec("wm density reset") }
        shizuku.exec("settings delete system peak_refresh_rate")
        shizuku.exec("settings delete system min_refresh_rate")
        if (_capability.value == SpoofCapability.FULL_ROOT) {
            backupProps.forEach { (k, v) -> shizuku.exec("resetprop $k \"$v\"") }
        }
        backupProps = emptyMap()
        backupSize = null
        backupDensity = null

        _appliedPreset.value = null
        settings.setSpoofApplied(false)
        out += "Device restored to ${Build.MODEL}"
        out
    }

    companion object {
        val PRESETS = listOf(
            SpoofPreset(
                "rog_8_pro", "ASUS ROG Phone 8 Pro", "asus", "ASUS",
                "ROG Phone 8 Pro", "ASUS_AI2401", "AI2401",
                "Adreno 750", 1080, 2400, 420, 165
            ),
            SpoofPreset(
                "redmagic_9_pro", "nubia REDMAGIC 9 Pro", "nubia", "REDMAGIC",
                "REDMAGIC 9 Pro", "NX769J", "NX769J",
                "Adreno 750", 1116, 2480, 440, 120
            ),
            SpoofPreset(
                "s24_ultra", "Samsung Galaxy S24 Ultra", "samsung", "samsung",
                "SM-S928B", "e3q", "e3qxxx",
                "Adreno 750", 1440, 3120, 505, 120
            ),
            SpoofPreset(
                "iqoo_12", "iQOO 12", "vivo", "iQOO",
                "I2220", "I2220", "I2220",
                "Adreno 750", 1260, 2800, 440, 144
            ),
            SpoofPreset(
                "pixel_8_pro", "Google Pixel 8 Pro", "google", "google",
                "Pixel 8 Pro", "husky", "husky",
                "Mali-G715", 1008, 2244, 390, 120
            ),
            SpoofPreset(
                "black_shark_5", "Black Shark 5 Pro", "blackshark", "blackshark",
                "SHARK KTUS-H0", "penrose", "penrose",
                "Adreno 730", 1080, 2400, 440, 144
            )
        )

        fun presetById(id: String?): SpoofPreset? = PRESETS.firstOrNull { it.id == id }
    }
}
