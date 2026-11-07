package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell

/**
 * Rendering safety net.
 *
 * Older builds of GamersHub forced global graphics props
 * (`debug.hwui.renderer skiavk`, `debug.renderengine.backend skiavkthreaded`,
 * `debug.hwui.disable_vsync`, `debug.sf.hw`, `game_driver_all_apps`). On a lot of
 * Mali/Adreno devices those props make unrelated apps render garbage — the classic
 * symptom is a **green or half-black surface** in a photo/video editor after using a
 * "booster" app. The props survive until reboot, so the corruption looks random and
 * "sometimes".
 *
 * This object never sets those props again, clears every one of them, and puts the
 * display surface, GameMode and engine configs back to stock. It is idempotent and
 * safe to call at any time.
 */
object GfxSafe {

    /** Props this app is allowed to have touched in the past, and how to clear them. */
    fun clearUnsafeProps(): List<String> {
        if (!Shell.available()) return listOf("Shizuku needed to repair rendering")
        val done = ArrayList<String>()
        val props = listOf(
            "debug.hwui.renderer",
            "debug.renderengine.backend",
            "debug.hwui.disable_vsync",
            "debug.sf.hw",
            "debug.egl.hw",
            "debug.egl.profiler",
            "debug.hwui.skia_tracing",
            "debug.hwui.show_dirty_regions"
        )
        for (prop in props) {
            val current = Shell.exec("getprop $prop")?.trim().orEmpty()
            if (current.isNotBlank()) {
                // empty string = "no debug override", which is the stock behaviour
                Shell.exec("setprop $prop ''")
                val after = Shell.exec("getprop $prop")?.trim().orEmpty()
                done.add("$prop: '$current' → '${after.ifBlank { "stock" }}'")
            }
        }
        if (Shell.ok("settings delete global game_driver_all_apps")) {
            done.add("updatable GPU driver override removed")
        }
        return done
    }

    /** Full repair: props, display surface, GameMode, GL driver selection, engine backups. */
    fun repair(context: Context, pkg: String = ""): List<String> {
        val log = ArrayList<String>()
        if (!Shell.available()) {
            log.add("Shizuku is not available — nothing could be repaired")
            return log
        }
        log.addAll(clearUnsafeProps())
        if (Shell.ok("wm size reset")) log.add("display surface back to native size")
        if (Shell.ok("wm density reset")) log.add("density reset")
        if (Shell.ok("settings delete global angle_gl_driver_selection_pkgs")) {
            log.add("ANGLE driver selection cleared")
        }
        if (Shell.ok("settings put system peak_refresh_rate 0")) log.add("refresh rate override cleared")
        if (Shell.ok("settings put system min_refresh_rate 0")) log.add("min refresh cleared")
        if (GfxEngine.hasGameManager()) {
            val target = pkg.ifBlank { GfxEngine.currentGamePackage(context) ?: "" }
            if (target.isNotBlank()) {
                Shell.ok("cmd game reset $target")
                Shell.ok("cmd game set --mode standard $target")
                Shell.ok("cmd game set --downscale 1.0 $target")
                log.add("game mode reset for ${target.substringAfterLast('.')}")
            }
            Shell.ok("cmd power set-fixed-performance-mode-enabled false")
            log.add("fixed performance mode off")
        }
        val restored = if (pkg.isNotBlank()) GfxEngine.restoreGameGfx(pkg) else 0
        if (restored > 0) log.add("$restored game config file(s) restored from backup")
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).edit()
            .putInt("ax_lossless", 100)
            .putInt("ax_res", 100)
            .putBoolean("ax_vulkan", false)
            .putBoolean("ax_vsync", false)
            .putBoolean("ax_driver", false)
            .apply()
        if (log.isEmpty()) log.add("everything was already stock")
        return log
    }

    /** True when the device still carries a graphics prop one of our old builds set. */
    fun isUnclean(): Boolean {
        if (!Shell.available()) return false
        val renderer = Shell.exec("getprop debug.hwui.renderer")?.trim().orEmpty()
        val backend = Shell.exec("getprop debug.renderengine.backend")?.trim().orEmpty()
        val vsync = Shell.exec("getprop debug.hwui.disable_vsync")?.trim().orEmpty()
        val size = Shell.exec("wm size")?.orEmpty() ?: ""
        return renderer.isNotBlank() || backend.isNotBlank() || vsync.isNotBlank() ||
            size.contains("Override size")
    }

    /** Human readable one-liner for the panel. */
    fun status(): String = when {
        !Shell.available() -> "Shizuku needed"
        isUnclean() -> "leftover graphics override detected — tap REPAIR"
        else -> "rendering clean"
    }
}
