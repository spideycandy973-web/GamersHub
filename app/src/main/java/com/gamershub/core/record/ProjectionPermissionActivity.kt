package com.gamershub.core.record

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * One-pixel translucent trampoline used when capture is requested from a
 * background context (floating-bubble gesture) and no visible Activity exists
 * to host the MediaProjection consent dialog.
 */
class ProjectionPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ProjectionBroker.onResult(result.resultCode, result.data)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mpm.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        if (isFinishing) {
            // If we never got a result (user backed out), release the waiter.
            ProjectionBroker.onResult(Activity.RESULT_CANCELED, null)
        }
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, ProjectionPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
