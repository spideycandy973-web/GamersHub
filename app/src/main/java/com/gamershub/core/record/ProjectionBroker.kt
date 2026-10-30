package com.gamershub.core.record

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher

/**
 * Bridges the MediaProjection permission prompt (an Activity result) and the
 * service/controller layers with a simple callback slot. Wired up by MainActivity.
 */
object ProjectionBroker {

    private var launcher: ActivityResultLauncher<Intent>? = null
    private var pending: ((resultCode: Int, data: Intent?) -> Unit)? = null

    fun attach(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    fun attachUnused() {
        this.launcher = null
    }

    fun onResult(resultCode: Int, data: Intent?) {
        val cb = pending
        pending = null
        cb?.invoke(resultCode, data)
    }

    /** Launches the system screen-capture consent dialog. */
    fun request(context: Context, cb: (resultCode: Int, data: Intent?) -> Unit): Boolean {
        return try {
            pending = cb
            val l = launcher
            if (l != null) {
                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                l.launch(mpm.createScreenCaptureIntent())
            } else {
                // no visible Activity (requested from the floating widget) — use trampoline
                ProjectionPermissionActivity.launch(context)
            }
            true
        } catch (t: Throwable) {
            pending = null
            false
        }
    }
}
