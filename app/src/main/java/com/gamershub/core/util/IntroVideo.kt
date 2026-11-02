package com.gamershub.core.util

import android.content.Context
import java.io.File

/**
 * The owner's Google-Drive intro video, downloaded ONCE into app storage and
 * played from disk afterwards (app launch intro + Game Space intro).
 *
 * Robustness: both Drive URL patterns are tried, and the bytes are validated
 * against the MP4 magic marker before being accepted — a Drive HTML error or
 * virus-scan "confirm" page can never be saved as "the video" again.
 */
object IntroVideo {

    private const val CACHE_NAME = "game_space_intro.mp4"

    private val URLS = listOf(
        "https://drive.usercontent.google.com/download?id=1_lro6flRERPAgyrImxEzdt0kt9x5Lbbb&export=download&confirm=t",
        "https://drive.google.com/uc?export=download&id=1_lro6flRERPAgyrImxEzdt0kt9x5Lbbb"
    )

    /** The cached video, or null when it was never downloaded / is invalid. */
    fun cached(context: Context): File? {
        val f = File(context.filesDir, CACHE_NAME)
        return if (f.exists() && f.length() > 10_000L) f else null
    }

    /**
     * Returns the intro video file. The video is BUILT INTO the APK
     * (`res/raw/game_space_intro.mp4`) and copied out once — no network needed.
     * The Google-Drive download is kept only as a fallback for stripped builds.
     */
    suspend fun ensureCached(context: Context, onProgress: (Float) -> Unit): File? {
        cached(context)?.let { return it }
        val fromRaw = runCatching {
            val dest = File(context.filesDir, CACHE_NAME)
            val resId = context.resources.getIdentifier(
                "game_space_intro", "raw", context.packageName
            )
            if (resId == 0) error("no built-in intro")
            context.resources.openRawResource(resId).use { input ->
                java.io.FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
            if (dest.length() < 10_000L) error("built-in intro too small")
            dest
        }.getOrNull()
        if (fromRaw != null && fromRaw.exists()) {
            onProgress(1f)
            return fromRaw
        }
        for (url in URLS) {
            val ok = runCatching {
                val conn = java.net.URL(url).openConnection()
                conn.connectTimeout = 8000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                val tmp = File(context.filesDir, "$CACHE_NAME.part")
                conn.getInputStream().use { input ->
                    java.io.FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            copied += n
                            if (total > 0) {
                                onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (tmp.length() < 10_000L) error("download too small")
                // MP4 magic: bytes 4..8 are "ftyp". HTML pages fail this check.
                val head = ByteArray(12)
                val read = java.io.FileInputStream(tmp).use { it.read(head) }
                val isMp4 = read >= 8 && String(head, 4, 4, Charsets.US_ASCII) == "ftyp"
                if (!isMp4) error("not an mp4 (got an HTML/confirm page)")
                tmp.renameTo(File(context.filesDir, CACHE_NAME))
                true
            }.getOrDefault(false)
            if (ok) return cached(context)
        }
        return null
    }
}
