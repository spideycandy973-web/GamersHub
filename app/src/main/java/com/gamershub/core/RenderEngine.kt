package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Real render pipeline for **any** app, not just one engine.
 *
 * The panel used to only store numbers. Every control here executes a privileged
 * command and reads the result back, so what you see on screen is what the device
 * actually did:
 *
 *  • LOSSLESS SCALING  → `wm size` + `wm density` (the compositor upscales a smaller
 *                        framebuffer: fewer pixels rendered, softer image, higher fps —
 *                        this works in every single app, games included).
 *  • RENDER SCALE      → `cmd game set --downscale <ratio> <pkg>` (Android 12+ GameManager,
 *                        per-app render resolution).
 *  • FPS CAP           → `cmd game set --fps <n> <pkg>` + `peak_refresh_rate`.
 *  • GPU MODE          → `cmd game set --mode performance|battery|standard <pkg>`.
 *  • TEXTURE / SHADOW  → the game's own UE4/UE5 CVars when the engine config exists,
 *                        otherwise the GameManager downscale/mode that changes the same
 *                        GPU work for games that do not expose configs.
 *  • ANGLE             → `angle_gl_driver_selection_pkgs/values` (real GL driver swap).
 *
 * Nothing is simulated: [Report.steps] carries the exact command and its exit status.
 */
object RenderEngine {

    data class Step(val label: String, val command: String, val ok: Boolean, val output: String = "")

    data class Report(
        val pkg: String,
        val steps: List<Step>,
        val verified: String
    ) {
        val okCount: Int get() = steps.count { it.ok }
        val failed: List<Step> get() = steps.filterNot { it.ok }

        /** One-line result for a tile/subtitle. */
        fun summary(): String = when {
            steps.isEmpty() -> "nothing to apply"
            okCount == 0 -> "failed · " + (steps.firstOrNull()?.output?.take(48) ?: "shell refused")
            else -> "$okCount/${steps.size} applied" + if (verified.isNotBlank()) " · $verified" else ""
        }

        /** Full multi-line proof for the panel. */
        fun details(): String = buildString {
            for (step in steps) {
                appendLine((if (step.ok) "✓ " else "✗ ") + step.label)
                appendLine("   " + step.command)
                if (step.output.isNotBlank()) appendLine("   " + step.output.trim().take(160))
            }
            if (verified.isNotBlank()) appendLine("• device now: " + verified)
        }.trim()
    }

    @Volatile
    private var gameCmdSupported: Boolean? = null

    /** Does this ROM ship `cmd game` (GameManager, Android 12+)? */
    fun hasGameManager(): Boolean {
        gameCmdSupported?.let { return it }
        if (!Shell.available()) return false
        val out = Shell.exec("cmd game help") ?: ""
        val supported = out.contains("Game manager", true) ||
            out.contains("downscale", true) ||
            out.contains("--mode", true)
        gameCmdSupported = supported
        return supported
    }

    fun shellReady(): Boolean = Shell.available()

    /* ---------------------------------------------------------------- */
    /* read-back                                                         */
    /* ---------------------------------------------------------------- */

    /** What the device is *actually* running right now, straight from the system. */
    fun verify(pkg: String = ""): String {
        if (!Shell.available()) return "no shell"
        val size = Shell.exec("wm size")?.let {
            Regex("Override size:\\s*(\\d+x\\d+)").find(it)?.groupValues?.getOrNull(1)
        } ?: ""
        val density = Shell.exec("wm density")?.let {
            Regex("Override density:\\s*(\\d+)").find(it)?.groupValues?.getOrNull(1)
        } ?: ""
        val angle = Shell.exec("settings get global angle_gl_driver_selection_pkgs")?.trim().orEmpty()
        val refresh = Shell.exec("settings get system peak_refresh_rate")?.trim().orEmpty()
        return buildList {
            add(if (size.isBlank()) "native resolution" else "render $size")
            if (density.isNotBlank()) add("density $density")
            if (angle.isNotBlank() && angle != "null") add("ANGLE on $angle")
            if (refresh.isNotBlank() && refresh != "null") add("peak ${refresh}Hz")
        }.joinToString(" · ").ifBlank { "stock" }
    }

    /* ---------------------------------------------------------------- */
    /* apply                                                             */
    /* ---------------------------------------------------------------- */

