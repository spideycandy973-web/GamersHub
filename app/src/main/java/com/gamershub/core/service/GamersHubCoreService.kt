package com.gamershub.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.gamershub.GamersHubApp
import com.gamershub.core.boost.BoostPlan
import com.gamershub.core.boost.BoostState
import com.gamershub.core.boost.ThermalProfile
import com.gamershub.core.launcher.GameEntry
import com.gamershub.core.notifications.NotificationHelper
import com.gamershub.core.spoof.DeviceSpoofer
import com.gamershub.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Companion foreground service. While running it:
 *  - watches the foreground app; when a profiled game launches it auto-applies
 *    that game's boost + thermal profile (+ spoof preset)
 *  - restores the system when the game exits
 *  - keeps the boost alive across process death
 */
class GamersHubCoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AppContainer
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        container = GamersHubApp.container(application as GamersHubApp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startEngine()
        }
        return START_STICKY
    }

    private fun startEngine() {
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_CORE,
            container.notifications.coreNotification("Game boost engine ready"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gamershub:core")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }

        // Watch foreground app -> auto game profiles
        scope.launch {
            container.games.refresh()
            var boostedPkg: String? = null
            container.monitor.stats.collect { stats ->
                val fg = stats.foregroundPackage
                if (fg == boostedPkg) return@collect

                val autoBoost = container.settings.autoBoost.first()
                if (!autoBoost) return@collect

                val profile = if (fg.isNotBlank()) container.profiles.profiles.value[fg] else null

                if (profile != null && boostedPkg == null) {
                    // Entering a profiled game -> apply its plan
                    boostedPkg = fg
                    val plan = BoostPlan(
                        performanceMode = true,
                        refreshRateHz = profile.refreshRate,
                        resolutionScale = profile.resolutionScale,
                        killBackground = profile.killBackground,
                        blockNotifications = profile.blockNotifications
                    )
                    runCatching { ThermalProfile.valueOf(profile.thermalProfile) }
                        .getOrNull()?.let { container.thermal.setProfile(it) }
                    container.thermal.enforceNow()
                    container.boost.applyBoost(plan, gamePackage = fg)
                    profile.spoofPresetId?.let { id ->
                        DeviceSpoofer.presetById(id)?.let { container.spoofer.apply(it) }
                    }
                    updateNotification("Boosting ${profile.label.ifBlank { fg }}")
                } else if (profile == null && boostedPkg != null) {
                    // Left the game -> restore
                    boostedPkg = null
                    container.boost.restoreBoost()
                    container.spoofer.remove()
                    updateNotification("Game boost engine ready")
                }
            }
        }

        // Keep notification fresh while boosted
        scope.launch {
            container.boost.state.collect { s ->
                if (s is BoostState.Active) {
                    val stats = container.monitor.stats.value
                    updateNotification(
                        "Boost active · ${stats.fps.takeIf { it >= 0 }?.toInt() ?: "--"} FPS · " +
                                "${stats.batteryTempC.takeIf { it > 0 } ?: "--"}°C"
                    )
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        runCatching {
            nm.notify(NotificationHelper.ID_CORE, container.notifications.coreNotification(text))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.gamershub.action.CORE_START"
        const val ACTION_STOP = "com.gamershub.action.CORE_STOP"

        fun start(context: Context) {
            val intent = Intent(context, GamersHubCoreService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GamersHubCoreService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
