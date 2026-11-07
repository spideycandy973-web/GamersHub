package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell
import kotlin.math.roundToInt

/**
 * Real graphics / performance control.
 *
 * Everything in here changes an actual system surface:
 *  - render scale: `wm size` only (the compositor upscales, the GPU renders fewer
 *    pixels — the lossless path; the density is never scaled);
 *  - refresh rate: `peak_refresh_rate` / `min_refresh_rate`;
 *  - renderer: `debug.hwui.renderer` (Skia Vulkan backend) applied to every app;
 *  - frame pacing: fixed-performance mode + GameMode override, which is what
 *    Android itself exposes for sustained frame rates;
 *  - per-game texture / shadow quality: the game's own config CVars (UE4/UE5).
 */
object GfxEngine {

    data class DisplayState(val width: Int, val height: Int, val density: Int, val refresh: Int)

    data class GfxOptions(
        val texture: String = "HIGH",     // LOW / MEDIUM / HIGH / ULTRA
        val shadow: String = "HIGH",
        val effects: String = "HIGH",
        val antiAliasing: String = "HIGH",
        val resolution: Int = 100,        // sg.ResolutionQuality
        val sharpness: Int = 60,          // r.Tonemapper.Sharpen 0..100
        val frameLimit: Int = 0           // 0 = unlimited
    )

    /** Does this ROM ship `cmd game` (GameManager, Android 12+)? */
    fun hasGameManager(): Boolean = RenderEngine.hasGameManager()

    fun displayState(context: Context): DisplayState {
        val metrics = context.resources.displayMetrics
        return DisplayState(
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            density = metrics.densityDpi,
            refresh = FpsMeter.refreshRate(context)
        )
    }

    /** Real `wm size` override currently active, empty when stock. */
    fun currentOverride(): String = try {
        val out = Shell.exec("wm size") ?: ""
        val override = Regex("Override size:\\s*(\\d+x\\d+)").find(out)?.groupValues?.getOrNull(1)
        override ?: ""
    } catch (_: Throwable) {
        ""
    }

