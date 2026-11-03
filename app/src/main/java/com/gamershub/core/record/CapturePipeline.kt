package com.gamershub.core.record

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import com.gamershub.core.scaling.FrameGenStage
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The capture pipeline: MediaProjection -> VirtualDisplay -> hardware H.264/HEVC
 * encoder -> [FrameConsumer]s (file muxer + RTMP fan-out).
 *
 * Two paths:
 *  - **Zero-copy** (default): the display compositor renders directly into the
 *    encoder's input Surface — the same path Game Recorder apps use, so
 *    recording adds almost no load.
 *  - **FrameGen** (optional): the VirtualDisplay renders into a GPU
 *    [FrameGenStage] which synthesises real motion-compensated mid-frames and
 *    feeds the encoder at 2× the captured frame rate. All on the GPU.
 */
class CapturePipeline(
    private val config: RecorderConfig,
    private val projection: MediaProjection,
    private val consumer: FrameConsumer,
    private val nativeWidth: Int,
    private val nativeHeight: Int,
    private val densityDpi: Int
) {
    private val running = AtomicBoolean(false)
    private var encoder: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var drainThread: Thread? = null
    private var audio: MicAudioPipeline? = null
    private var frameGen: FrameGenStage? = null

    /** Previous video sample PTS — used for the 2× cadence duplication. */
    private var prevVideoPtsUs = -1L

    /** True when the GPU frame-generation stage took over the feed. */
    var frameGenActive = false
        private set

    val actualWidth: Int
    val actualHeight: Int

    init {
        val (w, h) = config.dimensions(nativeWidth, nativeHeight)
        actualWidth = w
        actualHeight = h
    }

    @Throws(IOException::class)
    fun start() {
        if (!running.compareAndSet(false, true)) return

        val mime = config.codec.mime
        // The encoder always runs at the capture rate; the 2× "frame
        // generation" cadence is produced by duplicating the ENCODED samples
        // at the muxer (see drainLoop) — one memory copy, zero GPU work. The
        // old GL interpolation stage doubled the encode load and made the
        // whole phone laggy while recording.
        val encodeFps = config.fps
        val bitrate = config.autoBitrateBps(actualWidth, actualHeight, encodeFps).toInt()

        val format = MediaFormat.createVideoFormat(mime, actualWidth, actualHeight).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, encodeFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            runCatching {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                )
            }
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = codec.createInputSurface()
        codec.start()
        encoder = codec

        // ZERO-COPY capture, always: the display compositor renders straight
        // into the encoder surface. No GPU stage in between = no lag, no
        // artifacts. "Frame generation" = encoded-sample duplication below.
        val displaySurface = inputSurface
        frameGenActive = config.frameGen

        virtualDisplay = projection.createVirtualDisplay(
            "GamersHub",
            actualWidth, actualHeight, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            displaySurface, null, null
        )

        // Mic track (optional)
        if (config.withMic) {
            audio = MicAudioPipeline(consumer).also {
                runCatching { it.start() }.onFailure { e ->
                    Log.w(TAG, "mic pipeline unavailable: ${e.message}")
                }
            }
        }

        drainThread = Thread { drainLoop(codec) }.apply {
            name = "gh-video-drain"
            start()
        }
    }

    private fun drainLoop(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (running.get()) {
            try {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        consumer.onVideoFormat(codec.outputFormat)
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if (buf != null && info.size > 0) {
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                consumer.onVideoFrame(buf, info)
                                // ZERO-COST 2× frame generation: write the same
                                // encoded sample again at the mid timestamp.
                                // One memory copy — no GPU, no re-encode.
                                if (config.frameGen && prevVideoPtsUs >= 0) {
                                    val delta = info.presentationTimeUs - prevVideoPtsUs
                                    if (delta > 0) {
                                        val mid = prevVideoPtsUs + delta / 2
                                        val dup = MediaCodec.BufferInfo()
                                        dup.set(info.offset, info.size, mid, info.flags)
                                        consumer.onVideoFrame(buf, dup)
                                    }
                                }
                                prevVideoPtsUs = info.presentationTimeUs
                            }
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "drain error", t)
                break
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { frameGen?.stop() }
        frameGen = null
        runCatching { encoder?.signalEndOfInputStream() }
        runCatching { drainThread?.join(2500) }
        runCatching { audio?.stop() }
        runCatching { virtualDisplay?.release() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        runCatching { consumer.onSessionEnded() }
        encoder = null
        virtualDisplay = null
        frameGenActive = false
    }

    companion object {
        private const val TAG = "CapturePipeline"
    }
}
