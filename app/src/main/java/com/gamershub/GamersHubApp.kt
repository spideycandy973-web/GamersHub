package com.gamershub

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide safety net.
 *
 * The app drives a lot of system surfaces (Shizuku, MediaProjection, overlay windows,
 * foreground services). A single OEM-specific SecurityException in any of those used to
 * take the whole process down. Here the trace is written to `files/crash.log` and the
 * action is abandoned instead of killing the app.
 */
class GamersHubApp : Application() {

    companion object {
        /** Last trace caught in this process — the Settings card reads the file, the UI reads this. */
        @Volatile
        var lastCrash: String = ""

        @Volatile
        private var instance: GamersHubApp? = null

        @Volatile
        private var lastRecovery: Long = 0L

        /** Records a caught failure without letting it kill the process. */
        fun note(error: Throwable, where: String) {
            val ctx = instance ?: return
            runCatching { ctx.log(Thread.currentThread(), error) }
            runCatching { lastCrash = "$where\n" + error.stackTraceToString() }
        }

        /** Safe toast from any thread — used so a failed action is never silent. */
        fun toast(message: String) {
            val ctx = instance ?: return
            runCatching {
                Handler(Looper.getMainLooper()).post {
                    runCatching { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashGuard()
    }

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log(thread, error) }
            val now = System.currentTimeMillis()
            val main = Looper.getMainLooper().thread
            if (thread === main) {
                // Too many failures in a row: this is not something we can paper over,
                // let the system take it so we do not loop forever on a fresh start.
                if (now - lastRecovery < 6000) {
                    previous?.uncaughtException(thread, error)
                    return@setDefaultUncaughtExceptionHandler
                }
                lastRecovery = now
                // Put a usable screen back immediately, then leave the broken process.
                runCatching {
                    Toast.makeText(this, "That step failed — GamersHub reopened.", Toast.LENGTH_LONG).show()
                }
                runCatching {
                    startActivity(
                        android.content.Intent(this, MainActivity::class.java)
                            .addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                    )
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            } else {
                // A background worker (shell read, binder callback, encoder thread) must
                // not take the whole app down either — log it and keep running. If the
                // same failure repeats endlessly, hand it back to the system.
                if (now - lastRecovery < 4000) {
                    previous?.uncaughtException(thread, error)
                } else {
                    lastRecovery = now
                }
            }
        }
    }

    /** Copies the trace to the clipboard as well, so it can be pasted into a report. */
    private fun copyToClipboard(text: String) {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java) ?: return
            cm.setPrimaryClip(ClipData.newPlainText("GamersHub crash", text))
        }
    }

    private fun log(thread: Thread, error: Throwable) {
        val file = File(filesDir, "crash.log")
        if (file.exists() && file.length() > 512 * 1024) file.delete()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val trace = buildString {
            append("\n=== ").append(stamp).append(" · ").append(thread.name).append(" ===\n")
            append(error.toString()).append('\n')
            error.stackTrace.take(40).forEach { append("    at ").append(it).append('\n') }
        }
        file.appendText(trace)
        copyToClipboard(trace)
        lastCrash = trace
    }
}
