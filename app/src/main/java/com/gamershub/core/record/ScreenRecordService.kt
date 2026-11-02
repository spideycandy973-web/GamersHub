package com.gamershub.core.record

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.ServiceCompat
import com.gamershub.GamersHubApp
import com.gamershub.core.notifications.NotificationHelper
import com.gamershub.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service hosting the capture session. One instance handles either
 * recording, streaming, or both — the encoder output is fanned out through
 * [FrameConsumer]s.
 */
class ScreenRecordService : Service() {

    private val serviceScope = com.gamershub.core.util.SafeScopes.io("recorder")
    private lateinit var container: AppContainer
    private var projection: MediaProjection? = null
    private var pipeline: CapturePipeline? = null
    private var outputFile: File? = null
    private var currentMode: RecorderConfig.Mode = RecorderConfig.Mode.RECORD

    override fun onCreate() {
        super.onCreate()
        container = GamersHubApp.container(application as GamersHubApp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                // Typed reads: the projection token is an Intent, the config is a
                // RecorderConfig — mixing the classes up returns null on API 33+.
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val config: RecorderConfig? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_CONFIG, RecorderConfig::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_CONFIG)
                }
                if (data == null) {
                    container.recorder.pushState(
                        RecorderController.State.Error("Missing projection token")
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                startCapture(code, data, config ?: RecorderConfig())
            }
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, config: RecorderConfig) {
        if (pipeline != null) {
            container.recorder.pushEvent("Capture already running — duplicate start ignored")
            return
        }
        currentMode = config.mode

        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_RECORDER,
            container.notifications.recorderNotification(
                if (config.mode == RecorderConfig.Mode.STREAM) "Live stream starting…"
                else "Screen recorder running", "GamersHub capture is active"
            ),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            container.recorder.pushState(RecorderController.State.Error("Projection failed: ${t.message}"))
            stopSelf()
            return
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                this@ScreenRecordService.onProjectionStopped()
            }
        }, null)

        val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        val nativeW = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
        val nativeH = if (metrics.heightPixels > 0) metrics.heightPixels else 2400

        // Network connects (RTMP handshakes) + codec setup off the main thread.
        serviceScope.launch {
            val consumers = mutableListOf<FrameConsumer>()
            var targetsDesc: List<String> = emptyList()
            var micActive = false

            if (config.mode == RecorderConfig.Mode.RECORD || config.mode == RecorderConfig.Mode.BOTH) {
                val file = newOutputFile()
                outputFile = file
                // Build the sink with the real mic decision (permission + setting).
                val sink = RecordingSink(file, config.withMic)
                consumers += sink
                micActive = config.withMic
            }
            if (config.mode == RecorderConfig.Mode.STREAM || config.mode == RecorderConfig.Mode.BOTH) {
                val dims = config.dimensions(nativeW, nativeH)
                val session = container.stream.startSession(config, dims.first, dims.second)
                if (session == null) {
                    container.recorder.pushEvent("No stream targets went live — check Stream settings/keys")
                    if (config.mode == RecorderConfig.Mode.STREAM) {
                        container.recorder.pushState(
                            RecorderController.State.Error("Could not connect any stream target")
                        )
                        runCatching { mp.stop() }
                        ServiceCompat.stopForeground(
                            this@ScreenRecordService, ServiceCompat.STOP_FOREGROUND_REMOVE
                        )
                        stopSelf()
                        return@launch
                    }
                } else {
                    consumers += session
                    targetsDesc = session.targetNames
                }
            }
            if (consumers.isEmpty()) {
                container.recorder.pushState(
                    RecorderController.State.Error("Nothing to record — no sink configured")
                )
                stopSelf()
                return@launch
            }

            try {
                val pipe = CapturePipeline(
                    config, mp, CompositeConsumer(consumers),
                    nativeW, nativeH, metrics.densityDpi
                )
                pipe.start()
                pipeline = pipe
                container.recorder.pushState(
                    RecorderController.State.Active(
                        config.mode, System.currentTimeMillis(), targetsDesc
                    )
                )
                container.recorder.pushEvent(
                    if (micActive) "Capturing with microphone audio"
                    else "Capturing (no mic)"
                )
                container.analytics.onSessionStarted(config)
            } catch (t: Throwable) {
                container.recorder.pushState(
                    RecorderController.State.Error("Encoder error: ${t.message}")
                )
                stopCapture()
            }
        }
    }

    private fun onProjectionStopped() {
        container.recorder.pushEvent("Capture stopped by the system")
        stopCapture()
    }

    private fun stopCapture() {
        val file = outputFile
        val mode = currentMode
        runCatching { pipeline?.stop() }
        pipeline = null
        runCatching { projection?.stop() }
        projection = null

        container.stream.endSession()
        container.chat.stop()
        container.analytics.onSessionEnded()

        if (file != null && (mode == RecorderConfig.Mode.RECORD || mode == RecorderConfig.Mode.BOTH)) {
            val size = runCatching { file.length() }.getOrDefault(0L)
            if (size > 0) {
                container.recorder.pushEvent(
                    ("Saving ${file.name} (%.1f MB) …").format(size / 1048576f)
                )
                // Publish OFF the main thread. The old synchronous copy of a
                // multi-hundred-MB file blocked the UI thread into an ANR that
                // looked exactly like "the app crashes when I stop recording".
                // A dedicated scope survives even service destruction.
                com.gamershub.core.util.SafeScopes.io("recorder-save").launch {
                    runCatching {
                        MediaScannerConnection.scanFile(
                            this@ScreenRecordService,
                            arrayOf(file.absolutePath), arrayOf("video/mp4"), null
                        )
                        // Publish into the PUBLIC gallery (Movies/GamersHub) —
                        // files in Android/data are invisible to the Gallery.
                        val where = publishToGallery(file)
                        container.recorder.pushEvent(
                            ("Saved: ${file.name} (%.1f MB) → %s").format(size / 1048576f, where)
                        )
                        container.settings.incrementRecordings()
                    }.onFailure {
                        container.recorder.pushEvent("Gallery publish failed: ${it.message}")
                    }
                }
            } else {
                container.recorder.pushEvent("Recording failed — output file was empty")
            }
        }

        outputFile = null
        container.recorder.pushState(RecorderController.State.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Copies the finished recording into MediaStore so the Gallery shows it. */
    private fun publishToGallery(file: File): String {
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(
                        android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/GamersHub"
                    )
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return "gallery insert failed"
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            "Gallery ✓ Movies/GamersHub"
        } catch (t: Throwable) {
            "kept in app folder only (${t.message})"
        }
    }

    private fun newOutputFile(): File {
        val dir = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir,
            "GamersHub"
        )
        dir.mkdirs()
        val name = "GH_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        return File(dir, name)
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.gamershub.action.CAPTURE_START"
        const val ACTION_STOP = "com.gamershub.action.CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_CONFIG = "config"
    }
}
