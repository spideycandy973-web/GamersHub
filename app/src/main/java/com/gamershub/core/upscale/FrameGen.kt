package com.gamershub.core.upscale

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Real motion-compensated **frame generation**, OpenGL ES 2.0, no root.
 *
 * This is the Android equivalent of what LSFG (Lossless Scaling frame generation) does on
 * a PC, one layer up: a PC tool hooks the presenter and gets the frames for free. Here the
 * frames come from MediaProjection, and the interpolation is done by us:
 *
 *   1. every captured frame is copied into a texture (`cur`), the previous one is kept (`prev`);
 *   2. a **Lucas-Kanade** optical-flow pass at quarter resolution computes a per-block motion
 *      vector between `prev` and `cur` (9-tap gradients, 2x2 structure tensor solved per block);
 *   3. an **interpolation pass** warps `prev` forward and `cur` backward by half the motion
 *      vector, blends them, and falls back to the newer frame wherever the two warped samples
 *      disagree (occlusion / disocclusion), which is what kills most ghosting;
 *   4. both the real frame and the synthesised frame are presented, paced to half a capture
 *      interval, so the panel actually receives two pictures per captured frame.
 *
 * Honest limits, which the UI states instead of hiding:
 *  • this **does not** make the game render more frames — the game still renders at its own
 *    rate, the extra frames are synthesised and counted separately;
 *  • it costs latency and GPU time;
 *  • on hard cuts, fast scrolling text and fine UI it can show artifacts; the blend weight is
 *    biased to the real frame exactly there.
 */
class FrameGen(private val width: Int, private val height: Int) {

    /** Set when [begin] fails, with the driver message. */
    var error: String = ""

    private val flowW = (width / 4).coerceIn(64, 960)
    private val flowH = (height / 4).coerceIn(64, 540)

    private var copyProgram = 0
    private var copyPos = 0
    private var copyUv = 0
    private var copyUvMatrix = 0
    private var copyTex = 0

    private var flowProgram = 0
    private var flowPos = 0
    private var flowUv = 0
    private var flowUvMatrix = 0
    private var flowPrev = 0
    private var flowCur = 0
    private var flowStep = 0

    private var interpProgram = 0
    private var interpPos = 0
    private var interpUv = 0
    private var interpUvMatrix = 0
    private var interpPrev = 0
    private var interpCur = 0
    private var interpFlow = 0
    private var interpTexel = 0

    private var prevTex = 0
    private var curTex = 0
    private var outTex = 0
    private var flowTex = 0

    private var prevFbo = 0
    private var curFbo = 0
    private var outFbo = 0
    private var flowFbo = 0

    private var previousReady = false

