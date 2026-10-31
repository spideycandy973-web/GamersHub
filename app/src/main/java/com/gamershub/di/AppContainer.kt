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
    val enhancer = GraphicsEnhancer(shizuku)
    val engine = EngineCoordinator(
        app, settings, shizuku, boost, thermal, spoofer,
        scaling, frameGen, unlocker, battery, enhancer
    )

    fun init() {
        notifications.createChannels()
        shizuku.init()
        monitor.start()
        boost.attachThermalGovernor(thermal)
    }
}
