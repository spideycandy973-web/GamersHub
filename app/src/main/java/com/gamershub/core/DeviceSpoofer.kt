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
        if (Shell.ok("wm size ${newWidth}x${newHeight}")) {
            applied.add("Resolution → ${newWidth}x${newHeight} (aspect kept)")
        }
        if (Shell.ok("wm density $newDensity")) {
            applied.add("Density → $newDensity dpi (same physical size)")
        }
        return applied
    }

    fun resetDisplay(): Boolean {
        val size = Shell.ok("wm size reset")
        val density = Shell.ok("wm density reset")
        return size || density
    }

    fun setDeviceName(name: String): Boolean = try {
        if (name.isBlank()) {
            false
        } else {
            val safe = name.replace("'", "")
            Shell.ok("settings put global device_name '$safe'") &&
                Shell.ok("settings put secure bluetooth_name '$safe'")
        }
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
        if (setDeviceName(preset.name)) applied.add("Device name → ${preset.name}")
        if (GfxEngine.setRefreshRate(preset.refresh)) applied.add("Refresh → ${preset.refresh} Hz")
        return applied
    }

    fun restore(): List<String> {
        val restored = ArrayList<String>()
        if (resetDisplay()) restored.add("Display and density reset to stock")
        if (GfxEngine.setRefreshRate(60)) restored.add("Refresh reset to 60 Hz")
        return restored
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1
}
