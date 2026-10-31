package com.gamershub.ui

import android.content.Context
import android.widget.Toast
import com.gamershub.di.AppContainer

/**
 * Central gate for every action that modifies the device.
 * When the master engine switch is OFF the app is read-only: tapping an
 * engine button tells the user why nothing happened instead of silently
 * pretending to work.
 */
fun requireEngine(container: AppContainer, context: Context): Boolean {
    if (container.engine.enabled.value) return true
    Toast.makeText(
        context,
        "Engine is OFF — turn the master switch ON in Settings (or Dashboard)",
        Toast.LENGTH_SHORT
    ).show()
    return false
}
