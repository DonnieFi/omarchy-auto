package com.omarchy.auto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

class LiveDataController(context: Context) {
    private val app = context.applicationContext
    private var media: MediaWatcher? = null
    private var location: LocationWatcher? = null
    private var started = false

    fun start() {
        if (started) return
        started = true

        LiveDataStore.setNotificationAccess(NotificationAccess.isEnabled(app))
        LiveDataStore.setLocation(
            app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )

        media = MediaWatcher(
            app,
            onUpdate = { snapshot ->
                LiveDataStore.setMedia(snapshot, access = true)
            },
            onPermissionDenied = {
                LiveDataStore.setMedia(null, access = false)
            },
        ).also { it.start() }

        location = LocationWatcher(app) { speed, active ->
            LiveDataStore.setSpeed(speed, active)
            LiveDataStore.setLocation(
                app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }.also { it.start() }
    }

    fun stop() {
        if (!started) return
        started = false
        media?.stop()
        location?.stop()
        media = null
        location = null
    }
}
