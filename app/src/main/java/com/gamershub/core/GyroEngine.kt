package com.gamershub.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import org.json.JSONObject

/** Persisted gyro calibration: bias offsets + sensitivity + axis inversion. */
data class GyroConfig(
    val offX: Float = 0f,
    val offY: Float = 0f,
    val offZ: Float = 0f,
    val sens: Float = 1f,
    val invertY: Boolean = false,
    val calibrated: Boolean = false
) {
    /** Applies bias + sensitivity to a raw reading, producing the corrected value. */
    fun correct(x: Float, y: Float, z: Float): FloatArray {
        val cy = (y - offY) * sens * if (invertY) -1f else 1f
        return floatArrayOf((x - offX) * sens, cy, (z - offZ) * sens)
    }
}

/**
 * Gyro engine: reads the real gyroscope, keeps a bias calibration in prefs and
 * exposes the corrected stream used by the gyro panel and the aim test.
 */
object GyroEngine {

    private const val PREFS = "gamershub"
    private const val KEY = "gyro_config"

    fun sensorManager(context: Context): SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun sensor(context: Context): Sensor? =
        sensorManager(context)?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    fun accelerometer(context: Context): Sensor? =
        sensorManager(context)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun hasGyro(context: Context): Boolean = sensor(context) != null

    fun load(context: Context): GyroConfig {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return GyroConfig()
        return try {
            val json = JSONObject(text)
            GyroConfig(
                offX = json.optDouble("offX", 0.0).toFloat(),
                offY = json.optDouble("offY", 0.0).toFloat(),
                offZ = json.optDouble("offZ", 0.0).toFloat(),
                sens = json.optDouble("sens", 1.0).toFloat(),
                invertY = json.optBoolean("invertY", false),
                calibrated = json.optBoolean("calibrated", false)
            )
        } catch (_: Throwable) {
            GyroConfig()
        }
    }

    fun save(context: Context, config: GyroConfig) {
        val json = JSONObject()
            .put("offX", config.offX.toDouble())
            .put("offY", config.offY.toDouble())
            .put("offZ", config.offZ.toDouble())
            .put("sens", config.sens.toDouble())
            .put("invertY", config.invertY)
            .put("calibrated", config.calibrated)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    /** Mean bias of a still-device sample set — the actual calibration step. */
    fun biasOf(samples: List<FloatArray>): FloatArray {
        if (samples.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        var sx = 0f
        var sy = 0f
        var sz = 0f
        for (s in samples) {
            if (s.size < 3) continue
            sx += s[0]
            sy += s[1]
            sz += s[2]
        }
        val n = samples.size.toFloat()
        return floatArrayOf(sx / n, sy / n, sz / n)
    }

    /** Quality of a calibration sample: low spread = a good still capture. */
    fun drift(samples: List<FloatArray>): Float {
        if (samples.size < 2) return 0f
        val bias = biasOf(samples)
        var worst = 0f
        for (s in samples) {
            if (s.size < 3) continue
            val d = kotlin.math.abs(s[0] - bias[0]) + kotlin.math.abs(s[1] - bias[1]) + kotlin.math.abs(s[2] - bias[2])
            if (d > worst) worst = d
        }
        return worst
    }
}
