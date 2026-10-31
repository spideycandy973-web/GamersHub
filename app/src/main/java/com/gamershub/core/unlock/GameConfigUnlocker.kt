package com.gamershub.core.unlock

import android.content.Context
import android.util.Base64
import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Real game-graphics unlock engine (the mechanism that actually works).
 *
 * Why this exists: games like PUBG Mobile and Delta Force decide which graphics
 * options to expose by reading THEIR OWN saved config files — not only the
 * device model. Editing those configs (the same files root GFX tools edit)
 * unlocks the hidden Ultra / 90–120 FPS options **without root**, because the
 * Shizuku shell (UID 2000) can read and write `/sdcard/Android/data/<pkg>/files`.
 *
 * Per game we: locate config files → back up each one (`*.ghbak`) → apply the
 * patch → report exactly what changed. Undo restores every backup.
 */
class GameConfigUnlocker(
    private val context: Context,
    private val shizuku: ShizukuManager
) {

    data class UnlockReport(
        val game: String,
        val ok: Boolean,
        val lines: List<String>
    )

    data class InstalledGame(
        val packageName: String,
        val label: String,
        val preset: GameUnlockPreset?
    )

    private val _installed = MutableStateFlow<List<InstalledGame>>(emptyList())
    val installed: StateFlow<List<InstalledGame>> = _installed.asStateFlow()

    private val _lastReport = MutableStateFlow<UnlockReport?>(null)
    val lastReport: StateFlow<UnlockReport?> = _lastReport.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // ------------------------------------------------------------------ scan

    /** Installed apps known to the unlock database. */
    suspend fun refreshInstalled() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
        val out = resolved.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            val preset = GameUnlockPresets.forPackage(ai.packageName) ?: return@mapNotNull null
            InstalledGame(
                packageName = ai.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }
                    .getOrDefault(ai.packageName),
                preset = preset
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        _installed.value = out
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    // ---------------------------------------------------------------- unlock

    suspend fun unlock(preset: GameUnlockPreset): UnlockReport = withContext(Dispatchers.IO) {
        _busy.value = true
        val lines = mutableListOf<String>()
        var ok = false
        try {
            if (!shizuku.canUseShell) {
                lines += "Shizuku shell not connected — open the Dashboard and tap Reconnect"
                return@withContext UnlockReport(preset.displayName, false, lines)
                    .also { _lastReport.value = it }
            }
            if (preset.packageName.isNotEmpty() && !isInstalled(preset.packageName)) {
                lines += "${preset.displayName} (${preset.packageName}) is not installed"
                return@withContext UnlockReport(preset.displayName, false, lines)
                    .also { _lastReport.value = it }
            }

            val base = "/sdcard/Android/data/${preset.packageName}/files"
            lines += "Scanning $base …"
            val found = findFiles(base)
            if (found.isEmpty()) {
                lines += "No config files yet — launch ${preset.displayName} once, then unlock"
            } else {
                lines += "Found ${found.size} config file(s)"
            }

            for (file in found) {
                val patch = preset.patchFor(file) ?: continue

                // back up once
                val bak = "$file.ghbak"
                if (!fileExists(bak)) {
                    shizuku.exec("cp '$file' '$bak'")
                    lines += "Backed up ${short(file)}"
                }

                val newBody = when {
                    patch.transform != null -> {
                        val current = shizuku.exec("cat '$file'").output
                        patch.transform.invoke(current)
                    }
                    patch.merge -> patch.content
                    else -> patch.content
                }

                val result = writeFile(file, newBody, merge = patch.merge)
                if (result.ok) {
                    lines += "Patched ${short(file)} — ${patch.description}"
                    ok = true
                } else {
                    lines += "FAILED ${short(file)}: ${result.output}"
                }
            }
            if (ok) {
                lines += "Done — open the game: the new graphics / FPS options appear in its settings"
                lines += "Heads-up: big game updates can reset configs; run Unlock again after updating"
            }
        } catch (t: Throwable) {
            lines += "Error: ${t.message}"
        } finally {
            _busy.value = false
        }
        UnlockReport(preset.displayName, ok, lines).also { _lastReport.value = it }
    }

    /** Restores every `*.ghbak` backup for a game (undo). */
    suspend fun restore(preset: GameUnlockPreset): UnlockReport = withContext(Dispatchers.IO) {
        _busy.value = true
        val lines = mutableListOf<String>()
        var ok = false
        try {
            val base = "/sdcard/Android/data/${preset.packageName}/files"
            val baks = findFiles(base, suffix = ".ghbak")
            for (bak in baks) {
                val orig = bak.removeSuffix(".ghbak")
                shizuku.exec("cp '$bak' '$orig' && rm '$bak'")
                lines += "Restored ${short(orig)}"
                ok = true
            }
            if (baks.isEmpty()) lines += "Nothing to restore"
        } catch (t: Throwable) {
            lines += "Error: ${t.message}"
        } finally {
            _busy.value = false
        }
        UnlockReport(preset.displayName, ok, lines).also { _lastReport.value = it }
    }

    /**
     * Restores EVERY `*.ghbak` backup under /sdcard/Android/data — the global
     * rollback used by the master engine switch. Returns how many files were
     * put back.
     */
    suspend fun restoreAllBackups(): Int = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) return@withContext 0
        try {
            val baks = shizuku.exec(
                "find '/sdcard/Android/data' -type f -name '*.ghbak' 2>/dev/null | head -80"
            ).output.lines().map { it.trim() }.filter { it.startsWith("/") }
            var count = 0
            for (bak in baks) {
                val orig = bak.removeSuffix(".ghbak")
                val r = shizuku.exec("cp '$bak' '$orig' && rm '$bak'")
                if (r.ok) count++
            }
            count
        } catch (t: Throwable) {
            0
        }
    }

    // ------------------------------------------------------------- shell I/O

    private suspend fun findFiles(base: String, suffix: String = ".ini"): List<String> {
        val r = shizuku.exec(
            "find '$base' -type f -name '*$suffix' 2>/dev/null | head -40"
        )
        return r.output.lines().map { it.trim() }.filter { it.startsWith("/") }
    }

    private suspend fun fileExists(path: String): Boolean =
        shizuku.exec("test -f '$path' && echo yes").trimmed == "yes"

    /**
     * Writes content through the shell as base64 to dodge all quoting problems.
     */
    private suspend fun writeFile(
        path: String,
        content: String,
        merge: Boolean
    ): ShizukuManager.ShellResult {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val op = if (merge) ">>" else ">"
        return shizuku.exec("echo '$b64' | base64 -d $op '$path'")
    }

    private fun short(path: String) =
        path.substringAfterLast("/files/").ifBlank { path }
}

/** A named unlock recipe for one game. */
data class GameUnlockPreset(
    val id: String,
    val displayName: String,
    val packageName: String,
    val notes: String,
    /** Returns the patch to apply to one discovered config file, or null. */
    val patchFor: (fileName: String) -> Patch?
) {
    data class Patch(
        val description: String,
        /** true = append to the file, false = replace it */
        val merge: Boolean,
        /** Static content used when [transform] is null. */
        val content: String = "",
        /** Computed rewrite: current file body → new body. */
        val transform: ((String) -> String)? = null
    )
}
