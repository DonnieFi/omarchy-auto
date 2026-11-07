package com.omarchy.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import com.omarchy.auto.core.diag.CrashReporter

class OmarchySession : Session() {
    private var screen: OmarchyScreen? = null

    override fun onCreateScreen(intent: Intent): Screen {
        CrashReporter.hostPackage = carContext.hostInfo?.packageName
        return OmarchyScreen(carContext).also {
            screen = it
            it.offerNavigateIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        screen?.offerNavigateIntent(intent)
    }
}
