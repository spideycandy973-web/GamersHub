package com.gamershub.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.min

/**
 * The floating mark: the GamersHub emblem on a soft breathing light.
 *
 * Deliberately a plain [View] and not a Compose surface. A `ComposeView` has to
 * build a whole composition the moment it is attached to an overlay window
 * (`AbstractComposeView.onAttachedToWindow`), and anything that goes wrong in
 * that first pass takes the process with it — which is exactly what closed the
 * app when the floating button was switched on. A View cannot do that: the
 * picture is decoded once, drawn with two circles and a bitmap, and a single
 * animator drives the glow.
 */
class EmblemBubbleView(context: Context) : View(context) {

    var onTap: () -> Unit = {}
    var onDrag: (Float, Float) -> Unit = { _, _ -> }

    /** Held down: the way to get rid of the floating icon without opening the panel. */
    var onLongPress: () -> Unit = {}

    private val density = resources.displayMetrics.density

    /** Whole touch target, one size down from the old 58dp circle. */
    private val boxPx = 62f * density
    private val markPx = 44f * density

    private val emblem: Bitmap? = runCatching {
        BitmapFactory.decodeResource(resources, com.gamershub.R.drawable.ic_float_emblem)
    }.getOrNull()?.let { source ->
        runCatching {
            Bitmap.createScaledBitmap(source, markPx.toInt(), markPx.toInt(), true)
        }.getOrDefault(source)
    }

    private val bedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var pulse = 0.18f
    /** Mirrors GC.LightRed / IconAmber / IconViolet / Jade, kept as raw ARGB on purpose. */
    private val accentColor: Int = runCatching {
        val picked = com.gamershub.data.AppRepo(context).customAccent()
        if (picked != 0) return@runCatching picked
        when (com.gamershub.data.AppRepo(context).accentIndex()) {
            1 -> 0xFFFF7A18.toInt()
            2 -> 0xFF8A5CFF.toInt()
            3 -> 0xFF2FD8A0.toInt()
            else -> 0xFFFF5252.toInt()
        }
    }.getOrDefault(0xFFFF5252.toInt())

    private val pulseAnimator = ValueAnimator.ofFloat(0.18f, 0.92f).apply {
        duration = 1900L
        startDelay = 0L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    private val gesture = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                longPressFired = true
                dragging = false
                runCatching { onLongPress() }
            }
        }
    )

    private var longPressFired = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false

    init {
        // The window itself is the background; drawing happens in onDraw.
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = boxPx.toInt()
        setMeasuredDimension(side, side)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching {
            if (!pulseAnimator.isStarted) pulseAnimator.start() else pulseAnimator.resume()
        }
    }

    override fun onDetachedFromWindow() {
        runCatching { pulseAnimator.cancel() }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) {
            runCatching { if (visibility == VISIBLE) pulseAnimator.resume() else pulseAnimator.pause() }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(boxPx, min(width, height).toFloat()) / 2f

        // 1. Thin dark bed, so the mark stays readable over a bright game.
        bedPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.argb(102, 0, 0, 0), Color.argb(34, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, bedPaint)

        // 2. The breathing light — crimson at the rim of the mark, never a strobe.
        val glowRadius = radius * 0.94f
        val r = Color.red(accentColor)
        val g = Color.green(accentColor)
        val b = Color.blue(accentColor)
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(
                Color.argb((pulse * 0.30f * 255).toInt().coerceIn(0, 255), r, g, b),
                Color.argb((pulse * 255).toInt().coerceIn(0, 255), r, g, b),
                Color.argb((pulse * 0.35f * 255).toInt().coerceIn(0, 255), r, g, b),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.46f, 0.76f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // 3. The mark itself, breathing very slightly with the light.
        emblem?.let {
            val scale = 0.985f + pulse * 0.035f
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            canvas.drawBitmap(it, cx - it.width / 2f, cy - it.height / 2f, markPaint)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX; downRawY = event.rawY
                lastRawX = event.rawX; lastRawY = event.rawY
                dragging = false
                longPressFired = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                if (!dragging &&
                    hypot((event.rawX - downRawX).toDouble(), (event.rawY - downRawY).toDouble()) > touchSlop
                ) {
                    dragging = true
                }
                if (dragging) {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    runCatching { onDrag(dx, dy) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !longPressFired) runCatching { onTap() }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
