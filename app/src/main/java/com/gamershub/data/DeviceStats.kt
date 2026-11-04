package com.gamershub.data

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/** Real device metrics — no fake numbers. */
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
        return Mem(used, total, pct)
    }

    fun storage(): Storage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        val pct = if (total > 0) (used * 100 / total).toInt() else 0
        return Storage(used / (1024 * 1024 * 1024), total / (1024 * 1024 * 1024), pct)
    }

    fun cpu(): Cpu {
        var curKhz = -1L
        var maxKhz = -1L
        for (i in 0 until 16) {
            val cur = readKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val max = readKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (cur > curKhz) curKhz = cur
            if (max > maxKhz) maxKhz = max
        }
        if (maxKhz <= 0) maxKhz = readKhz("/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq")
        val curG = if (curKhz > 0) curKhz / 1_000_000.0 else 0.0
        val maxG = if (maxKhz > 0) maxKhz / 1_000_000.0 else 0.0
        val pct = if (maxKhz > 0 && curKhz > 0) (curKhz * 100 / maxKhz).toInt() else 0
        return Cpu(curG, maxG, pct)
    }

    private fun readKhz(path: String): Long = try {
        File(path).readText().trim().toLong()
    } catch (_: Throwable) {
        -1L
    }

    /** Real ICMP ping to 8.8.8.8 in ms, or -1 when unavailable. */
    fun pingMs(host: String = "8.8.8.8"): Int = try {
        val p = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", host))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val m = Regex("time=([0-9.]+)").find(out)
        m?.groupValues?.get(1)?.toFloat()?.toInt() ?: -1
    } catch (_: Throwable) {
        -1
    }

    /** Battery percent + temperature C from the sticky battery intent (real). */
    fun battery(context: Context): Pair<Int, Double> = try {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val tempTenths = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val temp = if (tempTenths > 0) tempTenths / 10.0 else -1.0
        pct to temp
    } catch (_: Throwable) {
        -1 to -1.0
    }
}
