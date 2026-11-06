package com.gamershub.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gamershub.core.shizuku.Shell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshot engine: real `screencap` executed with shell identity through
 * Shizuku, written to the public Pictures folder, verified on disk.
 */
object ScreenshotEngine {

    const val DIR = "/sdcard/Pictures/GamersHub"

    data class Shot(val path: String, val name: String, val sizeBytes: Long)

    fun capture(): Shot? = try {
        val name = "gh_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val path = "$DIR/$name"
        val out = Shell.exec("mkdir -p $DIR; screencap -p $path; ls -l $path") ?: return null
        if (!out.contains(name)) {
            null
        } else {
            val size = Regex("\\s(\\d+)\\s").findAll(out)
                .mapNotNull { it.groupValues.getOrNull(1)?.toLongOrNull() }
                .maxOrNull() ?: 0L
            if (size <= 0L) null else Shot(path = path, name = name, sizeBytes = size)
        }
    } catch (_: Throwable) {
        null
    }

    fun lastShot(): Shot? = try {
        val out = Shell.exec("ls -t $DIR | head -1") ?: return null
        val name = out.lines().firstOrNull { it.trim().endsWith(".png") }?.trim() ?: return null
        Shot(path = "$DIR/$name", name = name, sizeBytes = 0L)
    } catch (_: Throwable) {
        null
    }

    fun galleryIntent(context: Context): Intent? = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://media/external/images/media"),
                "image/*"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        intent
    } catch (_: Throwable) {
        null
    }

    fun shareIntent(shot: Shot): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_TEXT, "Captured with GamersHub — ${shot.path}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
