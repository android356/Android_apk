package com.autodeploy.infinityfree

import android.app.Application
import com.autodeploy.infinityfree.di.AppContainer

class AutoDeployApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
