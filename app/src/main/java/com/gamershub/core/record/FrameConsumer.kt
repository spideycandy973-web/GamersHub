package com.gamershub.core.record

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Anything that consumes the encoded A/V bitstream from [CapturePipeline]:
 * the MP4 muxer, the RTMP stream fan-out, or both (composite).
 */
interface FrameConsumer {
    fun onVideoFormat(format: MediaFormat)
    fun onVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo)
    fun onAudioFormat(format: MediaFormat)
    fun onAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo)
    fun onSessionEnded()
}

/** Forwards every callback to all children (each gets its own buffer view). */
class CompositeConsumer(private val children: List<FrameConsumer>) : FrameConsumer {
    override fun onVideoFormat(format: MediaFormat) =
        children.forEach { it.onVideoFormat(format) }

    override fun onVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) =
        children.forEach { it.onVideoFrame(data.duplicate(), MediaCodec.BufferInfo().apply { copyFrom(info) }) }

    override fun onAudioFormat(format: MediaFormat) =
        children.forEach { it.onAudioFormat(format) }

    override fun onAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) =
        children.forEach { it.onAudioFrame(data.duplicate(), MediaCodec.BufferInfo().apply { copyFrom(info) }) }

    override fun onSessionEnded() = children.forEach { it.onSessionEnded() }

    private fun MediaCodec.BufferInfo.copyFrom(src: MediaCodec.BufferInfo) {
        set(src.offset, src.size, src.presentationTimeUs, src.flags)
    }
}
