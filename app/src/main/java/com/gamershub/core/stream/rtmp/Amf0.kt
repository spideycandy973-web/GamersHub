package com.gamershub.core.stream.rtmp

import java.io.ByteArrayOutputStream
import java.io.DataInputStream

/** Minimal AMF0 codec: everything RTMP command messages need. */
object Amf0 {

    const val MARKER_NUMBER: Byte = 0x00
    const val MARKER_BOOLEAN: Byte = 0x01
    const val MARKER_STRING: Byte = 0x02
    const val MARKER_OBJECT: Byte = 0x03
    const val MARKER_NULL: Byte = 0x05
    const val MARKER_ECMA_ARRAY: Byte = 0x08
    const val MARKER_STRICT_ARRAY: Byte = 0x0A

    class Encoder {
        private val out = ByteArrayOutputStream()

        fun number(v: Double): Encoder {
            out.write(MARKER_NUMBER.toInt())
            val bits = java.lang.Double.doubleToLongBits(v)
            for (i in 7 downTo 0) out.write(((bits shr (8 * i)) and 0xFF).toInt())
            return this
        }

        fun bool(v: Boolean): Encoder {
            out.write(MARKER_BOOLEAN.toInt())
            out.write(if (v) 1 else 0)
            return this
        }

        fun string(v: String): Encoder {
            val bytes = v.toByteArray(Charsets.UTF_8)
            out.write(MARKER_STRING.toInt())
            out.write((bytes.size shr 8) and 0xFF)
            out.write(bytes.size and 0xFF)
            out.write(bytes)
            return this
        }

        fun nul(): Encoder {
            out.write(MARKER_NULL.toInt())
            return this
        }

        /** Writes an AMF0 object (or ECMA array body) WITHOUT the marker. */
        private fun objectBody(map: Map<String, Any?>): Encoder {
            for ((k, v) in map) {
                val kb = k.toByteArray(Charsets.UTF_8)
                out.write((kb.size shr 8) and 0xFF)
                out.write(kb.size and 0xFF)
                out.write(kb)
                value(v)
            }
            // object end: 00 00 09
            out.write(0); out.write(0); out.write(9)
            return this
        }

        fun obj(map: Map<String, Any?>): Encoder {
            out.write(MARKER_OBJECT.toInt())
            return objectBody(map)
        }

        fun ecma(map: Map<String, Any?>): Encoder {
            out.write(MARKER_ECMA_ARRAY.toInt())
            out.write(((map.size shr 24) and 0xFF))
            out.write(((map.size shr 16) and 0xFF))
            out.write(((map.size shr 8) and 0xFF))
            out.write((map.size and 0xFF))
            return objectBody(map)
        }

        fun strictArray(items: List<Any?>): Encoder {
            out.write(MARKER_STRICT_ARRAY.toInt())
            out.write((items.size shr 24) and 0xFF)
            out.write((items.size shr 16) and 0xFF)
            out.write((items.size shr 8) and 0xFF)
            out.write(items.size and 0xFF)
            items.forEach { value(it) }
            return this
        }

        private fun value(v: Any?) {
            when (v) {
                null -> nul()
                is Double -> number(v)
                is Int -> number(v.toDouble())
                is Long -> number(v.toDouble())
                is Boolean -> bool(v)
                is String -> string(v)
                is Map<*, *> -> nul() // nested objects unused in our messages
                is List<*> -> strictArray(v)
                else -> string(v.toString())
            }
        }

        fun bytes(): ByteArray = out.toByteArray()
    }

    // ---------------------------------------------------------------- reader

    class Reader(private val input: DataInputStream) {

        /** Reads one AMF0 value (type marker already peeked inside). */
        fun read(): Any? {
            val type = input.readByte().toInt() and 0xFF
            return when (type) {
                MARKER_NUMBER.toInt() -> input.readDouble()
                MARKER_BOOLEAN.toInt() -> input.readByte().toInt() != 0
                MARKER_STRING.toInt() -> {
                    val len = input.readUnsignedShort()
                    val b = ByteArray(len); input.readFully(b)
                    String(b, Charsets.UTF_8)
                }
                MARKER_OBJECT.toInt(), MARKER_ECMA_ARRAY.toInt() -> {
                    if (type == MARKER_ECMA_ARRAY.toInt()) input.readInt() // count
                    val map = LinkedHashMap<String, Any?>()
                    while (true) {
                        val klen = input.readUnsignedShort()
                        if (klen == 0) {
                            val end = input.readByte() // 0x09
                            break
                        }
                        val kb = ByteArray(klen); input.readFully(kb)
                        map[String(kb, Charsets.UTF_8)] = read()
                    }
                    map
                }
                MARKER_STRICT_ARRAY.toInt() -> {
                    val count = input.readInt()
                    List(count) { read() }
                }
                MARKER_NULL.toInt(), 0x06 /* undefined */ -> null
                0x0B /* date */ -> { input.readDouble(); input.readShort(); null }
                0x0C /* long string */ -> {
                    val len = input.readInt()
                    val b = ByteArray(len); input.readFully(b)
                    String(b, Charsets.UTF_8)
                }
                else -> throw IllegalArgumentException("AMF0 type $type not supported")
            }
        }
    }
}
