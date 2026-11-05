package com.omarchy.auto.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Interactive camera state for the Drive stage map (zoom, pan, follow mode)
 * and Web Mercator coordinate projection for route polylines and markers.
 */
object MapCameraStore {
    const val DEFAULT_ZOOM = 15.5
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 19.0
    private const val TILE_SIZE = 512.0

    @Volatile
    var zoom: Double = DEFAULT_ZOOM
        private set

    @Volatile
    var followGps: Boolean = true
        private set

    @Volatile
    var panLat: Double? = null
        private set

    @Volatile
    var panLon: Double? = null
        private set

    @Volatile
    var revision: Int = 0
        private set

    private var accumulatedZoomDelta: Double = 0.0

    fun targetLat(stateLat: Double?, fallbackLat: Double): Double =
        if (!followGps && panLat != null) panLat!! else (stateLat ?: panLat ?: fallbackLat)

    fun targetLon(stateLon: Double?, fallbackLon: Double): Double =
        if (!followGps && panLon != null) panLon!! else (stateLon ?: panLon ?: fallbackLon)

    fun effectiveCenter(stateLat: Double?, stateLon: Double?): Pair<Double, Double> {
        val defLat = MapSnapshotStore.latitude ?: 43.6532
        val defLon = MapSnapshotStore.longitude ?: -79.3832
        return targetLat(stateLat, defLat) to targetLon(stateLon, defLon)
    }

    fun zoomIn(step: Double = 1.0) {
        accumulatedZoomDelta = 0.0
        val next = (zoom + step).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next != zoom) {
            zoom = next
            revision++
            LiveDataStore.notifyDisplayChanged()
        }
    }

    fun zoomOut(step: Double = 1.0) {
        accumulatedZoomDelta = 0.0
        val next = (zoom - step).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next != zoom) {
            zoom = next
            revision++
            LiveDataStore.notifyDisplayChanged()
        }
    }

    fun scaleBy(scaleFactor: Float) {
        if (scaleFactor <= 0f || scaleFactor == 1f) return
        val delta = ln(scaleFactor.toDouble()) / ln(2.0)
        accumulatedZoomDelta += delta
        if (kotlin.math.abs(accumulatedZoomDelta) >= 0.02) {
            val next = (zoom + accumulatedZoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
            accumulatedZoomDelta = 0.0
            if (next != zoom) {
                zoom = next
                revision++
                LiveDataStore.notifyDisplayChanged()
            }
        }
    }

    /**
     * Pans the map center by screen pixel delta (dx > 0 moves camera right / east,
     * dy > 0 moves camera down / south).
     */
    fun panByPixels(
        dxPixels: Float,
        dyPixels: Float,
        baseLat: Double? = null,
        baseLon: Double? = null,
        bearingDeg: Float = 0f,
    ) {
        val startLat = panLat ?: baseLat ?: MapSnapshotStore.latitude ?: 43.6532
        val startLon = panLon ?: baseLon ?: MapSnapshotStore.longitude ?: -79.3832
        val worldSize = TILE_SIZE * 2.0.pow(zoom)
        val rad = Math.toRadians(bearingDeg.toDouble())
        val cosB = cos(rad)
        val sinB = sin(rad)
        val rotatedDx = dxPixels * cosB - dyPixels * sinB
        val rotatedDy = dxPixels * sinB + dyPixels * cosB

        val centerX = lonToWorldX(startLon, worldSize)
        val centerY = latToWorldY(startLat, worldSize)
        val newLon = worldXToLon(centerX + rotatedDx, worldSize).coerceIn(-180.0, 180.0)
        val newLat = worldYToLat(centerY + rotatedDy, worldSize).coerceIn(-85.0, 85.0)

        panLat = newLat
        panLon = newLon
        followGps = false
        revision++
        LiveDataStore.notifyDisplayChanged()
    }

    fun focusOn(lat: Double, lon: Double, targetZoom: Double? = null) {
        panLat = lat.coerceIn(-85.0, 85.0)
        panLon = lon.coerceIn(-180.0, 180.0)
        if (targetZoom != null) {
            zoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        }
        followGps = false
        revision++
        LiveDataStore.notifyDisplayChanged()
    }

    fun recenter() {
        followGps = true
        panLat = null
        panLon = null
        revision++
        LiveDataStore.notifyDisplayChanged()
    }

    fun resetForTest() {
        zoom = DEFAULT_ZOOM
        followGps = true
        panLat = null
        panLon = null
        accumulatedZoomDelta = 0.0
        revision = 0
    }

    /**
     * Projects a geographic (lat, lon) point onto the stage canvas given the
     * snapshot's center (centerLat, centerLon), zoom, bearing, and stage rect.
     */
    fun projectToScreen(
        lat: Double,
        lon: Double,
        centerLat: Double,
        centerLon: Double,
        zoomLevel: Double,
        centerXScreen: Float,
        centerYScreen: Float,
    ): Pair<Float, Float> = projectToScreen(
        lat = lat,
        lon = lon,
        centerLat = centerLat,
        centerLon = centerLon,
        zoomLevel = zoomLevel,
        bearingDeg = if (followGps) MapSnapshotStore.bearing else 0f,
        centerXScreen = centerXScreen,
        centerYScreen = centerYScreen,
        scaleFactor = 1f,
    )

    fun projectToScreen(
        lat: Double,
        lon: Double,
        centerLat: Double,
        centerLon: Double,
        zoomLevel: Double,
        bearingDeg: Float,
        centerXScreen: Float,
        centerYScreen: Float,
        scaleFactor: Float = 1f,
    ): Pair<Float, Float> {
        val worldSize = TILE_SIZE * 2.0.pow(zoomLevel)
        val cx = lonToWorldX(centerLon, worldSize)
        val cy = latToWorldY(centerLat, worldSize)
        val px = lonToWorldX(lon, worldSize)
        val py = latToWorldY(lat, worldSize)
        val dx = (px - cx) * scaleFactor
        val dy = (py - cy) * scaleFactor
        val rad = Math.toRadians(-bearingDeg.toDouble())
        val rx = dx * cos(rad) - dy * sin(rad)
        val ry = dx * sin(rad) + dy * cos(rad)
        return (centerXScreen + rx.toFloat()) to (centerYScreen + ry.toFloat())
    }

    private fun lonToWorldX(lon: Double, worldSize: Double): Double =
        ((lon + 180.0) / 360.0) * worldSize

    private fun latToWorldY(lat: Double, worldSize: Double): Double {
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clamped)
        val mercN = ln(tan(PI / 4.0 + latRad / 2.0))
        return (0.5 - mercN / (2.0 * PI)) * worldSize
    }

    private fun worldXToLon(x: Double, worldSize: Double): Double =
        ((x / worldSize) * 360.0 - 180.0)

    private fun worldYToLat(y: Double, worldSize: Double): Double {
        val n = PI - 2.0 * PI * (y / worldSize)
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }
}
