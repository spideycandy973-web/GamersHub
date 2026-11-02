package com.gamershub.di

import android.app.Application
import com.gamershub.core.analytics.AnalyticsEngine
import com.gamershub.core.battery.BatteryManager
import com.gamershub.core.boost.BoostEngine
import com.gamershub.core.boost.ThermalManager
import com.gamershub.core.engine.EngineCoordinator
import com.gamershub.core.enhance.GraphicsEnhancer
import com.gamershub.core.launcher.GameLibrary
import com.gamershub.core.monitor.HardwareMonitor
import com.gamershub.core.notifications.NotificationHelper
import com.gamershub.core.profiles.GameProfileStore
import com.gamershub.core.record.RecorderController
import com.gamershub.core.scaling.FrameGenEngine
import com.gamershub.core.scaling.LosslessScalingEngine
import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.shizuku.ShizukuManager
import com.gamershub.core.spoof.DeviceSpoofer
import com.gamershub.core.stream.StreamEngine
import com.gamershub.core.stream.chat.ChatHub
import com.gamershub.core.unlock.GameConfigUnlocker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lightweight manual dependency container. Keeps the app fast to start and free
 * of annotation-processing overhead.
 */
class AppContainer(private val app: Application) {

    val notifications = NotificationHelper(app)
    val settings = SettingsRepository(app)
    val shizuku = ShizukuManager(app)
    val monitor = HardwareMonitor(app, shizuku)
    val thermal = ThermalManager(shizuku, monitor, settings)
    val boost = BoostEngine(app, shizuku, settings, monitor)
    val spoofer = DeviceSpoofer(app, shizuku, settings)
    val battery = BatteryManager(shizuku)
    val unlocker = GameConfigUnlocker(app, shizuku)
    val scaling = LosslessScalingEngine(shizuku)
    val frameGen = FrameGenEngine(shizuku)
    val profiles = GameProfileStore(app)
    val games = GameLibrary(app, profiles)
    val analytics = AnalyticsEngine(app, monitor)
    val recorder = RecorderController(app, settings, analytics)
    val stream = StreamEngine(app, settings)
    val chat = ChatHub(settings)
    val enhancer = GraphicsEnhancer(shizuku, scaling)
    val engine = EngineCoordinator(
        app, settings, shizuku, boost, thermal, spoofer,
        scaling, frameGen, unlocker, battery, enhancer, monitor
    )

    private val appScope = com.gamershub.core.util.SafeScopes.io("container")

    fun init() {
        notifications.createChannels()
        boost.attachThermalGovernor(thermal)
        // Honor the master switch persisted from the last session: when it was
        // OFF the phone must stay completely stock — no monitor loops, no
        // Shizuku shell binding, nothing running in the background.
        appScope.launch {
            val engineOn = runCatching {
                settings.engineEnabled.first()
            }.getOrDefault(true)
            shizuku.setShellSuspended(!engineOn)
            shizuku.init()
            if (engineOn) {
                monitor.start()
                // Heal system animation scales left at 0 / 0.5 (old boost
                // versions, dev options). With animators disabled the OS SKIPS
                // every transition — which is why "the app has no animations".
                // Only broken values are touched; 1.0/1.5/2.0 are respected.
                listOf(
                    "window_animation_scale",
                    "transition_animation_scale",
                    "animator_duration_scale"
                ).forEach { k ->
                    val v = shizuku.exec("settings get global $k").trimmed
                    if (v == "0" || v == "0.0" || v == "0.5") {
                        shizuku.exec("settings put global $k 1.0")
                        shizuku.exec("settings put system $k 1.0")
                    }
                }
            }
        }
    }
}
