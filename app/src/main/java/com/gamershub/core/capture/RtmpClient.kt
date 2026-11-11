package com.gamershub.core.capture

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Minimal but real RTMP 1.0 publisher.
 *
 * Implements the C0/C1/C2 handshake, AMF0 `connect` / `createStream` /
 * `publish` commands, chunked message writing, and FLV video tag framing for
 * H.264 (AVC sequence header + AVCC NALUs). Bytes go straight to the ingest
 * server, so "Go On Air" really publishes a stream.
 */
class RtmpClient(private val url: String) {

    private var socket: Socket? = null
    private var out: OutputStream? = null
    private var input: InputStream? = null
    private var chunkSize = 128
    private var streamId = 1
    private var reader: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var bytesSent: Long = 0L
        private set

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var lastError: String = ""
        private set

    data class Endpoint(val host: String, val port: Int, val app: String, val stream: String, val tcUrl: String)

    companion object {
        fun parse(url: String): Endpoint? = try {
            val text = url.trim()
            if (!text.startsWith("rtmp://", ignoreCase = true)) {
                null
            } else {
                val rest = text.substring(7)
                val slash = rest.indexOf('/')
                val hostPart = if (slash >= 0) rest.substring(0, slash) else rest
                val path = if (slash >= 0) rest.substring(slash + 1) else ""
                val host = hostPart.substringBefore(':')
                val port = hostPart.substringAfter(':', "1935").toIntOrNull() ?: 1935
                val lastSlash = path.lastIndexOf('/')
                val app = if (lastSlash > 0) path.substring(0, lastSlash) else path
                val stream = if (lastSlash >= 0) path.substring(lastSlash + 1) else "stream"
                if (host.isBlank() || app.isBlank() || stream.isBlank()) null
                else Endpoint(host, port, app, stream, "rtmp://$host:$port/$app")
            }
        } catch (_: Throwable) {
            null
        }

        fun isValid(url: String): Boolean = parse(url) != null
    }

