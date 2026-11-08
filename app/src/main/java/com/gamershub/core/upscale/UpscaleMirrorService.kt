package com.gamershub.core.upscale

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.gamershub.core.RenderEngine
import com.gamershub.core.capture.ProjectionActivity
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * The closest thing to Lossless Scaling that standard Android allows.
 *
 * Lossless Scaling hooks a game's present call and re-presents the frame itself. Android
 * gives a normal app exactly one legitimate way to get those frames: **MediaProjection**.
 * So this service does what LS does, one layer up:
 *
 *   game renders at the (smaller) logical resolution
 *        → MediaProjection hands us the frame as an external OES texture
 *        → [UpscaleEngine] sharpens / upscales it full-panel in OpenGL ES
 *        → we present it in an opaque, non-touchable overlay over the whole screen.
 *
 * Touches still go to the game underneath, nothing is resized and nothing is cropped.
 *
 * Requirements, stated honestly in the UI:
 *  • Android 14+ — you must pick **the game** in the capture dialog ("single app").
 *    If you pick "entire screen" the overlay is inside the capture and would mirror
 *    itself, so the panel refuses to start on older releases where single-app capture
 *    does not exist.
 *  • The cost is latency (measured and shown live), because the frame has to travel
 *    through the compositor once more. It is a real trade: sharpness for a few ms.
 */
object UpscaleMirror {

    const val ACTION_START = "com.gamershub.upscale.START"
    const val ACTION_STOP = "com.gamershub.upscale.STOP"
    const val EXTRA_RESULT = "resultCode"
    const val EXTRA_DATA = "resultData"

    /** Single-app capture (and therefore a feedback-free mirror) needs Android 14. */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= 34

    fun whyNot(): String = if (supported())
        "ready"
    else
        "needs Android 14+ (single-app capture). This ROM would capture the mirror itself."

    @Volatile var isRunning = false
    @Volatile var lastError = ""
    @Volatile var srcW = 0
    @Volatile var srcH = 0
    @Volatile var dstW = 0
    @Volatile var dstH = 0
    @Volatile var fps = 0
    @Volatile var latencyMs = 0
    @Volatile var quality = UpscaleEngine.Quality.BALANCED
    @Volatile var sharpen = 0.35f
    @Volatile var integer = false
    /** Motion-compensated frame generation on top of the mirrored feed. */
    @Volatile var frameGen = false
    /** Synthesised frames presented in the last second — never counted as game frames. */
    @Volatile var generated = 0
    @Volatile var frameGenError = ""

    fun state(): String = when {
        isRunning && frameGen && frameGenError.isBlank() ->
            "${srcW}×$srcH → ${dstW}×$dstH · $fps fps presented · $generated/s generated · ${latencyMs}ms"
        isRunning -> "${srcW}×$srcH → ${dstW}×$dstH · $fps fps · ${latencyMs}ms"
        lastError.isNotBlank() -> lastError
        else -> "idle"
    }

    /** Asks for capture consent through the invisible activity, then starts the mirror. */
    fun start(context: Context) {
        lastError = ""
        if (!supported()) {
            lastError = whyNot()
            return
        }
        ProjectionActivity.launch(context, ProjectionActivity.MODE_UPSCALE)
    }