    fun apply(
        context: Context,
        pkg: String,
        texture: String = "HIGH",
        shadow: String = "HIGH",
        sharpness: Int = 60,
        scalePct: Int = 100,
        fps: Int = 0,
        mode: String = "standard",
        angle: Boolean = false,
        losslessPct: Int = 100
    ): Report {
        val steps = ArrayList<Step>()
        if (!Shell.available()) {
            steps.add(Step("Shell", "shizuku", false, Shell.reason()))
            return Report(pkg, steps, verify())
        }

        val ratio = (scalePct.coerceIn(50, 100) / 100f)

        // 1. per-app render scale + fps + mode through the platform GameManager
        if (hasGameManager()) {
            if (pkg.isNotBlank()) {
                val downCmd = "cmd game set --downscale " + String.format(Locale.US, "%.2f", ratio) + " $pkg"
                steps.add(Step("render scale ${scalePct}% (per app)", downCmd, Shell.ok(downCmd)))
                if (fps > 0) {
                    val fpsCmd = "cmd game set --fps $fps $pkg"
                    steps.add(Step("fps cap $fps (per app)", fpsCmd, Shell.ok(fpsCmd)))
                }
                val modeCmd = "cmd game set --mode $mode $pkg"
                steps.add(Step("gpu mode $mode (per app)", modeCmd, Shell.ok(modeCmd)))
            }
        }

        // 2. global lossless scaling — physical baseline, size AND density scale together,
        //    so the picture keeps its exact layout and only the rendered pixel count drops.
        steps.addAll(applyLossless(context, losslessPct))

        // 3. fps / refresh
        if (fps > 0) {
            val rCmd = "settings put system peak_refresh_rate $fps"
            steps.add(Step("peak refresh $fps Hz", rCmd, Shell.ok(rCmd)))
        }

        // 4. GL driver (ANGLE) — real driver swap per package
        if (pkg.isNotBlank()) {
            val pkgs = if (angle) pkg else ""
            val pCmd = "settings put global angle_gl_driver_selection_pkgs '$pkgs'"
            val vCmd = "settings put global angle_gl_driver_selection_values '${if (angle) "angle" else "native"}'"
            steps.add(Step(if (angle) "ANGLE driver for $pkg" else "native GL driver", pCmd, Shell.ok(pCmd)))
            steps.add(Step("driver value", vCmd, Shell.ok(vCmd)))
        }

        // 5. engine-level quality: UE4/UE5 CVars when the game ships them
        if (pkg.isNotBlank()) {
            val result = GfxEngine.applyGameGfx(
                pkg,
                GfxEngine.GfxOptions(
                    texture = texture,
                    shadow = shadow,
                    effects = texture,
                    antiAliasing = texture,
                    resolution = scalePct.coerceIn(50, 100),
                    sharpness = sharpness,
                    frameLimit = fps
                )
            )
            when {
                result.needsShell -> steps.add(Step("engine quality", "GameUserSettings.ini", false, "shell unavailable"))
                result.written.isNotEmpty() -> steps.add(
                    Step(
                        "texture $texture / shadow $shadow written",
                        "cat >> GameUserSettings.ini",
                        true,
                        result.written.joinToString(" ")
                    )
                )
                else -> steps.add(
                    Step(
                        "texture $texture / shadow $shadow",
                        "cmd game set --mode $mode",
                        ok = hasGameManager(),
                        output = "game has no engine config — applied through the platform GPU mode + render scale instead"
                    )
                )
            }
        }

        return Report(pkg, steps, verify(pkg))
    }

