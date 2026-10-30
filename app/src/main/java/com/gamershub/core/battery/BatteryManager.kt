package com.gamershub.core.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Battery control & bypass charging.
 *
 * Bypass charging routes charger power straight to the motherboard so the
 * battery is (almost) excluded during gaming - huge for thermals + longevity.
 * It needs a kernel power-supply node; availability is fully probed at runtime
 * and the UI reflects what's actually supported on the device.
 */
class BatteryManager(private val shizuku: ShizukuManager) {

    enum class BypassSupport { UNKNOWN, SUPPORTED_SHELL, NEEDS_ROOT, UNSUPPORTED }

    data class BatteryInfo(
        val levelPct: Int = -1,
        val tempC: Float = -1f,
        val voltageMv: Int = -1,
        val currentMa: Int = -1,
        val charging: Boolean = false,
        val health: String = "unknown"
    )

    private val _bypassEnabled = MutableStateFlow<Boolean?>(null)
    val bypassEnabled: StateFlow<Boolean?> = _bypassEnabled.asStateFlow()

    private val _support = MutableStateFlow(BypassSupport.UNKNOWN)
    val support: StateFlow<BypassSupport> = _support.asStateFlow()

    private var activeNode: String? = null

    private data class BypassNode(val path: String, val enable: String, val disable: String, val label: String)

    companion object {
        /** Known bypass / charge-control nodes across OEMs. */
        private val NODES = listOf(
            BypassNode("/sys/class/power_supply/battery/input_suspend", "1", "0", "Generic input_suspend"),
            BypassNode("/sys/class/power_supply/battery/battery_charging_enabled", "0", "1", "Samsung charging_enabled"),
            BypassNode("/sys/class/power_supply/battery/bd_trickle_cntl", "1", "0", "ASUS ROG bypass"),
            BypassNode("/sys/class/power_supply/battery/charging_enabled", "0", "1", "Legacy charging_enabled"),
            BypassNode("/sys/class/qnovo/qnovo_enable", "0", "1", "Qnovo (disable Qnovo = bypass-ish)"),
            BypassNode("/sys/devices/platform/charge_input/suspend", "1", "0", "Charge input suspend"),
            BypassNode("/proc/mtk_battery_cmd/current_cmd", "0 1", "0 0", "MediaTek current_cmd"),
        )
    }

    suspend fun probe(): BypassSupport = withContext(Dispatchers.IO) {
        if (!shizuku.canUseShell) {
            _support.value = BypassSupport.UNKNOWN
            return@withContext BypassSupport.UNKNOWN
        }
        for (node in NODES) {
            val exists = shizuku.exec("test -e ${node.path} && echo Y").trimmed
            if (exists == "Y") {
                activeNode = node.path
                val writable = shizuku.exec("test -w ${node.path} && echo W").trimmed == "W"
                _support.value = when {
                    writable -> BypassSupport.SUPPORTED_SHELL
                    shizuku.isRoot.value -> BypassSupport.SUPPORTED_SHELL // root can chmod/write
                    else -> BypassSupport.NEEDS_ROOT
                }
                // Read current state if we can
                readNodeState(node)
                return@withContext _support.value
            }
        }
        _support.value = BypassSupport.UNSUPPORTED
        BypassSupport.UNSUPPORTED
    }

    private suspend fun readNodeState(node: BypassNode) {
        val raw = shizuku.exec("cat ${node.path}").trimmed
        _bypassEnabled.value = raw == node.enable
    }

    /**
     * Enable/disable bypass charging. Returns a human-readable result line.
     */
    suspend fun setBypassCharging(on: Boolean): String = withContext(Dispatchers.IO) {
        val path = activeNode ?: probe().let {
            activeNode ?: return@withContext "No bypass-charging node found on this device"
        }
        val node = NODES.first { it.path == path }
        val value = if (on) node.enable else node.disable

        var res = shizuku.exec("echo $value > $path")
        if (!res.ok && shizuku.isRoot.value) {
            res = shizuku.exec("su -c 'echo $value > $path'")
        }
        return@withContext if (res.ok) {
            _bypassEnabled.value = on
            if (on) "Bypass charging ON (${node.label}) - battery is now bypassed"
            else "Bypass charging OFF (${node.label})"
        } else {
            "Failed: ${res.trimmed.ifBlank { "permission denied (root required)" }}"
        }
    }

    /** Battery info snapshot (no root needed; uses sticky broadcast + sysfs). */
    fun readBatteryInfo(context: Context): BatteryInfo {
        val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = i?.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, -1000) ?: -1000
        val voltage = i?.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val status = i?.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = when (i?.getIntExtra(AndroidBatteryManager.EXTRA_HEALTH, -1)) {
            AndroidBatteryManager.BATTERY_HEALTH_GOOD -> "good"
            AndroidBatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            AndroidBatteryManager.BATTERY_HEALTH_COLD -> "cold"
            AndroidBatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over-voltage"
            else -> "unknown"
        }
        val currentNow = runCatching {
            File("/sys/class/power_supply/battery/current_now").readText().trim().toInt() / 1000
        }.getOrDefault(-1)
        return BatteryInfo(
            levelPct = if (scale > 0) (level * 100) / scale else -1,
            tempC = if (temp == -1000) -1f else temp / 10f,
            voltageMv = voltage,
            currentMa = currentNow,
            charging = status == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                    status == AndroidBatteryManager.BATTERY_STATUS_FULL,
            health = health
        )
    }

    /**
     * Stops the battery manager daemon view ("cmd battery"): fake-unplug helpers are
     * only for diagnostics; real bypass needs the nodes above.
     */
    suspend fun estimateHealthHint(): String {
        val res = shizuku.exec("dumpsys battery | grep -E 'level|health|status|temp' | head -n 8")
        return if (res.ok) res.trimmed else "unavailable"
    }
}
