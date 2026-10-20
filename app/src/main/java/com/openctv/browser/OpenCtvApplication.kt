package com.openctv.browser

import android.app.Application
import com.openctv.browser.core.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OpenCtvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenCtvApplication)
            modules(appModules)
        }
    }
}
