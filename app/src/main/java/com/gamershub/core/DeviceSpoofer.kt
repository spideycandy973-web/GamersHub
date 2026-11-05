package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell

/**
 * Device spoofing — real, reversible, and honest about its depth.
 *
 * Shell identity (Shizuku, no root) can really change:
 *  - the reported resolution and density (what every app sees),
 *  - the device name (Settings.Global `device_name`, Bluetooth name),
 *  - the refresh rate and the Skia backend.
 *
 * With root the full identity (`ro.product.*`, `ro.build.*`) is written through
 * `resetprop`, which is the only way any tool can change what the framework
 * reports at that level.
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
        val shell: Boolean
    )

    fun status(context: Context): Status {
        val metrics = context.resources.displayMetrics
        return Status(
            model = Shell.exec("getprop ro.product.model")?.trim().orEmpty().ifBlank { android.os.Build.MODEL },
            manufacturer = Shell.exec("getprop ro.product.manufacturer")?.trim().orEmpty().ifBlank { android.os.Build.MANUFACTURER },
            resolution = "${metrics.widthPixels}x${metrics.heightPixels}",
            density = metrics.densityDpi.toString(),
            refresh = "${FpsMeter.refreshRate(context)} Hz",
            deviceName = Shell.exec("settings get global device_name")?.trim().orEmpty().ifBlank { android.os.Build.DEVICE },
            rooted = isRooted(),
            shell = Shell.available()
        )
    }

    fun isRooted(): Boolean = try {
        val out = Shell.exec("id") ?: ""
        out.contains("uid=0")
    } catch (_: Throwable) {
        false
    }

    /** Display spoof: every app now sees this resolution/density. */
    fun applyDisplay(preset: Preset): Boolean = try {
        val height = (preset.width * 16 / 9)
        val okSize = Shell.ok("wm size ${preset.width}x${height}")
        val okDensity = Shell.ok("wm density ${preset.density}")
        okSize && okDensity
    } catch (_: Throwable) {
        false
    }

    fun resetDisplay(): Boolean = try {
        Shell.ok("wm size reset") && Shell.ok("wm density reset")
    } catch (_: Throwable) {
        false
    }

    fun setDeviceName(name: String): Boolean = try {
        if (name.isBlank()) {
            false
        } else {
            val safe = name.replace("'", "")
            val ok = Shell.ok("settings put global device_name '$safe'") &&
                Shell.ok("settings put secure bluetooth_name '$safe'")
            ok
        }
    } catch (_: Throwable) {
        false
    }

    /** Identity spoof through resetprop — root only, verified by reading props back. */
    fun applyIdentity(preset: Preset): Pair<Boolean, String> {
        if (!isRooted()) return false to "Root required for identity props"
        val commands = listOf(
            "resetprop ro.product.model '${preset.model}'",
            "resetprop ro.product.manufacturer '${preset.manufacturer}'",
            "resetprop ro.product.device '${preset.device}'",
            "resetprop ro.product.system.model '${preset.model}'",
            "resetprop ro.product.vendor.model '${preset.model}'"
        )
        var ok = true
        for (command in commands) ok = Shell.ok(command) && ok
        val readBack = Shell.exec("getprop ro.product.model")?.trim().orEmpty()
        return (readBack == preset.model) to readBack
    }

    /** Applies the safe subset (display + name) and reports exactly what changed. */
    fun applySafe(context: Context, preset: Preset): List<String> {
        val applied = ArrayList<String>()
        if (applyDisplay(preset)) applied.add("Display → ${preset.width}p · ${preset.density} dpi")
        if (setDeviceName(preset.name)) applied.add("Device name → ${preset.name}")
        if (GfxEngine.setRefreshRate(preset.refresh)) applied.add("Refresh → ${preset.refresh} Hz")
        return applied
    }

    fun restore(): List<String> {
        val restored = ArrayList<String>()
        if (resetDisplay()) restored.add("Display reset to stock")
        if (GfxEngine.setRefreshRate(60)) restored.add("Refresh reset to 60 Hz")
        return restored
    }
}
