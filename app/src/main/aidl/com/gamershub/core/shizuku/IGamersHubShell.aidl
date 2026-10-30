// IGamersHubShell.aidl
// Interface implemented by GamersHubShellService which runs *inside* the Shizuku
// server process with the identity of shell (UID 2000, adb) or root (UID 0, Sui).
package com.gamershub.core.shizuku;

interface IGamersHubShell {

    // Destroy method defined by the Shizuku server (reserved transaction code)
    void destroy() = 16777114;

    /**
     * Execute a shell command and return its combined output.
     * The last line of the returned string is always in the form:
     *     __GH_EXIT__=<exitCode>
     */
    String exec(String command) = 1;
}
