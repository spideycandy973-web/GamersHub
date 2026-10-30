package com.gamershub.core.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Mic -> AAC hardware encoder -> [FrameConsumer]. Started only when permitted. */
class MicAudioPipeline(private val consumer: FrameConsumer) {

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var encoder: MediaCodec? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO first
    fun start() {
        if (!running.compareAndSet(false, true)) return

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNELS, ENCODING,
            maxOf(minBuf, 8192)
        )
        check(ar.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }

        val format = MediaFormat.createAudioFormat(MIME, SAMPLE_RATE, 2).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        encoder = codec
        record = ar
        ar.startRecording()

        thread = Thread { loop(codec, ar) }.apply { name = "gh-audio"; start() }
    }

    private fun loop(codec: MediaCodec, ar: AudioRecord) {
        val inBuf = ByteArray(4096)
        val info = MediaCodec.BufferInfo()
        var pts = System.nanoTime() / 1000
        while (running.get()) {
            // ---- feed PCM in
            val read = ar.read(inBuf, 0, inBuf.size)
            if (read > 0) {
                val inIdx = codec.dequeueInputBuffer(2_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)
                    buf?.clear()
                    buf?.put(inBuf, 0, read)
                    pts += (read / 4L) * 1_000_000L / SAMPLE_RATE // 16-bit stereo => 4 bytes/frame
                    codec.queueInputBuffer(inIdx, 0, read, pts, 0)
                }
            }
            // ---- drain AAC out
            var outIdx = codec.dequeueOutputBuffer(info, 0)
            while (outIdx >= 0 || outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    consumer.onAudioFormat(codec.outputFormat)
                } else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0 &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        consumer.onAudioFrame(buf, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
                outIdx = codec.dequeueOutputBuffer(info, 0)
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { thread?.join(1500) }
        runCatching { record?.stop() }
        runCatching { record?.release() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        thread = null
        record = null
        encoder = null
    }

    companion object {
        private const val MIME = "audio/mp4a-latm"
        private const val SAMPLE_RATE = 44_100
        private const val CHANNELS = AudioFormat.CHANNEL_IN_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "MicAudioPipeline"
    }
}
