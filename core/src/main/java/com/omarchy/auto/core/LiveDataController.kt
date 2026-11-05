package com.omarchy.auto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

class LiveDataController(context: Context) {
    private val app = context.applicationContext
    private var media: MediaWatcher? = null
    private var location: LocationWatcher? = null
    private var battery: BatteryWatcher? = null
    private var map: MapSnapshotController? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        RailConfigStore.init(app)
        LaunchTargetStore.init(app)
        StageWebController.init(app)

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

        location = LocationWatcher(app) { speed, active, latitude, longitude, bearing, accuracy ->
            val now = if (active) System.currentTimeMillis() else 0L
            LiveDataStore.setLocationFix(speed, active, latitude, longitude, bearing, accuracy, now)
            LiveDataStore.setLocation(
                app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED,
            )
            if (active) {
                NavigationController.onLocationUpdate(latitude, longitude, accuracy, now)
            }
        }.also { it.start() }

        battery = BatteryWatcher(app) { percent, charging ->
            LiveDataStore.setBattery(percent, charging)
        }.also { it.start() }

        map = MapSnapshotController(app).also { it.start() }
    }

    fun stop() {
        if (!started) return
        started = false
        StageWebController.setActive(false)
        map?.stop()
        battery?.stop()
        media?.stop()
        location?.stop()
        map = null
        battery = null
        media = null
        location = null
    }
}