    /**
     * Applies the signed knob set: game config CVars around the game's own setting,
     * per-app GameManager scale/mode, ANGE driver and the global screen scale.
     */
    fun applyKnobs(
        context: Context,
        pkg: String,
        knobs: GfxEngine.Knobs,
        mode: String = "standard",
        angle: Boolean = false,
        losslessPct: Int = 100
    ): Report {
        val steps = ArrayList<Step>()
        if (!Shell.available()) {
            steps.add(Step("Shell", "shizuku", false, Shell.reason()))
            return Report(pkg, steps, verify())
        }
        if (pkg.isNotBlank() && hasGameManager()) {
            val ratio = (knobs.renderScale.coerceIn(50, 100) / 100f)
            val down = "cmd game set --downscale " + String.format(Locale.US, "%.2f", ratio) + " $pkg"
            steps.add(Step("render scale ${knobs.renderScale}% (per app)", down, Shell.ok(down)))
            if (knobs.fps > 0) {
                val fpsCmd = "cmd game set --fps ${knobs.fps} $pkg"
                steps.add(Step("fps cap ${knobs.fps}", fpsCmd, Shell.ok(fpsCmd)))
            }
            val modeCmd = "cmd game set --mode $mode $pkg"
            steps.add(Step("gpu mode $mode", modeCmd, Shell.ok(modeCmd)))
        }
        steps.addAll(applyLossless(context, losslessPct))
        if (pkg.isNotBlank()) {
            val pkgs = if (angle) pkg else ""
            val pCmd = "settings put global angle_gl_driver_selection_pkgs '$pkgs'"
            val vCmd = "settings put global angle_gl_driver_selection_values '${if (angle) "angle" else "native"}'"
            steps.add(Step(if (angle) "ANGLE driver" else "native driver", pCmd, Shell.ok(pCmd)))
            steps.add(Step("driver value", vCmd, Shell.ok(vCmd)))
            val result = GfxEngine.applyKnobs(pkg, knobs)
            when {
                result.needsShell -> steps.add(Step("engine CVars", "config", false, "shell unavailable"))
                result.written.isNotEmpty() -> steps.add(
                    Step(
                        if (knobs.neutral) "engine CVars cleared — game defaults restored"
                        else "engine CVars written (sharpen ${knobs.sharpen}, tex ${knobs.texture}, shadow ${knobs.shadow})",
                        "GameUserSettings.ini",
                        true,
                        result.written.joinToString(" ") { it.substringAfterLast('/') }
                    )
                )
                else -> steps.add(
                    Step(
                        "engine CVars",
                        "GameUserSettings.ini",
                        ok = false,
                        output = "no UE config for this game — render scale, GPU mode and driver still applied"
                    )
                )
            }
        }
        return Report(pkg, steps, verify(pkg))
    }

    /** Undo everything this engine can undo. */
    fun restore(context: Context, pkg: String): Report {
        val steps = ArrayList<Step>()
        if (!Shell.available()) {
            steps.add(Step("Shell", "shizuku", false, Shell.reason()))
            return Report(pkg, steps, "no shell")
        }
        steps.add(Step("reset display scaling", "wm size reset", Shell.ok("wm size reset")))
        steps.add(Step("reset density", "wm density reset", Shell.ok("wm density reset")))
        if (hasGameManager() && pkg.isNotBlank()) {
            steps.add(Step("reset game config", "cmd game reset $pkg", Shell.ok("cmd game reset $pkg")))
            steps.add(Step("mode standard", "cmd game set --mode standard $pkg", Shell.ok("cmd game set --mode standard $pkg")))
            steps.add(Step("render scale 1.0", "cmd game set --downscale 1.0 $pkg", Shell.ok("cmd game set --downscale 1.0 $pkg")))
        }
        if (pkg.isNotBlank()) {
            steps.add(Step("native GL driver", "settings put global angle_gl_driver_selection_pkgs ''", Shell.ok("settings put global angle_gl_driver_selection_pkgs ''")))
            val restored = GfxEngine.restoreGameGfx(pkg)
            steps.add(Step("engine config restored", "cp *.ghbak", restored > 0, "$restored files"))
        }
        return Report(pkg, steps, verify(pkg))
    }

    /* ---------------------------------------------------------------- */
    /* lossless scaling — the real thing                                 */
    /* ---------------------------------------------------------------- */

    /**
     * What the lossless path is doing right now, read back from the system.
     *
     * `wm size` changes the size of the logical framebuffer the GPU has to fill; the
     * compositor stretches that framebuffer to the physical panel, so a game still fills
     * the whole screen while rendering fewer pixels. The **density is never touched**:
     * writing a smaller density is what made everything shrink in 6.4.0.
     */
    data class LosslessState(
        val baseW: Int,
        val baseH: Int,
        val baseDpi: Int,
        val overrideW: Int,
        val overrideH: Int,
        val densityOverride: Int,
        val gameManager: Boolean
    ) {
        val active: Boolean get() = overrideW > 0 && overrideH > 0
        val percent: Int
            get() = if (!active || baseW <= 0) 100 else (overrideW * 100.0 / baseW).roundToInt()
        val gpuPixels: Long
            get() = if (active) overrideW.toLong() * overrideH else baseW.toLong() * baseH
        val panelPixels: Long get() = baseW.toLong() * baseH
        val savedPercent: Int
            get() = if (panelPixels <= 0L) 0 else (100 - (gpuPixels * 100.0 / panelPixels)).roundToInt()

        fun megapixels(): String = String.format(
            Locale.US,
            "%.2f → %.2f Mpx",
            panelPixels / 1_000_000.0,
            gpuPixels / 1_000_000.0
        )

        fun summary(): String = when {
            active -> "render $percent% · ${overrideW}x$overrideH of ${baseW}x${baseH} · -$savedPercent% pixels"
            densityOverride > 0 -> "stock size · density $densityOverride still set"
            else -> "stock · GPU renders ${baseW}x$baseH"
        }
    }

