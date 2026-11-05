package com.gamershub.core

import android.content.Context
import androidx.compose.ui.graphics.Color

/**
 * Configuration of the floating monitor (which fields, size, colours).
 * Stored in the same "gamershub" preference file the rest of the app uses.
 */
object MonitorPrefs {

    private const val PREFS = "gamershub"
    private const val KEY_FIELDS = "monitor_fields"
    private const val KEY_SCALE = "monitor_scale"
    private const val KEY_ENABLED = "monitor_enabled"

    val ORDER = listOf("fps", "cpu", "gpu", "ram", "temp", "batt", "ping", "hz", "clk")

    val LABELS = mapOf(
        "fps" to "FPS",
        "cpu" to "CPU",
        "gpu" to "GPU",
        "ram" to "RAM",
        "temp" to "TEMP",
        "batt" to "BATT",
        "ping" to "PING",
        "hz" to "HZ",
        "clk" to "CLK"
    )

    fun colorFor(field: String): Color = when (field) {
        "fps" -> Color(0xFF52E08A)
        "cpu" -> Color(0xFFFF6B5E)
        "gpu" -> Color(0xFFB98BFF)
        "ram" -> Color(0xFFFF4FA3)
        "temp" -> Color(0xFFFFC145)
        "batt" -> Color(0xFF6BD3FF)
        "ping" -> Color(0xFF7CE0C3)
        "hz" -> Color(0xFFE8E1D9)
        else -> Color(0xFFC9B8A8)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val DEFAULT_FIELDS = listOf("fps", "cpu", "ram", "temp", "ping")

    fun fields(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_FIELDS, null) ?: return DEFAULT_FIELDS
        val list = raw.split(",").map { it.trim() }.filter { ORDER.contains(it) }
        return if (list.isEmpty()) DEFAULT_FIELDS else list
    }

    fun setFields(context: Context, fields: List<String>) {
        prefs(context).edit().putString(KEY_FIELDS, fields.joinToString(",")).apply()
    }

    fun toggleField(context: Context, field: String): Boolean {
        val current = fields(context).toMutableList()
        if (current.contains(field)) {
            if (current.size == 1) return false
            current.remove(field)
        } else {
            current.add(field)
        }
        setFields(context, current)
        return true
    }

    fun scale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1f).coerceIn(0.7f, 1.8f)

    fun setScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale.coerceIn(0.7f, 1.8f)).apply()
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