    fun stop(context: Context) {
        runCatching {
            context.startService(
                Intent(context, UpscaleMirrorService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

class UpscaleMirrorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val frameLock = ReentrantLock()
    private val frameSignal = frameLock.newCondition()
    @Volatile private var frameReady = false
    @Volatile private var running = false

    private var windowManager: WindowManager? = null
    private var view: SurfaceView? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == UpscaleMirror.ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!UpscaleMirror.supported()) {
            UpscaleMirror.lastError = UpscaleMirror.whyNot()
            toast(UpscaleMirror.lastError)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            UpscaleMirror.lastError = "Allow display over other apps first"
            toast(UpscaleMirror.lastError)
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(UpscaleMirror.EXTRA_RESULT, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(UpscaleMirror.EXTRA_DATA)
        if (resultCode == 0 || data == null) {
            UpscaleMirror.lastError = "Screen capture consent missing"
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (mp == null) {
            UpscaleMirror.lastError = "Capture session refused by the system"
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { stopSelf() }
            }
        }, handler)

        showOverlay()
        return START_NOT_STICKY
    }

    /* ---------------------------------------------------------------- */

    private fun startInForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Upscale mirror", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = android.app.Notification.Builder(this, CHANNEL)
            .setContentTitle("GamersHub upscale mirror")
            .setContentText("Sharpening the screen in real time")
            .setSmallIcon(com.gamershub.R.drawable.ic_stat_gh)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val surfaceView = SurfaceView(this)
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startWorker(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                running = false
            }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        runCatching { windowManager?.addView(surfaceView, params) }
            .onFailure {
                UpscaleMirror.lastError = "Overlay refused: " + (it.message ?: "unknown")
                stopSelf()
                return
            }
        view = surfaceView
    }

    private fun startWorker(surface: Surface) {
        if (worker != null) return
        val physical = RenderEngine.physicalMetrics(this)
        val metrics = resources.displayMetrics
        val srcWidth = even(metrics.widthPixels)
        val srcHeight = even(metrics.heightPixels)
        val dstWidth = even(if (physical.first > 0) physical.first else metrics.widthPixels)
        val dstHeight = even(if (physical.second > 0) physical.second else metrics.heightPixels)
        UpscaleMirror.srcW = srcWidth
        UpscaleMirror.srcH = srcHeight
        UpscaleMirror.dstW = dstWidth
        UpscaleMirror.dstH = dstHeight
        running = true
        worker = Thread({ renderLoop(surface, srcWidth, srcHeight, dstWidth, dstHeight) }, "gh-upscale")
            .apply { isDaemon = true; start() }
    }

    /**
     * One GL thread: capture frame → sharpen → present. Every number reported to the UI is
     * measured here (frame counter and the SurfaceTexture timestamp delta).
     */
    private fun renderLoop(surface: Surface, srcW: Int, srcH: Int, dstW: Int, dstH: Int) {
        val egl = EglWindow(surface)
        if (!egl.init()) {
            fail("EGL: ${egl.error}")
            return
        }
        val gl = UpscaleEngine.Gl(external = true)
        gl.quality = UpscaleMirror.quality
        gl.sharpness = UpscaleMirror.sharpen
        gl.integer = UpscaleMirror.integer
        if (!gl.build()) {
            egl.release()
            fail("shader: " + gl.log.toString().take(120))
            return
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texture = SurfaceTexture(textures[0])
        texture.setDefaultBufferSize(srcW, srcH)
        texture.setOnFrameAvailableListener({
            frameLock.lock()
            try {
                frameReady = true
                frameSignal.signalAll()
            } finally {
                frameLock.unlock()
            }
        }, handler)

        val input = Surface(texture)
        virtualDisplay = runCatching {
            projection?.createVirtualDisplay(
                "gh-upscale-src",
                srcW,
                srcH,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                input,
                null,
                handler
            )
        }.getOrNull()
        if (virtualDisplay == null) {
            texture.release()
            input.release()
            GLES20.glDeleteTextures(1, textures, 0)
            gl.release()
            egl.release()
            fail("capture surface refused")
            return
        }

        // Real frame generation: three render targets + optical flow, or nothing at all.
        var gen: FrameGen? = null
        if (UpscaleMirror.frameGen) {
            val attempt = FrameGen(srcW, srcH)
            if (attempt.begin()) {
                gen = attempt
                UpscaleMirror.frameGenError = ""
            } else {
                UpscaleMirror.frameGenError = attempt.error.ifBlank { "frame gen unavailable" }
                attempt.release()
            }
        }
        val plain = UpscaleEngine.Gl(external = false)
        plain.quality = UpscaleMirror.quality
        plain.sharpness = UpscaleMirror.sharpen
        plain.integer = UpscaleMirror.integer
        if (gen != null && !plain.build()) {
            UpscaleMirror.frameGenError = "2D presenter: " + plain.log.toString().take(100)
            gen?.release()
            gen = null
        }

        UpscaleMirror.isRunning = true
        val matrix = FloatArray(16)
        var presented = 0
        var generated = 0
        var lastStat = System.nanoTime()
        var lastStamp = 0L
        var intervalNs = 16_666_667L

        while (running) {
            frameLock.lock()
            try {
                if (!frameReady) {
                    runCatching { frameSignal.await(20L, TimeUnit.MILLISECONDS) }
                }
                frameReady = false
            } finally {
                frameLock.unlock()
            }
            val active = gen
            val drew = runCatching {
                texture.updateTexImage()
                texture.getTransformMatrix(matrix)
                val stamp = texture.timestamp
                if (stamp > 0L && lastStamp > 0L && stamp > lastStamp) {
                    // measured capture interval, clamped to 24..120 fps
                    intervalNs = (stamp - lastStamp).coerceIn(8_000_000L, 42_000_000L)
                }
                if (stamp > 0L) lastStamp = stamp

                if (active == null) {
                    gl.draw(textures[0], srcW, srcH, dstW, dstH, matrix)
                    egl.swap()
                    presented++
                } else {
                    active.capture(textures[0], matrix)
                    val real = active.current()
                    plain.draw(real, srcW, srcH, dstW, dstH, null)
                    egl.swap()
                    presented++
                    if (active.hasPrevious()) {
                        val synthesised = active.interpolate()
                        if (synthesised != 0) {
                            // pace the generated frame to the middle of the capture interval
                            val target = stamp + intervalNs / 2
                            val waitNs = target - System.nanoTime()
                            if (waitNs > 1_000_000L) {
                                runCatching {
                                    Thread.sleep(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
                                }
                            }
                            plain.draw(synthesised, srcW, srcH, dstW, dstH, null)
                            egl.swap()
                            presented++
                            generated++
                        }
                    }
                    active.promote()
                }
                stamp
            }.getOrNull()
            if (drew == null) {
                // capture stopped or surface lost — one bad frame is not a reason to die
                gen?.discard()
                runCatching { Thread.sleep(4L) }
                continue
            }
            val now = System.nanoTime()
            if (drew > 0L) {
                UpscaleMirror.latencyMs = ((now - drew) / 1_000_000L).toInt().coerceIn(0, 999)
            }
            if (now - lastStat >= 1_000_000_000L) {
                UpscaleMirror.fps = presented
                UpscaleMirror.generated = generated
                presented = 0
                generated = 0
                lastStat = now
            }
        }

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { texture.setOnFrameAvailableListener(null) }
        texture.release()
        input.release()
        GLES20.glDeleteTextures(1, textures, 0)
        gen?.release()
        plain.release()
        gl.release()
        egl.release()
        UpscaleMirror.isRunning = false
        UpscaleMirror.generated = 0
    }

    private fun fail(message: String) {
        UpscaleMirror.lastError = message
        UpscaleMirror.isRunning = false
        handler.post { stopSelf() }
    }

    override fun onDestroy() {
        running = false
        frameLock.lock()
        try {
            frameSignal.signalAll()
        } finally {
            frameLock.unlock()
        }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.stop() }
        projection = null
        worker = null
        runCatching { view?.let { windowManager?.removeView(it) } }
        view = null
        UpscaleMirror.isRunning = false
        super.onDestroy()
    }

    private fun toast(message: String) {
        runCatching {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun even(value: Int): Int = if (value % 2 == 0) value else value - 1

    companion object {
        private const val CHANNEL = "upscale_mirror"
        private const val NOTIF_ID = 57

        /** Called by the consent proxy once the user allowed screen capture. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, UpscaleMirrorService::class.java)
                .putExtra(UpscaleMirror.EXTRA_RESULT, resultCode)
                .putExtra(UpscaleMirror.EXTRA_DATA, data)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
