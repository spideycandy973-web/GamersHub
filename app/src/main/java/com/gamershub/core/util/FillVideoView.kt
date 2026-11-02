package com.gamershub.core.util

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

/**
 * A VideoView that always measures to the FULL size of its parent.
 *
 * The stock [VideoView] shrinks itself to the video's own aspect ratio during
 * measurement — which is exactly why the intro video floated in the middle of
 * the screen with bars around it, no matter how the player was scaled. This
 * subclass forces the full window size; combined with
 * `MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING` the video is
 * cropped to cover the display edge-to-edge.
 */
class FillVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : VideoView(context, attrs, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        val h = android.view.View.MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            setMeasuredDimension(w, h)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
