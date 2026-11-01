package com.omarchy.auto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

class LocationWatcher(
    context: Context,
    private val onUpdate: (speedKph: Float?, active: Boolean) -> Unit,
) : LocationListener {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    private var started = false

    fun start() {
        if (started) return
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onUpdate(null, false)
            return
        }
        val lm = manager ?: run {
            onUpdate(null, false)
            return
        }
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onUpdate(null, false)
            return
        }
        started = true
        try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::publish)
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            started = false
            onUpdate(null, false)
        } catch (_: Exception) {
            started = false
            onUpdate(null, false)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            manager?.removeUpdates(this)
        } catch (_: Exception) {
            // Ignore teardown races.
        }
    }

    override fun onLocationChanged(location: Location) = publish(location)

    override fun onProviderDisabled(provider: String) {
        if (provider != LocationManager.GPS_PROVIDER) return
        started = false
        try {
            manager?.removeUpdates(this)
        } catch (_: Exception) {
            // Provider teardown races are harmless.
        }
        onUpdate(null, false)
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) start()
    }

    @Deprecated("Deprecated in the platform")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun publish(location: Location) {
        val speed = if (location.hasSpeed()) location.speed * 3.6f else null
        onUpdate(speed, true)
    }
}
