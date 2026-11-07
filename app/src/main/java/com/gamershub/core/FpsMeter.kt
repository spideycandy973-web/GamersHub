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

    /* ------------------------------------------------------------------ */
    /* Real frame rate from SurfaceFlinger                                 */
    /* ------------------------------------------------------------------ */

    @Volatile
    private var cachedLayer: String? = null
    @Volatile
    private var cachedLayerPkg: String? = null

    /** The compositor layer of [pkg] (`dumpsys SurfaceFlinger --list`). */
    fun layerFor(pkg: String): String? {
        if (cachedLayerPkg == pkg && cachedLayer != null) return cachedLayer
        val list = Shell.exec("dumpsys SurfaceFlinger --list") ?: return null
        val candidates = list.lines().map { it.trim() }.filter { it.isNotBlank() && it.contains(pkg) }
        if (candidates.isEmpty()) return null
        // Prefer the real game surface, then the first window of the package.
        val best = candidates.firstOrNull { it.contains("SurfaceView", true) }
            ?: candidates.firstOrNull { it.contains("BLAST", true) }
            ?: candidates.firstOrNull { it.contains("#0") }
            ?: candidates.first()
        cachedLayer = best
        cachedLayerPkg = pkg
        return best
    }

    /**
     * Frames per second straight from the compositor's present timestamps.
     * Returns -1 when the ROM does not expose latency data.
     */
    fun surfaceFlingerFps(pkg: String): Int {
        val layer = layerFor(pkg) ?: return -1
        val out = Shell.exec("dumpsys SurfaceFlinger --latency '$layer'") ?: return -1
        val stamps = ArrayList<Long>(160)
        for (line in out.lines()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 3) continue
            val present = parts[1].toLongOrNull() ?: continue
            if (present <= 0L) continue
            if (present == Long.MAX_VALUE || present >= 9_223_372_036_854_775_000L) continue
            stamps.add(present)
        }
        if (stamps.size < 3) return -1
        val sorted = stamps.sorted()
        val first = sorted.first()
        val last = sorted.last()
        val spanNs = last - first
        if (spanNs <= 0L) return -1
        val fps = ((sorted.size - 1) * 1_000_000_000.0 / spanNs).toInt()
        return if (fps in 1..240) fps else -1
    }

    /** Best available FPS: compositor first, then the app's own frame counter. */
    fun liveFps(context: Context): Int {
        val pkg = foregroundPackageDeep(context) ?: return -1
        val fromCompositor = surfaceFlingerFps(pkg)
        if (fromCompositor > 0) return fromCompositor
        return -1
    }

    /**
     * Foreground package: usage-stats when the permission exists, otherwise the
     * window manager's focus through shell — so the monitor still works on ROMs
     * where usage access was never granted.
     */
    fun foregroundPackageDeep(context: Context): String? {
        foregroundPackage(context)?.let { return it }
        val focus = Shell.exec("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'") ?: ""
        val match = Regex("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/").find(focus)
        val pkg = match?.groupValues?.getOrNull(1)
        if (!pkg.isNullOrBlank() && pkg != context.packageName) return pkg
        val top = Shell.exec("dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity") ?: ""
        val match2 = Regex("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/").find(top)
        return match2?.groupValues?.getOrNull(1)?.takeIf { it != context.packageName }
    }

    /* ------------------------------------------------------------------ */
    /* Real frame times                                                    */
    /* ------------------------------------------------------------------ */

    data class FrameStats(
        val frames: Int = 0,
        val p50: Float = 0f,
        val p95: Float = 0f,
        val worst: Float = 0f,
        val jankPct: Int = 0,
        val hz: Int = 60
    ) {
        val ok: Boolean get() = frames > 0
        fun line(): String = if (!ok) "no frame data" else String.format(
            java.util.Locale.US,
            "%d frames · p50 %.1fms · p95 %.1fms · jank %d%%",
            frames, p50, p95, jankPct
        )
    }

    /**
     * Real frame times from `dumpsys gfxinfo <pkg> framestats`, the CSV the platform
     * writes for every produced frame. Frame latency = FrameCompleted - IntendedVsync.
     * Jank = frames that missed one and a half refresh intervals.
     */
    fun frameStats(pkg: String, hz: Int = 60): FrameStats {
        if (pkg.isBlank()) return FrameStats(hz = hz)
        val out = Shell.exec("dumpsys gfxinfo $pkg framestats") ?: return FrameStats(hz = hz)
        var header: List<String>? = null
        val durations = ArrayList<Float>(256)
        for (line in out.lines()) {
            val text = line.trim()
            if (text.startsWith("Flags")) {
                header = text.split(",").map { it.trim() }
                continue
            }
            val columns = header ?: continue
            if (text.isEmpty() || !text.first().isDigit()) continue
            val cells = text.split(",")
            if (cells.size < columns.size) continue
            val startIdx = columns.indexOf("IntendedVsync")
            val endIdx = columns.indexOf("FrameCompleted")
            if (startIdx < 0 || endIdx < 0) continue
            val start = cells[startIdx].trim().toLongOrNull() ?: continue
            val end = cells[endIdx].trim().toLongOrNull() ?: continue
            if (end <= start) continue
            val ms = (end - start) / 1_000_000.0
            if (ms in 0.5..2000.0) durations.add(ms.toFloat())
        }
        if (durations.isEmpty()) return FrameStats(hz = hz)
        durations.sort()
        val p50 = durations[durations.size / 2]
        val p95 = durations[(durations.size * 95 / 100).coerceAtMost(durations.size - 1)]
        val budget = 1000.0 / hz.coerceIn(30, 240)
        val jank = durations.count { it > budget * 1.5 }
        return FrameStats(
            frames = durations.size,
            p50 = p50,
            p95 = p95,
            worst = durations.last(),
            jankPct = jank * 100 / durations.size,
            hz = hz
        )
    }

    /** Display refresh rate as the compositor reports it (falls back to WM). */
    fun compositorHz(context: Context): Int {
        val out = Shell.exec("dumpsys display 2>/dev/null | grep -m1 -oE 'refreshRate[= ]+[0-9.]+'") ?: ""
        val value = Regex("([0-9.]+)").find(out)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (value != null && value > 20) return value.toInt()
        return refreshRate(context)
    }
}
