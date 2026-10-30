package com.gamershub

import android.app.Application
import com.gamershub.di.AppContainer

class GamersHubApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.init()
    }

    companion object {
        fun container(app: Application): AppContainer = (app as GamersHubApp).container
    }
}
