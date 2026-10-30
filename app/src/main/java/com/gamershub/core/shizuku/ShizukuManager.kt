package com.gamershub.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.gamershub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Central gateway to every privileged operation in GamersHub.
 *
 * Uses the official Shizuku user-service API. The [GamersHubShellService] AIDL stub
 * runs inside the Shizuku server process, so shell commands execute as
 * shell (UID 2000, started through adb / wireless debugging) or root (UID 0, Sui /
 * rooted Shizuku). No root is required on the device itself.
 */
class ShizukuManager(private val context: Context) {

    enum class Status {
        /** Shizuku (or Sui) is not installed on this device. */
        NOT_INSTALLED,
        /** Installed but the Shizuku service is not running. */
        NOT_RUNNING,
        /** Running but the user has not granted GamersHub permission yet. */
        PERMISSION_NEEDED,
        /** Binder alive + permission granted. All systems go. */
        READY
    }

    data class ShellResult(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
        val trimmed: String get() = output.trim()
    }

    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** True when the backing identity is root (Sui / rooted Shizuku). */
    private val _isRoot = MutableStateFlow(false)
    val isRoot: StateFlow<Boolean> = _isRoot.asStateFlow()

    /** True while the AIDL user service is bound and usable. */
    private val _shellReady = MutableStateFlow(false)
    val shellReady: StateFlow<Boolean> = _shellReady.asStateFlow()

    private val hasPermission get() =
        Shizuku.isPreV11() ||
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    @Volatile
    private var shell: IGamersHubShell? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, GamersHubShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("gh_shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                shell = IGamersHubShell.Stub.asInterface(binder)
                _shellReady.value = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shell = null
            _shellReady.value = false
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshState() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _shellReady.value = false
        shell = null
        refreshState()
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grant ->
        if (grant == PackageManager.PERMISSION_GRANTED) refreshState() else refreshState()
    }

    fun init() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        refreshState()
    }

    /** Re-evaluates [Status] and (re)binds the user service if possible. */
    fun refreshState() {
        val installed = isShizukuInstalled()
        if (!installed) {
            _status.value = Status.NOT_INSTALLED
            return
        }
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            _status.value = Status.NOT_RUNNING
            _isRoot.value = false
            return
        }
        if (!hasPermission) {
            _status.value = Status.PERMISSION_NEEDED
            return
        }
        _status.value = Status.READY
        _isRoot.value = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
        bindShell()
    }

    /** Show the Shizuku permission dialog. Safe to call from anywhere. */
    fun requestPermission() {
        runCatching {
            if (!Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    fun bindShell() {
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
    }

    /** Execute a command through the privileged shell service. */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val service = shell ?: return@withContext ShellResult(-1, "shell service not bound")
        return@withContext try {
            val raw = service.exec(command) ?: ""
            val marker = raw.indexOf(EXIT_MARKER)
            if (marker >= 0) {
                val code = raw.substring(marker + EXIT_MARKER.length)
                    .trim().toIntOrNull() ?: -1
                ShellResult(code, raw.substring(0, marker))
            } else {
                ShellResult(0, raw)
            }
        } catch (t: Throwable) {
            ShellResult(-1, t.message ?: "exec failed")
        }
    }

    /** True when the shell can be used right now. */
    val canUseShell: Boolean get() = _shellReady.value

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            // Can't enumerate packages (visibility) - assume installed, let pingBinder decide.
            true
        }
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 60418
        const val EXIT_MARKER = "__GH_EXIT__="
    }
}
