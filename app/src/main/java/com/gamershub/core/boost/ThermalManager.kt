package com.gamershub.core.boost

import com.gamershub.core.monitor.HardwareMonitor
import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thermal profiles the user can bind globally or per-game.
 *
 *  - COOL:      never override thermal status, cap refresh 60 Hz. Longest
 *               sessions, safest for battery + hardware longevity.
 *  - BALANCED:  light override (status LIGHT), refresh 90 Hz.
 *  - BEAST:     override status NONE (apps believe the device is cold and never
 *               self-throttle), fixed performance mode, max refresh.
 *  - EXTREME:   BEAST + a higher governor target temperature. Use with caution.
 */
enum class ThermalProfile {
    COOL, BALANCED, BEAST, EXTREME;

    val targetBatteryTempC: Int
        get() = when (this) {
            COOL -> 38
            BALANCED -> 40
            BEAST -> 42
            EXTREME -> 45
        }

    val overrideStatusCmd: String?
        get() = when (this) {
            COOL -> null // natural behaviour
            BALANCED -> "cmd thermalservice override-status 1"
            BEAST, EXTREME -> "cmd thermalservice override-status 0"
        }

    val refreshCapHz: Int
        get() = when (this) {
            COOL -> 60
            BALANCED -> 90
            BEAST, EXTREME -> 120
        }
}

class ThermalManager(
    private val shizuku: ShizukuManager,
    private val monitor: HardwareMonitor,
    private val settings: SettingsRepository
) {
    private val scope = com.gamershub.core.util.SafeScopes.io("thermal")

    /** Currently enforced profile. */
    private val _activeProfile = MutableStateFlow(ThermalProfile.BEAST)
    val activeProfile: StateFlow<ThermalProfile> = _activeProfile.asStateFlow()

    init {
        scope.launch {
            settings.thermalProfile.collect { name ->
                runCatching { ThermalProfile.valueOf(name) }.getOrNull()?.let {
                    _activeProfile.value = it
                }
            }
        }
    }

    /** Per-game override wins over the global profile. */
    fun targetTempFor(profile: ThermalProfile, foregroundPackage: String): Int {
        // The governor passes the foreground package; GameProfile binding is
        // resolved by GamersHubCoreService before boost is applied - here we just
        // honor the active profile's target.
        return profile.targetBatteryTempC
    }

    suspend fun setProfile(profile: ThermalProfile) {
        _activeProfile.value = profile
        settings.setThermalProfile(profile.name)
        settings.setThermalTargetTemp(profile.targetBatteryTempC)
    }

    /** Applies profile's kernel-facing commands right now. */
    suspend fun enforceNow() {
        if (!shizuku.canUseShell) return
        val p = _activeProfile.value
        val cmd = p.overrideStatusCmd
        if (cmd != null) shizuku.exec(cmd) else shizuku.exec("cmd thermalservice reset")
    }

    /** Snapshot of all important temps for the thermal screen. */
    fun currentTemps(): Triple<Float, Float, Float> {
        val s = monitor.stats.value
        return Triple(s.cpuTempC, s.gpuTempC, s.batteryTempC)
    }
}
