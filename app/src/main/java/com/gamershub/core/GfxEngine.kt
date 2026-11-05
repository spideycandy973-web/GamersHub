package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell
import kotlin.math.roundToInt

/**
 * Real graphics / performance control.
 *
 * Everything in here changes an actual system surface:
 *  - render scale and density: `wm size` + `wm density` (compositor upscales,
 *    the GPU renders fewer pixels — the lossless-scaling path);
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
        val frameLimit: Int = 0           // 0 = unlimited
    )

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

    /** Renders at [percent] of the panel resolution and lets the compositor upscale. */
    fun setRenderScale(context: Context, percent: Int, scaleDensity: Boolean = true): Boolean {
        val state = displayState(context)
        val clamped = percent.coerceIn(40, 100)
        val width = even((state.width * clamped) / 100)
        val height = even((state.height * clamped) / 100)
        var ok = Shell.ok("wm size ${width}x${height}")
        if (scaleDensity) {
            val density = ((state.density * clamped) / 100.0).roundToInt().coerceIn(120, 640)
            ok = Shell.ok("wm density $density") || ok
        }
        return ok
    }

    fun resetRenderScale(): Boolean {
        val size = Shell.ok("wm size reset")
        val density = Shell.ok("wm density reset")
        return size || density
    }

    fun setRefreshRate(hz: Int): Boolean = try {
        val value = hz.coerceIn(48, 240)
        val okSystem = Shell.ok("settings put system peak_refresh_rate $value")
        val okMin = Shell.ok("settings put system min_refresh_rate $value")
        okMin || okSystem
    } catch (_: Throwable) {
        false
    }

    fun setVulkanRenderer(on: Boolean): Boolean = try {
        if (on) {
            Shell.ok("setprop debug.hwui.renderer skiavk") &&
                Shell.ok("setprop debug.renderengine.backend skiavkthreaded")
        } else {
            Shell.ok("setprop debug.hwui.renderer opengl") &&
                Shell.ok("setprop debug.renderengine.backend skiaglthreaded")
        }
    } catch (_: Throwable) {
        false
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
            appendLine("r.Tonemapper.Sharpen=${if (options.effects.contains("HIGH")) "0.6" else "0.0"}")
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
        val written = ArrayList<String>()
        val failed = ArrayList<String>()
        for (path in files) {
            if (!path.endsWith("GameUserSettings.ini") && !path.endsWith("UserCustom.ini") && !path.endsWith("Engine.ini")) continue
            val backup = "$path.ghbak"
            Shell.exec("cp -n $path $backup")
            val current = Shell.exec("cat $path") ?: ""
            val cleaned = current.replace(Regex("; >>> GamersHub graphics.*?; <<< GamersHub graphics <<<\n?", RegexOption.DOT_MATCHES_ALL), "")
            val temp = "/data/local/tmp/gh_gfx.ini"
            val escaped = block.replace("'", "'\\''")
            val write = Shell.exec("printf '%s' '$escaped' > $temp") ?: ""
            if (write.contains("Permission denied")) {
                failed.add(path)
                continue
            }
            val merge = Shell.exec("cat $temp >> $path && cat $path | tail -5")
            if (merge != null && merge.contains("GamersHub graphics", ignoreCase = false)) {
                written.add(path)
            } else if (merge != null && merge.isNotBlank()) {
                written.add(path)
            } else {
                failed.add(path)
            }
        }
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
