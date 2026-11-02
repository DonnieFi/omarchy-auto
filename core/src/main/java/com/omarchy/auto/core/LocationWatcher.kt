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
    private val onUpdate: (
        speedKph: Float?,
        active: Boolean,
        latitude: Double?,
        longitude: Double?,
        bearing: Float,
        accuracyM: Float?,
    ) -> Unit,
) : LocationListener {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    private var started = false
    private var wanted = false
    private var lastBearing = 0f

    fun start() {
        if (started) return
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onUpdate(null, false, null, null, 0f, null)
            return
        }
        val lm = manager ?: run {
            onUpdate(null, false, null, null, 0f, null)
            return
        }
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onUpdate(null, false, null, null, 0f, null)
            return
        }
        wanted = true
        started = true
        try {
            // Last-known is useful only while fresh; stale fixes must not be
            // labelled GPS LIVE. A real callback follows in a second or two.
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeIf { System.currentTimeMillis() - it.time in 0..30_000L }
                ?.let(::publish)
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            started = false
            onUpdate(null, false, null, null, 0f, null)
        } catch (_: Exception) {
            started = false
            onUpdate(null, false, null, null, 0f, null)
        }
    }

    fun stop() {
        wanted = false
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
        onUpdate(null, false, null, null, 0f, null)
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && wanted) start()
    }

    @Deprecated("Deprecated in the platform")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun publish(location: Location) {
        val speed = if (location.hasSpeed()) location.speed * 3.6f else null
        val moving = speed == null || speed >= 5f
        if (location.hasBearing() && moving) {
            lastBearing = location.bearing
        }
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        onUpdate(speed, true, location.latitude, location.longitude, lastBearing, accuracy)
    }
}
