package com.omarchy.auto.navspike

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.*
import androidx.car.app.validation.HostValidator

class SpikeCarService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = SpikeCarScreen(carContext)
    }
}
