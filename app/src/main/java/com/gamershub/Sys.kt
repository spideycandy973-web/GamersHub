package com.gamershub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Real system-setting writes with truthful feedback. */
internal fun putSystemSetting(context: Context, name: String, value: Int): Boolean = try {
    Settings.System.putInt(context.contentResolver, name, value)
} catch (_: Throwable) {
    false
}

internal fun feedbackOnWrite(context: Context, ok: Boolean) {
    if (ok) return
    android.widget.Toast.makeText(
        context,
        "Grant 'Modify System Settings' to GamersHub — opening Settings…",
        android.widget.Toast.LENGTH_LONG
    ).show()
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
