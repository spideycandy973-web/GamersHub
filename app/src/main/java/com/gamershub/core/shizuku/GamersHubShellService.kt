package com.gamershub.core.shizuku

import kotlin.system.exitProcess

/**
 * Runs INSIDE the Shizuku server process with shell/root identity.
 * Shizuku instantiates this class via reflection and binds it through
 * the IGamersHubShell AIDL interface.
 */
class GamersHubShellService : IGamersHubShell.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        out + err + "\n__GH_EXIT__=$code"
    } catch (t: Throwable) {
        "${t.message}\n__GH_EXIT__=-1"
    }
}
