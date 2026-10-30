package com.gamershub.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gamershub_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class GestureAction { NONE, TOGGLE_RECORD, TOGGLE_BOOST, OPEN_PANEL }

/** Global user settings persisted with DataStore (single source of truth). */
class SettingsRepository(private val context: Context) {

    private object K {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val OVERRIDE_DARK_AMOLED = booleanPreferencesKey("amoled_dark")

        val AUTO_BOOST = booleanPreferencesKey("auto_boost_on_game")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val DEFAULT_REFRESH = intPreferencesKey("default_refresh_rate")
        val DEFAULT_RESOLUTION_SCALE = floatPreferencesKey("default_resolution_scale")

        val OVERLAY_BUBBLE = booleanPreferencesKey("overlay_bubble")
        val OVERLAY_HUD = booleanPreferencesKey("overlay_hud")
        val OVERLAY_CHAT = booleanPreferencesKey("overlay_chat")
        val GESTURE_DOUBLE_TAP = stringPreferencesKey("gesture_double_tap")
        val GESTURE_TRIPLE_TAP = stringPreferencesKey("gesture_triple_tap")

        val REC_RESOLUTION = stringPreferencesKey("rec_resolution")
        val REC_FPS = intPreferencesKey("rec_fps")
        val REC_CODEC = stringPreferencesKey("rec_codec")
        val REC_BITRATE_MBPS = intPreferencesKey("rec_bitrate_mbps")
        val REC_MIC = booleanPreferencesKey("rec_mic")
        val REC_FRAME_GEN = booleanPreferencesKey("rec_frame_gen")

        val SCALE_MODE = stringPreferencesKey("scale_mode")
        val SCALE_CUSTOM_PCT = intPreferencesKey("scale_custom_pct")
        val FRAMEGEN_DISPLAY = booleanPreferencesKey("framegen_display")
        val FRAMEGEN_CAPTURE = booleanPreferencesKey("framegen_capture")

        val STREAM_KEY_PREFIX = "stream_key_"      // + platform id
        val STREAM_ENABLED_PREFIX = "stream_on_"   // + platform id
        val STREAM_CHAT_PREFIX = "stream_chat_"    // + platform id
        val YT_API_KEY = stringPreferencesKey("yt_api_key")
        val YT_LIVE_CHAT_ID = stringPreferencesKey("yt_live_chat_id")

        val SPOOF_PRESET = stringPreferencesKey("spoof_preset")
        val SPOOF_APPLIED = booleanPreferencesKey("spoof_applied")

        val THERMAL_PROFILE = stringPreferencesKey("thermal_profile")
        val THERMAL_TARGET_TEMP = intPreferencesKey("thermal_target_temp")

        val STATS_TOTAL_STREAM_SECONDS = intPreferencesKey("stats_total_stream_seconds")
        val STATS_TOTAL_RECORDINGS = intPreferencesKey("stats_total_recordings")
    }

