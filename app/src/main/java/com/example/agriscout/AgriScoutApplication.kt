package com.example.agriscout

import android.app.Application
import androidx.work.Configuration
import com.example.agriscout.di.AppContainer
import com.example.agriscout.di.DefaultAppContainer

class AgriScoutApplication : Application(), Configuration.Provider {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