    private val quadPos: FloatBuffer = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val quadUv: FloatBuffer = floats(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /** Builds the three programs and all render targets. False = frame gen unavailable. */
    fun begin(): Boolean {
        copyProgram = link(VERTEX, COPY_FRAGMENT)
        if (copyProgram == 0) return false
        copyPos = GLES20.glGetAttribLocation(copyProgram, "aPos")
        copyUv = GLES20.glGetAttribLocation(copyProgram, "aUv")
        copyUvMatrix = GLES20.glGetUniformLocation(copyProgram, "uUvMatrix")
        copyTex = GLES20.glGetUniformLocation(copyProgram, "uTex")

        flowProgram = link(VERTEX, FLOW_FRAGMENT)
        if (flowProgram == 0) return false
        flowPos = GLES20.glGetAttribLocation(flowProgram, "aPos")
        flowUv = GLES20.glGetAttribLocation(flowProgram, "aUv")
        flowUvMatrix = GLES20.glGetUniformLocation(flowProgram, "uUvMatrix")
        flowPrev = GLES20.glGetUniformLocation(flowProgram, "uPrev")
        flowCur = GLES20.glGetUniformLocation(flowProgram, "uCur")
        flowStep = GLES20.glGetUniformLocation(flowProgram, "uStep")

        interpProgram = link(VERTEX, INTERP_FRAGMENT)
        if (interpProgram == 0) return false
        interpPos = GLES20.glGetAttribLocation(interpProgram, "aPos")
        interpUv = GLES20.glGetAttribLocation(interpProgram, "aUv")
        interpUvMatrix = GLES20.glGetUniformLocation(interpProgram, "uUvMatrix")
        interpPrev = GLES20.glGetUniformLocation(interpProgram, "uPrev")
        interpCur = GLES20.glGetUniformLocation(interpProgram, "uCur")
        interpFlow = GLES20.glGetUniformLocation(interpProgram, "uFlow")
        interpTexel = GLES20.glGetUniformLocation(interpProgram, "uTexel")

        val prev = target(width, height)
        val cur = target(width, height)
        val out = target(width, height)
        val flow = target(flowW, flowH)
        if (prev == null || cur == null || out == null || flow == null) {
            error = "render target refused at ${width}x$height"
            release()
            return false
        }
        prevTex = prev.first
        prevFbo = prev.second
        curTex = cur.first
        curFbo = cur.second
        outTex = out.first
        outFbo = out.second
        flowTex = flow.first
        flowFbo = flow.second
        return true
    }

    fun hasPrevious(): Boolean = previousReady

    /** Copies the captured screen texture into `cur`. */
    fun capture(externalTexture: Int, uvMatrix: FloatArray) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, curFbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(copyProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTexture)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glUniform1i(copyTex, 0)
        GLES20.glUniformMatrix4fv(copyUvMatrix, 1, false, uvMatrix, 0)
        drawQuad(copyProgram, copyPos, copyUv)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /** Current real frame, as a 2D texture id. */
    fun current(): Int = curTex

    /**
     * Motion estimation + synthesis. Returns the interpolated texture id, or 0 when the
     * pass could not run (in which case the caller just presents the real frame).
     */
    fun interpolate(): Int {
        if (!previousReady || prevTex == 0) return 0

        // 1. optical flow, quarter resolution
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, flowFbo)
        GLES20.glViewport(0, 0, flowW, flowH)
        GLES20.glUseProgram(flowProgram)
        bind2D(0, prevTex)
        bind2D(1, curTex)
        GLES20.glUniform1i(flowPrev, 0)
        GLES20.glUniform1i(flowCur, 1)
        GLES20.glUniform2f(flowStep, 4f / width.toFloat(), 4f / height.toFloat())
        GLES20.glUniformMatrix4fv(flowUvMatrix, 1, false, identity, 0)
        drawQuad(flowProgram, flowPos, flowUv)

        // 2. warp + blend at full resolution
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(interpProgram)
        bind2D(0, prevTex)
        bind2D(1, curTex)
        bind2D(2, flowTex)
        GLES20.glUniform1i(interpPrev, 0)
        GLES20.glUniform1i(interpCur, 1)
        GLES20.glUniform1i(interpFlow, 2)
        GLES20.glUniform2f(interpTexel, 1f / width.toFloat(), 1f / height.toFloat())
        GLES20.glUniformMatrix4fv(interpUvMatrix, 1, false, identity, 0)
        drawQuad(interpProgram, interpPos, interpUv)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return outTex
    }

    /** Makes the frame we just presented the new reference. */
    fun promote() {
        if (curTex == 0) return
        val tex = curTex
        val fbo = curFbo
        curTex = prevTex
        curFbo = prevFbo
        prevTex = tex
        prevFbo = fbo
        previousReady = true
    }

    /** The reference frame is the one we just wrote, so swap targets before drawing again. */
    fun discard() {
        previousReady = false
    }

    fun release() {
        val textures = intArrayOf(prevTex, curTex, outTex, flowTex)
        GLES20.glDeleteTextures(4, textures, 0)
        val fbos = intArrayOf(prevFbo, curFbo, outFbo, flowFbo)
        GLES20.glDeleteFramebuffers(4, fbos, 0)
        listOf(copyProgram, flowProgram, interpProgram).forEach {
            if (it != 0) GLES20.glDeleteProgram(it)
        }
        prevTex = 0
        curTex = 0
        outTex = 0
        flowTex = 0
        prevFbo = 0
        curFbo = 0
        outFbo = 0
        flowFbo = 0
        copyProgram = 0
        flowProgram = 0
        interpProgram = 0
        previousReady = false
    }

