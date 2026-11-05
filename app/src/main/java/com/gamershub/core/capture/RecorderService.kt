package com.gamershub.core.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real screen recorder.
 *
 * MediaProjection → VirtualDisplay → MediaRecorder (H.264/AAC) → MP4 in
 * Movies/GamersHub. Nothing is simulated: the file on disk is a standard MP4
 * that any gallery or editor can open, and it is verified after the stop.
 */
class RecorderService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        if (intent.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
            if (resultCode != 0 && data != null) {
                begin(resultCode, data, intent)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun begin(resultCode: Int, data: Intent, intent: Intent) {
        if (Recorder.isRecording) return
        val mic = intent.getBooleanExtra(EXTRA_MIC, false)
        val targetHeight = intent.getIntExtra(EXTRA_HEIGHT, 1080)
        val fps = intent.getIntExtra(EXTRA_FPS, 60)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 12_000_000)

        val metrics = resources.displayMetrics
        val size = evenSize(metrics, targetHeight)

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = manager.getMediaProjection(resultCode, data)
        if (mp == null) {
            stopSelf()
            return
        }
        projection = mp
        val cb = object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
            }
        }
        projectionCallback = cb
        mp.registerCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))

        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MOVIES
        ), "GamersHub")
        if (!dir.exists()) dir.mkdirs()
        val name = "GH_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val file = File(dir, name)
        outputFile = file

        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()
        recorder = rec
        try {
            if (mic) {
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioSamplingRate(44_100)
                rec.setAudioEncodingBitRate(128_000)
            }
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(size.first, size.second)
            rec.setVideoFrameRate(fps.coerceIn(24, 144))
            rec.setVideoEncodingBitRate(bitrate)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
        } catch (t: Throwable) {
            Recorder.lastError = t.message ?: "prepare failed"
            cleanup()
            stopSelf()
            return
        }

        val surface = rec.surface
        virtualDisplay = mp.createVirtualDisplay(
            "gamershub-recorder",
            size.first,
            size.second,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )
        try {
            rec.start()
        } catch (t: Throwable) {
            Recorder.lastError = t.message ?: "start failed"
            cleanup()
            stopSelf()
            return
        }
        Recorder.setRecording(true)
        Recorder.startedAt = System.currentTimeMillis()
        Recorder.lastPath = file.absolutePath
        Recorder.micEnabled = mic
        updateNotification()
    }

    private fun evenSize(metrics: DisplayMetrics, targetHeight: Int): Pair<Int, Int> {
        val srcW = metrics.widthPixels
        val srcH = metrics.heightPixels
        val scale = if (srcH > 0) targetHeight.toFloat() / srcH else 1f
        var w = (srcW * scale).toInt()
        var h = targetHeight
        if (w % 2 != 0) w -= 1
        if (h % 2 != 0) h -= 1
        if (w < 2) w = 2
        if (h < 2) h = 2
        return w to h
    }

    private fun stopRecording() {
        val file = outputFile
        Recorder.setRecording(false)
        runCatching { recorder?.stop() }
        cleanup()
        if (file != null && file.exists() && file.length() > 1024) {
            Recorder.lastPath = file.absolutePath
            Recorder.lastSizeBytes = file.length()
            Recorder.lastDurationMs = System.currentTimeMillis() - Recorder.startedAt
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null
            )
        } else {
            file?.delete()
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun cleanup() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { recorder?.release() }
        recorder = null
        runCatching {
            projectionCallback?.let { cb -> projection?.unregisterCallback(cb) }
            projection?.stop()
        }
        projectionCallback = null
        projection = null
    }

    override fun onDestroy() {
        if (Recorder.isRecording) stopRecording()
        cleanup()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen recorder", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            41,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return builder
            .setContentTitle("Recording the screen")
            .setContentText("GamersHub · tap stop to save the MP4")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL = "recorder"
        private const val NOTIF_ID = 56
        const val ACTION_START = "com.gamershub.RECORD_START"
        const val ACTION_STOP = "com.gamershub.RECORD_STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_MIC = "mic"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            mic: Boolean,
            height: Int,
            fps: Int,
            bitrate: Int
        ) {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_MIC, mic)
                putExtra(EXTRA_HEIGHT, height)
                putExtra(EXTRA_FPS, fps)
                putExtra(EXTRA_BITRATE, bitrate)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecorderService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun captureIntent(context: Context): Intent? = try {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            manager.createScreenCaptureIntent()
        } catch (_: Throwable) {
            null
        }
    }
}

/** Shared recorder state + the recordings library. */
object Recorder {

    private val recordingState = androidx.compose.runtime.mutableStateOf(false)

    var isRecording: Boolean
        get() = recordingState.value
        private set(value) {
            recordingState.value = value
        }

    @Volatile
    var startedAt = 0L

    @Volatile
    var lastPath: String = ""

    @Volatile
    var lastSizeBytes: Long = 0L

    @Volatile
    var lastDurationMs: Long = 0L

    @Volatile
    var lastError: String = ""

    @Volatile
    var micEnabled = false

    internal fun setRecording(value: Boolean) {
        isRecording = value
    }

    fun elapsedMs(): Long = if (isRecording) System.currentTimeMillis() - startedAt else 0L

    fun folder(): File = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
        "GamersHub"
    )

    fun recordings(): List<File> = try {
        folder().listFiles { f -> f.isFile && f.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    fun delete(file: File): Boolean = try {
        file.delete()
    } catch (_: Throwable) {
        false
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }
}
