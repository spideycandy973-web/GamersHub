package com.gamershub.core.engine

import android.content.Context
import com.gamershub.core.battery.BatteryManager
import com.gamershub.core.boost.BoostEngine
import com.gamershub.core.boost.ThermalManager
import com.gamershub.core.boost.ThermalProfile
import com.gamershub.core.enhance.GraphicsEnhancer
import com.gamershub.core.overlay.FloatingWidgetService
import com.gamershub.core.scaling.FrameGenEngine
import com.gamershub.core.scaling.LosslessScalingEngine
import com.gamershub.core.service.GamersHubCoreService
import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.shizuku.ShizukuManager
import com.gamershub.core.spoof.DeviceSpoofer
import com.gamershub.core.unlock.GameConfigUnlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The master ON/OFF switch of the whole app.
 *
 * ON  — every engine is live (boost, thermal, spoof, unlock, scaling, frame-gen,
 *       enhancer, widget, auto profiles).
 * OFF — *everything stops immediately and the device goes back to stock*:
 *       display size/density/refresh restored, boost & thermal overrides reset,
 *       spoofed props removed, MEMC / GPU-rendering / colour overrides removed,
 *       bypass charging disabled, every patched game config rolled back from its
 *       `.ghbak` backup, widget + core services stopped. The app becomes
 *       read-only (monitoring only) until the switch is turned on again.
 */
class EngineCoordinator(
    private val context: Context,
    private val settings: SettingsRepository,
    private val shizuku: ShizukuManager,
    private val boost: BoostEngine,
    private val thermal: ThermalManager,
    private val spoofer: DeviceSpoofer,
    private val scaling: LosslessScalingEngine,
    private val frameGen: FrameGenEngine,
    private val unlocker: GameConfigUnlocker,
    private val battery: BatteryManager,
    private val enhancer: GraphicsEnhancer
) {

    private val scope = com.gamershub.core.util.SafeScopes.io("engine")

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastAction = MutableStateFlow("Engine ready")
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    init {
        scope.launch {
            settings.engineEnabled.collect { _enabled.value = it }
        }
    }

    /** Flips the switch. OFF runs the full teardown and returns its log. */
    suspend fun setEnabled(on: Boolean): List<String> {
        _busy.value = true
        settings.setEngineEnabled(on)
        _enabled.value = on
        val log = try {
            if (on) {
                listOf("Engine ON — all systems live")
            } else {
                teardown()
            }
        } catch (t: Throwable) {
            listOf("Teardown error: ${t.message}")
        }
        _busy.value = false
        _lastAction.value = log.lastOrNull() ?: ""
        return log
    }

    /**
     * Restores the device to stock state. Every step is wrapped so one failing
     * subsystem can never block the rest of the rollback.
     */
    suspend fun teardown(): List<String> {
        val log = mutableListOf<String>()
        log += "Master switch OFF — restoring device…"

        // 1. background services first — nothing may re-apply settings
        runCatching {
            GamersHubCoreService.stop(context)
            log += "Boost engine service stopped"
        }.onFailure { log += "Core service: ${it.message}" }
        runCatching {
            FloatingWidgetService.stop(context)
            log += "Floating widget stopped"
        }.onFailure { log += "Widget: ${it.message}" }

        if (!shizuku.canUseShell) {
            log += "Shizuku shell not connected — system-level state untouched"
            log += "Device is idle (engine off)"
            return log
        }

        // 2. boost (refresh rate, resolution, animator scales, DND…)
        runCatching {
            boost.restoreBoost()
            log += "Boost overrides restored"
        }.onFailure { log += "Boost restore: ${it.message}" }

        // 3. thermal service back to stock
        runCatching {
            thermal.setProfile(ThermalProfile.COOL)
            thermal.enforceNow() // COOL → 'cmd thermalservice reset'
            log += "Thermal service reset"
        }.onFailure { log += "Thermal: ${it.message}" }

        // 4. device spoof props
        runCatching {
            spoofer.remove()
            log += "Spoofed properties removed"
        }.onFailure { log += "Spoof: ${it.message}" }

        // 5. frame generation / MEMC
        runCatching {
            frameGen.setEnabled(false)
            log += "Frame generation off"
        }.onFailure { log += "Frame-gen: ${it.message}" }

        // 6. lossless scaling — display size / density / refresh back to native
        runCatching {
            scaling.restore()
            log += "Display resolution & refresh restored"
        }.onFailure { log += "Scaling: ${it.message}" }

        // 7. graphics enhancer overrides
        runCatching {
            log += enhancer.disableAll()
        }.onFailure { log += "Enhancer: ${it.message}" }

        // 8. bypass charging off
        runCatching {
            battery.setBypassCharging(false)
            log += "Bypass charging disabled"
        }.onFailure { log += "Battery: ${it.message}" }

        // 9. game configs — roll every patched file back from its backup
        runCatching {
            val restored = unlocker.restoreAllBackups()
            log += if (restored > 0) "Restored $restored game config file(s)"
            else "No patched game configs to restore"
        }.onFailure { log += "Game configs: ${it.message}" }

        log += "Done — the phone is back to normal"
        return log
    }
}
