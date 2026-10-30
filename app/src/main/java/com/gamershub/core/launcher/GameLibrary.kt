package com.gamershub.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.gamershub.core.profiles.GameProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class GameEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val hasProfile: Boolean
)

/** Built-in game launcher library + launch intents. */
class GameLibrary(
    private val context: Context,
    private val profiles: GameProfileStore
) {
    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    val games: StateFlow<List<GameEntry>> = _games.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcher, 0)
        val list = resolved.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ai.category == ApplicationInfo.CATEGORY_GAME
            } else {
                (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
            if (!isGame) return@mapNotNull null
            GameEntry(
                packageName = ai.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                hasProfile = profiles.profiles.value.containsKey(ai.packageName)
            )
        }.sortedBy { it.label.lowercase() }
        _games.value = list
    }

    fun isGame(packageName: String): Boolean =
        _games.value.any { it.packageName == packageName }

    /** Launch a game by package name. */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