    fun connect(): Boolean {
        val endpoint = parse(url) ?: run {
            lastError = "Invalid rtmp:// URL"
            return false
        }
        return try {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 8000)
            socket.soTimeout = 500
            this.socket = socket
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            this.out = output
            this.input = input

            // ---- C0 + C1 ----
            val c1 = ByteArray(1536)
            java.util.Random().nextBytes(c1)
            output.write(byteArrayOf(0x03))
            output.write(c1)
            output.flush()

            // ---- S0 + S1 + S2 ----
            val s0 = ByteArray(1)
            readFully(input, s0)
            if (s0[0].toInt() and 0xFF != 3) {
                lastError = "Server rejected RTMP version"
                return false
            }
            val s1 = ByteArray(1536)
            readFully(input, s1)
            val s2 = ByteArray(1536)
            readFully(input, s2)
            // ---- C2 ----
            output.write(s1)
            output.flush()

            // ---- connect ----
            sendCommand(
                payload = concat(
                    amfString("connect"),
                    amfNumber(1.0),
                    amfObject(
                        linkedMapOf(
                            "app" to endpoint.app,
                            "type" to "nonprivate",
                            "flashVer" to "FMLE/3.0 (compatible; FMSc/1.0)",
                            "tcUrl" to endpoint.tcUrl
                        )
                    )
                ),
                messageType = 20,
                streamId = 0
            )
            drain(1200)

            // ---- createStream ----
            sendCommand(
                payload = concat(amfString("createStream"), amfNumber(2.0), byteArrayOf(0x05)),
                messageType = 20,
                streamId = 0
            )
            drain(700)

            // ---- publish ----
            sendCommand(
                payload = concat(
                    amfString("publish"),
                    amfNumber(3.0),
                    byteArrayOf(0x05),
                    amfString(endpoint.stream),
                    amfString("live")
                ),
                messageType = 20,
                streamId = streamId
            )
            drain(900)

            running = true
            startReader()
            connected = true
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "connect failed"
            close()
            false
        }
    }

    /** H.264 decoder configuration (SPS/PPS) — must be the first video message. */
    fun writeVideoConfig(config: ByteArray) {
        if (!running) return
        val payload = concat(byteArrayOf(0x17, 0x00, 0x00, 0x00, 0x00), config)
        writeMessage(messageType = 9, streamId = streamId, payload = payload, csid = 6)
    }

    fun writeVideo(data: ByteArray, timestampMs: Long, keyFrame: Boolean) {
        if (!running) return
        val header = byteArrayOf(
            if (keyFrame) 0x17 else 0x27,
            0x01,
            0x00, 0x00, 0x00
        )
        writeMessage(messageType = 9, streamId = streamId, payload = header + data, csid = 6, timestamp = timestampMs)
    }

    fun writeAudioConfig(config: ByteArray) {
        if (!running) return
        writeMessage(messageType = 8, streamId = streamId, payload = concat(byteArrayOf(0xAF.toByte(), 0x00), config), csid = 4)
    }

    fun writeAudio(data: ByteArray, timestampMs: Long) {
        if (!running) return
        writeMessage(messageType = 8, streamId = streamId, payload = concat(byteArrayOf(0xAF.toByte(), 0x01), data), csid = 4, timestamp = timestampMs)
    }

    /** Announces our chunk size to the server (4096) for faster bulk writes. */
    fun setChunkSize(size: Int) {
        chunkSize = size.coerceIn(128, 65536)
        val payload = ByteBuffer.allocate(4).putInt(chunkSize).array()
        writeMessage(messageType = 1, streamId = 0, payload = payload, csid = 2)
    }

    private fun startReader() {
        val source = input ?: return
        reader = Thread {
            val buffer = ByteArray(4096)
            while (running) {
                try {
                    val read = source.read(buffer)
                    if (read < 0) {
                        running = false
                        break
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // nothing to read, keep the connection warm
                } catch (_: Throwable) {
                    running = false
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun drain(millis: Long) {
        val source = input ?: return
        val deadline = System.currentTimeMillis() + millis
        val buffer = ByteArray(4096)
        while (System.currentTimeMillis() < deadline) {
            try {
                val read = source.read(buffer)
                if (read < 0) break
            } catch (_: java.net.SocketTimeoutException) {
                // keep draining until the deadline
            } catch (_: Throwable) {
                break
            }
        }
    }

    private fun readFully(source: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = source.read(target, offset, target.size - offset)
            if (read <= 0) throw java.io.IOException("RTMP handshake truncated")
            offset += read
        }
    }

    private fun sendCommand(payload: ByteArray, messageType: Int, streamId: Int) {
        writeMessage(messageType, streamId, payload, csid = 3)
    }

    @Synchronized
    private fun writeMessage(
        messageType: Int,
        streamId: Int,
        payload: ByteArray,
        csid: Int,
        timestamp: Long = 0L
    ) {
        val output = out ?: return
        try {
            val header = ByteBuffer.allocate(12)
            header.put((csid and 0x3F).toByte())
            putUInt24(header, (timestamp and 0xFFFFFF).toInt())
            putUInt24(header, payload.size)
            header.put(messageType.toByte())
            header.putInt(Integer.reverseBytes(streamId))
            output.write(header.array())

            var offset = 0
            var first = true
            while (offset < payload.size) {
                if (!first) {
                    output.write(0xC0 or (csid and 0x3F))
                }
                val length = minOf(chunkSize, payload.size - offset)
                output.write(payload, offset, length)
                offset += length
                first = false
            }
            output.flush()
            bytesSent += payload.size.toLong()
        } catch (t: Throwable) {
            lastError = t.message ?: "write failed"
            running = false
        }
    }

    private fun putUInt24(buffer: ByteBuffer, value: Int) {
        buffer.put(((value shr 16) and 0xFF).toByte())
        buffer.put(((value shr 8) and 0xFF).toByte())
        buffer.put((value and 0xFF).toByte())
    }

    fun close() {
        running = false
        connected = false
        try {
            reader?.interrupt()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        socket = null
        out = null
        input = null
    }

    /* ---------------- AMF0 ---------------- */

    private fun amfNumber(value: Double): ByteArray {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(0x00)
        buffer.putDouble(value)
        return buffer.array()
    }

    private fun amfString(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(3 + bytes.size)
        buffer.put(0x02)
        buffer.putShort(bytes.size.toShort())
        buffer.put(bytes)
        return buffer.array()
    }

    private fun amfObject(values: Map<String, String>): ByteArray {
        val outBytes = java.io.ByteArrayOutputStream()
        outBytes.write(0x03)
        values.forEach { (key, value) ->
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            outBytes.write((keyBytes.size shr 8) and 0xFF)
            outBytes.write(keyBytes.size and 0xFF)
            outBytes.write(keyBytes)
            outBytes.write(amfString(value))
        }
        outBytes.write(0x00)
        outBytes.write(0x00)
        outBytes.write(0x09)
        return outBytes.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val total = arrays.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        arrays.forEach { array ->
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }
        return result
    }
}
