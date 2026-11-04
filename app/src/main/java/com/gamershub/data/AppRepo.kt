package com.gamershub.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class InstalledApp(val name: String, val pkg: String, val icon: ImageBitmap)

/** Installed launchable apps + local state (added set, play time, toggles, energy). */
class AppRepo(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)

    fun userId(): String {
        var id = prefs.getString("uid", null)
        if (id == null) {
            id = (10000000..99999999).random().toString()
            prefs.edit().putString("uid", id).apply()
        }
        return id
    }

    // ---- library ----
    fun added(): Set<String> = prefs.getStringSet("added", emptySet()) ?: emptySet()

    fun setAdded(pkg: String, on: Boolean) {
        val cur = added().toMutableSet()
        if (on) cur.add(pkg) else cur.remove(pkg)
        prefs.edit().putStringSet("added", cur).apply()
    }

    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            try {
                val name = pm.getApplicationLabel(ri.activityInfo.applicationInfo).toString()
                InstalledApp(name, pkg, iconBitmap(ri.activityInfo.applicationInfo))
            } catch (_: Throwable) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun iconBitmap(info: android.content.pm.ApplicationInfo): ImageBitmap {
        val bmp = Bitmap.createBitmap(148, 148, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        try {
            val d: Drawable = context.packageManager.getApplicationIcon(info)
            d.setBounds(0, 0, 148, 148)
            d.draw(canvas)
        } catch (_: Throwable) {
        }
        return bmp.asImageBitmap()
    }

    fun playMinutes(pkg: String): Long = prefs.getLong("play_$pkg", 0L)

    // ---- energy economy (simulated — no real ads or billing) ----
    fun energy(): Int = prefs.getInt("energy", 0)

    fun setEnergy(v: Int) {
        prefs.edit().putInt("energy", v.coerceIn(0, 560)).apply()
    }

    fun addEnergy(v: Int) = setEnergy(energy() + v)

    /** +10 energy per minute of real time, capped at 560. */
    fun tickEnergy() {
        val last = prefs.getLong("energy_ts", 0L)
        val now = System.currentTimeMillis()
        if (last > 0) {
            val gained = ((now - last) / 60000L).toInt() * 10
            if (gained > 0) setEnergy(energy() + gained)
        }
        prefs.edit().putLong("energy_ts", now).apply()
    }

    fun premium(): Boolean = prefs.getBoolean("premium", false)

    fun setPremium(v: Boolean) {
        prefs.edit().putBoolean("premium", v).apply()
    }

    // ---- toggles / accent / first run ----
    fun toggle(key: String): Boolean = prefs.getBoolean("t_$key", false)

    fun setToggle(key: String, v: Boolean) {
        prefs.edit().putBoolean("t_$key", v).apply()
    }

    fun accentIndex(): Int = prefs.getInt("accent", 0)

    fun setAccentIndex(i: Int) {
        prefs.edit().putInt("accent", i).apply()
    }

    fun firstRunDone(): Boolean = prefs.getBoolean("first_run", false)

    fun setFirstRunDone() {
        prefs.edit().putBoolean("first_run", true).apply()
    }

    fun launch(pkg: String) {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        }
    }
}
