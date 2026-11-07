package com.omarchy.auto.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Keeps the last trustworthy course when GPS bearing is noisy at walking or
 * stopped speeds. A reported speed of 0 holds the previous course. A bearing
 * with no speed at all is still accepted: some providers omit speed. Speed is
 * never a parked signal.
 */
object HeadingHold {
    const val HOLD_BELOW_KPH = 5f

    fun nextBearing(
        previous: Float,
        hasBearing: Boolean,
        bearing: Float,
        speedKph: Float?,
    ): Float {
        if (!hasBearing) return previous
        if (speedKph != null && speedKph < HOLD_BELOW_KPH) return previous
        return bearing
    }

    fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }
}
