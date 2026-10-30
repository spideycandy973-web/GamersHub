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
import android.os.Parcelable
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                val data: Intent? = intent.parcelable(EXTRA_RESULT_DATA)
                val config = intent.parcelable<RecorderConfig>(EXTRA_CONFIG)
                    ?: RecorderConfig()
                startCapture(code, data, config)
            }
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent?, config: RecorderConfig) {
        if (data == null) {
            container.recorder.pushState(RecorderController.State.Error("Missing projection token"))
            stopSelf()
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
                this@ScreenRecordService.onStop()
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

            if (config.mode == RecorderConfig.Mode.RECORD || config.mode == RecorderConfig.Mode.BOTH) {
                val file = newOutputFile()
                outputFile = file
                consumers += RecordingSink(file, config.withMic)
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
                container.analytics.onSessionStarted(config)
            } catch (t: Throwable) {
                container.recorder.pushState(RecorderController.State.Error("Encoder error: ${t.message}"))
                onStop()
            }
        }
    }

    private fun onStop() {
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
            MediaScannerConnection.scanFile(
                this, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
            )
            container.recorder.pushEvent("Saved: ${file.name}")
            serviceScope.launch { container.settings.incrementRecordings() }
        }

        container.recorder.pushState(RecorderController.State.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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

        @Suppress("UNCHECKED_CAST")
        private fun <T : Parcelable> Intent.parcelable(key: String): T? {
            return if (Build.VERSION.SDK_INT >= 33) {
                getParcelableExtra(key, RecorderConfig::class.java) as? T
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key) as? T
            }
        }
    }
}
