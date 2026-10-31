package com.gamershub.core.stream

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.gamershub.core.record.FrameConsumer
import com.gamershub.core.record.RecorderConfig
import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.stream.rtmp.RtmpConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-platform live streaming engine: fans out the encoder bitstream to every
 * enabled platform over parallel RTMP/RTMPS connections (YouTube + Facebook +
 * Instagram simultaneously).
 */
class StreamEngine(
    private val context: Context,
    private val settings: SettingsRepository
) {
    data class Platform(
        val id: String,
        val label: String,
        val baseUrl: String,
        val help: String
    )

    data class TargetStats(
        val status: String = "idle",
        val bytesSent: Long = 0,
        val framesSent: Long = 0,
        val drops: Long = 0,
        val error: String? = null
    )

    private val _stats = MutableStateFlow<Map<String, TargetStats>>(emptyMap())
    val stats: StateFlow<Map<String, TargetStats>> = _stats.asStateFlow()

    private val _liveSinceMs = MutableStateFlow<Long?>(null)
    val liveSinceMs: StateFlow<Long?> = _liveSinceMs.asStateFlow()

    @Volatile private var session: StreamSession? = null
    private val scope = com.gamershub.core.util.SafeScopes.io("stream")

    companion object {
        val PLATFORMS = listOf(
            Platform(
                "youtube", "YouTube", "rtmp://a.rtmp.youtube.com/live2",
                "YouTube Studio → Go Live → copy the Stream key"
            ),
            Platform(
                "facebook", "Facebook", "rtmps://live-api-s.facebook.com:443/rtmp",
                "facebook.com/live/producer → Stream key (enable the persistent key)"
            ),
            Platform(
                "instagram", "Instagram", "rtmps://live-upload.instagram.com:443/rtmp",
                "Instagram Live Producer (professional account required)"
            ),
        )

        fun platformById(id: String): Platform? = PLATFORMS.firstOrNull { it.id == id }

        private const val TAG = "StreamEngine"
    }

    /** Streams the encoder output to every connected target. */
    inner class StreamSession(
        private val connections: List<RtmpConnection>
    ) : FrameConsumer {
        val targetNames: List<String> = connections.map { it.name }
        private val videoPtsOffset = AtomicLong(-1)

        override fun onVideoFormat(format: MediaFormat) {
            format.getByteBuffer("csd-0")?.toByteArray()?.let { csd ->
                connections.forEach { it.sendVideoConfig(csd) }
            }
        }

        override fun onVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
            val bytes = ByteArray(info.size)
            data.position(info.offset)
            data.get(bytes)
            if (videoPtsOffset.get() < 0) videoPtsOffset.set(info.presentationTimeUs)
            val ptsMs = (info.presentationTimeUs - videoPtsOffset.get()) / 1000
            val keyframe = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
            connections.forEach { it.sendVideo(bytes, keyframe, ptsMs) }
        }

        override fun onAudioFormat(format: MediaFormat) {
            format.getByteBuffer("csd-0")?.toByteArray()?.let { asc ->
                connections.forEach { it.sendAudioConfig(asc) }
            }
        }

        override fun onAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
            val bytes = ByteArray(info.size)
            data.position(info.offset)
            data.get(bytes)
            val ptsMs = info.presentationTimeUs / 1000
            connections.forEach { it.sendAudio(bytes, ptsMs) }
        }

        override fun onSessionEnded() {
            connections.forEach { it.close() }
        }

        fun snapshot(): Map<String, TargetStats> = connections.associate { conn ->
            conn.name to TargetStats(
                status = conn.status.name.lowercase(),
                bytesSent = conn.bytesSent.get(),
                framesSent = conn.framesSent.get(),
                drops = conn.videoDrops.get(),
                error = conn.lastError
            )
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val dup = duplicate()
        val pos = dup.position()
        val lim = dup.limit()
        dup.position(0)
        dup.limit(dup.capacity())
        val b = ByteArray(dup.remaining())
        dup.get(b)
        dup.position(pos)
        dup.limit(lim)
        return b
    }

    /** Enabled platforms that have a non-empty stream key. */
    suspend fun enabledTargets(): List<Pair<Platform, String>> {
        return PLATFORMS.mapNotNull { p ->
            val enabled = settings.streamEnabled(p.id).first()
            val key = settings.streamKey(p.id).first()
            if (enabled && key.isNotBlank()) p to key else null
        }
    }

    /**
     * Connects all enabled targets in parallel (one automatic retry each) and
     * returns a session [FrameConsumer], or null when nothing could go live.
     */
    suspend fun startSession(config: RecorderConfig, width: Int, height: Int): StreamSession? {
        endSession() // safety
        val targets = enabledTargets()
        if (targets.isEmpty()) {
            Log.w(TAG, "startSession: no enabled targets with stream keys")
            return null
        }
        val meta = RtmpConnection.Metadata(
            width, height, config.fps,
            config.autoBitrateBps(width, height).toInt(), config.withMic
        )

        val conns = coroutineScope {
            targets.map { (platform, key) ->
                async(Dispatchers.IO) {
                    var conn = RtmpConnection(platform.label, platform.baseUrl, key, meta)
                    if (!conn.connect()) {
                        conn.close()
                        delay(2000)
                        conn = RtmpConnection(platform.label, platform.baseUrl, key, meta)
                        if (!conn.connect()) {
                            conn.close()
                            return@async null
                        }
                    }
                    conn
                }
            }.awaitAll()
        }.filterNotNull()

        if (conns.isEmpty()) return null

        val s = StreamSession(conns)
        session = s
        _liveSinceMs.value = System.currentTimeMillis()

        scope.launch {
            while (isActive) {
                val sess = session ?: break
                _stats.value = sess.snapshot()
                settings.addStreamSeconds(1)
                delay(1000)
            }
        }
        return s
    }

    fun endSession() {
        session?.onSessionEnded()
        session = null
        _liveSinceMs.value = null
        _stats.value = emptyMap()
    }

    val sessionActive: Boolean get() = session != null
    fun currentSessionTargets(): List<String> = session?.targetNames ?: emptyList()
}
