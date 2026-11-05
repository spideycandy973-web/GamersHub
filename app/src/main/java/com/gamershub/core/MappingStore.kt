package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * One macro key of the mapping overlay. Position is stored as a fraction of the
 * screen so a layout created in portrait still lands correctly in a landscape game.
 */
data class MacroKey(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val kind: String = MappingStore.TAP,
    val payload: String = "",
    val size: Int = 52
)

/**
 * Mapping store + real injector.
 *
 * Every key compiles down to an `input` command that is executed with shell
 * identity through Shizuku, so a tap on the overlay actually reaches the game.
 */
object MappingStore {

    const val TAP = "TAP"
    const val MULTI = "MULTI"
    const val SWIPE = "SWIPE"
    const val KEYEVENT = "KEY"
    const val CMD = "CMD"

    const val PREFS = "gamershub"
    const val KEY_KEYS = "mapping_keys"
    const val KEY_EDIT = "mapping_edit_mode"
    const val KEY_ENABLED = "mapping_enabled"

    val kinds = listOf(TAP, MULTI, SWIPE, KEYEVENT, CMD)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<MacroKey> {
        val text = prefs(context).getString(KEY_KEYS, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                MacroKey(
                    id = o.optString("id", "k$i"),
                    label = o.optString("label", "KEY"),
                    x = o.optDouble("x", 0.5).toFloat(),
                    y = o.optDouble("y", 0.5).toFloat(),
                    kind = o.optString("kind", TAP),
                    payload = o.optString("payload", ""),
                    size = o.optInt("size", 52)
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(context: Context, keys: List<MacroKey>) {
        val array = JSONArray()
        keys.forEach { key ->
            array.put(
                JSONObject()
                    .put("id", key.id)
                    .put("label", key.label)
                    .put("x", key.x.toDouble())
                    .put("y", key.y.toDouble())
                    .put("kind", key.kind)
                    .put("payload", key.payload)
                    .put("size", key.size)
            )
        }
        prefs(context).edit().putString(KEY_KEYS, array.toString()).apply()
    }

    fun upsert(context: Context, key: MacroKey) {
        val keys = load(context).toMutableList()
        val index = keys.indexOfFirst { it.id == key.id }
        if (index >= 0) keys[index] = key else keys.add(key)
        save(context, keys)
    }

    fun remove(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun move(context: Context, id: String, x: Float, y: Float) {
        val keys = load(context).toMutableList()
        val index = keys.indexOfFirst { it.id == id }
        if (index >= 0) {
            keys[index] = keys[index].copy(
                x = x.coerceIn(0.02f, 0.98f),
                y = y.coerceIn(0.02f, 0.98f)
            )
            save(context, keys)
        }
    }

    fun editMode(context: Context): Boolean = prefs(context).getBoolean(KEY_EDIT, true)

    fun setEditMode(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_EDIT, on).apply()
    }

    /** Compiles a key into the shell command that performs it at real screen pixels. */
    fun buildCommand(key: MacroKey, width: Int, height: Int): String {
        val px = (key.x * width).roundToInt().coerceAtLeast(0)
        val py = (key.y * height).roundToInt().coerceAtLeast(0)
        return when (key.kind) {
            MULTI -> {
                val parts = key.payload.split(",").mapNotNull { it.trim().toIntOrNull() }
                val times = parts.getOrNull(0)?.coerceIn(1, 20) ?: 2
                val gap = parts.getOrNull(1)?.coerceIn(10, 500) ?: 60
                val single = "input tap $px $py"
                val pause = "sleep ${(gap / 1000.0)}"
                (1..times).joinToString("; ") { single } + "; " + pause
            }
            SWIPE -> {
                val p = key.payload.split(",").mapNotNull { it.trim().toFloatOrNull() }
                val dx = p.getOrNull(0) ?: 200f
                val dy = p.getOrNull(1) ?: 0f
                val ms = (p.getOrNull(2) ?: 220f).toInt()
                val x2 = (px + dx).roundToInt()
                val y2 = (py + dy).roundToInt()
                "input swipe $px $py $x2 $y2 $ms"
            }
            KEYEVENT -> "input keyevent ${key.payload.ifBlank { "4" }}"
            CMD -> key.payload.ifBlank { "true" }
            else -> "input tap $px $py"
        }
    }

    fun execute(context: Context, key: MacroKey): Boolean {
        val metrics = context.resources.displayMetrics
        val command = buildCommand(key, metrics.widthPixels, metrics.heightPixels)
        return Shell.ok(command)
    }

    /** Ships with a sensible FPS layout so the overlay is usable immediately. */
    fun defaults(): List<MacroKey> = listOf(
        MacroKey("fire", "FIRE", 0.86f, 0.80f, TAP, "", 62),
        MacroKey("scope", "SCOPE", 0.74f, 0.86f, TAP, "", 48),
        MacroKey("jump", "JUMP", 0.93f, 0.66f, TAP, "", 48),
        MacroKey("reload", "RELOAD", 0.66f, 0.90f, TAP, "", 46),
        MacroKey("burst", "BURST", 0.80f, 0.62f, MULTI, "3,80", 46)
    )

    fun ensureSeeded(context: Context) {
        if (load(context).isEmpty()) save(context, defaults())
    }
}