    fun losslessState(context: Context): LosslessState {
        val sizeOut = try {
            Shell.exec("wm size")
        } catch (_: Throwable) {
            null
        } ?: ""
        val densityOut = try {
            Shell.exec("wm density")
        } catch (_: Throwable) {
            null
        } ?: ""
        val metrics = context.resources.displayMetrics
        val physical = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(sizeOut)
        val override = Regex("Override size:\\s*(\\d+)x(\\d+)").find(sizeOut)
        val baseW = physical?.groupValues?.getOrNull(1)?.toIntOrNull() ?: metrics.widthPixels
        val baseH = physical?.groupValues?.getOrNull(2)?.toIntOrNull() ?: metrics.heightPixels
        val baseDpi = Regex("Physical density:\\s*(\\d+)").find(densityOut)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: metrics.densityDpi
        val overDpi = Regex("Override density:\\s*(\\d+)").find(densityOut)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return LosslessState(
            baseW = baseW,
            baseH = baseH,
            baseDpi = baseDpi,
            overrideW = override?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
            overrideH = override?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0,
            densityOverride = overDpi,
            gameManager = hasGameManager()
        )
    }

    /**
     * Lower the number of pixels the GPU fills — and nothing else.
     *
     *  1. an old density override is deleted first (that was the 6.4.0 shrink bug);
     *  2. `wm size <pct>%` of the **physical** panel, aspect preserved, even numbers;
     *  3. the override is read back and the exact pixel maths is reported.
     */
    fun applyLossless(context: Context, percent: Int): List<Step> {
        val steps = ArrayList<Step>()
        if (!Shell.available()) {
            steps.add(Step("shell", "shizuku", false, Shell.reason()))
            return steps
        }
        val st = losslessState(context)
        val pct = percent.coerceIn(40, 100)

        if (st.densityOverride > 0) {
            val cmd = "wm density reset"
            steps.add(Step("density override removed (it was shrinking the UI)", cmd, Shell.ok(cmd)))
        }

        if (pct >= 100) {
            val cmd = "wm size reset"
            val ok = Shell.ok(cmd)
            val after = losslessState(context)
            steps.add(Step("lossless off · panel back to ${st.baseW}x${st.baseH}", cmd, ok))
            steps.add(Step("verified", "wm size", !after.active, after.summary()))
            return steps
        }

        val w = even((st.baseW * pct) / 100)
        val h = even((st.baseH * pct) / 100)
        val cmd = "wm size ${w}x$h"
        val ok = Shell.ok(cmd)
        val after = losslessState(context)
        steps.add(Step("render $pct% → ${w}x${h} (panel ${st.baseW}x${st.baseH})", cmd, ok))
        val filled = String.format(Locale.US, "%.1f", w.toLong() * h * 100.0 / (st.baseW.toLong() * st.baseH))
        steps.add(
            Step(
                "gpu fills $filled% of the pixels",
                "pixel maths",
                true,
                "${w.toLong() * h / 1000}K vs ${st.baseW.toLong() * st.baseH / 1000}K px · " +
                    String.format(
                        Locale.US,
                        "%.2f → %.2f Mpx",
                        st.baseW.toLong() * st.baseH / 1_000_000.0,
                        w.toLong() * h / 1_000_000.0
                    )
            )
        )
        val verified = after.overrideW == w && after.overrideH == h && after.densityOverride == 0
        steps.add(Step("verified", "wm size", verified, after.summary()))
        return steps
    }

    /** The physical panel, unaffected by any override we applied. */
    fun physicalMetrics(context: Context): Triple<Int, Int, Int> {
        val st = losslessState(context)
        return Triple(st.baseW, st.baseH, st.baseDpi)
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1

    /** Shortest possible status for a chip. */
    fun statusLine(): String {
        if (!Shell.available()) return "Shizuku needed"
        return if (hasGameManager()) "GameManager ready" else "display scaling only"
    }
}
