package com.gamershub.core

import android.app.ActivityManager
import android.content.Context
import com.gamershub.core.shizuku.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real memory work — nothing simulated.
 *
 *  • CLEAN      → `am kill-all` + `pm trim-caches` (shell identity)
 *  • BOOST      → the above plus cache drop (root) and a measured result
 *  • DEEP CLEAN → also force-stops background third-party apps that keep
 *                 resident processes alive, then re-measures.
 *
 * The freed amount is the difference of ActivityManager's real availMem.
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
    }

    fun availableMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    suspend fun clean(context: Context): Report = withContext(Dispatchers.IO) {
        val before = availableMb(context)
        val shell = Shell.available()
        Shell.exec("am kill-all")
        Shell.exec("pm trim-caches 999999999999")
        Thread.sleep(700)
        val after = availableMb(context)
        Report(before, after, 0, shell)
    }

    suspend fun boost(context: Context): Report = withContext(Dispatchers.IO) {
        val before = availableMb(context)
        val shell = Shell.available()
        Shell.exec("am kill-all")
        Shell.exec("pm trim-caches 999999999999")
        // Root only: drop the page cache too.
        Shell.exec("su -c 'sync; echo 3 > /proc/sys/vm/drop_caches'")
        Thread.sleep(900)
        val after = availableMb(context)
        Report(before, after, 0, shell, "cache drop requested")
    }

    suspend fun deepClean(context: Context): Report = withContext(Dispatchers.IO) {
        val before = availableMb(context)
        val shell = Shell.available()
        val stopped = stopBackgroundApps(context)
        Shell.exec("am kill-all")
        Shell.exec("pm trim-caches 999999999999")
        Shell.exec("su -c 'sync; echo 3 > /proc/sys/vm/drop_caches'")
        Thread.sleep(1200)
        val after = availableMb(context)
        Report(before, after, stopped, shell, "background apps force-stopped")
    }

    private fun stopBackgroundApps(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val own = context.packageName
        val processes = am.runningAppProcesses ?: return 0
        var stopped = 0
        for (process in processes) {
            val pkg = process.pkgList?.firstOrNull() ?: continue
            if (pkg == own || pkg == "android" || pkg.startsWith("com.android.systemui")) continue
            // Only third-party apps that are fully in the background — never the
            // app the player is looking at.
            val importance = process.importance
            if (importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) continue
            if (!isThirdParty(context, pkg)) continue
            if (Shell.ok("am force-stop $pkg")) stopped++
        }
        return stopped
    }

    private fun isThirdParty(context: Context, pkg: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (_: Throwable) {
        false
    }
}
