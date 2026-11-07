package com.voidlauncher.app

import android.app.Application
import com.voidlauncher.app.data.log.AppLog

class VoidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.write("App started")
    }
}
