package com.gamershub.core.record

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/** Muxes encoded frames into an MP4 file. */
class RecordingSink(private val outputFile: File, private val withAudio: Boolean) : FrameConsumer {

    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var started = false
    private var failed = false

    init {
        outputFile.parentFile?.mkdirs()
    }

    @Synchronized
    override fun onVideoFormat(format: MediaFormat) {
        try {
            val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            videoTrack = m.addTrack(format)
            maybeStart()
        } catch (t: Throwable) {
            failed = true
            Log.e(TAG, "muxer init failed", t)
        }
    }

    @Synchronized
    override fun onAudioFormat(format: MediaFormat) {
        val m = muxer ?: return
        try {
            audioTrack = m.addTrack(format)
            maybeStart()
        } catch (t: Throwable) {
            Log.e(TAG, "audio track add failed", t)
        }
    }

    private fun maybeStart() {
        val m = muxer ?: return
        if (!started && videoTrack >= 0 && (!withAudio || audioTrack >= 0)) {
            m.start()
            started = true
        }
    }

    @Synchronized
    override fun onVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        if (!started || videoTrack < 0) return
        try {
            data.position(info.offset)
            data.limit(info.offset + info.size)
            m.writeSampleData(videoTrack, data, info)
        } catch (t: Throwable) {
            Log.e(TAG, "video write failed", t)
        }
    }

    @Synchronized
    override fun onAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        if (!started || audioTrack < 0) return
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
        runCatching { if (started) muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        started = false
    }

    companion object {
        private const val TAG = "RecordingSink"
    }
}
