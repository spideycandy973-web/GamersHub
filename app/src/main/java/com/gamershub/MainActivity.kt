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
import androidx.core.content.ContextCompat
import com.gamershub.core.boost.BoostEngine
import com.gamershub.core.record.ProjectionBroker
import com.gamershub.di.AppContainer
import com.gamershub.ui.GamersHubRoot

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
            GamersHubRoot(container as AppContainer)
        }
    }

    override fun onDestroy() {
        ProjectionBroker.attachUnused()
        runCatching { unregisterReceiver(keepScreenOnReceiver) }
        super.onDestroy()
    }
}
