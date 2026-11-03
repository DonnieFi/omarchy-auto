package com.omarchy.auto

import android.app.Application
import com.omarchy.auto.core.diag.CrashReporter
import com.omarchy.auto.core.diag.OaLog

class OmarchyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OaLog.init(this)
        CrashReporter.install(this, BuildConfig.CRASH_TOKEN)
    }
}
