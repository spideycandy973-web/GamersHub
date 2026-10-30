package com.gamershub.core.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.profileStore by preferencesDataStore(name = "gamershub_profiles")

/** Per-game tuning: boost, thermal, recording + overlay behaviour. */
@Serializable
data class GameProfile(
    val packageName: String,
    val label: String = "",
    val boostEnabled: Boolean = true,
    val refreshRate: Int = 120,
    val resolutionScale: Float = 1.0f,
    /** COOL | BALANCED | BEAST | EXTREME */
    val thermalProfile: String = "BEAST",
    val killBackground: Boolean = true,
    val blockNotifications: Boolean = true,
    val showHud: Boolean = true,
    /** Optional device spoof preset applied while this game is in foreground. */
    val spoofPresetId: String? = null,
    /** Governor eases off when battery skin temp passes this threshold. */
    val targetBatteryTempC: Int = 42,
    val recordFps: Int = 60,
    val recordResolution: String = "1080p",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class GameProfileStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _profiles = MutableStateFlow<Map<String, GameProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, GameProfile>> = _profiles.asStateFlow()

    private val key = stringPreferencesKey("profiles_json")

    init {
        scope.launch {
            val raw = context.profileStore.data.first()[key].orEmpty()
            if (raw.isNotBlank()) {
                runCatching {
                    _profiles.value = json.decodeFromString<Map<String, GameProfile>>(raw)
                }
            }
        }
    }

    fun profileFor(packageName: String): GameProfile =
        _profiles.value[packageName] ?: GameProfile(packageName = packageName)

    fun upsert(profile: GameProfile) {
        val updated = profile.copy(updatedAt = System.currentTimeMillis())
        scope.launch {
            mutex.withLock {
                val map = _profiles.value.toMutableMap()
                map[updated.packageName] = updated
                _profiles.value = map
                persist()
            }
        }
    }

    fun remove(packageName: String) {
        scope.launch {
            mutex.withLock {
                val map = _profiles.value.toMutableMap()
                if (map.remove(packageName) != null) {
                    _profiles.value = map
                    persist()
                }
            }
        }
    }

    private suspend fun persist() {
        val raw = json.encodeToString(_profiles.value)
        context.profileStore.edit { it[key] = raw }
    }

    /** Used by cloud sync. */
    suspend fun exportJson(): String = json.encodeToString(_profiles.value)

    suspend fun importJson(raw: String) {
        mutex.withLock {
            runCatching { json.decodeFromString<Map<String, GameProfile>>(raw) }
                .onSuccess {
                    _profiles.value = it
                    persist()
                }
        }
    }
}
