package com.gamershub.core.scaling

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Capture-side **real** frame generation.
 *
 * The VirtualDisplay renders into a [SurfaceTexture] instead of straight into
 * the encoder. For every captured frame pair (prev → cur) the stage:
 *   1. estimates per-block motion on the GPU (8×8 block SAD search),
 *   2. synthesises a true intermediate frame (motion-compensated warp+blend),
 *   3. outputs *two* frames to the encoder per captured frame — the generated
 *      mid-frame at t−Δ/2 and the real frame at t — with correct PTS via
 *      `eglPresentationTimeANDROID`.
 *
 * The result doubles the recorded/streamed frame rate with genuine generated
 * content — not frame duplication. All work stays on the GPU (zero CPU pixel
 * copies). If any GL setup fails the factory returns null and the pipeline
 * falls back to zero-copy pass-through, so nothing ever breaks.
 */
class FrameGenStage private constructor(
    private val outSurface: Surface,
    private val width: Int,
    private val height: Int
) {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    // GL objects
    private var oesTex = 0
    private var prevTex = 0
    private var curTex = 0
    private var motionTex = 0
    private var copyFbo = 0
    private var motionFbo = 0
    private var motionW = 0
    private var motionH = 0

    private var progCopy = 0
    private var progMotion = 0
    private var progInterp = 0
    private var progPassthrough = 0
    private var quadVbo = 0

    private val running = AtomicBoolean(false)
    private var havePrev = false
    private var prevTsNs = 0L
    private var firstTsNs = -1L

    /** Surface the VirtualDisplay should render into. */
    val renderSurface: Surface
        get() = inputSurface ?: outSurface

    val stats = FrameGenStats()

    class FrameGenStats {
        @Volatile var capturedFrames = 0L
        @Volatile var generatedFrames = 0L
    }

    // ------------------------------------------------------------------ setup

    private fun initEgl(): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) return false

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
            return false
        }
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0]!!, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false

        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0]!!, outSurface, surfAttribs, 0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false

        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun initGl(): Boolean {
        // ---- geometry: full-screen triangle strip
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val bb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
        val fb: FloatBuffer = bb.asFloatBuffer().put(verts); fb.position(0)
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        quadVbo = vbo[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, verts.size * 4, fb, GLES30.GL_STATIC_DRAW
        )

        // ---- textures
        val tex = IntArray(4)
        GLES30.glGenTextures(4, tex, 0)
        oesTex = tex[0]; prevTex = tex[1]; curTex = tex[2]; motionTex = tex[3]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE
        )

        fun allocTex(id: Int, w: Int, h: Int, filter: Int = GLES30.GL_LINEAR) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        allocTex(prevTex, width, height)
        allocTex(curTex, width, height)

        motionW = maxOf(1, width / 8)
        motionH = maxOf(1, height / 8)
        allocTex(motionTex, motionW, motionH, GLES30.GL_NEAREST)

        // ---- FBOs
        val fbo = IntArray(2)
        GLES30.glGenFramebuffers(2, fbo, 0)
        copyFbo = fbo[0]; motionFbo = fbo[1]

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, copyFbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, curTex, 0
        )
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) !=
            GLES30.GL_FRAMEBUFFER_COMPLETE
        ) return false

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, motionFbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, motionTex, 0
        )
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) !=
            GLES30.GL_FRAMEBUFFER_COMPLETE
        ) return false
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // ---- programs
        progCopy = buildProgram(VS, FS_COPY) ?: return false
        progMotion = buildProgram(VS, FS_MOTION) ?: return false
        progInterp = buildProgram(VS, FS_INTERP) ?: return false
        progPassthrough = buildProgram(VS, FS_COPY) ?: return false
        return true
    }

    private fun buildProgram(vsSrc: String, fsSrc: String): Int? {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vsSrc) ?: return null
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fsSrc) ?: return null
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glBindAttribLocation(p, 0, "aPos")
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return if (ok[0] == 1) p else { GLES30.glDeleteProgram(p); null }
    }

    private fun compile(type: Int, src: String): Int? {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] != 1) {
            Log.e(TAG, "shader compile failed: ${GLES30.glGetShaderInfoLog(s)}")
            GLES30.glDeleteShader(s)
            return null
        }
        return s
    }

    // ----------------------------------------------------------------- render

    private fun onFrameAvailable() {
        handler?.post { renderFrame() } ?: renderFrame()
    }

    private fun renderFrame() {
        if (!running.get()) return
        try {
            val st = surfaceTexture ?: return
            st.updateTexImage()
            val tsNs = st.timestamp
            if (firstTsNs < 0) firstTsNs = tsNs

            // 1. copy OES frame into the 2D FBO texture (cur)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, copyFbo)
            GLES30.glViewport(0, 0, width, height)
            drawOesToTexture()

            if (havePrev && frameGenEnabled) {
                // 2. motion estimation at 1/8 res
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, motionFbo)
                GLES30.glViewport(0, 0, motionW, motionH)
                drawMotion()

                // 3a. generated mid-frame at (prevTs + Δ/2)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(0, 0, width, height)
                drawInterp()
                val midTs = prevTsNs + (tsNs - prevTsNs) / 2
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, midTs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                stats.generatedFrames++
            }

            // 3b. the real current frame
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, width, height)
            drawCopy()
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, tsNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            // rotate prev/cur (cheap: swap texture ids — content already in FBOs)
            val t = prevTex; prevTex = curTex; curTex = t
            // re-bind the copy FBO to the new cur target for the next round
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, copyFbo)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, curTex, 0
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            havePrev = true
            prevTsNs = tsNs
            stats.capturedFrames++
        } catch (t: Throwable) {
            Log.e(TAG, "render error", t)
        }
    }

    private fun bindQuad(prog: Int) {
        GLES30.glUseProgram(prog)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
    }

    private fun drawOesToTexture() {
        bindQuad(progCopy)
        val uTex = GLES30.glGetUniformLocation(progCopy, "uTex")
        val uIsOes = GLES30.glGetUniformLocation(progCopy, "uIsOes")
        val uMatrix = GLES30.glGetUniformLocation(progCopy, "uTexMatrix")
        val mtx = FloatArray(16)
        surfaceTexture?.getTransformMatrix(mtx)
        // Keep BOTH sampler types bound to matching texture targets at all
        // times — GLES validates every active sampler on draw.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progCopy, "uTex"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progCopy, "uTexOes"), 0)
        GLES30.glUniform1i(uTex, 1)
        GLES30.glUniform1i(uIsOes, 1)
        // No sharpening on the raw capture copy — sharpening happens on output.
        GLES30.glUniform1f(GLES30.glGetUniformLocation(progCopy, "uSharpen"), 0f)
        GLES30.glUniformMatrix4fv(uMatrix, 1, false, mtx, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawCopy() {
        bindQuad(progPassthrough)
        val uTex = GLES30.glGetUniformLocation(progPassthrough, "uTex")
        val uIsOes = GLES30.glGetUniformLocation(progPassthrough, "uIsOes")
        val uMatrix = GLES30.glGetUniformLocation(progPassthrough, "uTexMatrix")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progPassthrough, "uTexOes"), 0)
        GLES30.glUniform1i(uTex, 1)
        GLES30.glUniform1i(uIsOes, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(progPassthrough, "uSharpen"), sharpen)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(progPassthrough, "uSharpTexel"),
            1f / width, 1f / height
        )
        GLES30.glUniformMatrix4fv(uMatrix, 1, false, IDENTITY, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawMotion() {
        bindQuad(progMotion)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progMotion, "uPrev"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progMotion, "uCur"), 1)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(progMotion, "uTexel"),
            1f / width, 1f / height
        )
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawInterp() {
        bindQuad(progInterp)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progInterp, "uPrev"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progInterp, "uCur"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, motionTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(progInterp, "uMotion"), 2)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(progInterp, "uTexel"),
            1f / width, 1f / height
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(progInterp, "uSharpen"), sharpen)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    // ---------------------------------------------------------------- control

    @Volatile
    var frameGenEnabled = true

    /**
     * Unsharp-mask strength for the output pass (0 = off, 0.9 = max).
     * Driven by the Graphics Enhancer screen — real pixel-level sharpening of
     * every frame this stage emits (record / stream / frame-generation).
     */
    @Volatile
    var sharpen = 0f

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val ht = HandlerThread("gh-framegen").also { it.start() }
        thread = ht
        val h = Handler(ht.looper)
        handler = h
        // EGL + GL must be initialised on the render thread that will use them.
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        h.post {
            ok = initEgl() && initGl()
            if (!ok) {
                runCatching {
                    EGL14.eglMakeCurrent(
                        eglDisplay, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
            }
            latch.countDown()
        }
        latch.await()
        if (!ok) throw IllegalStateException("frame-gen GL init failed")
        // SurfaceTexture is created on the GL thread (texture ownership)
        val stReady = java.util.concurrent.CountDownLatch(1)
        h.post {
            val st = SurfaceTexture(oesTex).also { it.setDefaultBufferSize(width, height) }
            surfaceTexture = st
            inputSurface = Surface(st)
            st.setOnFrameAvailableListener({ onFrameAvailable() }, h)
            stReady.countDown()
        }
        stReady.await()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        handler?.post {
            runCatching { EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
            runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
            runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
            runCatching { EGL14.eglTerminate(eglDisplay) }
        }
        thread?.quitSafely()
        runCatching { surfaceTexture?.release() }
        runCatching { inputSurface?.release() }
        surfaceTexture = null
        inputSurface = null
        handler = null
        thread = null
    }

    companion object {
        private const val TAG = "FrameGenStage"

        private val IDENTITY = FloatArray(16).also {
            it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f
        }

        /**
         * Creates and initialises the stage; all GL work runs on a dedicated
         * render thread inside the stage. Returns null on any failure — the
         * caller then uses zero-copy pass-through instead (nothing breaks,
         * frame-gen just turns off).
         */
        fun create(outSurface: Surface, width: Int, height: Int): FrameGenStage? {
            return try {
                val stage = FrameGenStage(outSurface, width, height)
                stage.start()
                stage
            } catch (t: Throwable) {
                Log.e(TAG, "frame-gen setup failed", t)
                null
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shaders (GLES 3.0)
// ---------------------------------------------------------------------------

private object GLES11Ext {
    const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
}

private const val VS = """#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

/** Copies a texture (OES or 2D) into the current render target; the 2D path
 *  applies an unsharp mask when [FrameGenStage.sharpen] is active. */
private const val FS_COPY = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vUv;
uniform sampler2D uTex;
uniform samplerExternalOES uTexOes;
uniform int uIsOes;
uniform mat4 uTexMatrix;
uniform float uSharpen;
uniform vec2 uSharpTexel;
out vec4 fragColor;
void main() {
    if (uIsOes == 1) {
        vec2 uv = (uTexMatrix * vec4(vUv, 0.0, 1.0)).xy;
        fragColor = texture(uTexOes, uv);
    } else {
        vec3 c = texture(uTex, vUv).rgb;
        if (uSharpen > 0.001) {
            vec3 blur = (
                texture(uTex, vUv + vec2(uSharpTexel.x, 0.0)).rgb +
                texture(uTex, vUv - vec2(uSharpTexel.x, 0.0)).rgb +
                texture(uTex, vUv + vec2(0.0, uSharpTexel.y)).rgb +
                texture(uTex, vUv - vec2(0.0, uSharpTexel.y)).rgb
            ) * 0.25;
            c = clamp(c + (c - blur) * uSharpen, 0.0, 1.0);
        }
        fragColor = vec4(c, 1.0);
    }
}
"""

/**
 * Per-block motion estimation: each fragment owns an 8×8 block (render target
 * is 1/8 res) and searches ±8 px for the best SAD match of 4 sample points.
 * Motion is encoded in RG as (mv/16 * 0.5 + 0.5) over a ±16 px window.
 */
private const val FS_MOTION = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform vec2 uTexel;
out vec4 fragColor;

const float RANGE = 16.0;

float sad(vec2 uv, vec2 off) {
    vec2 d = off * uTexel;
    float s = 0.0;
    s += abs(texture(uPrev, uv + d + vec2(-2.0, -2.0) * uTexel).r - texture(uCur, uv + vec2(-2.0, -2.0) * uTexel).r);
    s += abs(texture(uPrev, uv + d + vec2( 2.0, -2.0) * uTexel).r - texture(uCur, uv + vec2( 2.0, -2.0) * uTexel).r);
    s += abs(texture(uPrev, uv + d + vec2(-2.0,  2.0) * uTexel).r - texture(uCur, uv + vec2(-2.0,  2.0) * uTexel).r);
    s += abs(texture(uPrev, uv + d + vec2( 2.0,  2.0) * uTexel).r - texture(uCur, uv + vec2( 2.0,  2.0) * uTexel).r);
    return s;
}

void main() {
    vec2 best = vec2(0.0);
    float bestSad = 1e9;
    for (int dy = -2; dy <= 2; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
            vec2 off = vec2(float(dx), float(dy)) * 4.0;
            float s = sad(vUv, off);
            if (s < bestSad) {
                bestSad = s;
                best = off;
            }
        }
    }
    fragColor = vec4(best / RANGE * 0.5 + 0.5, 0.0, 1.0);
}
"""

/**
 * Motion-compensated midpoint: warp prev forward by mv/2 and cur backward by
 * mv/2, then blend. Static areas (mv≈0) fall out as a clean average.
 */
private const val FS_INTERP = """#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uMotion;
uniform vec2 uTexel;
uniform float uSharpen;
out vec4 fragColor;

const float RANGE = 16.0;

vec3 blendAt(vec2 uv, vec2 mvUv) {
    vec3 a = texture(uPrev, uv - mvUv * 0.5).rgb;
    vec3 b = texture(uCur,  uv + mvUv * 0.5).rgb;
    return mix(a, b, 0.5);
}

void main() {
    vec2 enc = texture(uMotion, vUv).rg;
    vec2 mv = (enc * 2.0 - 1.0) * RANGE;      // px
    vec2 mvUv = mv * uTexel;
    vec3 outc = blendAt(vUv, mvUv);
    if (uSharpen > 0.001) {
        vec3 blur = (
            blendAt(vUv + vec2(uTexel.x, 0.0), mvUv) +
            blendAt(vUv - vec2(uTexel.x, 0.0), mvUv) +
            blendAt(vUv + vec2(0.0, uTexel.y), mvUv) +
            blendAt(vUv - vec2(0.0, uTexel.y), mvUv)
        ) * 0.25;
        outc = clamp(outc + (outc - blur) * uSharpen, 0.0, 1.0);
    }
    fragColor = vec4(outc, 1.0);
}
"""
