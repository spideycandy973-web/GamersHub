package com.gamershub.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gamershub.GamersHubApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts the game-boost engine after boot when the user opted in. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        com.gamershub.core.util.SafeScopes.io("boot").launch {
            try {
                val app = context.applicationContext as? GamersHubApp ?: return@launch
                val settings = app.container.settings
                if (settings.startOnBoot.first() && settings.engineEnabled.first()) {
                    GamersHubCoreService.start(context)
                }
            } catch (t: Throwable) {
                // boot-time start must never crash the receiver
                runCatching { android.util.Log.w("GamersHub", "boot start failed", t) }
            } finally {
                pending.finish()
            }
        }
    }
}
