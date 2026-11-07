package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell
import kotlin.math.roundToInt

/**
 * Device spoofing — real, reversible and geometry-safe.
 *
 * The display spoof **never changes the aspect ratio**: it scales the physical
 * panel size by one factor and scales the density by the same factor, so the
 * picture fills the screen exactly as before (no cropping, no letterboxing, no
 * "smaller screen") while every app reports the new resolution and DPI.
 *
 * Identity properties (`ro.product.model`, …) are read-only for apps and for
 * the shell user on a stock ROM — they are written through `resetprop` only
 * when root is actually detected, and the result is read back and reported.
 */
object DeviceSpoofer {

    data class Preset(
        val name: String,
        val model: String,
        val manufacturer: String,
        val device: String,
        val width: Int,
        val density: Int,
        val refresh: Int
    )

    val presets = listOf(
        Preset("ROG Phone 8 Pro", "ASUS_AI2401_D", "asus", "AI2401", 1080, 420, 165),
        Preset("REDMAGIC 9 Pro", "NX769J", "nubia", "NX769J", 1116, 440, 165),
        Preset("Galaxy S24 Ultra", "SM-S928B", "samsung", "e3q", 1440, 560, 120),
        Preset("Pixel 8 Pro", "Pixel 8 Pro", "Google", "husky", 1344, 480, 120),
        Preset("OnePlus 12", "CPH2583", "OnePlus", "OP5D0DL1", 1440, 510, 120)
    )

    data class Status(
        val model: String,
        val manufacturer: String,
        val resolution: String,
        val density: String,
        val refresh: String,
        val deviceName: String,
        val rooted: Boolean,
        val shell: Boolean,
        val overrideSize: String
    )

    /** Physical panel size, unaffected by any `wm size` override. */
    data class Panel(val width: Int, val height: Int, val density: Int)

    fun panel(context: Context): Panel {
        val metrics = context.resources.displayMetrics
        var width = metrics.widthPixels
        var height = metrics.heightPixels
        var density = metrics.densityDpi
        val sizeOut = Shell.exec("wm size") ?: ""
        Regex("Physical size:\\s*(\\d+)x(\\d+)").find(sizeOut)?.let { match ->
            width = match.groupValues[1].toIntOrNull() ?: width
            height = match.groupValues[2].toIntOrNull() ?: height
        }
        val densityOut = Shell.exec("wm density") ?: ""
        Regex("Physical density:\\s*(\\d+)").find(densityOut)?.let { match ->
            density = match.groupValues[1].toIntOrNull() ?: density
        }
        if (width <= 0) width = metrics.widthPixels
        if (height <= 0) height = metrics.heightPixels
        if (density <= 0) density = metrics.densityDpi
        return Panel(width, height, density)
    }

    fun currentOverrideSize(): String = try {
        val out = Shell.exec("wm size") ?: ""
        Regex("Override size:\\s*(\\d+x\\d+)").find(out)?.groupValues?.getOrNull(1) ?: ""
    } catch (_: Throwable) {
        ""
    }

    fun status(context: Context): Status {
        val panel = panel(context)
        val override = currentOverrideSize()
        return Status(
            model = Shell.exec("getprop ro.product.model")?.trim().orEmpty().ifBlank { android.os.Build.MODEL },
            manufacturer = Shell.exec("getprop ro.product.manufacturer")?.trim().orEmpty()
                .ifBlank { android.os.Build.MANUFACTURER },
            resolution = if (override.isNotEmpty()) override else "${panel.width}x${panel.height}",
            density = Shell.exec("wm density")?.let { out ->
                Regex("Override density:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)
            } ?: panel.density.toString(),
            refresh = "${FpsMeter.refreshRate(context)} Hz",
            deviceName = Shell.exec("settings get global device_name")?.trim().orEmpty()
                .ifBlank { android.os.Build.DEVICE },
            rooted = isRooted(),
            shell = Shell.available(),
            overrideSize = override
        )
    }

    fun isRooted(): Boolean {
        val direct = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            text
        } catch (_: Throwable) {
            ""
        }
        if (direct.contains("uid=0")) return true
        val viaShell = Shell.exec("su -c id") ?: ""
        return viaShell.contains("uid=0")
    }

    /**
     * Applies an aspect-preserving display spoof.
     * Returns the list of human-readable changes, empty when Shizuku is missing.
     */
    fun applyDisplay(context: Context, preset: Preset): List<String> {
        if (!Shell.available()) return emptyList()
        val panel = panel(context)
        if (panel.width <= 0 || panel.height <= 0) return emptyList()

        // One scale factor for both axes → identical geometry, different reported size.
        val factor = preset.width.toFloat() / panel.width.toFloat()
        val newWidth = even((panel.width * factor).roundToInt()).coerceAtLeast(320)
        val newHeight = even((panel.height * factor).roundToInt()).coerceAtLeast(320)
        val newDensity = (panel.density * factor).roundToInt().coerceIn(120, 640)

        val applied = ArrayList<String>()
        val sizeOk = Shell.ok("wm size ${newWidth}x${newHeight}")
        val densityOk = Shell.ok("wm density $newDensity")
        if (sizeOk && !densityOk) {
            // Resolution without density would shrink the UI — never leave that state.
            Shell.ok("wm size reset")
            return emptyList()
        }
        if (sizeOk) applied.add("Resolution → ${newWidth}x${newHeight} (aspect kept)")
        if (densityOk) applied.add("Density → $newDensity dpi (same physical size)")
        return applied
    }

    fun resetDisplay(): Boolean {
        val size = Shell.ok("wm size reset")
        val density = Shell.ok("wm density reset")
        return size || density
    }

