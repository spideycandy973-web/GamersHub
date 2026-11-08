package com.gamershub

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        installCrashGuard()
    }

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log(thread, error) }
            val main = Looper.getMainLooper().thread
            if (thread === main) {
                // Keep the process alive: unwind this one action, tell the user, carry on.
                runCatching {
                    Handler(main).post {
                        runCatching {
                            Toast.makeText(
                                this,
                                "That action hit a system limit — GamersHub kept running",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    private fun log(thread: Thread, error: Throwable) {
        val file = File(filesDir, "crash.log")
        if (file.exists() && file.length() > 512 * 1024) file.delete()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file.appendText(
            buildString {
                append("\n=== ").append(stamp).append(" · ").append(thread.name).append(" ===\n")
                append(error.toString()).append('\n')
                error.stackTrace.take(40).forEach { append("    at ").append(it).append('\n') }
            }
        )
    }
}