    // ---------- helpers ----------
    private fun <T> flow(key: androidx.datastore.preferences.core.Preferences.Key<T>, def: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: def }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[K.THEME_MODE] = mode.name }
    }
    val themeMode: Flow<ThemeMode> = flow(K.THEME_MODE, "SYSTEM").map {
        runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val accent: Flow<String> = flow(K.ACCENT, "GRAPHITE")
    suspend fun setAccent(value: String) = context.dataStore.edit { it[K.ACCENT] = value }

    val amoledDark: Flow<Boolean> = flow(K.OVERRIDE_DARK_AMOLED, false)
    suspend fun setAmoledDark(v: Boolean) = context.dataStore.edit { it[K.OVERRIDE_DARK_AMOLED] = v }

    val autoBoost: Flow<Boolean> = flow(K.AUTO_BOOST, true)
    suspend fun setAutoBoost(v: Boolean) = context.dataStore.edit { it[K.AUTO_BOOST] = v }

    val keepScreenOn: Flow<Boolean> = flow(K.KEEP_SCREEN_ON, true)
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.KEEP_SCREEN_ON] = v }

    val startOnBoot: Flow<Boolean> = flow(K.START_ON_BOOT, false)
    suspend fun setStartOnBoot(v: Boolean) = context.dataStore.edit { it[K.START_ON_BOOT] = v }

    val defaultRefresh: Flow<Int> = flow(K.DEFAULT_REFRESH, 120)
    suspend fun setDefaultRefresh(v: Int) = context.dataStore.edit { it[K.DEFAULT_REFRESH] = v }

    val defaultResolutionScale: Flow<Float> = flow(K.DEFAULT_RESOLUTION_SCALE, 1.0f)
    suspend fun setDefaultResolutionScale(v: Float) =
        context.dataStore.edit { it[K.DEFAULT_RESOLUTION_SCALE] = v }

    // ---------- overlay ----------
    val overlayBubble: Flow<Boolean> = flow(K.OVERLAY_BUBBLE, true)
    suspend fun setOverlayBubble(v: Boolean) = context.dataStore.edit { it[K.OVERLAY_BUBBLE] = v }
    val overlayHud: Flow<Boolean> = flow(K.OVERLAY_HUD, true)
    suspend fun setOverlayHud(v: Boolean) = context.dataStore.edit { it[K.OVERLAY_HUD] = v }
    val overlayChat: Flow<Boolean> = flow(K.OVERLAY_CHAT, true)
    suspend fun setOverlayChat(v: Boolean) = context.dataStore.edit { it[K.OVERLAY_CHAT] = v }

    val gestureDoubleTap: Flow<GestureAction> =
        flow(K.GESTURE_DOUBLE_TAP, "TOGGLE_RECORD").map {
            runCatching { GestureAction.valueOf(it) }.getOrDefault(GestureAction.TOGGLE_RECORD)
        }
    suspend fun setGestureDoubleTap(a: GestureAction) =
        context.dataStore.edit { it[K.GESTURE_DOUBLE_TAP] = a.name }

    val gestureTripleTap: Flow<GestureAction> =
        flow(K.GESTURE_TRIPLE_TAP, "TOGGLE_BOOST").map {
            runCatching { GestureAction.valueOf(it) }.getOrDefault(GestureAction.TOGGLE_BOOST)
        }
    suspend fun setGestureTripleTap(a: GestureAction) =
        context.dataStore.edit { it[K.GESTURE_TRIPLE_TAP] = a.name }

    // ---------- recorder ----------
    val recResolution: Flow<String> = flow(K.REC_RESOLUTION, "1080p")
    suspend fun setRecResolution(v: String) = context.dataStore.edit { it[K.REC_RESOLUTION] = v }
    val recFps: Flow<Int> = flow(K.REC_FPS, 60)
    suspend fun setRecFps(v: Int) = context.dataStore.edit { it[K.REC_FPS] = v }
    val recCodec: Flow<String> = flow(K.REC_CODEC, "H264")
    suspend fun setRecCodec(v: String) = context.dataStore.edit { it[K.REC_CODEC] = v }
    val recBitrateMbps: Flow<Int> = flow(K.REC_BITRATE_MBPS, 0) // 0 = auto
    suspend fun setRecBitrateMbps(v: Int) = context.dataStore.edit { it[K.REC_BITRATE_MBPS] = v }
    val recMic: Flow<Boolean> = flow(K.REC_MIC, false)
    suspend fun setRecMic(v: Boolean) = context.dataStore.edit { it[K.REC_MIC] = v }
    val recFrameGen: Flow<Boolean> = flow(K.REC_FRAME_GEN, false)
    suspend fun setRecFrameGen(v: Boolean) = context.dataStore.edit { it[K.REC_FRAME_GEN] = v }

    // ---------- lossless scaling / frame generation ----------
    val scaleMode: Flow<String> = flow(K.SCALE_MODE, "NATIVE")
    suspend fun setScaleMode(v: String) = context.dataStore.edit { it[K.SCALE_MODE] = v }
    val scaleCustomPct: Flow<Int> = flow(K.SCALE_CUSTOM_PCT, 85)
    suspend fun setScaleCustomPct(v: Int) = context.dataStore.edit { it[K.SCALE_CUSTOM_PCT] = v }
    val frameGenDisplay: Flow<Boolean> = flow(K.FRAMEGEN_DISPLAY, false)
    suspend fun setFrameGenDisplay(v: Boolean) = context.dataStore.edit { it[K.FRAMEGEN_DISPLAY] = v }
    val frameGenCapture: Flow<Boolean> = flow(K.FRAMEGEN_CAPTURE, false)
    suspend fun setFrameGenCapture(v: Boolean) = context.dataStore.edit { it[K.FRAMEGEN_CAPTURE] = v }

    // ---------- streaming ----------
    fun streamKey(platform: String): Flow<String> =
        flow(stringPreferencesKey(K.STREAM_KEY_PREFIX + platform), "")
    suspend fun setStreamKey(platform: String, key: String) =
        context.dataStore.edit { it[stringPreferencesKey(K.STREAM_KEY_PREFIX + platform)] = key }

    fun streamEnabled(platform: String): Flow<Boolean> =
        flow(booleanPreferencesKey(K.STREAM_ENABLED_PREFIX + platform), false)
    suspend fun setStreamEnabled(platform: String, on: Boolean) =
        context.dataStore.edit { it[booleanPreferencesKey(K.STREAM_ENABLED_PREFIX + platform)] = on }

    fun streamChatEnabled(platform: String): Flow<Boolean> =
        flow(booleanPreferencesKey(K.STREAM_CHAT_PREFIX + platform), true)
    suspend fun setStreamChatEnabled(platform: String, on: Boolean) =
        context.dataStore.edit { it[booleanPreferencesKey(K.STREAM_CHAT_PREFIX + platform)] = on }

    val ytApiKey: Flow<String> = flow(K.YT_API_KEY, "")
    suspend fun setYtApiKey(v: String) = context.dataStore.edit { it[K.YT_API_KEY] = v }
    val ytLiveChatId: Flow<String> = flow(K.YT_LIVE_CHAT_ID, "")
    suspend fun setYtLiveChatId(v: String) = context.dataStore.edit { it[K.YT_LIVE_CHAT_ID] = v }

    // ---------- spoofing ----------
    val spoofPreset: Flow<String> = flow(K.SPOOF_PRESET, "")
    suspend fun setSpoofPreset(v: String) = context.dataStore.edit { it[K.SPOOF_PRESET] = v }
    val spoofApplied: Flow<Boolean> = flow(K.SPOOF_APPLIED, false)
    suspend fun setSpoofApplied(v: Boolean) = context.dataStore.edit { it[K.SPOOF_APPLIED] = v }

    // ---------- thermal ----------
    val thermalProfile: Flow<String> = flow(K.THERMAL_PROFILE, "BEAST")
    suspend fun setThermalProfile(v: String) = context.dataStore.edit { it[K.THERMAL_PROFILE] = v }
    val thermalTargetTemp: Flow<Int> = flow(K.THERMAL_TARGET_TEMP, 42)
    suspend fun setThermalTargetTemp(v: Int) =
        context.dataStore.edit { it[K.THERMAL_TARGET_TEMP] = v }

    // ---------- analytics counters ----------
    suspend fun addStreamSeconds(seconds: Int) {
        context.dataStore.edit {
            it[K.STATS_TOTAL_STREAM_SECONDS] = (it[K.STATS_TOTAL_STREAM_SECONDS] ?: 0) + seconds
        }
    }
    val totalStreamSeconds: Flow<Int> = flow(K.STATS_TOTAL_STREAM_SECONDS, 0)

    suspend fun incrementRecordings() {
        context.dataStore.edit {
            it[K.STATS_TOTAL_RECORDINGS] = (it[K.STATS_TOTAL_RECORDINGS] ?: 0) + 1
        }
    }
    val totalRecordings: Flow<Int> = flow(K.STATS_TOTAL_RECORDINGS, 0)

    suspend fun deviceId(): String {
        val prefs = context.dataStore.data.first()
        prefs[stringPreferencesKey("device_id")]?.let { return it }
        val id = java.util.UUID.randomUUID().toString().take(8)
        context.dataStore.edit { it[stringPreferencesKey("device_id")] = id }
        return id
    }
}
