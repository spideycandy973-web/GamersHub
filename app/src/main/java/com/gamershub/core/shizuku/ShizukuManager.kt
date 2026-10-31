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
 * Connection lifecycle (verified against the Shizuku API contract):
 *  1. [Shizuku.pingBinder]      — is the Shizuku service alive at all?
 *  2. [Shizuku.checkSelfPermission] — did the user allow THIS app?
 *  3. bind the AIDL user-service ([GamersHubShellService]) that executes
 *     commands as shell (UID 2000) or root (UID 0 via Sui).
 *
 * Every transition is pushed to [status] / [shellReady] in real time so the UI
 * never shows a stale connection state. [reconnect] resets the whole listener
 * chain + user-service binding — the manual fix for "it says not connected".
 */
class ShizukuManager(private val context: Context) {

    enum class Status {
        /** Shizuku (or Sui) is not installed on this device. */
        NOT_INSTALLED,
        /** Installed but the Shizuku service is not running. */
        NOT_RUNNING,
        /** Running but the user has not granted GamersHub permission yet. */
        PERMISSION_NEEDED,
        /** Binder alive + permission granted. Shell bound and executing. */
        READY
    }

    data class ShellResult(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
        val trimmed: String get() = output.trim()
    }

    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** One-line human description of the current state, for UI display. */
    private val _statusDetail = MutableStateFlow("Starting…")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    /** True when the backing identity is root (Sui / rooted Shizuku). */
    private val _isRoot = MutableStateFlow(false)
    val isRoot: StateFlow<Boolean> = _isRoot.asStateFlow()

    /** True while the AIDL user service is bound and usable. */
    private val _shellReady = MutableStateFlow(false)
    val shellReady: StateFlow<Boolean> = _shellReady.asStateFlow()

    @Volatile
    private var shell: IGamersHubShell? = null

    @Volatile
    private var bound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, GamersHubShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("gh_shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            // Binder callbacks can arrive on non-main threads — marshal to main
            // and never let an exception escape into the Shizuku server.
            mainHandler.post {
                runCatching {
                    if (binder != null && binder.pingBinder()) {
                        shell = IGamersHubShell.Stub.asInterface(binder)
                        bound = true
                        _shellReady.value = true
                        _status.value = Status.READY
                        _statusDetail.value = "Shell bound${if (_isRoot.value) " (root)" else " (shell)"}"
                    } else {
                        _statusDetail.value = "Shell binder died on connect"
                        _shellReady.value = false
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mainHandler.post {
                runCatching {
                    shell = null
                    bound = false
                    _shellReady.value = false
                    // binder vanished (Shizuku killed) — refresh so UI reflects it immediately
                    refreshState()
                }
            }
        }
    }

    // ---- listeners: kept in fields so reconnect can remove & re-add them ----

    private var binderReceivedListener: Shizuku.OnBinderReceivedListener? = null
    private var binderDeadListener: Shizuku.OnBinderDeadListener? = null
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null

    fun init() {
        registerListeners()
        refreshState()
    }

    private fun registerListeners() {
        runCatching {
            val received = Shizuku.OnBinderReceivedListener {
                mainHandler.post {
                    runCatching {
                        _statusDetail.value = "Shizuku binder received"
                        refreshState()
                    }
                }
            }
            val dead = Shizuku.OnBinderDeadListener {
                mainHandler.post {
                    runCatching {
                        shell = null
                        bound = false
                        _shellReady.value = false
                        _statusDetail.value = "Shizuku binder lost"
                        refreshState()
                    }
                }
            }
            val perm = Shizuku.OnRequestPermissionResultListener { _, grant ->
                mainHandler.post {
                    runCatching {
                        _statusDetail.value =
                            if (grant == PackageManager.PERMISSION_GRANTED) "Permission granted"
                            else "Permission denied"
                        refreshState()
                    }
                }
            }
            binderReceivedListener = received
            binderDeadListener = dead
            permissionListener = perm
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
            Shizuku.addRequestPermissionResultListener(perm)
        }
    }

    private fun unregisterListeners() {
        runCatching { binderReceivedListener?.let(Shizuku::removeBinderReceivedListener) }
        runCatching { binderDeadListener?.let(Shizuku::removeBinderDeadListener) }
        runCatching { permissionListener?.let(Shizuku::removeRequestPermissionResultListener) }
        binderReceivedListener = null
        binderDeadListener = null
        permissionListener = null
    }

    /**
     * Manual recovery: tears down the listener chain and user-service binding,
     * then rebuilds both and re-evaluates state. Wired to the Reconnect button.
     */
    fun reconnect() {
        _statusDetail.value = "Reconnecting…"
        runCatching {
            if (bound) Shizuku.unbindUserService(userServiceArgs, connection, /*remove=*/true)
        }
        bound = false
        shell = null
        _shellReady.value = false
        unregisterListeners()
        registerListeners()
        refreshState()
    }

    /** Re-evaluates [Status] and (re)binds the user service if possible. */
    fun refreshState() {
        if (!isShizukuInstalled()) {
            _status.value = Status.NOT_INSTALLED
            _statusDetail.value = "Shizuku not installed"
            return
        }
        // 1. is the Shizuku binder alive?
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            _status.value = Status.NOT_RUNNING
            _isRoot.value = false
            _statusDetail.value = "Shizuku is not running — open Shizuku and start it"
            return
        }
        // 2. did the user grant this app permission?
        val granted = runCatching {
            Shizuku.isPreV11() ||
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            _status.value = Status.PERMISSION_NEEDED
            _statusDetail.value = "Tap Allow to grant GamersHub Shizuku access"
            return
        }
        // 3. bind the privileged shell
        _status.value = Status.READY
        _isRoot.value = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
        if (!_shellReady.value) {
            _statusDetail.value = "Binding shell…"
            bindShell()
        } else {
            _statusDetail.value = "Shell bound${if (_isRoot.value) " (root)" else " (shell)"}"
        }
    }

    /** Show the Shizuku permission dialog. Safe to call from anywhere. */
    fun requestPermission() {
        runCatching {
            if (!Shizuku.pingBinder()) {
                _statusDetail.value = "Shizuku is not running"
                refreshState()
                return
            }
            if (!Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(REQUEST_CODE)
            } else {
                refreshState()
            }
        }.onFailure {
            _statusDetail.value = "Permission request failed: ${it.message}"
        }
    }

    fun bindShell() {
        runCatching {
            if (bound) return
            Shizuku.bindUserService(userServiceArgs, connection)
        }.onFailure {
            _statusDetail.value = "Shell bind failed: ${it.message}"
        }
    }

    /**
     * Execute a command through the privileged shell service.
     * Auto-recovers once if the binding was lost (rebind + retry), so a dropped
     * binder does not permanently break every engine.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        var service = shell
        if (service == null || !runCatching { service.asBinder().pingBinder() }.getOrDefault(false)) {
            // binder stale — rebuild once
            shell = null
            bound = false
            refreshState()
            service = shell
        }
        if (service == null) return@withContext ShellResult(-1, "shell service not bound")
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
