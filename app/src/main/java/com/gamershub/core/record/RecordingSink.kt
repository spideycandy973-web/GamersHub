package com.gamershub.core.record

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Muxes encoded frames into an MP4 file.
 *
 * Robustness rules (learned the hard way):
 *  - MediaMuxer needs ALL tracks added before start(), so when the mic is
 *    requested we wait for its format — but only for [AUDIO_FORMAT_TIMEOUT_MS].
 *    If the mic never delivers (permission revoked, device busy), we start
 *    video-only instead of silently producing an empty file.
 *  - Every write is guarded so a mid-session failure can't crash the service.
 */
class RecordingSink(private val outputFile: File, private val withAudio: Boolean) : FrameConsumer {

    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var started = false
    private var failed = false
    private var videoFormatSeen = false

    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "gh-mux-watchdog").apply { isDaemon = true }
    }
    private var audioTimeout: ScheduledFuture<*>? = null

    init {
        outputFile.parentFile?.mkdirs()
    }

    @Synchronized
    override fun onVideoFormat(format: MediaFormat) {
        if (failed || videoFormatSeen) return
        videoFormatSeen = true
        try {
            val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            videoTrack = m.addTrack(format)
            if (withAudio) {
                // give the mic pipeline a moment; then go video-only
                audioTimeout = watchdog.schedule({
                    synchronized(this) {
                        if (!started) {
                            Log.w(TAG, "mic format never arrived — starting video-only muxer")
                            maybeStart()
                        }
                    }
                }, AUDIO_FORMAT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } else {
                maybeStart()
            }
        } catch (t: Throwable) {
            failed = true
            Log.e(TAG, "muxer init failed", t)
        }
    }

    @Synchronized
    override fun onAudioFormat(format: MediaFormat) {
        val m = muxer ?: return
        if (failed || started || audioTrack >= 0) return
        try {
            audioTrack = m.addTrack(format)
            maybeStart()
        } catch (t: Throwable) {
            Log.e(TAG, "audio track add failed — continuing video-only", t)
            audioTrack = -1
            maybeStart()
        }
    }

    private fun maybeStart() {
        val m = muxer ?: return
        if (started || failed) return
        val audioReady = !withAudio || audioTrack >= 0
        if (videoTrack >= 0 && audioReady) {
            try {
                m.start()
                started = true
                audioTimeout?.cancel(false)
                audioTimeout = null
                Log.i(TAG, "muxer started (audio=${audioTrack >= 0})")
            } catch (t: Throwable) {
                failed = true
                Log.e(TAG, "muxer start failed", t)
            }
        }
    }

    @Synchronized
    override fun onVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        if (!started || failed || videoTrack < 0 || info.size <= 0) return
        try {
            data.position(info.offset)
            data.limit(info.offset + info.size)
            m.writeSampleData(videoTrack, data, info)
        } catch (t: Throwable) {
            failed = true
            Log.e(TAG, "video write failed", t)
        }
    }

    @Synchronized
    override fun onAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        if (!started || failed || audioTrack < 0 || info.size <= 0) return
        try {
            data.position(info.offset)
            data.limit(info.offset + info.size)
            m.writeSampleData(audioTrack, data, info)
        } catch (t: Throwable) {
            Log.e(TAG, "audio write failed", t)
        }
    }

    @Synchronized
    override fun onSessionEnded() {
        audioTimeout?.cancel(false)
        audioTimeout = null
        runCatching { watchdog.shutdownNow() }
        runCatching { if (started && !failed) muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        started = false
    }

    companion object {
        private const val TAG = "RecordingSink"
        private const val AUDIO_FORMAT_TIMEOUT_MS = 2_000L
    }
}
