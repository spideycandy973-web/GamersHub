package com.gamershub

import android.app.Application
import android.util.Log
import com.gamershub.di.AppContainer

class GamersHubApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashNet()
        container = AppContainer(this)
        container.init()
    }

    /**
     * Last-resort safety net. Everything in this app is already exception-safe
     * (engine scopes carry CoroutineExceptionHandler, every loop body is
     * guarded), but if ANYTHING still throws on ANY thread we log it and keep
     * the process alive instead of dying — a booster that kills the phone's
     * foreground app is worse than useless. A storm guard (more than 5 crashes
     * inside a minute) defers to the system handler so a pathological loop
     * cannot turn into a zombie.
     */
    private fun installCrashNet() {
        val system = Thread.getDefaultUncaughtExceptionHandler()
        val stamps = java.util.ArrayDeque<Long>()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { Log.e("GamersHubCrash", "uncaught on ${t.name} (kept alive)", e) }
            val now = System.currentTimeMillis()
            val storm = synchronized(stamps) {
                stamps.addLast(now)
                while (stamps.isNotEmpty() && now - stamps.first() > 60_000) stamps.removeFirst()
                stamps.size > 5
            }
            if (storm) system?.uncaughtException(t, e)
            // else: swallow — the thread dies, the app and its engines keep running
        }
    }

    companion object {
        fun container(app: Application): AppContainer = (app as GamersHubApp).container
    }
}
