package com.gamershub.core.record

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Capture / encode configuration for the recorder & streamer. */
@Parcelize
data class RecorderConfig(
    /** Target long-edge resolution: 720 / 1080 / 1440 / 2160 */
    val verticalResolution: Int = 1080,
    val fps: Int = 60,
    val codec: Codec = Codec.H264,
    /** 0 = auto */
    val bitrateMbps: Int = 0,
    val withMic: Boolean = false,
    val mode: Mode = Mode.RECORD,
    /** Real GPU frame generation (2× output via motion interpolation). */
    val frameGen: Boolean = false,
    /** Unsharp-mask strength for the GPU frame pipeline (0f = off). */
    val sharpen: Float = 0f
) : Parcelable {
    enum class Codec(val mime: String) {
        H264("video/avc"),
        HEVC("video/hevc")
    }

    enum class Mode {
        /** Only save an MP4 locally. */
        RECORD,
        /** Only push to RTMP targets. */
        STREAM,
        /** Save locally AND stream. */
        BOTH
    }

    val bothVideo: Boolean get() = true

    fun dimensions(nativeW: Int, nativeH: Int): Pair<Int, Int> {
        // Fit a canvas with the same aspect ratio, with short edge = verticalResolution.
        val portrait = nativeH >= nativeW
        val shortEdge = verticalResolution.coerceIn(360, 2160)
        val scale = shortEdge.toFloat() / (if (portrait) nativeW else nativeH)
        var w = (nativeW * scale).toInt()
        var h = (nativeH * scale).toInt()
        // Encoders want even dimensions
        w -= w % 2
        h -= h % 2
        return w to h
    }

    /** Sensible default bitrate for the given canvas and encode rate. */
    fun autoBitrateBps(width: Int, height: Int, encodeFps: Int = fps): Long {
        val bpp = if (codec == Codec.HEVC) 0.075 else 0.10
        val raw = (width.toLong() * height * encodeFps * bpp).toLong()
        val capMbps = if (bitrateMbps > 0) bitrateMbps.toLong() * 1_000_000 else Long.MAX_VALUE
        val floor = 2_000_000L
        val ceiling = minOf(capMbps, 80_000_000L)
        return raw.coerceIn(floor, ceiling)
    }
}