    /* ---------------------------------------------------------------- */

    private fun bind2D(unit: Int, texture: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun drawQuad(program: Int, attribPos: Int, attribUv: Int) {
        GLES20.glUseProgram(program)
        quadPos.position(0)
        GLES20.glEnableVertexAttribArray(attribPos)
        GLES20.glVertexAttribPointer(attribPos, 2, GLES20.GL_FLOAT, false, 0, quadPos)
        quadUv.position(0)
        GLES20.glEnableVertexAttribArray(attribUv)
        GLES20.glVertexAttribPointer(attribUv, 2, GLES20.GL_FLOAT, false, 0, quadUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** texture + framebuffer pair, or null when the driver refuses it. */
    private fun target(w: Int, h: Int): Pair<Int, Int>? {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texture[0], 0
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteTextures(1, texture, 0)
            GLES20.glDeleteFramebuffers(1, fbo, 0)
            return null
        }
        return texture[0] to fbo[0]
    }

    private fun link(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertex == 0 || fragment == 0) return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (linked[0] == 0) {
            error = "link: " + GLES20.glGetProgramInfoLog(program).take(120)
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            error = "compile: " + GLES20.glGetShaderInfoLog(shader).take(120)
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun floats(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

    private companion object {

        const val VERTEX = """
attribute vec4 aPos;
attribute vec2 aUv;
uniform mat4 uUvMatrix;
varying vec2 vUv;
void main() {
    vUv = (uUvMatrix * vec4(aUv, 0.0, 1.0)).xy;
    gl_Position = aPos;
}
"""

        const val COPY_FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vUv;
uniform samplerExternalOES uTex;
void main() {
    gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
}
"""

        /** Lucas-Kanade motion between the two frames, per quarter-res block. */
        const val FLOW_FRAGMENT = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform vec2 uStep;

float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    float a = 0.0; float b = 0.0; float c = 0.0; float e = 0.0; float f = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 o = vec2(float(i), float(j)) * uStep;
            float p1 = lum(texture2D(uPrev, vUv + o).rgb);
            float p2 = lum(texture2D(uCur, vUv + o).rgb);
            float gx = (lum(texture2D(uPrev, vUv + o + vec2(uStep.x, 0.0)).rgb)
                      - lum(texture2D(uPrev, vUv + o - vec2(uStep.x, 0.0)).rgb)) * 0.5;
            float gy = (lum(texture2D(uPrev, vUv + o + vec2(0.0, uStep.y)).rgb)
                      - lum(texture2D(uPrev, vUv + o - vec2(0.0, uStep.y)).rgb)) * 0.5;
            float gt = p2 - p1;
            a += gx * gx; b += gx * gy; c += gy * gy;
            e += gx * gt; f += gy * gt;
        }
    }
    float det = a * c - b * b;
    vec2 d = vec2(0.0);
    if (abs(det) > 1e-6) {
        d.x = (-c * e + b * f) / det;
        d.y = (b * e - a * f) / det;
    }
    d = clamp(d, vec2(-16.0), vec2(16.0));
    gl_FragColor = vec4(d / 32.0 + 0.5, 0.0, 1.0);
}
"""

        /** Half-way warp + occlusion-aware blend; falls back to the real frame where they disagree. */
        const val INTERP_FRAGMENT = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uFlow;
uniform vec2 uTexel;

void main() {
    vec2 enc = texture2D(uFlow, vUv).rg;
    vec2 motion = (enc * 2.0 - 1.0) * 16.0;
    vec2 half = motion * 0.5 * uTexel;
    vec3 a = texture2D(uPrev, clamp(vUv + half, uTexel, 1.0 - uTexel)).rgb;
    vec3 b = texture2D(uCur, clamp(vUv - half, uTexel, 1.0 - uTexel)).rgb;
    float diff = length(a - b);
    // where the two warped samples disagree the block is occluded: trust the real frame
    float w = clamp(1.0 - diff * 2.5, 0.0, 1.0);
    vec3 mixed = mix(b, (a + b) * 0.5, w);
    gl_FragColor = vec4(mixed, 1.0);
}
"""
    }
}
