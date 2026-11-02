package com.gamershub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gamershub.core.boost.BoostEngine
import com.gamershub.core.record.ProjectionBroker
import com.gamershub.di.AppContainer
import com.gamershub.ui.GamersHubRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ProjectionBroker.onResult(result.resultCode, result.data)
    }

    private val keepScreenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = intent.getBooleanExtra(BoostEngine.EXTRA_ON, false)
            if (on) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProjectionBroker.attach(projectionLauncher)
        enableEdgeToEdge()

        // NOTE: no window blur here on purpose. FLAG_BLUR_BEHIND /
        // blurBehindRadius made MIUI blur the entire screen behind any window
        // of this app (see-through overlay bug) and cost real GPU frame time.
        // The liquid-glass look comes from translucent gradients in the theme.

        val container = (application as GamersHubApp).container

        val filter = IntentFilter(BoostEngine.ACTION_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(keepScreenOnReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keepScreenOnReceiver, filter)
        }

        setContent {
            val c = container as AppContainer
            var introDone by rememberSaveable { mutableStateOf(false) }
            val introFile = remember { java.io.File(filesDir, "game_space_intro.mp4") }
            Box(Modifier.fillMaxSize()) {
                GamersHubRoot(c)
                if (!introDone) {
                    if (introFile.exists() && introFile.length() > 10_000L) {
                        AppIntroVideo(introFile) { introDone = true }
                    } else {
                        // Not cached yet — prefetch in the background for the
                        // next launch instead of blocking this one.
                        LaunchedEffect(Unit) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    com.gamershub.core.util.IntroVideo.ensureCached(
                                        this@MainActivity
                                    ) {}
                                }
                            }
                            introDone = true
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProjectionBroker.attachUnused()
        runCatching { unregisterReceiver(keepScreenOnReceiver) }
        super.onDestroy()
    }
}

/** Fullscreen tap-to-skip video intro (the owner's Drive video, cached locally). */
@Composable
private fun AppIntroVideo(
    file: java.io.File,
    onDone: () -> Unit
) {
    AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    // fill the whole screen (crop-to-fit) — no letterbox bars
                    mp.setVideoScalingMode(
                        android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    )
                    start()
                }
                setOnCompletionListener { onDone() }
                setOnErrorListener { _, _, _ ->
                    onDone()
                    true
                }
                setOnTouchListener { _, _ ->
                    onDone()
                    true
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}
