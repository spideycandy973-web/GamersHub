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

/** Installed launchable apps + local GamersHub state (added set, energy, play time). */
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

    fun energy(): Int = prefs.getInt("energy", 0)
    fun addEnergy(delta: Int) = prefs.edit().putInt("energy", (energy() + delta).coerceAtLeast(0)).apply()
    fun premium(): Boolean = prefs.getBoolean("premium", false)
    fun setPremium(v: Boolean) = prefs.edit().putBoolean("premium", v).apply()

    fun added(): Set<String> = prefs.getStringSet("added", emptySet()) ?: emptySet()
    fun isAdded(pkg: String): Boolean = added().contains(pkg)
    fun setAdded(pkg: String, value: Boolean) {
        val cur = added().toMutableSet()
        if (value) cur.add(pkg) else cur.remove(pkg)
        prefs.edit().putStringSet("added", cur).apply()
    }

    fun playMinutes(pkg: String): Long = prefs.getLong("play_$pkg", 0) / 60
    fun addPlaySeconds(pkg: String, seconds: Long) {
        if (seconds <= 0) return
        prefs.edit().putLong("play_$pkg", prefs.getLong("play_$pkg", 0) + seconds).apply()
    }

    fun toggle(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun setToggle(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun firstRunDone(): Boolean = prefs.getBoolean("first_run", false)
    fun setFirstRunDone() = prefs.edit().putBoolean("first_run", true).apply()

    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo?.packageName != context.packageName }
            .map { it.activityInfo.packageName to pm.getApplicationLabel(it.activityInfo.applicationInfo).toString() }
            .distinctBy { it.first }
            .map { (pkg, label) -> InstalledApp(label, pkg, iconBitmap(pkg)) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun launch(pkg: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else false
    } catch (_: Throwable) {
        false
    }

    private fun iconBitmap(pkg: String): ImageBitmap {
        val drawable: Drawable = try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (_: Throwable) {
            context.packageManager.defaultActivityIcon
        }
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp.asImageBitmap()
    }
}
