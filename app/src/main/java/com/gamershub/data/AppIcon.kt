package com.gamershub.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Launcher icon choice. Three real icon sets ship in the APK; each one is an
 * <activity-alias> entry in the manifest, and exactly one alias is enabled at a time,
 * so the home screen shows the icon the user picked.
 */
object AppIcon {
    private val ALIASES = arrayOf("IconBolt", "IconPad", "IconChevron", "IconGH")
    private const val PREFS = "gamershub"
    private const val KEY = "appIcon"

    const val COUNT = 4

    fun current(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0).coerceIn(0, COUNT - 1)

    fun label(index: Int): String = when (index) {
        1 -> "Pad"
        2 -> "Boost"
        3 -> "Mono"
        else -> "GH"
    }

    /** Enables [index] first, then disables the others, so the launcher never shows a gap. */
    fun set(ctx: Context, index: Int) {
        val i = index.coerceIn(0, COUNT - 1)
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg.${ALIASES[i]}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            ALIASES.forEachIndexed { k, alias ->
                if (k != i) pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.$alias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, i).apply()
    }
}
