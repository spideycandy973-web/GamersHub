package com.gamershub.core.stream.rtmp

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/**
 * A compact, single-target RTMP/RTMPS publisher.
 *
 * Implements: 3-way handshake, connect/createStream/publish AMF0 commands,
 * chunked messsage I/O, @setDataFrame/onMetaData, AVC + AAC tag muxing
 * (exactly what YouTube Live, Facebook Live and Instagram Live ingests speak),
 * ping/pong and acknowledgement bookkeeping.
 */
class RtmpConnection(
    val name: String,
    baseUrl: String,
    streamKey: String,
    private val meta: Metadata
) {
    data class Metadata(
        val width: Int, val height: Int, val fps: Int,
        val videoBitrateBps: Int, val hasAudio: Boolean
    )

    enum class Status { IDLE, CONNECTING, LIVE, FAILED, CLOSED }

    @Volatile var status: Status = Status.IDLE
        private set
    @Volatile var lastError: String? = null
        private set

    val bytesSent = AtomicLong(0)
    val framesSent = AtomicLong(0)
    val videoDrops = AtomicLong(0)

    // ------------------------------------------------------------------ URL
    private val uri = URI(baseUrl.trim())
    private val useTls = uri.scheme.equals("rtmps", true)
    private val host: String = uri.host
    private val port: Int = if (uri.port > 0) uri.port else if (useTls) 443 else 1935
    private val app: String = uri.path.removePrefix("/").ifBlank { "live" }
    private val tcUrl = "${if (useTls) "rtmps" else "rtmp"}://$host:$port/$app"
    private val playPath = streamKey.trim()

    // ------------------------------------------------------------------ IO
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private val running = AtomicBoolean(false)
    private val queue = ArrayBlockingQueue<Tag>(600)
    private var writerThread: Thread? = null
    private var readerThread: Thread? = null
    private var sessionStartMs = 0L

    private var inChunkSize = 128
    private val outChunkSize = 60000
    private var ackWindow = 0L
    private var bytesReceivedSinceAck = 0L

    private data class Tag(val type: Int, val data: ByteArray, val ptsMs: Long)

    // ------------------------------------------------------------ public API

    /** Blocking connect + handshake + publish. Returns true when live. */
    fun connect(timeoutMs: Int = 10_000): Boolean {
        if (status == Status.LIVE) return true
        status = Status.CONNECTING
        lastError = null
        return try {
            val s = if (useTls) {
                (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(host, port).apply {
                        soTimeout = timeoutMs
                    }
            } else {
                Socket(host, port).apply { soTimeout = timeoutMs }
            }
            s.tcpNoDelay = true
            socket = s
            input = DataInputStream(s.getInputStream())
            output = DataOutputStream(s.getOutputStream())

            handshake()
            negotiate()
            sendMetadata()

            s.soTimeout = 0
            running.set(true)
            sessionStartMs = System.currentTimeMillis()
            writerThread = Thread({ writerLoop() }, "gh-rtmp-w-$name").apply { start() }
            readerThread = Thread({ readerLoop() }, "gh-rtmp-r-$name").apply { start() }
            status = Status.LIVE
            Log.i(TAG, "[$name] live on $tcUrl")
            true
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            status = Status.FAILED
            Log.e(TAG, "[$name] connect failed: $lastError")
            runCatching { socket?.close() }
            false
        }
    }

    fun sendVideoConfig(avcC: ByteArray) = enqueue(Tag(TYPE_VIDEO, flvVideo(avcC, keyframe = true, config = true), 0))
    fun sendVideo(nalus: ByteArray, keyframe: Boolean, ptsMs: Long) =
        enqueue(Tag(TYPE_VIDEO, flvVideo(nalus, keyframe, config = false), ptsMs))
    fun sendAudioConfig(asc: ByteArray) {
        val payload = ByteArray(2 + asc.size)
        payload[0] = 0xAF.toByte()
        payload[1] = 0 // AAC sequence header
        System.arraycopy(asc, 0, payload, 2, asc.size)
        enqueue(Tag(TYPE_AUDIO, payload, 0))
    }
    fun sendAudio(aac: ByteArray, ptsMs: Long) {
        val payload = ByteArray(2 + aac.size)
        payload[0] = 0xAF.toByte()
        payload[1] = 1 // raw AAC frame
        System.arraycopy(aac, 0, payload, 2, aac.size)
        enqueue(Tag(TYPE_AUDIO, payload, ptsMs))
    }

    fun close() {
        running.set(false)
        runCatching { socket?.close() }
        runCatching { writerThread?.join(1500) }
        runCatching { readerThread?.join(1500) }
        if (status != Status.FAILED) status = Status.CLOSED
    }

    // -------------------------------------------------------------- internals

    private fun enqueue(tag: Tag) {
        if (status != Status.LIVE) return
        if (!queue.offer(tag)) {
            // Queue full: drop. Keyframes prefer to evict older non-key tags is
            // overkill for v1 - count drops so the UI can warn the user.
            videoDrops.incrementAndGet()
        }
    }

    private fun handshake() {
        val inp = input!!; val out = output!!
        val c1 = ByteArray(1536).also { SecureRandom().nextBytes(it) }
        // time + zero
        for (i in 0..7) c1[i] = 0
        out.writeByte(3) // C0
        out.write(c1)    // C1
        out.flush()

        val s0 = inp.readByte()
        if (s0.toInt() != 3) throw IllegalStateException("RTMP version rejected: $s0")
        val s1 = ByteArray(1536).also { inp.readFully(it) }
        val s2 = ByteArray(1536).also { inp.readFully(it) }
        out.write(s1) // C2 echoes S1
        out.flush()
    }

    private fun negotiate() {
        // connect
        val connect = Amf0.Encoder()
            .string("connect")
            .number(1.0)
            .obj(
                linkedMapOf(
                    "app" to app,
                    "type" to "nonprivate",
                    "flashVer" to "FMLE/3.0 (compatible; GamersHub/1.0)",
                    "swfUrl" to "", "tcUrl" to tcUrl
                )
            ).bytes()
        writeMessage(3, TYPE_COMMAND_AMF0, 0, connect)

        // read until _result(1)
        if (!awaitCommandResult(1.0, 9_000)) throw IllegalStateException("connect refused")

        // our chunk size for outbound messages
        val scs = ByteArray(4)
        scs[0] = ((outChunkSize shr 24) and 0xFF).toByte()
        scs[1] = ((outChunkSize shr 16) and 0xFF).toByte()
        scs[2] = ((outChunkSize shr 8) and 0xFF).toByte()
        scs[3] = (outChunkSize and 0xFF).toByte()
        writeMessage(2, TYPE_SET_CHUNK_SIZE, 0, scs)

        // createStream
        val create = Amf0.Encoder().string("createStream").number(2.0).nul().bytes()
        writeMessage(3, TYPE_COMMAND_AMF0, 0, create)
        val streamId = awaitCreateStreamResult(9_000)
            ?: throw IllegalStateException("createStream failed")

        // publish
        val publish = Amf0.Encoder()
            .string("publish").number(3.0).nul().string(playPath).string("live")
            .bytes()
        writeMessage(3, TYPE_COMMAND_AMF0, streamId, publish)

        this.msgStreamId = streamId
        // wait for onStatus NetStream.Publish.Start (best effort)
        awaitPublishStart(4_000)
    }

    private var msgStreamId = 1

    private fun sendMetadata() {
        val m = meta
        val ecma = linkedMapOf<String, Any?>(
            "width" to m.width.toDouble(),
            "height" to m.height.toDouble(),
            "framerate" to m.fps.toDouble(),
            "videodatarate" to (m.videoBitrateBps / 1000).toDouble(),
            "videocodecid" to 7.0,
        ) + if (m.hasAudio) linkedMapOf(
            "audiodatarate" to 128.0,
            "audiocodecid" to 10.0,
            "audiosamplerate" to 44100.0,
            "stereo" to true
        ) else emptyMap()

        val payload = Amf0.Encoder()
            .string("@setDataFrame")
            .string("onMetaData")
            .ecma(ecma)
            .bytes()
        writeMessage(3, TYPE_DATA_AMF0, msgStreamId, payload)
    }

    // ------------------------------------------------------------- AMF waits

    private fun awaitCommandResult(txn: Double, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val msg = readMessage() ?: continue
            if (msg.type != TYPE_COMMAND_AMF0) continue
            val values = readAmf(msg.payload)
            if ((values.getOrNull(0) as? String) == "_result" &&
                (values.getOrNull(1) as? Double) == txn
            ) return true
            if (values.getOrNull(0) == "_error") return false
        }
        return false
    }

    private fun awaitCreateStreamResult(timeoutMs: Long): Int? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val msg = readMessage() ?: continue
            if (msg.type != TYPE_COMMAND_AMF0) continue
            val values = readAmf(msg.payload)
            if ((values.getOrNull(0) as? String) == "_result" &&
                (values.getOrNull(1) as? Double) == 2.0
            ) {
                return (values.getOrNull(3) as? Double)?.toInt()
            }
        }
        return null
    }

    private fun awaitPublishStart(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val msg = readMessage() ?: continue
            if (msg.type != TYPE_COMMAND_AMF0) continue
            val values = readAmf(msg.payload)
            val name = values.getOrNull(0) as? String
            if (name == "onStatus") {
                val info = values.firstNotNullOfOrNull { it as? Map<*, *> }
                val code = info?.get("code") as? String
                if (code == "NetStream.Publish.Start") return true
                if (code?.contains("Failed", true) == true ||
                    code?.contains("BadAuth", true) == true
                ) throw IllegalStateException("publish rejected: $code")
            }
            if (name == "_error") throw IllegalStateException("publish error")
        }
        return false
    }

    private fun readAmf(payload: ByteArray): List<Any?> {
        val reader = Amf0.Reader(DataInputStream(payload.inputStream()))
        val out = mutableListOf<Any?>()
        while (true) {
            try {
                out.add(reader.read())
            } catch (_: Exception) {
                break
            }
        }
        return out
    }

    // ------------------------------------------------------------- writer

    private fun writerLoop() {
        try {
            while (running.get()) {
                val tag = queue.take()
                sendTag(tag)
            }
        } catch (t: Throwable) {
            if (running.get()) {
                lastError = t.message
                status = Status.FAILED
            }
        }
    }

    private fun sendTag(tag: Tag) {
        val ts = ((tag.ptsMs).coerceAtLeast(0) % 0xFFFFFFL).toInt()
        writeMessage(
            if (tag.type == TYPE_VIDEO) 6 else 4,
            tag.type, msgStreamId, tag.data, ts
        )
        bytesSent.addAndGet(tag.data.size.toLong())
        if (tag.type == TYPE_VIDEO) framesSent.incrementAndGet()
    }

    /** FLV VideoData wrapper. */
    private fun flvVideo(data: ByteArray, keyframe: Boolean, config: Boolean): ByteArray {
        val payload = ByteArray(5 + data.size)
        payload[0] = (if (keyframe) 0x17 else 0x27).toByte()
        payload[1] = (if (config) 0 else 1).toByte()
        payload[2] = 0; payload[3] = 0; payload[4] = 0 // composition time
        System.arraycopy(data, 0, payload, 5, data.size)
        return payload
    }

    /** Writes one chunked RTMP message. Synchronized: commands + tags share the socket. */
    @Synchronized
    private fun writeMessage(csid: Int, type: Int, streamId: Int, payload: ByteArray, ts: Int = 0) {
        val out = output ?: return
        var offset = 0
        var first = true
        while (first || offset < payload.size) {
            val chunkLen = minOf(outChunkSize, payload.size - offset)
            if (first) {
                out.writeByte(csid) // fmt 0, 1-byte csid
                out.writeByte((ts shr 16) and 0xFF)
                out.writeByte((ts shr 8) and 0xFF)
                out.writeByte(ts and 0xFF)
                out.writeByte((payload.size shr 16) and 0xFF)
                out.writeByte((payload.size shr 8) and 0xFF)
                out.writeByte(payload.size and 0xFF)
                out.writeByte(type)
                out.writeByte(streamId and 0xFF)
                out.writeByte((streamId shr 8) and 0xFF)
                out.writeByte((streamId shr 16) and 0xFF)
                out.writeByte((streamId shr 24) and 0xFF)
                first = false
            } else {
                out.writeByte(0xC0 or csid) // fmt 3 continuation
            }
            out.write(payload, offset, chunkLen)
            offset += chunkLen
        }
        out.flush()
    }

    // ------------------------------------------------------------- reader

    private data class InHeader(
        var ts: Long = 0, var delta: Long = 0, var len: Int = 0,
        var type: Int = 0, var streamId: Int = 0, var got: Int = 0,
        var buf: ByteArrayOutputStream = ByteArrayOutputStream()
    )

    private data class InMessage(val type: Int, val payload: ByteArray, val ts: Long)

    private val inHeaders = HashMap<Int, InHeader>()

    /** Reads one complete RTMP message; null on protocol events it consumed. */
    private fun readMessage(): InMessage? {
        val inp = input ?: return null
        while (true) {
            val b0 = inp.readUnsignedByte()
            val fmt = b0 shr 6
            var csid = b0 and 0x3F
            if (csid == 0) csid = inp.readUnsignedByte() + 64
            else if (csid == 1) csid = inp.readUnsignedByte() + inp.readUnsignedByte() * 256 + 64

            val h = inHeaders.getOrPut(csid) { InHeader() }
            when (fmt) {
                0 -> {
                    h.ts = readU24(inp).toLong()
                    h.delta = h.ts
                    h.len = readU24(inp)
                    h.type = inp.readUnsignedByte()
                    h.streamId = readU32LE(inp).toInt()
                    if (h.ts == 0xFFFFFFL) h.ts = inp.readInt().toLong() and 0xFFFFFFFFL
                    h.got = 0
                    h.buf.reset()
                }
                1 -> {
                    h.delta = readU24(inp).toLong()
                    h.len = readU24(inp)
                    h.type = inp.readUnsignedByte()
                    if (h.delta == 0xFFFFFFL) h.delta = inp.readInt().toLong() and 0xFFFFFFFFL
                    h.ts += h.delta
                    h.got = 0
                    h.buf.reset()
                }
                2 -> {
                    h.delta = readU24(inp).toLong()
                    if (h.delta == 0xFFFFFFL) h.delta = inp.readInt().toLong() and 0xFFFFFFFFL
                    h.ts += h.delta
                    h.got = 0
                    h.buf.reset()
                }
                3 -> {
                    if (h.got == 0 || h.got >= h.len) {
                        // brand-new message reusing header
                        h.ts += h.delta
                        h.got = 0
                        h.buf.reset()
                    }
                    if (h.delta == 0xFFFFFFL) inp.readInt() // extended ts eats 4 bytes
                }
            }

            val want = minOf(inChunkSize, h.len - h.got)
            val chunk = ByteArray(want)
            inp.readFully(chunk)
            h.buf.write(chunk)
            h.got += want
            bytesReceivedSinceAck += want

            if (h.got < h.len) continue // continuation chunks follow

            val payload = h.buf.toByteArray()
            h.got = 0
            h.buf.reset()

            when (h.type) {
                TYPE_SET_CHUNK_SIZE -> {
                    inChunkSize = ((payload[0].toInt() and 0xFF) shl 24) or
                            ((payload[1].toInt() and 0xFF) shl 16) or
                            ((payload[2].toInt() and 0xFF) shl 8) or
                            (payload[3].toInt() and 0xFF)
                    inChunkSize = inChunkSize.coerceIn(128, 0xFFFFFF)
                }
                TYPE_WINDOW_ACK_SIZE -> {
                    ackWindow = (((payload[0].toInt() and 0xFF) shl 24) or
                            ((payload[1].toInt() and 0xFF) shl 16) or
                            ((payload[2].toInt() and 0xFF) shl 8) or
                            (payload[3].toInt() and 0xFF)).toLong()
                }
                TYPE_PING -> {
                    if (payload.size >= 2) {
                        val event = ((payload[0].toInt() and 0xFF) shl 8) or
                                (payload[1].toInt() and 0xFF)
                        if (event == 6 && payload.size >= 6) {
                            val pong = ByteArray(6)
                            pong[0] = 0; pong[1] = 7
                            System.arraycopy(payload, 2, pong, 2, 4)
                            writeMessage(2, TYPE_PING, 0, pong)
                        }
                    }
                }
                TYPE_ACK -> { /* ignore */ }
                else -> maybeAck(); return InMessage(h.type, payload, h.ts)
            }
            maybeAck()
        }
    }

    private fun maybeAck() {
        if (ackWindow > 0 && bytesReceivedSinceAck >= ackWindow / 2) {
            val ack = ByteArray(4)
            val v = bytesReceivedSinceAck.toInt()
            ack[0] = ((v shr 24) and 0xFF).toByte()
            ack[1] = ((v shr 16) and 0xFF).toByte()
            ack[2] = ((v shr 8) and 0xFF).toByte()
            ack[3] = (v and 0xFF).toByte()
            writeMessage(2, TYPE_ACK, 0, ack)
            bytesReceivedSinceAck = 0
        }
    }

    private fun readerLoop() {
        try {
            while (running.get()) {
                readMessage()
            }
        } catch (t: Throwable) {
            if (running.get()) {
                lastError = t.message
                status = Status.FAILED
                Log.e(TAG, "[$name] reader ended: ${t.message}")
            }
        }
    }

    private fun readU24(inp: DataInputStream): Int =
        (inp.readUnsignedByte() shl 16) or (inp.readUnsignedByte() shl 8) or inp.readUnsignedByte()

    private fun readU32LE(inp: DataInputStream): Long =
        inp.readUnsignedByte().toLong() or
                (inp.readUnsignedByte().toLong() shl 8) or
                (inp.readUnsignedByte().toLong() shl 16) or
                (inp.readUnsignedByte().toLong() shl 24)

    companion object {
        private const val TAG = "RtmpConnection"

        const val TYPE_SET_CHUNK_SIZE = 1
        const val TYPE_ACK = 3
        const val TYPE_PING = 4
        const val TYPE_WINDOW_ACK_SIZE = 5
        const val TYPE_AUDIO = 8
        const val TYPE_VIDEO = 9
        const val TYPE_DATA_AMF0 = 18
        const val TYPE_COMMAND_AMF0 = 20
    }
}
