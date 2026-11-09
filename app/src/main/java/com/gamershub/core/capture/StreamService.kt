package com.gamershub.core.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import java.nio.ByteBuffer

/**
 * Real RTMP live stream.
 *
 * MediaProjection → VirtualDisplay → MediaCodec H.264 → [RtmpClient] → the
 * ingest server. The stream is published over the actual RTMP protocol, so the
 * receiver (YouTube / Twitch / any RTMP endpoint) shows the live picture.
 */
class StreamService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var rtmp: RtmpClient? = null
    private var worker: Thread? = null
    private var projectionCallback: MediaProjection.Callback? = null

    @Volatile
    private var streaming = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
    }

    /** API 34 requires the media-projection type when the service goes foreground. */
    private fun startForegroundSafely() {
        val type = if (Build.VERSION.SDK_INT >= 29)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        runCatching {
            androidx.core.app.ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        }.recoverCatching {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            ACTION_STOP -> {
                stopStream()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(RecorderService.EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(RecorderService.EXTRA_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(RecorderService.EXTRA_DATA)
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                if (resultCode != 0 && data != null) {
                    begin(resultCode, data, url, intent)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun begin(resultCode: Int, data: Intent, url: String, intent: Intent) {
        if (Stream.isStreaming) return
        val endpoint = RtmpClient.parse(url)
        if (endpoint == null) {
            Stream.lastError = "Invalid rtmp:// URL"
            stopSelf()
            return
        }
        val height = intent.getIntExtra(RecorderService.EXTRA_HEIGHT, 1080)
        val fps = intent.getIntExtra(RecorderService.EXTRA_FPS, 60)
        val bitrate = intent.getIntExtra(RecorderService.EXTRA_BITRATE, 8_000_000)

        Stream.url = url
        Stream.status = "Connecting"
        Stream.lastError = ""
        Stream.startedAt = System.currentTimeMillis()
        Stream.bytesSent = 0L
        Stream.fps = 0

        val metrics = resources.displayMetrics
        val size = evenSize(metrics, height)

        val client = RtmpClient(url)
        rtmp = client
        if (!client.connect()) {
            Stream.lastError = client.lastError.ifBlank { "RTMP connect failed" }
            Stream.status = "Idle"
            stopSelf()
            return
        }
        client.setChunkSize(4096)

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = manager.getMediaProjection(resultCode, data)
        if (mp == null) {
            Stream.lastError = "Screen capture permission denied"
            stopSelf()
            return
        }
        projection = mp
        val cb = object : MediaProjection.Callback() {
            override fun onStop() {
                stopStream()
            }
        }
        projectionCallback = cb
        mp.registerCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.first, size.second).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceIn(24, 120))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }

        val encoder = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        } catch (t: Throwable) {
            Stream.lastError = t.message ?: "No H.264 encoder"
            stopSelf()
            return
        }
        codec = encoder
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (t: Throwable) {
            Stream.lastError = t.message ?: "Encoder configure failed"
            stopSelf()
            return
        }
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        virtualDisplay = mp.createVirtualDisplay(
            "gamershub-live",
            size.first,
            size.second,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        )

        streaming = true
        Stream.isStreaming = true
        Stream.status = "Live"
        startForegroundSafely()
        startDrain()
    }

    private fun startDrain() {
        worker = Thread {
            val encoder = codec ?: return@Thread
            val client = rtmp ?: return@Thread
            val info = MediaCodec.BufferInfo()
            var frames = 0
            var lastStat = System.currentTimeMillis()
            var configSent = false
            while (streaming) {
                try {
                    when (val index = encoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = encoder.outputFormat
                            val csd0 = outFormat.getByteBuffer("csd-0")
                            val csd1 = outFormat.getByteBuffer("csd-1")
                            val config = buildAvcConfig(csd0, csd1)
                            if (config != null) {
                                client.writeVideoConfig(config)
                                configSent = true
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // encoder still working
                        }
                        else -> {
                            if (index >= 0) {
                                val buffer = encoder.getOutputBuffer(index)
                                if (buffer != null && info.size > 0) {
                                    val data = ByteArray(info.size)
                                    buffer.position(info.offset)
                                    buffer.get(data)
                                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                                    if (isConfig) {
                                        if (!configSent) {
                                            val config = buildAvcConfigFromCodecConfig(data)
                                            if (config != null) {
                                                client.writeVideoConfig(config)
                                                configSent = true
                                            }
                                        }
                                    } else {
                                        val avcc = annexBToAvcc(data)
                                        if (avcc.isNotEmpty()) {
                                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                                            client.writeVideo(avcc, info.presentationTimeUs / 1000L, key)
                                            frames++
                                        }
                                    }
                                }
                                encoder.releaseOutputBuffer(index, false)
                            }
                        }
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastStat >= 1000) {
                        Stream.fps = frames
                        frames = 0
                        lastStat = now
                        Stream.bytesSent = client.bytesSent
                        if (!client.connected && client.lastError.isNotBlank()) {
                            Stream.lastError = client.lastError
                            Stream.status = "Dropped"
                            streaming = false
                        }
                    }
                } catch (t: Throwable) {
                    Stream.lastError = t.message ?: "stream error"
                    Stream.status = "Error"
                    streaming = false
                }
            }
            Stream.isStreaming = false
            Stream.status = "Idle"
        }.also { it.isDaemon = true; it.start() }
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

    /** Splits Annex-B NALUs and re-frames them as AVCC (4-byte length prefix). */
    private fun annexBToAvcc(data: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream(data.size + 32)
        var i = 0
        var naluStart = -1
        while (i + 2 < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data[i + 1].toInt() and 0xFF
            val b2 = data[i + 2].toInt() and 0xFF
            if (b0 == 0 && b1 == 0 && b2 == 1) {
                if (naluStart >= 0) writeNalu(output, data, naluStart, i)
                i += 3
                naluStart = i
            } else if (b0 == 0 && b1 == 0 && b2 == 0 && i + 3 < data.size && (data[i + 3].toInt() and 0xFF) == 1) {
                if (naluStart >= 0) writeNalu(output, data, naluStart, i)
                i += 4
                naluStart = i
            } else {
                i++
            }
        }
        if (naluStart >= 0) writeNalu(output, data, naluStart, data.size)
        if (output.size() == 0) writeNalu(output, data, 0, data.size)
        return output.toByteArray()
    }

    private fun writeNalu(output: java.io.ByteArrayOutputStream, data: ByteArray, from: Int, to: Int) {
        if (to <= from) return
        output.write(ByteBuffer.allocate(4).putInt(to - from).array())
        output.write(data, from, to - from)
    }

    private fun buildAvcConfig(csd0: ByteBuffer?, csd1: ByteBuffer?): ByteArray? {
        val sps = toArray(csd0) ?: return null
        val pps = toArray(csd1) ?: return null
        return avcRecord(stripStartCodes(sps), stripStartCodes(pps))
    }

    private fun buildAvcConfigFromCodecConfig(data: ByteArray): ByteArray? = try {
        // codec config contains SPS and PPS in Annex-B form
        val parts = splitNalus(data)
        if (parts.size >= 2) avcRecord(parts[0], parts[1]) else null
    } catch (_: Throwable) {
        null
    }

    private fun splitNalus(data: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var i = 0
        var lastStart = -1
        while (i + 2 < data.size) {
            if ((data[i].toInt() and 0xFF) == 0 && (data[i + 1].toInt() and 0xFF) == 0 && (data[i + 2].toInt() and 0xFF) == 1) {
                if (lastStart >= 0) out.add(data.copyOfRange(lastStart, i))
                i += 3
                lastStart = i
            } else {
                i++
            }
        }
        if (lastStart >= 0 && lastStart < data.size) out.add(data.copyOfRange(lastStart, data.size))
        return out
    }

    private fun stripStartCodes(data: ByteArray): ByteArray {
        var i = 0
        while (i + 3 < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data[i + 1].toInt() and 0xFF
            val b2 = data[i + 2].toInt() and 0xFF
            if (b0 == 0 && b1 == 0 && b2 == 1) {
                return data.copyOfRange(i + 3, data.size)
            }
            if (b0 == 0 && b1 == 0 && b2 == 0 && (data[i + 3].toInt() and 0xFF) == 1) {
                return data.copyOfRange(i + 4, data.size)
            }
            i++
        }
        return data
    }

    private fun avcRecord(sps: ByteArray, pps: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream(sps.size + pps.size + 11)
        output.write(0x01)
        output.write(sps.getOrElse(1) { 0x42.toByte() }.toInt())
        output.write(sps.getOrElse(2) { 0x00.toByte() }.toInt())
        output.write(sps.getOrElse(3) { 0x1E.toByte() }.toInt())
        output.write(0xFF)
        output.write(0xE1)
        output.write((sps.size shr 8) and 0xFF)
        output.write(sps.size and 0xFF)
        output.write(sps)
        output.write(0x01)
        output.write((pps.size shr 8) and 0xFF)
        output.write(pps.size and 0xFF)
        output.write(pps)
        return output.toByteArray()
    }

    private fun toArray(buffer: ByteBuffer?): ByteArray? {
        if (buffer == null) return null
        val copy = buffer.duplicate()
        val array = ByteArray(copy.remaining())
        copy.get(array)
        return array
    }

    private fun stopStream() {
        streaming = false
        Stream.isStreaming = false
        Stream.status = "Idle"
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { rtmp?.close() }
        rtmp = null
        runCatching {
            projectionCallback?.let { cb -> projection?.unregisterCallback(cb) }
            projection?.stop()
        }
        projectionCallback = null
        projection = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        streaming = false
        Stream.isStreaming = false
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Live stream", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            42,
            Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return builder
            .setContentTitle("Live on RTMP")
            .setContentText(Stream.url.take(48))
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "live"
        private const val NOTIF_ID = 57
        const val ACTION_START = "com.gamershub.LIVE_START"
        const val ACTION_STOP = "com.gamershub.LIVE_STOP"
        const val EXTRA_URL = "url"

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            url: String,
            height: Int,
            fps: Int,
            bitrate: Int
        ) {
            val intent = Intent(context, StreamService::class.java).apply {
                action = ACTION_START
                putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecorderService.EXTRA_DATA, data)
                putExtra(EXTRA_URL, url)
                putExtra(RecorderService.EXTRA_HEIGHT, height)
                putExtra(RecorderService.EXTRA_FPS, fps)
                putExtra(RecorderService.EXTRA_BITRATE, bitrate)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure {
                com.gamershub.GamersHubApp.toast("Go Live couldn't start — screen-capture permission missing")
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, StreamService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}

/** Shared live-stream state. */
object Stream {

    private val streamingState = androidx.compose.runtime.mutableStateOf(false)

    var isStreaming: Boolean
        get() = streamingState.value
        set(value) {
            streamingState.value = value
        }

    @Volatile
    var status: String = "Idle"

    @Volatile
    var url: String = ""

    @Volatile
    var lastError: String = ""

    @Volatile
    var bytesSent: Long = 0L

    @Volatile
    var fps: Int = 0

    @Volatile
    var startedAt: Long = 0L

    fun elapsedMs(): Long = if (isStreaming) System.currentTimeMillis() - startedAt else 0L

    fun mbps(): Double {
        val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
        if (seconds <= 0.0) return 0.0
        return (bytesSent * 8.0) / (seconds * 1_000_000.0)
    }
}
