package com.gamershub.core.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Invisible consent proxy.
 *
 * MediaProjection always needs the system consent dialog, which only an Activity
 * can request. The overlay panel starts this activity, the user taps "Start now",
 * the capture service is started and the activity disappears again — the running
 * game is never closed and never has to be relaunched.
 */
class ProjectionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as android.media.projection.MediaProjectionManager
        val capture = runCatching { manager.createScreenCaptureIntent() }.getOrNull()
        if (capture == null) {
            finish()
            return
        }
        startActivityForResult(capture, REQ)
    }

    @Deprecated("MediaProjection consent is a legacy activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RECORD
            if (mode == MODE_LIVE) {
                Stream.start(
                    this,
                    resultCode,
                    data,
                    intent?.getStringExtra(EXTRA_URL) ?: "",
                    intent?.getIntExtra(EXTRA_HEIGHT, 1080) ?: 1080,
                    intent?.getIntExtra(EXTRA_FPS, 60) ?: 60,
                    intent?.getIntExtra(EXTRA_BITRATE, 8_000_000) ?: 8_000_000
                )
            } else {
                RecorderService.start(
                    this,
                    resultCode,
                    data,
                    mic = false,
                    height = intent?.getIntExtra(EXTRA_HEIGHT, 1080) ?: 1080,
                    fps = intent?.getIntExtra(EXTRA_FPS, 60) ?: 60,
                    bitrate = intent?.getIntExtra(EXTRA_BITRATE, 12_000_000) ?: 12_000_000
                )
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQ = 0x9A51
        const val EXTRA_MODE = "mode"
        const val EXTRA_URL = "url"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"
        const val MODE_RECORD = "record"
        const val MODE_LIVE = "live"

        /** Fire-and-forget start from an overlay service. */
        fun launch(context: Context, mode: String, url: String = "") {
            val intent = Intent(context, ProjectionActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_HEIGHT, 1080)
                putExtra(EXTRA_FPS, 60)
                putExtra(EXTRA_BITRATE, if (mode == MODE_LIVE) 8_000_000 else 12_000_000)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
