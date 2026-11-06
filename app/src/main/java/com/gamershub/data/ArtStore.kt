package com.gamershub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.gamershub.core.shizuku.Shell
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Per-app artwork for the Game Space cards.
 *
 * The reference shows a real frame of the game on every card. That is what this
 * does: while the game is in front, `screencap` runs with shell identity
 * (Shizuku) and the PNG is written straight into the app's own external files
 * dir — no media permission, no MediaProjection, no fake gradient.
 *
 * The capture is delayed after launch so it lands on the game's own screen and
 * not on the launcher animation.
 */
object ArtStore {

    private const val DIR = "art"
    private val pool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gh-art").apply { isDaemon = true }
    }

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, pkg: String): File =
        File(dir(context), pkg.replace('/', '_') + ".png")

    fun has(context: Context, pkg: String): Boolean {
        val f = file(context, pkg)
        return f.exists() && f.length() > 2048
    }

    /**
     * Captures the screen right now and stores it for [pkg].
     * Returns true only when a real, non-empty PNG landed in the app's folder.
     */
    fun captureNow(context: Context, pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (!Shell.available()) return false
        val target = file(context, pkg)
        target.parentFile?.mkdirs()
        val path = target.absolutePath
        // shell identity: screencap + verify in one round trip
        val out = Shell.exec("screencap -p '$path' 2>&1; ls -l '$path' 2>&1") ?: return false
        val ok = target.exists() && target.length() > 2048L
        return if (ok) {
            touchIndex(context, pkg)
            true
        } else {
            // last resort: write to /data/local/tmp and try to copy it in
            Shell.exec("screencap -p /data/local/tmp/gh_art.png; cp /data/local/tmp/gh_art.png '$path' 2>&1")
            target.exists() && target.length() > 2048L
        }.also { _ -> out.trim() }
    }

    /** Waits [delayMs] (the game needs the foreground) then captures. */
    fun captureLater(context: Context, pkg: String, delayMs: Long = 9000L) {
        if (pkg.isBlank()) return
        val app = context.applicationContext
        pool.execute {
            runCatching {
                TimeUnit.MILLISECONDS.sleep(delayMs)
                captureNow(app, pkg)
            }
        }
    }

    fun load(context: Context, pkg: String, maxWidth: Int = 900): ImageBitmap? = try {
        val f = file(context, pkg)
        if (!f.exists()) null
        else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
        }
    } catch (_: Throwable) {
        null
    }

    fun delete(context: Context, pkg: String) {
        runCatching { file(context, pkg).delete() }
    }

    /** PNG bytes for in-app use (recording thumbnails etc.). */
    fun bytes(context: Context, pkg: String): ByteArray? = try {
        file(context, pkg).takeIf { it.exists() }?.readBytes()
    } catch (_: Throwable) {
        null
    }

    fun bitmap(context: Context, pkg: String): Bitmap? = try {
        file(context, pkg).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    } catch (_: Throwable) {
        null
    }

    private fun touchIndex(context: Context, pkg: String) {
        runCatching {
            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                .edit()
                .putLong("art_ts_$pkg", System.currentTimeMillis())
                .apply()
        }
    }

    fun capturedAt(context: Context, pkg: String): Long =
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).getLong("art_ts_$pkg", 0L)
}
