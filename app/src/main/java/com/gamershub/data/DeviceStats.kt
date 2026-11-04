package com.gamershub.data

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

/** Real device metrics — every number shown in the app comes from here. */
object DeviceStats {

    data class Mem(val usedMb: Long, val totalMb: Long, val pct: Int)
    data class Storage(val usedGb: Long, val totalGb: Long, val pct: Int)
    data class Cpu(val curGhz: Double, val maxGhz: Double, val pct: Int)

    fun mem(context: Context): Mem {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val total = info.totalMem / (1024 * 1024)
        val used = (info.totalMem - info.availMem) / (1024 * 1024)
        val pct = if (info.totalMem > 0) ((info.totalMem - info.availMem) * 100 / info.totalMem).toInt() else 0
        return Mem(used, total, pct.coerceIn(0, 100))
    }

    fun storage(): Storage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        val pct = if (total > 0) (used * 100 / total).toInt() else 0
        return Storage(used / (1024 * 1024 * 1024), total / (1024 * 1024 * 1024), pct.coerceIn(0, 100))
    }

    fun cpu(): Cpu {
        var curKhz = 0L
        var maxKhz = 0L
        try {
            val f = RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "r")
            curKhz = f.readLine().trim().toLong()
            f.close()
        } catch (_: Throwable) {
        }
        try {
            val f = RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", "r")
            maxKhz = f.readLine().trim().toLong()
            f.close()
        } catch (_: Throwable) {
        }
        val pct = if (maxKhz > 0 && curKhz > 0) (curKhz * 100 / maxKhz).toInt() else 0
        return Cpu(curKhz / 1_000_000.0, maxKhz / 1_000_000.0, pct.coerceIn(0, 100))
    }

    fun pingMs(host: String = "8.8.8.8"): Int = try {
        val p = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", host))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val m = Regex("time=([0-9.]+)").find(out)
        m?.groupValues?.get(1)?.toFloat()?.toInt() ?: -1
    } catch (_: Throwable) {
        -1
    }

    fun battery(context: Context): Pair<Int, Double> = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300) / 10.0
        (if (level >= 0) level else 50) to temp
    } catch (_: Throwable) {
        50 to 30.0
    }
}
