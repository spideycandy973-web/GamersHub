package com.gamershub.core

import android.app.usage.UsageStatsManager
import android.content.Context
import com.gamershub.core.shizuku.Shell

/**
 * Real frame-rate metering.
 *
 * The total frame counter of the foreground game is read from
 * `dumpsys gfxinfo <pkg>` (through Shizuku) and the delta between two samples
 * one second apart is the actual rendered FPS. No synthetic numbers.
 */
object FpsMeter {

    /** Frame counter for [pkg], or null when gfxinfo is not readable. */
    fun totalFrames(pkg: String): Long? = try {
        val out = Shell.exec("dumpsys gfxinfo $pkg")
        if (out == null) {
            null
        } else {
            Regex("Total frames rendered:\\s*(\\d+)").find(out)
                ?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
    } catch (_: Throwable) {
        null
    }

    /** Foreground (top) package from the usage-stats service, or null. */
    fun foregroundPackage(context: Context): String? = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000L, now)
        var lastPkg: String? = null
        if (events != null) {
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    lastPkg = event.packageName
                }
            }
        }
        lastPkg?.takeIf { it != context.packageName }
    } catch (_: Throwable) {
        null
    }

    /** Current display refresh rate in Hz (real, from the window manager). */
    fun refreshRate(context: Context): Int = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay.refreshRate.toInt().coerceIn(30, 240)
    } catch (_: Throwable) {
        60
    }

    /** GPU load percentage when the vendor exposes it (Qualcomm/Mali paths). */
    fun gpuLoad(): Int {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/kernel/gpu/gpu_busy"
        )
        for (path in paths) {
            try {
                val out = Shell.exec("cat $path") ?: continue
                val text = out.trim()
                if (text.isEmpty()) continue
                if (path.endsWith("gpubusy")) {
                    val parts = text.split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        val busy = parts[0].toLongOrNull() ?: continue
                        val total = parts[1].toLongOrNull() ?: continue
                        if (total > 0) return ((busy * 100) / total).toInt().coerceIn(0, 100)
                    }
                } else {
                    val digits = text.filter { it.isDigit() }
                    if (digits.isNotEmpty()) return digits.toInt().coerceIn(0, 100)
                }
            } catch (_: Throwable) {
                // try the next vendor path
            }
        }
        return -1
    }

    /** Battery current draw in mA (negative = discharging), or -1. */
    fun currentNow(context: Context): Int = try {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
    } catch (_: Throwable) {
        -1
    }
}
