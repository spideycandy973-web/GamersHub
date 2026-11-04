package com.gamershub.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder

/**
 * Shizuku-backed shell. Binds a user service that runs with shell (adb)
 * identity, so every control in the app can do REAL system work:
 * settings writes, am kill, wm size/density, screencap, drop_caches…
 */
object Shell {

    @Volatile
    private var binder: IGamersHubShell? = null

    @Volatile
    private var binding = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = IGamersHubShell.Stub.asInterface(service)
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    fun available(): Boolean = try {
        rikka.shizuku.Shizuku.pingBinder() &&
            rikka.shizuku.Shizuku.checkSelfPermission() ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun ensure(context: Context) {
        if (binder != null || binding || !available()) return
        binding = true
        try {
            val args = rikka.shizuku.Shizuku.UserServiceArgs(
                ComponentName(context.packageName, GamersHubShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(2)
            rikka.shizuku.Shizuku.bindUserService(args, conn)
        } catch (_: Throwable) {
            binding = false
        }
    }

    /** Execute as shell; returns combined output (null when unavailable). */
    fun exec(command: String): String? = try {
        binder?.exec(command)
    } catch (_: Throwable) {
        null
    }

    /** Execute and report success (exit code 0 parsed from output). */
    fun ok(command: String): Boolean {
        val out = exec(command) ?: return false
        val m = Regex("__GH_EXIT__=(-?\\d+)").find(out)
        return m == null || m.groupValues[1] == "0"
    }

    /** Last resort: plain app-side exec (no privileges). */
    fun appExec(command: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (t: Throwable) {
        t.message ?: ""
    }
}