    fun currentDensityOverride(): String = try {
        val out = Shell.exec("wm density") ?: ""
        Regex("Override density:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1) ?: ""
    } catch (_: Throwable) {
        ""
    }

    /**
     * Renders at [percent] of the **physical** panel and lets the compositor upscale.
     *
     * The density is deliberately left untouched: scaling it as well is what made the
     * whole interface shrink instead of the GPU simply doing less work.
     */
    fun setRenderScale(context: Context, percent: Int): Boolean {
        val physical = RenderEngine.physicalMetrics(context)
        val clamped = percent.coerceIn(40, 100)
        if (clamped >= 100) return resetRenderScale()
        // clear any legacy density override first (the 6.4.0 shrink bug)
        Shell.ok("wm density reset")
        val width = even((physical.first * clamped) / 100)
        val height = even((physical.second * clamped) / 100)
        return Shell.ok("wm size ${width}x${height}")
    }

    fun resetRenderScale(): Boolean {
        val size = Shell.ok("wm size reset")
        val density = Shell.ok("wm density reset")
        return size || density
    }

    /**
     * Requests a refresh rate the way the platform actually reads it.
     *
     * `peak_refresh_rate` / `min_refresh_rate` are **floats** ("144.0"). Writing the
     * integer string is silently ignored by a lot of ROMs, which is why "144 Hz" used
     * to do nothing. MIUI also keeps its own `user_refresh_rate`. All three are
     * written, then the result is read back.
     */
    fun setRefreshRate(hz: Int): Boolean {
        val value = hz.coerceIn(48, 240)
        val asFloat = String.format(java.util.Locale.US, "%.1f", value.toFloat())
        var ok = false
        ok = Shell.ok("settings put system peak_refresh_rate $asFloat") || ok
        ok = Shell.ok("settings put system min_refresh_rate $asFloat") || ok
        ok = Shell.ok("settings put system user_refresh_rate $value") || ok
        ok = Shell.ok("settings put global peak_refresh_rate $asFloat") || ok
        // Some ROMs only honour the display-mode switch through the window manager.
        Shell.ok("service call SurfaceFlinger 1035 i32 1")
        return ok && refreshApplied(value)
    }

    /** Reads the requested rate back; true when it survived. */
    fun refreshApplied(hz: Int): Boolean = try {
        val peak = Shell.exec("settings get system peak_refresh_rate")?.trim().orEmpty()
        val min = Shell.exec("settings get system min_refresh_rate")?.trim().orEmpty()
        val request = peak.toDoubleOrNull() ?: min.toDoubleOrNull()
        if (request == null) false else Math.abs(request - hz) < 1.0
    } catch (_: Throwable) {
        false
    }

    /** Every refresh rate this panel can do, from the display manager. */
    fun panelRates(): List<Int> = try {
        val out = Shell.exec("dumpsys display | grep -oE 'fps=[0-9.]+'") ?: ""
        out.lines()
            .mapNotNull { Regex("([0-9.]+)").find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            .map { it.toInt() }
            .filter { it in 30..240 }
            .distinct()
            .sorted()
    } catch (_: Throwable) {
        emptyList()
    }

    /** What the panel is doing right now, for the UI. */
    fun refreshSummary(context: Context): String {
        val current = FpsMeter.refreshRate(context)
        val requested = Shell.exec("settings get system peak_refresh_rate")?.trim().orEmpty()
        val rates = panelRates()
        val max = rates.maxOrNull()
        val req = requested.toDoubleOrNull()?.toInt()
        return buildString {
            append(if (req != null && req > 0) "requested ${req}Hz" else "auto")
            append(" · now ${current}Hz")
            if (max != null) append(" · panel max ${max}Hz")
        }
    }

    /**
     * Kept for source compatibility only.
     *
     * Forcing `debug.hwui.renderer skiavk` globally corrupts rendering on many
     * devices (green / half-black surfaces in unrelated apps) and survives until a
     * reboot. This now always *clears* the override instead of setting it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setVulkanRenderer(on: Boolean): Boolean {
        GfxSafe.clearUnsafeProps()
        return true
    }

    fun rendererStatus(): String = try {
        val out = Shell.exec("getprop debug.hwui.renderer") ?: ""
        out.trim().ifBlank { "default" }
    } catch (_: Throwable) {
        "default"
    }

    /** Sustained-performance + GameMode override: real frame pacing, no thermal spikes. */
    fun setFrameBoost(on: Boolean): Boolean = try {
        var ok = false
        if (on) {
            ok = Shell.ok("cmd power set-fixed-performance-mode-enabled true") || ok
            ok = Shell.ok("cmd game set --mode performance") || ok
            ok = Shell.ok("cmd game set --downscale 1.0") || ok
        } else {
            ok = Shell.ok("cmd power set-fixed-performance-mode-enabled false") || ok
            ok = Shell.ok("cmd game set --mode standard") || ok
        }
        ok
    } catch (_: Throwable) {
        false
    }

    fun frameBoostActive(): Boolean = try {
        val out = Shell.exec("dumpsys power | grep -i 'mFixedPerformanceMode'") ?: ""
        out.contains("true")
    } catch (_: Throwable) {
        false
    }

    /** Thermal headroom the platform reports right now (0 none … 6 shutdown). */
    fun thermalStatus(): Int {
        return try {
            val out = Shell.exec("dumpsys thermalservice | grep -m1 'Thermal Status'") ?: ""
            Regex("Thermal Status:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    /* ------------------------------------------------------------------ */
    /* System-wide graphics pipeline — real props for EVERY app            */
    /* ------------------------------------------------------------------ */

    data class SysGfx(
        val vulkan: Boolean = false,
        val frameBoost: Boolean = false,
        val noVsync: Boolean = false,
        val gameDriver: Boolean = false,
        val gpuComposition: Boolean = true
    )

    /** Reads back what the device is actually running right now. */
    fun systemGfxState(): SysGfx {
        val renderer = Shell.exec("getprop debug.hwui.renderer")?.trim().orEmpty()
        val backend = Shell.exec("getprop debug.renderengine.backend")?.trim().orEmpty()
        val vsync = Shell.exec("getprop debug.hwui.disable_vsync")?.trim().orEmpty()
        val driver = Shell.exec("settings get global game_driver_all_apps")?.trim().orEmpty()
        val sf = Shell.exec("getprop debug.sf.hw")?.trim().orEmpty()
        return SysGfx(
            vulkan = renderer.contains("skiavk", true) || backend.contains("skiavk", true),
            frameBoost = frameBoostActive(),
            noVsync = vsync == "true",
            gameDriver = driver == "1",
            gpuComposition = sf != "0"
        )
    }

    /**
     * Applies the *safe* system switches only.
     *
     * The three prop-based switches of older builds (Skia Vulkan backend, vsync
     * disable, updatable driver) are gone: they are global, survive a reboot and
     * corrupt rendering on a large number of GPUs. Anything they may have left
     * behind is cleared first.
     */
    fun applySystemGfx(opts: SysGfx): List<String> {
        val log = ArrayList<String>()
        if (!Shell.available()) {
            log.add("No shell — grant Shizuku first")
            return log
        }
        val cleared = GfxSafe.clearUnsafeProps()
        if (cleared.isNotEmpty()) log.add("cleared " + cleared.size + " old graphics override(s)")
        val boost = Shell.ok(
            if (opts.frameBoost) "cmd power set-fixed-performance-mode-enabled true && cmd game set --mode performance"
            else "cmd power set-fixed-performance-mode-enabled false && cmd game set --mode standard"
        )
        log.add(if (boost) "Frame pacing ${if (opts.frameBoost) "sustained" else "standard"}" else "Frame pacing unchanged")
        return log
    }

    /**
     * Applies texture / shadow / effects / AA / resolution / fps to the game that
     * is in front of the panel, using that game's own CVars.
     */
    fun applyToForeground(
        context: Context,
        texture: String,
        shadow: String,
        effects: String,
        antiAliasing: String,
        resolution: Int,
        sharpness: Int,
        frameLimit: Int
    ): Pair<String, ApplyResult> {
        val pkg = FpsMeter.foregroundPackage(context)
            ?: currentGamePackage(context)
            ?: ""
        if (pkg.isBlank()) return "" to ApplyResult(emptyList(), emptyList(), !Shell.available())
        val result = applyGameGfx(
            pkg,
            GfxOptions(
                texture = texture,
                shadow = shadow,
                effects = effects,
                antiAliasing = antiAliasing,
                resolution = resolution,
                sharpness = sharpness,
                frameLimit = frameLimit
            )
        )
        return pkg to result
    }

    /** Last launched game, stored by the app when a game is started. */
    fun currentGamePackage(context: Context): String? = try {
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
            .getString("last_game_pkg", null)
    } catch (_: Throwable) {
        null
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1

    /* ------------------------------------------------------------------ */
    /* Per-game texture / shadow quality — real config CVars               */
    /* ------------------------------------------------------------------ */

    private val qualityIndex = mapOf(
        "LOW" to 0,
        "MEDIUM" to 1,
        "HIGH" to 2,
        "ULTRA" to 3,
        "EPIC" to 3
    )

    private fun cvarBlock(options: GfxOptions): String {
        val texture = qualityIndex[options.texture] ?: 2
        val shadow = qualityIndex[options.shadow] ?: 2
        val effects = qualityIndex[options.effects] ?: 2
        val aa = qualityIndex[options.antiAliasing] ?: 2
        val resolution = options.resolution.coerceIn(50, 100)
        return buildString {
            appendLine("; >>> GamersHub graphics (managed block) >>>")
            appendLine("sg.TextureQuality=$texture")
            appendLine("sg.ShadowQuality=$shadow")
            appendLine("sg.EffectsQuality=$effects")
            appendLine("sg.PostProcessQuality=$effects")
            appendLine("sg.AntiAliasingQuality=$aa")
            appendLine("sg.ResolutionQuality=$resolution")
            appendLine("r.Streaming.PoolSize=${if (texture >= 2) 1500 else 800}")
            appendLine("r.Streaming.MaxTempMemoryAllowed=128")
            appendLine("r.ShadowQuality=$shadow")
            appendLine("r.Shadow.MaxResolution=${if (shadow >= 2) 2048 else 1024}")
            appendLine("r.MipMapLODBias=${if (texture >= 2) -1 else 0}")
            val sharpen = options.sharpness.coerceIn(0, 100) / 100f
            appendLine("r.Tonemapper.Sharpen=${String.format(java.util.Locale.US, "%.2f", sharpen)}")
            appendLine("r.Tonemapper.Quality=${if (sharpen > 0.4f) 3 else 1}")
            appendLine("r.Upscale.Quality=${if (sharpen > 0.4f) 3 else 1}")
            if (options.frameLimit > 0) {
                appendLine("FrameRateLimit=${options.frameLimit}")
                appendLine("bUseVSync=False")
            }
            appendLine("; <<< GamersHub graphics <<<")
        }
    }

    /** Candidate config files for the package, discovered through shell. */
    fun configFiles(pkg: String): List<String> {
        val roots = listOf(
            "/sdcard/Android/data/$pkg/files/UE4Game",
            "/sdcard/Android/data/$pkg/files/UnrealGame",
            "/storage/emulated/0/Android/data/$pkg/files/UE4Game"
        )
        val found = LinkedHashSet<String>()
        for (root in roots) {
            val out = Shell.exec("find $root -name GameUserSettings.ini -o -name Engine.ini -o -name UserCustom.ini 2>/dev/null") ?: continue
            out.lines().map { it.trim() }.filter { it.endsWith(".ini") }.forEach { found.add(it) }
            if (found.size >= 6) break
        }
        return found.toList()
    }

    data class ApplyResult(val written: List<String>, val failed: List<String>, val needsShell: Boolean)

    fun applyGameGfx(pkg: String, options: GfxOptions): ApplyResult {
        if (!Shell.available()) return ApplyResult(emptyList(), emptyList(), true)
        val files = configFiles(pkg)
        if (files.isEmpty()) return ApplyResult(emptyList(), emptyList(), false)
        val block = cvarBlock(options)
        // Base64 keeps the payload away from shell quoting entirely.
        val encoded = android.util.Base64.encodeToString(block.toByteArray(), android.util.Base64.NO_WRAP)
        val temp = "/data/local/tmp/gh_gfx_${System.currentTimeMillis()}.txt"
        Shell.exec("echo '$encoded' | base64 -d > $temp")
        val written = ArrayList<String>()
        val failed = ArrayList<String>()
        for (path in files) {
            if (!path.endsWith("GameUserSettings.ini") &&
                !path.endsWith("UserCustom.ini") &&
                !path.endsWith("Engine.ini")
            ) continue
            // One-time backup of the untouched file.
            Shell.exec("cp -n '$path' '$path.ghbak'")
            // Drop the previous managed block, then append the new one.
            Shell.exec(
                "sed -i '/>>> GamersHub graphics (managed block) >>>/,/<<< GamersHub graphics <<</d' '$path'"
            )
            val appended = Shell.ok("cat $temp >> '$path'")
            val check = Shell.exec("grep -c 'sg.TextureQuality' '$path'")?.trim()
            if (appended && (check?.toIntOrNull() ?: 0) > 0) written.add(path) else failed.add(path)
        }
        Shell.exec("rm -f $temp")
        return ApplyResult(written, failed, false)
    }

    fun restoreGameGfx(pkg: String): Int {
        if (!Shell.available()) return 0
        var restored = 0
        for (path in configFiles(pkg)) {
            val backup = "$path.ghbak"
            val ok = Shell.ok("cp -f $backup $path")
            if (ok) restored++
        }
        return restored
    }
}
