package com.gamershub.core.shizuku

import android.content.Context
import android.os.RemoteException
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * This class is instantiated by the Shizuku server *inside its own process*
 * (identity: shell/UID 2000 via adb or wireless debugging, root/UID 0 via Sui).
 *
 * It is intentionally tiny: every privileged action in GamersHub funnels through
 * [exec] so the whole surface area of the elevated process is one shell runner.
 *
 * NOTE: this is NOT an Android component - do not declare it in the manifest.
 */
class GamersHubShellService : IGamersHubShell.Stub {

    /** No-arg constructor is required by the Shizuku server. */
    constructor()

    /** Context constructor available from Shizuku API v13. */
    @Keep
    constructor(context: Context) : this()

    /** Reserved destroy method invoked by the Shizuku server. */
    override fun destroy() {
        exitProcess(0)
    }

    /**
     * Runs [command] with `sh -c` and returns combined stdout+stderr followed by
     * "\n__GH_EXIT__=<code>" so callers can distinguish failures.
     */
    @Throws(RemoteException::class)
    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            // Read concurrently with waitFor: big outputs (e.g. pm list packages)
            // would otherwise fill the pipe buffer and block the child forever.
            var output = ""
            val reader = Thread {
                output = process.inputStream.bufferedReader().use { it.readText() }
            }
            reader.start()
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            reader.join(3000)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                output + "\n${ShizukuManager.EXIT_MARKER}124"
            } else {
                output + "\n${ShizukuManager.EXIT_MARKER}${process.exitValue()}"
            }
        } catch (t: Throwable) {
            "exec error: ${t.message}\n${ShizukuManager.EXIT_MARKER}-1"
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 25L
    }
}
