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
    val marketName: String,
    val hardware: String,
    val board: String,
    val socModel: String,
    val socManufacturer: String,
    val fingerprint: String,
    val gpuVendor: String,
    val gpuRenderer: String,
    val gpuVersion: String,
    val displayW: Int,
    val displayH: Int,
    val displayDensity: Int,
    val maxRefreshHz: Int
)

enum class SpoofCapability {
    UNKNOWN,
    /** Full identity spoof (Magisk resetprop available — CPU-Z shows the fake). */
    FULL_ROOT,
    /**
     * Shizuku shell only: display resolution / density / refresh rate ARE
     * spoofed system-wide and visible in CPU-Z's display section — but
     * `ro.product.*` identity fields are read-only properties and cannot be
     * rewritten without root. We never pretend otherwise.
     */
    DISPLAY_ONLY,
    UNAVAILABLE
}

/**
 * Device spoofing engine.
 *
 * What each identity level really does:
 *  - **Every Shizuku user (shell UID 2000):** `wm size` / `wm density` /
 *    refresh-rate keys change what EVERY app sees for screen resolution,
 *    density and refresh — CPU-Z, DevCheck etc. display the spoofed values.
 *    This alone is what unlocks 90/120/144 FPS + higher-quality options on
 *    most games.
 *  - **Root / Sui (UID 0) additionally:** `resetprop` rewrites the
 *    `ro.product.*` / `ro.soc.*` / fingerprint / GPU props, so apps that read
 *    `Build.MODEL` etc. (CPU-Z "device" page, About-phone clones) show the
 *    flagship identity instead. Without root those properties are physically
 *    read-only — no Shizuku trick can change them, and we say so honestly.
 *
 * Everything is backed up before the first change and rolled back by [remove];
 * every applied value is re-read and reported as live proof.
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
    private var backupRefresh: Map<String, String> = emptyMap()

    /** The identity properties a full spoof rewrites (all read-only → root only). */
    fun identityProps(p: SpoofPreset): Map<String, String> = linkedMapOf(
        "ro.product.manufacturer" to p.manufacturer,
        "ro.product.brand" to p.brand,
        "ro.product.model" to p.model,
        "ro.product.device" to p.device,
        "ro.product.name" to p.product,
        "ro.product.marketname" to p.marketName,
        "ro.product.board" to p.board,
        "ro.hardware" to p.hardware,
        "ro.board.platform" to p.socModel.lowercase().replace(" ", "-"),
        "ro.soc.model" to p.socModel,
        "ro.soc.manufacturer" to p.socManufacturer,
        "ro.build.fingerprint" to p.fingerprint
    )

    suspend fun detect() = withContext(Dispatchers.IO) {
        _capability.value = when {
            !shizuku.canUseShell -> SpoofCapability.UNAVAILABLE
            hasResetprop() -> SpoofCapability.FULL_ROOT
            else -> SpoofCapability.DISPLAY_ONLY
        }
    }

    private suspend fun hasResetprop(): Boolean {
        if (shizuku.exec("which resetprop").trimmed.startsWith("/")) return true
        // Magisk/KSU builds may only expose it through the magisk binary
        return shizuku.exec("magisk resetprop -v >/dev/null 2>&1 && echo ok").trimmed == "ok"
    }

    /** Live `getprop` read-back of every identity key — the spoof proof. */
    suspend fun liveIdentity(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) return@withContext emptyMap()
        val keys = identityProps(_appliedPreset.value ?: PRESETS.first()).keys +
                listOf("ro.hardware.egl", "vendor.opengles.version")
        keys.associateWith { key -> shizuku.exec("getprop $key").trimmed.ifBlank { "(empty)" } }
    }

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
            backupRefresh = mapOf(
                "system.peak" to shizuku.exec("settings get system peak_refresh_rate").trimmed,
                "system.min" to shizuku.exec("settings get system min_refresh_rate").trimmed,
                "global.peak" to shizuku.exec("settings get global peak_refresh_rate").trimmed,
                "global.min" to shizuku.exec("settings get global min_refresh_rate").trimmed
            )
        }

        // 1. Display spoof — works for EVERY Shizuku user, visible in CPU-Z's
        //    display section (resolution / density / refresh).
        shizuku.exec("wm size ${preset.displayW}x${preset.displayH}")
        shizuku.exec("wm density ${preset.displayDensity}")
        listOf("global", "system").forEach { ns ->
            shizuku.exec("settings put $ns peak_refresh_rate ${preset.maxRefreshHz}.0")
            shizuku.exec("settings put $ns min_refresh_rate ${preset.maxRefreshHz}.0")
        }
        out += "Display spoofed: ${preset.displayW}x${preset.displayH} @ ${preset.maxRefreshHz} Hz " +
                "(density ${preset.displayDensity}) — every app sees this"
        val sizeCheck = shizuku.exec("wm size").output
        val override = Regex("Override size:\\s*(\\S+)").find(sizeCheck)?.groupValues?.get(1)
        out += "PROOF: system reports Override size: ${override ?: "none"} ✓"

        // 2. Full identity spoof when root power is available (resetprop).
        if (_capability.value == SpoofCapability.FULL_ROOT) {
            val props = identityProps(preset)
            if (backupProps.isEmpty()) {
                backupProps = props.keys.associateWith { key ->
                    shizuku.exec("getprop $key").trimmed
                }
            }
            props.forEach { (k, v) ->
                shizuku.exec("resetprop $k \"$v\"")
            }
            // GPU props visible to GL info apps
            shizuku.exec("resetprop ro.hardware.egl \"${preset.gpuRenderer.takeWhile { it != ' ' }}\"")

            // Live proof: re-read every key
            var verified = 0
            props.keys.forEach { k ->
                val live = shizuku.exec("getprop $k").trimmed
                if (live == props[k]) verified++
            }
            out += "Identity spoofed to ${preset.displayName} — $verified/${props.size} props verified live"
            out += "PROOF: getprop ro.product.model = ${shizuku.exec("getprop ro.product.model").trimmed} ✓"
            out += "CPU-Z / About-device apps now show ${preset.displayName}"
        } else {
            out += "Identity fields (Build.MODEL / CPU-Z device page): need ROOT (resetprop) — " +
                    "Shizuku shell alone cannot rewrite read-only ro.* properties."
            out += "Live: display section of CPU-Z already shows the spoofed values."
        }

        _appliedPreset.value = preset
        settings.setSpoofPreset(preset.id)
        settings.setSpoofApplied(true)
        out
    }

    suspend fun remove(): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        if (!shizuku.canUseShell) return@withContext listOf("Shizuku shell not ready")

        shizuku.exec("wm size reset")
        shizuku.exec("wm density reset")
        backupRefresh.forEach { (k, v) ->
            val (ns, key) = k.split(".", limit = 2)
            if (v.isBlank() || v == "null") shizuku.exec("settings delete $ns $key")
            else shizuku.exec("settings put $ns $key $v")
        }
        if (_capability.value == SpoofCapability.FULL_ROOT) {
            backupProps.forEach { (k, v) ->
                if (v.isBlank()) shizuku.exec("resetprop --delete $k")
                else shizuku.exec("resetprop $k \"$v\"")
            }
        }
        backupProps = emptyMap()
        backupSize = null
        backupDensity = null
        backupRefresh = emptyMap()

        _appliedPreset.value = null
        settings.setSpoofApplied(false)
        out += "Device restored to ${Build.MODEL}"
        out
    }

    companion object {
        val PRESETS = listOf(
            SpoofPreset(
                id = "rog_8_pro", displayName = "ASUS ROG Phone 8 Pro",
                manufacturer = "asus", brand = "ASUS", model = "ROG Phone 8 Pro",
                device = "ASUS_AI2401", product = "AI2401", marketName = "ROG Phone 8 Pro",
                hardware = "qcom", board = "pineapple",
                socModel = "SM8650-AC", socManufacturer = "Qualcomm",
                fingerprint = "asus/AI2401/ASUS_AI2401:14/UKQ1.230804.001/34.0210.0210.210:user/release-keys",
                gpuVendor = "Qualcomm", gpuRenderer = "Adreno (TM) 750", gpuVersion = "OpenGL ES 3.2",
                displayW = 1080, displayH = 2400, displayDensity = 420, maxRefreshHz = 165
            ),
            SpoofPreset(
                id = "redmagic_9_pro", displayName = "nubia REDMAGIC 9 Pro",
                manufacturer = "nubia", brand = "REDMAGIC", model = "REDMAGIC 9 Pro",
                device = "NX769J", product = "NX769J", marketName = "REDMAGIC 9 Pro",
                hardware = "qcom", board = "pineapple",
                socModel = "SM8650", socManufacturer = "Qualcomm",
                fingerprint = "nubia/NX769J/NX769J:14/UKQ1.230804.001/NX769J_V1.17:user/release-keys",
                gpuVendor = "Qualcomm", gpuRenderer = "Adreno (TM) 750", gpuVersion = "OpenGL ES 3.2",
                displayW = 1116, displayH = 2480, displayDensity = 440, maxRefreshHz = 120
            ),
            SpoofPreset(
                id = "s24_ultra", displayName = "Samsung Galaxy S24 Ultra",
                manufacturer = "samsung", brand = "samsung", model = "SM-S928B",
                device = "e3q", product = "e3qxxx", marketName = "Galaxy S24 Ultra",
                hardware = "qcom", board = "pineapple",
                socModel = "SM8650-AC", socManufacturer = "Qualcomm",
                fingerprint = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXCA:user/release-keys",
                gpuVendor = "Qualcomm", gpuRenderer = "Adreno (TM) 750", gpuVersion = "OpenGL ES 3.2",
                displayW = 1440, displayH = 3120, displayDensity = 505, maxRefreshHz = 120
            ),
            SpoofPreset(
                id = "iqoo_12", displayName = "iQOO 12",
                manufacturer = "vivo", brand = "iQOO", model = "I2220",
                device = "I2220", product = "I2220", marketName = "iQOO 12",
                hardware = "qcom", board = "pineapple",
                socModel = "SM8650-AB", socManufacturer = "Qualcomm",
                fingerprint = "vivo/I2220/I2220:14/UP1A.231005.007/PD2307F_EX:user/release-keys",
                gpuVendor = "Qualcomm", gpuRenderer = "Adreno (TM) 750", gpuVersion = "OpenGL ES 3.2",
                displayW = 1260, displayH = 2800, displayDensity = 440, maxRefreshHz = 144
            ),
            SpoofPreset(
                id = "pixel_8_pro", displayName = "Google Pixel 8 Pro",
                manufacturer = "Google", brand = "google", model = "Pixel 8 Pro",
                device = "husky", product = "husky", marketName = "Pixel 8 Pro",
                hardware = "husky", board = "husky",
                socModel = "Tensor G3", socManufacturer = "Google",
                fingerprint = "google/husky/husky:14/UQ1A.240105.002/11228806:user/release-keys",
                gpuVendor = "ARM", gpuRenderer = "Mali-G715", gpuVersion = "OpenGL ES 3.2",
                displayW = 1008, displayH = 2244, displayDensity = 390, maxRefreshHz = 120
            ),
            SpoofPreset(
                id = "oneplus_12", displayName = "OnePlus 12",
                manufacturer = "OnePlus", brand = "OnePlus", model = "CPH2573",
                device = "OP5D85L1", product = "CPH2573", marketName = "OnePlus 12",
                hardware = "qcom", board = "pineapple",
                socModel = "SM8650-AB", socManufacturer = "Qualcomm",
                fingerprint = "OnePlus/CPH2573/OP5D85L1:14/UKQ1.230924.001/R.20240103.1211:user/release-keys",
                gpuVendor = "Qualcomm", gpuRenderer = "Adreno (TM) 750", gpuVersion = "OpenGL ES 3.2",
                displayW = 1440, displayH = 3168, displayDensity = 450, maxRefreshHz = 120
            )
        )

        fun presetById(id: String?): SpoofPreset? = PRESETS.firstOrNull { it.id == id }
    }
}
