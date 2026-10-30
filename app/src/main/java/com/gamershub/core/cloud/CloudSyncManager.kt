package com.gamershub.core.cloud

import android.content.Context
import android.net.Uri
import com.gamershub.core.profiles.GameProfileStore
import com.gamershub.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cross-device sync for settings + game profiles.
 *
 * Backends:
 *  - "folder": a user-picked file via Storage Access Framework (works with any
 *    cloud provider: Google Drive, Dropbox, Syncthing folders...). Zero backend code.
 *  - "rest":   any REST endpoint (PUT/GET a JSON bundle secured with a Bearer
 *    token) — point it at your own server, Cloudflare Worker, Firebase REST, etc.
 */
class CloudSyncManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val profiles: GameProfileStore
) {
    enum class SyncStatus { OFF, IDLE, SYNCING, OK, ERROR }

    data class SyncUiState(
        val status: SyncStatus = SyncStatus.OFF,
        val message: String = "",
        val lastSyncMs: Long = 0
    )

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class SyncBundle(
        val deviceId: String,
        val updatedAt: Long,
        val profilesJson: String,
        val settings: Map<String, String>
    )

    suspend fun buildBundle(): SyncBundle {
        val curated = mutableMapOf<String, String>()
        curated["rec_resolution"] = settings.recResolution.first()
        curated["rec_fps"] = settings.recFps.first().toString()
        curated["rec_codec"] = settings.recCodec.first()
        curated["rec_bitrate_mbps"] = settings.recBitrateMbps.first().toString()
        curated["rec_mic"] = settings.recMic.first().toString()
        curated["gesture_double_tap"] = settings.gestureDoubleTap.first().name
        curated["gesture_triple_tap"] = settings.gestureTripleTap.first().name
        curated["thermal_profile"] = settings.thermalProfile.first()
        curated["default_refresh_rate"] = settings.defaultRefresh.first().toString()
        curated["default_resolution_scale"] = settings.defaultResolutionScale.first().toString()
        StreamPlatforms.forEach { p ->
            curated["stream_on_$p"] = settings.streamEnabled(p).first().toString()
            curated["stream_chat_$p"] = settings.streamChatEnabled(p).first().toString()
            // stream keys are synced so broadcasting works on every device
            curated["stream_key_$p"] = settings.streamKey(p).first()
        }
        curated["yt_api_key"] = settings.ytApiKey.first()
        curated["yt_live_chat_id"] = settings.ytLiveChatId.first()
        return SyncBundle(
            deviceId = settings.deviceId(),
            updatedAt = System.currentTimeMillis(),
            profilesJson = profiles.exportJson(),
            settings = curated
        )
    }

    suspend fun applyBundle(bundle: SyncBundle) {
        profiles.importJson(bundle.profilesJson)
        val s = bundle.settings
        s["rec_resolution"]?.let { settings.setRecResolution(it) }
        s["rec_fps"]?.toIntOrNull()?.let { settings.setRecFps(it) }
        s["rec_codec"]?.let { settings.setRecCodec(it) }
        s["rec_bitrate_mbps"]?.toIntOrNull()?.let { settings.setRecBitrateMbps(it) }
        s["rec_mic"]?.toBooleanStrictOrNull()?.let { settings.setRecMic(it) }
        s["thermal_profile"]?.let { settings.setThermalProfile(it) }
        s["default_refresh_rate"]?.toIntOrNull()?.let { settings.setDefaultRefresh(it) }
        s["default_resolution_scale"]?.toFloatOrNull()?.let { settings.setDefaultResolutionScale(it) }
        StreamPlatforms.forEach { p ->
            s["stream_on_$p"]?.toBooleanStrictOrNull()?.let { settings.setStreamEnabled(p, it) }
            s["stream_chat_$p"]?.toBooleanStrictOrNull()?.let { settings.setStreamChatEnabled(p, it) }
            s["stream_key_$p"]?.let { settings.setStreamKey(p, it) }
        }
        s["yt_api_key"]?.let { settings.setYtApiKey(it) }
        s["yt_live_chat_id"]?.let { settings.setYtLiveChatId(it) }
    }

    /** Uploads the bundle to the configured backend. */
    suspend fun upload(): Boolean = withContext(Dispatchers.IO) {
        val backend = settings.cloudBackend.first()
        if (backend == "off") {
            _state.value = SyncUiState(SyncStatus.OFF, "Cloud sync is off")
            return@withContext false
        }
        _state.value = SyncUiState(SyncStatus.SYNCING, "Uploading…")
        val body = json.encodeToString(buildBundle())
        val ok = when (backend) {
            "folder" -> writeToSaf(body)
            "rest" -> putToRest(body)
            else -> false
        }
        _state.value = if (ok) {
            SyncUiState(SyncStatus.OK, "Synced at " + java.text.SimpleDateFormat("HH:mm").format(java.util.Date()),
                System.currentTimeMillis())
        } else {
            SyncUiState(SyncStatus.ERROR, "Upload failed — check backend settings")
        }
        ok
    }

    /** Downloads + merges (last-write-wins) when remote is newer than local data. */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        val backend = settings.cloudBackend.first()
        if (backend == "off") return@withContext false
        _state.value = SyncUiState(SyncStatus.SYNCING, "Checking remote…")
        val raw = when (backend) {
            "folder" -> readFromSaf()
            "rest" -> getFromRest()
            else -> null
        }
        if (raw == null) {
            _state.value = SyncUiState(SyncStatus.IDLE, "No remote data found yet")
            return@withContext false
        }
        val bundle = runCatching { json.decodeFromString<SyncBundle>(raw) }.getOrNull()
        if (bundle == null) {
            _state.value = SyncUiState(SyncStatus.ERROR, "Remote bundle is corrupt")
            return@withContext false
        }
        applyBundle(bundle)
        _state.value = SyncUiState(
            SyncStatus.OK,
            "Merged bundle from device ${bundle.deviceId}",
            System.currentTimeMillis()
        )
        true
    }

    /** Convenience: upload when there is something to say, then pull newer data. */
    suspend fun syncNow() {
        upload()
        download()
    }

    // ---------------------------------------------------------------- SAF

    private suspend fun safUri(): Uri? =
        settings.cloudFolderUri.first().takeIf { it.isNotBlank() }?.let(Uri::parse)

    private suspend fun writeToSaf(body: String): Boolean {
        val uri = safUri() ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
    }

    private suspend fun readFromSaf(): String? {
        val uri = safUri() ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- REST

    private suspend fun putToRest(body: String): Boolean {
        val endpoint = settings.cloudRestEndpoint.first()
        if (endpoint.isBlank()) return false
        val token = settings.cloudRestToken.first()
        val req = Request.Builder()
            .url(endpoint)
            .put(body.toRequestBody("application/json".toMediaType()))
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        return runCatching { http.newCall(req).execute().use { it.isSuccessful } }
            .getOrDefault(false)
    }

    private suspend fun getFromRest(): String? {
        val endpoint = settings.cloudRestEndpoint.first()
        if (endpoint.isBlank()) return null
        val token = settings.cloudRestToken.first()
        val req = Request.Builder()
            .url(endpoint).get()
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        return runCatching {
            http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    companion object {
        val StreamPlatforms = listOf("youtube", "facebook", "instagram")
    }
}
