package com.gamershub.core.upscale

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * The real upscaling pipeline — OpenGL ES 2.0, no root, no fake numbers.
 *
 * What this actually does, per frame:
 *
 *   1. the source image (the compositor's low-resolution framebuffer, or any texture)
 *      is sampled with the GPU's bilinear filter, exactly like Lossless Scaling's
 *      cheapest path;
 *   2. a **contrast-adaptive sharpening** pass (AMD CAS style: 4 cross taps, local
 *      min/max anti-ringing clamp, adaptive strength) re-creates the edge detail that
 *      bilinear filtering destroyed;
 *   3. on the higher presets four diagonal taps add a micro-detail term, again clamped
 *      to the local neighbourhood so it can never ring or halo.
 *
 * Integer mode switches the sampler to `GL_NEAREST` so a 2x/3x/4x integer scale stays
 * pixel-perfect (no interpolation at all) — the retro / emulator path.
 *
 * Everything here is measured, not promised: [benchmark] renders real frames on the
 * device's own GPU and returns the measured milliseconds per frame.
 */
object UpscaleEngine {

    /** Presets map 1:1 to shader uniforms, so the label always matches the maths. */
    enum class Quality(val label: String, val sharpen: Float, val detail: Float, val blurb: String) {
        PERFORMANCE("Speed", 0.12f, 0.0f, "bilinear + light CAS"),
        BALANCED("Balanced", 0.28f, 0.0f, "bilinear + CAS"),
        QUALITY("Grade", 0.45f, 0.5f, "CAS + micro detail"),
        ULTRA("Ultra", 0.62f, 1.0f, "CAS + full micro detail")
    }

    data class Caps(
        val renderer: String = "unknown",
        val vendor: String = "unknown",
        val glVersion: String = "unknown",
        val gles: String = "unknown",
        val maxTexture: Int = 0,
        val external: Boolean = false,
        val vulkan: Boolean = false,
        val ok: Boolean = false,
        val error: String = ""
    ) {
        fun line(): String = buildList {
            add(gles)
            add(if (vulkan) "Vulkan" else "no Vulkan")
            add(if (external) "screen-texture" else "2D only")
            if (maxTexture > 0) add("max ${maxTexture}px")
        }.joinToString(" · ")
    }

    data class Bench(
        val ok: Boolean = false,
        val ms: Float = 0f,
        val fps: Int = 0,
        val dstW: Int = 0,
        val dstH: Int = 0,
        val quality: Quality = Quality.BALANCED,
        val renderer: String = "",
        val error: String = ""
    ) {
        fun line(): String = if (!ok) "cannot measure · $error"
        else "$dstW×$dstH · ${String.format(java.util.Locale.US, "%.2f", ms)} ms/frame · ceiling ${fps} fps"
    }

    /* ------------------------------------------------------------------ */
    /* capability detection                                               */
    /* ------------------------------------------------------------------ */

    fun caps(context: Context): Caps {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val glEs = am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val glesLabel = when {
            glEs >= 0x00030002 -> "GLES 3.2"
            glEs >= 0x00030001 -> "GLES 3.1"
            glEs >= 0x00030000 -> "GLES 3.0"
            glEs >= 0x00020000 -> "GLES 2.0"
            else -> "GLES unknown"
        }
        val vulkan = runCatching { pm.hasSystemFeature("android.hardware.vulkan.version") }
            .getOrDefault(false) ||
            runCatching {
                pm.systemAvailableFeatures.any { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            }.getOrDefault(false)

        val off = EglPbuffer(64, 64)
        val err = off.init()
        if (err != null) {
            off.release()
            return Caps(gles = glesLabel, vulkan = vulkan, ok = false, error = err)
        }
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
        val version = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
        val maxTex = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        off.release()
        return Caps(
            renderer = renderer.ifBlank { "unknown" },
            vendor = vendor.ifBlank { "unknown" },
            glVersion = version.ifBlank { "unknown" },
            gles = glesLabel,
            maxTexture = maxTex[0],
            external = extensions.contains("GL_OES_EGL_image_external"),
            vulkan = vulkan,
            ok = true
        )
    }

    /* ------------------------------------------------------------------ */
    /* shaders                                                            */
    /* ------------------------------------------------------------------ */

    private const val VERTEX_SHADER = """
attribute vec4 aPos;
attribute vec2 aUv;
uniform mat4 uUvMatrix;
varying vec2 vUv;
void main() {
    vUv = (uUvMatrix * vec4(aUv, 0.0, 1.0)).xy;
    gl_Position = aPos;
}
"""

    /** 5 taps + optional 4 diagonal taps, all clamped to the local min/max (no ringing). */
    private fun fragmentShader(): String = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uStep;
uniform float uSharp;
uniform float uDetail;

vec3 tap(vec2 uv) {
    return texture2D(uTex, uv).rgb;
}

void main() {
    vec2 s = uStep;
    vec3 e = tap(vUv);
    vec3 a = tap(vUv + vec2(-s.x, 0.0));
    vec3 b = tap(vUv + vec2( s.x, 0.0));
    vec3 c = tap(vUv + vec2(0.0, -s.y));
    vec3 d = tap(vUv + vec2(0.0,  s.y));

    vec3 mn = min(min(a, b), min(c, d));
    vec3 mx = max(max(a, b), max(c, d));
    vec3 blur = (a + b + c + d) * 0.25;
    vec3 sharpened = e + (e - blur) * (uSharp * 1.75);

    // anti-ringing: stay inside the neighbourhood, so no halos or white edges
    sharpened = clamp(sharpened, min(mn, e), max(mx, e));

    if (uDetail > 0.001) {
        vec3 d1 = tap(vUv + vec2(-s.x, -s.y));
        vec3 d2 = tap(vUv + vec2( s.x, -s.y));
        vec3 d3 = tap(vUv + vec2(-s.x,  s.y));
        vec3 d4 = tap(vUv + vec2( s.x,  s.y));
        vec3 micro = e - (d1 + d2 + d3 + d4) * 0.25;
        sharpened += micro * (uDetail * 1.35);
        sharpened = clamp(sharpened, min(mn, e), max(mx, e));
    }

    gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), 1.0);
}
"""

    private const val EXTERNAL_PREFIX = "#extension GL_OES_EGL_image_external : require\n"

    /* ------------------------------------------------------------------ */
    /* renderer                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * One compiled program. [external] = true samples the screen texture
     * (`samplerExternalOES`, which is what a MediaProjection frame is), false samples a
     * normal 2D texture (used for the benchmark and for still frames).
     */
    class Gl(private val external: Boolean) {

        private var program = 0
        private var aPos = 0
        private var aUv = 0
        private var uUvMatrix = 0
        private var uTex = 0
        private var uStep = 0
        private var uSharp = 0
        private var uDetail = 0

        var quality: Quality = Quality.BALANCED
        /** -1 = follow the preset, otherwise an explicit 0..1 strength. */
        var sharpness: Float = -1f
        var integer: Boolean = false

        val log = StringBuilder()

        private val posBuf: FloatBuffer = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val uvBuf: FloatBuffer = floats(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        private val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        fun build(): Boolean {
            val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val source = buildString {
                if (external) append(EXTERNAL_PREFIX)
                append(fragmentShader().replace("uniform sampler2D uTex;", declaredSampler()))
            }
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, source)
            if (vertex == 0 || fragment == 0) return false

            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                log.append("link failed: " + GLES20.glGetProgramInfoLog(program))
                release()
                return false
            }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)

            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUv = GLES20.glGetAttribLocation(program, "aUv")
            uUvMatrix = GLES20.glGetUniformLocation(program, "uUvMatrix")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uStep = GLES20.glGetUniformLocation(program, "uStep")
            uSharp = GLES20.glGetUniformLocation(program, "uSharp")
            uDetail = GLES20.glGetUniformLocation(program, "uDetail")
            return program != 0
        }

        private fun declaredSampler(): String =
            if (external) "uniform samplerExternalOES uTex;" else "uniform sampler2D uTex;"

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                log.append("compile failed: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }

        /** Draws [tex] (an OES or 2D texture) stretched to [dstW]x[dstH]. */
        fun draw(
            tex: Int,
            srcW: Int,
            srcH: Int,
            dstW: Int,
            dstH: Int,
            uvMatrix: FloatArray? = null,
            clear: Boolean = true
        ) {
            if (program == 0) return
            GLES20.glViewport(0, 0, dstW, dstH)
            if (clear) {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
            GLES20.glUseProgram(program)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(
                if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                if (integer) GLES20.GL_NEAREST else GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                if (integer) GLES20.GL_NEAREST else GLES20.GL_LINEAR
            )
            GLES20.glUniform1i(uTex, 0)

            // Taps never land closer than one source texel: that is where the detail is.
            val sx = max(1f / srcW.toFloat(), 1f / dstW.toFloat())
            val sy = max(1f / srcH.toFloat(), 1f / dstH.toFloat())
            GLES20.glUniform2f(uStep, sx, sy)
            GLES20.glUniform1f(uSharp, if (sharpness >= 0f) sharpness else quality.sharpen)
            GLES20.glUniform1f(uDetail, quality.detail)
            GLES20.glUniformMatrix4fv(uUvMatrix, 1, false, uvMatrix ?: identity, 0)

            posBuf.position(0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, posBuf)
            uvBuf.position(0)
            GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uvBuf)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun release() {
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        private fun floats(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(data); position(0) }
    }

    /* ------------------------------------------------------------------ */
    /* real benchmark                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Renders [frames] real frames at [dstW]x[dstH] on this device's GPU and reports the
     * measured cost. Nothing is estimated: the number comes straight from the GPU queue.
     */
    fun benchmark(
        context: Context,
        dstW: Int,
        dstH: Int,
        quality: Quality,
        srcW: Int = 0,
        srcH: Int = 0,
        frames: Int = 40
    ): Bench {
        val outW = dstW.coerceIn(320, 3840)
        val outH = dstH.coerceIn(320, 3840)
        val inW = (if (srcW > 0) srcW else outW * 2 / 3).coerceIn(64, 2048)
        val inH = (if (srcH > 0) srcH else outH * 2 / 3).coerceIn(64, 2048)

        val off = EglPbuffer(outW, outH)
        val err = off.init()
        if (err != null) {
            off.release()
            return Bench(ok = false, error = err, dstW = outW, dstH = outH, quality = quality)
        }
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        val gl = Gl(external = false)
        gl.quality = quality
        if (!gl.build()) {
            val message = gl.log.toString().take(140).ifBlank { "shader refused by driver" }
            gl.release()
            off.release()
            return Bench(ok = false, error = message, dstW = outW, dstH = outH, quality = quality, renderer = renderer)
        }

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, inW, inH, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, testPattern(inW, inH)
        )

        // warm-up: the first frames pay shader upload + driver setup, not the real cost
        repeat(4) { gl.draw(tex[0], inW, inH, outW, outH, clear = false) }
        GLES20.glFinish()

        val start = System.nanoTime()
        repeat(frames) { gl.draw(tex[0], inW, inH, outW, outH, clear = false) }
        GLES20.glFinish()
        val totalNs = System.nanoTime() - start

        GLES20.glDeleteTextures(1, tex, 0)
        gl.release()
        off.release()

        val ms = (totalNs / 1_000_000.0 / frames).toFloat()
        return Bench(
            ok = ms.isFinite() && ms > 0f,
            ms = if (ms.isFinite()) ms else 0f,
            fps = if (ms > 0f) (1000.0 / ms).toInt() else 0,
            dstW = outW,
            dstH = outH,
            quality = quality,
            renderer = renderer
        )
    }

    /** A real image with hard edges and gradients, so sharpening has something to do. */
    private fun testPattern(w: Int, h: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until h) {
            for (x in 0 until w) {
                val checker = ((x / 8) + (y / 8)) % 2 == 0
                val gradient = (x * 255 / w)
                val r = if (checker) gradient else 255 - gradient
                val g = (y * 255 / h)
                val b = if (checker) 240 else 24
                buffer.put(r.toByte()).put(g.toByte()).put(b.toByte()).put(0xFF.toByte())
            }
        }
        buffer.position(0)
        return buffer
    }
}

/* ---------------------------------------------------------------------- */
/* EGL helpers (shared with the mirror service)                            */
/* ---------------------------------------------------------------------- */

/** Off-screen EGL context: capability queries and the benchmark. */
internal class EglPbuffer(private val width: Int, private val height: Int) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null

    fun init(): String? {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return "no EGL display"
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "EGL init failed"

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            return "no EGL config"
        }
        val cfg = configs[0] ?: return "no EGL config"
        config = cfg
        context = EGL14.eglCreateContext(
            display, cfg, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) return "no GL context"
        surface = EGL14.eglCreatePbufferSurface(
            display, cfg,
            intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0
        )
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) return "no pbuffer"
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return "makeCurrent failed"
        return null
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
    }
}

/** Window-surface EGL context: the mirror draws straight into the overlay SurfaceView. */
internal class EglWindow(private val surface: Surface) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    var error: String = ""

    fun init(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            error = "no EGL display"
            return false
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            error = "EGL init failed"
            return false
        }
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            error = "no EGL config"
            return false
        }
        val cfg = configs[0] ?: run {
            error = "no EGL config"
            return false
        }
        config = cfg
        context = EGL14.eglCreateContext(
            display, cfg, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            error = "no GL context"
            return false
        }
        window = EGL14.eglCreateWindowSurface(display, cfg, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (window == null || window == EGL14.EGL_NO_SURFACE) {
            error = "no window surface"
            return false
        }
        if (!EGL14.eglMakeCurrent(display, window, window, context)) {
            error = "makeCurrent failed"
            return false
        }
        return true
    }

    fun swap(): Boolean = EGL14.eglSwapBuffers(display, window)

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        window = EGL14.EGL_NO_SURFACE
    }
}
