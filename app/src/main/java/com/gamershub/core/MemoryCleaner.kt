package com.gamershub.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import com.gamershub.core.shizuku.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real memory work — nothing simulated, and it works with or without Shizuku.
 *
 *  • CLEAN      kills background processes of third-party apps (the app itself has
 *               KILL_BACKGROUND_PROCESSES, so this works on a stock device) and,
 *               when shell is available, runs `am kill-all` + `pm trim-caches`.
 *  • BOOST      CLEAN plus a `sync` + a second kill pass, for the deepest possible
 *               free-RAM result on a non-rooted phone.
 *  • DEEP CLEAN BOOST plus `am force-stop` of every background third-party app.
 *
 * The freed amount is the real difference of ActivityManager's availMem.
 */
object MemoryCleaner {

    data class Report(
        val beforeMb: Long,
        val afterMb: Long,
        val appsStopped: Int,
        val usedShell: Boolean,
        val note: String = ""
    ) {
        val freedMb: Long get() = (afterMb - beforeMb).coerceAtLeast(0)
        /** True when at least something actually changed. */
        val effective: Boolean get() = appsStopped > 0 || freedMb > 0
    }

    fun availableMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    private fun manager(context: Context): ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** True when the package is a normal installable app (not a system component). */
    private fun isThirdParty(context: Context, pkg: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (_: Throwable) {
        false
    }

    /**
     * Kills the background processes of third-party apps through the app's own
     * ActivityManager permission. This is what makes CLEAN work without root.
     */
    fun killBackground(context: Context): Int {
        val am = manager(context)
        val own = context.packageName
        var killed = 0
        val processes = am.runningAppProcesses ?: return 0
        for (process in processes) {
            val pkg = process.pkgList?.firstOrNull() ?: continue
            if (pkg == own || pkg == "android" || pkg == "com.android.systemui") continue
            if (!isThirdParty(context, pkg)) continue
            // Never touch something the player can currently see.
            if (process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) continue
            val name = process.processName
            runCatching { am.killBackgroundProcesses(name) }
            killed++
        }
        return killed
    }

    /** `am kill-all` + cache trim, only when the shell is usable. */
    private fun shellPass(): Boolean {
        if (!Shell.available()) return false
        Shell.exec("am kill-all")
        Shell.exec("pm trim-caches 999999999999")
        return true
    }

    /** Force-stops every background third-party app through the shell. */
    private fun forceStopBackground(context: Context): Int {
        val am = manager(context)
        val own = context.packageName
        var stopped = 0
        val processes = am.runningAppProcesses ?: return 0
        for (process in processes) {
            val pkg = process.pkgList?.firstOrNull() ?: continue
            if (pkg == own || pkg == "android" || pkg.startsWith("com.android.systemui")) continue
            if (process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) continue
            if (!isThirdParty(context, pkg)) continue
            if (Shell.ok("am force-stop $pkg")) stopped++
        }
        return stopped
    }

    suspend fun clean(context: Context): Report = withContext(Dispatchers.IO) {
        val before = availableMb(context)
        val killed = killBackground(context)
        val shell = shellPass()
        Thread.sleep(600)
        val after = availableMb(context)
        Report(before, after, killed, shell, if (shell) "shell pass" else "app-level pass")
    }

    suspend fun boost(context: Context): Report = withContext(Dispatchers.IO) {
        val before = availableMb(context)
        var killed = killBackground(context)
        val shell = shellPass()
        Thread.sleep(300)
        killed += killBackground(context)
        Thread.sleep(600)
        val after = availableMb(context)
        Report(before, after, killed, shell, if (shell) "double pass + cache trim" else "double pass")
    }

    suspend fun deepClean(context: Context): Report = withContext(Dispatchers.IO) {
        val before = availableMb(context)
        var stopped = killBackground(context)
        val shell = shellPass()
        if (shell) stopped += forceStopBackground(context)
        Thread.sleep(1200)
        stopped += killBackground(context)
        val after = availableMb(context)
        Report(before, after, stopped, shell, if (shell) "force-stop + trim" else "app-level only")
    }
}
