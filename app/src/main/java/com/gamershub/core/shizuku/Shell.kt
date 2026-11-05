package com.gamershub.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/**
 * Shizuku-backed shell with a real connection state machine.
 *
 * - [ensure] binds the user service as soon as the permission exists and is
 *   safe to call on every resume (it never binds twice).
 * - [requestPermission] can only fire once per process, so the Shizuku dialog
 *   is never shown in a loop to a user that already granted access.
 * - [exec] / [ok] self-heal: if the binder dropped (Shizuku restarted) they
 *   rebind with the stored application context before giving up.
 */
object Shell {

    enum class Status { NOT_RUNNING, NO_PERMISSION, BINDING, CONNECTED }

    @Volatile
    private var binder: IGamersHubShell? = null

    @Volatile
    private var binding = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var requestedOnce = false

    @Volatile
    var lastError: String = ""

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = IGamersHubShell.Stub.asInterface(service)
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun connected(): Boolean = binder != null

    fun status(): Status = when {
        !running() -> Status.NOT_RUNNING
        !granted() -> Status.NO_PERMISSION
        binder != null -> Status.CONNECTED
        else -> Status.BINDING
    }

    fun statusLabel(): String = when (status()) {
        Status.CONNECTED -> "Connected"
        Status.BINDING -> "Connecting"
        Status.NO_PERMISSION -> "Permission needed"
        Status.NOT_RUNNING -> "Shizuku not running"
    }

    /** Binds the user service when possible. Safe to call on every resume. */
    fun ensure(context: Context): Boolean {
        appContext = context.applicationContext
        if (!granted()) return false
        if (binder != null) return true
        if (binding) return false
        binding = true
        return try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, GamersHubShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(3)
            Shizuku.bindUserService(args, conn)
            true
        } catch (t: Throwable) {
            binding = false
            lastError = t.message ?: "bind failed"
            false
        }
    }

    fun rebind(context: Context) {
        binder = null
        binding = false
        ensure(context)
    }

    /**
     * Requests the runtime Shizuku grant. Returns true only when a prompt was
     * actually raised; calling it again in the same process is a no-op so the
     * user is never nagged after granting.
     */
    fun requestPermission(requestCode: Int = 7331): Boolean {
        if (!running() || granted()) return false
        if (requestedOnce) return false
        requestedOnce = true
        return try {
            Shizuku.requestPermission(requestCode)
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "request failed"
            false
        }
    }

    private fun autoBind() {
        val context = appContext ?: return
        if (granted() && binder == null && !binding) ensure(context)
    }

    /** Execute as shell; returns combined output (null when unavailable). */
    fun exec(command: String): String? {
        if (binder == null) autoBind()
        return try {
            binder?.exec(command)
        } catch (t: Throwable) {
            lastError = t.message ?: "exec failed"
            null
        }
    }

    /** Execute and report success (exit code 0 parsed from output). */
    fun ok(command: String): Boolean {
        val out = exec(command) ?: return false
        val match = Regex("__GH_EXIT__=(-?\\d+)").find(out)
        return match == null || match.groupValues[1] == "0"
    }

    /** True when privileged commands can actually run right now. */
    fun available(): Boolean = binder != null || granted()

    /** Last resort: plain app-side exec (no privileges). */
    fun appExec(command: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = process.inputStream.bufferedReader().readText() +
            process.errorStream.bufferedReader().readText()
        process.waitFor()
        out
    } catch (t: Throwable) {
        t.message ?: ""
    }

    /** Releases the binding (used by the activation sheet's Reconnect button). */
    fun disconnect() {
        binder = null
        binding = false
    }
}
