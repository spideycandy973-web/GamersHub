package com.gamershub.core.record

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gamershub.core.analytics.AnalyticsEngine
import com.gamershub.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * App-process controller for capture sessions. Coordinates the permission flow,
 * builds the [RecorderConfig] from settings, and starts/stops
 * [ScreenRecordService]. State is shared with the service in-process.
 */
class RecorderController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val analytics: AnalyticsEngine?
) {
    sealed interface State {
        data object Idle : State
        data class Active(
            val mode: RecorderConfig.Mode,
            val sinceMs: Long,
            val streamTargets: List<String> = emptyList()
        ) : State

        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Snackbar/toast-style events for the UI. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    internal fun pushState(s: State) {
        _state.value = s
    }

    internal fun pushEvent(msg: String) {
        _events.tryEmit(msg)
    }

    /** Current capture config assembled from the user's settings. */
    suspend fun currentConfig(mode: RecorderConfig.Mode): RecorderConfig {
        val res = settings.recResolution.first().filter { it.isDigit() }.toIntOrNull() ?: 1080
        val fps = settings.recFps.first().coerceIn(24, 120)
        var codec = runCatching {
            RecorderConfig.Codec.valueOf(settings.recCodec.first())
        }.getOrDefault(RecorderConfig.Codec.H264)
        // RTMP ingest on every major platform is H.264/AVC - force it when streaming.
        if (mode != RecorderConfig.Mode.RECORD && codec == RecorderConfig.Codec.HEVC) {
            codec = RecorderConfig.Codec.H264
            _events.tryEmit("HEVC isn't supported by RTMP platforms — using H.264 for this stream")
        }
        val bitrate = settings.recBitrateMbps.first().coerceIn(0, 80)
        val micPref = settings.recMic.first()
        val micGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (micPref && !micGranted) {
            _events.tryEmit("Mic permission not granted — capturing without microphone audio")
        }
        val frameGen = settings.recFrameGen.first()
        // Graphics-enhancer sharpen level (0..3) → shader unsharp amount.
        val sharpen = when (settings.enhanceLevel.first()) {
            1 -> 0.35f
            2 -> 0.6f
            3 -> 0.9f
            else -> 0f
        }
        return RecorderConfig(
            verticalResolution = res,
            fps = fps,
            codec = codec,
            bitrateMbps = bitrate,
            withMic = micPref && micGranted,
            mode = mode,
            frameGen = frameGen,
            sharpen = sharpen
        )
    }

    /**
     * Requests the MediaProjection consent and starts capture.
     * Returns false when the launch machinery is not available.
     */
    suspend fun requestStart(mode: RecorderConfig.Mode): Boolean {
        if (_state.value is State.Active) {
            _events.tryEmit("A capture session is already running")
            return false
        }
        val config = currentConfig(mode)
        val launched = ProjectionBroker.request(context) { resultCode, data ->
            if (resultCode != Activity.RESULT_OK || data == null) {
                pushEvent("Screen capture permission denied")
                pushState(State.Idle)
                return@request
            }
            val intent = Intent(context, ScreenRecordService::class.java)
                .setAction(ScreenRecordService.ACTION_START)
                .putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
                .putExtra(ScreenRecordService.EXTRA_CONFIG, config)
            ContextCompat.startForegroundService(context, intent)
        }
        if (!launched) {
            // Never fail silently — surface it on the Recorder screen.
            _events.tryEmit("Could not open the screen-capture permission dialog")
            _state.value = State.Error("Could not open the screen-capture permission dialog")
        }
        return launched
    }

    fun stop() {
        // ACTION_STOP via startService throws from background on API 26+
        // (widget gesture path) — fall back to stopService, whose onDestroy
        // runs the same full cleanup.
        runCatching {
            context.startService(
                Intent(context, ScreenRecordService::class.java)
                    .setAction(ScreenRecordService.ACTION_STOP)
            )
        }.onFailure {
            runCatching {
                context.stopService(Intent(context, ScreenRecordService::class.java))
            }
        }
    }

    /** Toggle helper used by gestures: starts RECORD or stops. */
    suspend fun toggleRecording() {
        if (_state.value is State.Active) stop()
        else requestStart(RecorderConfig.Mode.RECORD)
    }
}