    /** The name the phone shipped with, as the system itself reports it. */
    fun stockDeviceName(): String = try {
        android.os.Build.MODEL.ifBlank { "Android" }
    } catch (_: Throwable) {
        "Android"
    }

    /**
     * Renames the phone (Bluetooth / USB / file transfer / casting all read this).
     *
     * The stock name is remembered first so it can always be put back — an older
     * build left devices permanently named "REDMAGIC 9 Pro" because restore() only
     * reset the display.
     */
    fun setDeviceName(context: Context?, name: String, rememberStock: Boolean = true): Boolean = try {
        if (name.isBlank()) {
            false
        } else {
            if (rememberStock && context != null) {
                val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("spoof_name_active", false)) {
                    val current = Shell.exec("settings get global device_name")?.trim().orEmpty()
                    prefs.edit()
                        .putString("spoof_name_stock", current.ifBlank { stockDeviceName() })
                        .putBoolean("spoof_name_active", true)
                        .apply()
                }
            }
            val safe = name.replace("'", "")
            Shell.ok("settings put global device_name '$safe'") &&
                Shell.ok("settings put secure bluetooth_name '$safe'")
        }
    } catch (_: Throwable) {
        false
    }

    /**
     * Puts the phone's real name back. Deletes the overrides so the system falls
     * back to `ro.product.model`, and writes the remembered stock name as a fallback.
     */
    fun restoreDeviceName(context: Context): List<String> {
        val done = ArrayList<String>()
        if (!Shell.available()) return done
        val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
        val stock = prefs.getString("spoof_name_stock", null)?.ifBlank { null } ?: stockDeviceName()
        val safe = stock.replace("'", "")
        val delGlobal = Shell.ok("settings delete global device_name")
        val delBt = Shell.ok("settings delete secure bluetooth_name")
        val current = Shell.exec("settings get global device_name")?.trim().orEmpty()
        if (current.isBlank() || current == "null") {
            Shell.ok("settings put global device_name '$safe'")
        }
        val after = Shell.exec("settings get global device_name")?.trim().orEmpty()
        if (delGlobal || delBt) {
            done.add("Device name back to '${after.ifBlank { safe }}'")
        }
        prefs.edit().putBoolean("spoof_name_active", false).apply()
        return done
    }

    /**
     * Repairs a phone that an older build renamed permanently (e.g. shows up as
     * "REDMAGIC 9 Pro" in Bluetooth / USB / file transfer and never went back).
     * Only touches the name when it is one of our presets.
     */
    fun cleanStaleSpoofName(context: Context): String? {
        if (!Shell.available()) return null
        val current = Shell.exec("settings get global device_name")?.trim().orEmpty()
        if (current.isBlank() || current == "null") return null
        val isPreset = presets.any { it.name.equals(current, ignoreCase = true) }
        if (!isPreset) return null
        val stock = stockDeviceName()
        Shell.ok("settings delete global device_name")
        Shell.ok("settings delete secure bluetooth_name")
        val after = Shell.exec("settings get global device_name")?.trim().orEmpty()
        if (after.isBlank() || after == "null") {
            Shell.ok("settings put global device_name '${stock.replace("'", "")}'")
        }
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).edit()
            .putBoolean("spoof_name_active", false)
            .apply()
        return "device name restored to $stock"
    }

    /** True when a spoofed name is still live on the device. */
    fun nameSpoofActive(context: Context): Boolean = try {
        val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
        prefs.getBoolean("spoof_name_active", false)
    } catch (_: Throwable) {
        false
    }

    /** Identity spoof through resetprop/setprop — root only, read back and verified. */
    fun applyIdentity(preset: Preset): Pair<Boolean, String> {
        if (!isRooted()) return false to "Root required for model/build properties"
        val commands = listOf(
            "resetprop -n ro.product.model '${preset.model}'",
            "resetprop -n ro.product.manufacturer '${preset.manufacturer}'",
            "resetprop -n ro.product.device '${preset.device}'",
            "resetprop -n ro.product.system.model '${preset.model}'",
            "resetprop -n ro.product.vendor.model '${preset.model}'",
            "resetprop -n ro.build.product '${preset.device}'"
        )
        for (command in commands) {
            Shell.exec("su -c \"$command\"")
        }
        val readBack = Shell.exec("getprop ro.product.model")?.trim().orEmpty()
        return (readBack == preset.model) to readBack
    }

    /** Applies the whole safe subset and reports exactly what changed. */
    fun applySafe(context: Context, preset: Preset): List<String> {
        val applied = ArrayList<String>()
        applied.addAll(applyDisplay(context, preset))
        if (setDeviceName(context, preset.name)) applied.add("Device name → ${preset.name} (restored automatically)")
        if (GfxEngine.setRefreshRate(preset.refresh)) applied.add("Refresh → ${preset.refresh} Hz")
        return applied
    }

    fun restore(context: Context? = null): List<String> {
        val restored = ArrayList<String>()
        if (resetDisplay()) restored.add("Display and density reset to stock")
        // Deleting the override returns the panel to its own adaptive rate — an
        // older build pinned every phone to 60 Hz here, which is a downgrade.
        val peak = Shell.ok("settings delete system peak_refresh_rate")
        val min = Shell.ok("settings delete system min_refresh_rate")
        if (peak || min) restored.add("Refresh rate back to the panel default")
        if (context != null) {
            restored.addAll(restoreDeviceName(context))
            // leftover GPU property from older builds
            restored.addAll(GfxSafe.clearUnsafeProps())
        }
        return restored
    }

    /** What other apps currently observe (after any override). */
    fun observed(context: Context): String {
        val metrics = context.resources.displayMetrics
        return "${metrics.widthPixels}x${metrics.heightPixels} · ${metrics.densityDpi}dpi"
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1
}
