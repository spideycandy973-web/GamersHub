package com.gamershub.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamershub.core.profiles.GameProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "gamershub_library")

private val PINNED_KEY = stringPreferencesKey("pinned_apps")

data class GameEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val hasProfile: Boolean,
    /** True when the OS flags it as a game; false for user-added apps. */
    val isGame: Boolean,
    /** True when the user explicitly added it (survives filtering). */
    val userAdded: Boolean
)

/**
 * Built-in game launcher + app selector.
 *
 * Two sources feed the library:
 *  1. every app the OS flags as a game (CATEGORY_GAME / FLAG_IS_GAME),
 *  2. every app the USER pinned — so you can add whatever you want, always.
 *
 * [allApps] powers the app-selector sheet: every launchable app on the device
 * with search, so nothing is locked out.
 */
class GameLibrary(
    private val context: Context,
    private val profiles: GameProfileStore
) {
    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    val games: StateFlow<List<GameEntry>> = _games.asStateFlow()

    private val _allApps = MutableStateFlow<List<GameEntry>>(emptyList())
    val allApps: StateFlow<List<GameEntry>> = _allApps.asStateFlow()

    /** Package names the user pinned into the library (persisted). */
    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    init {
        com.gamershub.core.util.SafeScopes.io("library").launch {
            val raw = context.dataStore.data.first()[PINNED_KEY].orEmpty()
            _pinned.value = raw.split(',').filter { it.isNotBlank() }.toSet()
            refresh()
        }
    }

    /** Persists + applies the pinned set, then refreshes the library. */
    fun setPinned(pkgs: Set<String>) {
        _pinned.value = pkgs
        com.gamershub.core.util.SafeScopes.io("library").launch {
            context.dataStore.edit { it[PINNED_KEY] = pkgs.joinToString(",") }
            refresh()
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(launcher, 0) }
            .getOrDefault(emptyList())
        val selfPkg = context.packageName

        val entries = resolved.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == selfPkg) return@mapNotNull null
            val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ai.category == ApplicationInfo.CATEGORY_GAME
            } else {
                (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
            GameEntry(
                packageName = ai.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                hasProfile = profiles.profiles.value.containsKey(ai.packageName),
                isGame = isGame,
                userAdded = ai.packageName in _pinned.value
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        // all apps → the selector sheet
        _allApps.value = entries

        // library = OS games + user-pinned apps
        _games.value = entries.filter { it.isGame || it.userAdded }
    }

    fun isGame(packageName: String): Boolean =
        _games.value.any { it.packageName == packageName }

    /** Launch any app by package name. */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun labelOf(packageName: String): String = runCatching {
        val ai = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(ai).toString()
    }.getOrDefault(packageName)
}
