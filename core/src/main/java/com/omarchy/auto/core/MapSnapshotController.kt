package com.omarchy.auto.core

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.omarchy.auto.core.diag.OaLog
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.math.abs

/**
 * Renders a real OpenFreeMap/MapLibre snapshot around the current fix and
 * publishes it to the shared renderer. This is deliberately a snapshot map,
 * not a routing engine: it follows location and heading, and does not claim
 * turn-by-turn guidance.
 */
class MapSnapshotController(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var snapshotter: MapSnapshotter? = null
    private var snapshotWidth = 0
    private var snapshotHeight = 0
    private var inFlight = false
    private var started = false
    private var lastRequestAt = 0L
    private var retryAt = 0L
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastBearing = 0f

    private val listener: (DashboardState) -> Unit = { state ->
        if (started) onState(state)
    }

    fun start() {
        if (started) return
        started = true
        try {
            MapLibre.getInstance(app)
        } catch (error: Exception) {
            MapSnapshotStore.setError("MapLibre init failed: ${error.message}")
            OaLog.w(TAG, "MapLibre init failed", error)
            return
        }
        LiveDataStore.addListener(listener)
    }

    fun stop() {
        if (!started) return
        started = false
        LiveDataStore.removeListener(listener)
        main.post {
            inFlight = false
            try {
                snapshotter?.cancel()
            } catch (_: Exception) {
                // Snapshot teardown races are harmless.
            }
            snapshotter = null
            snapshotWidth = 0
            snapshotHeight = 0
        }
    }

    private fun onState(state: DashboardState) {
        if (!MapViewport.active) return
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        if (!state.gpsActive) return
        val now = SystemClock.elapsedRealtime()
        if (inFlight) return
        if (now < retryAt) return
        if (now - lastRequestAt < MIN_INTERVAL_MS) return
        if (!movedEnough(lat, lon, state.bearing)) return
        lastRequestAt = now
        lastLat = lat
        lastLon = lon
        lastBearing = state.bearing
        request(lat, lon, state.bearing)
    }

    private fun movedEnough(lat: Double, lon: Double, bearing: Float): Boolean {
        val oldLat = lastLat ?: return true
        val oldLon = lastLon ?: return true
        val distance = FloatArray(1)
        Location.distanceBetween(oldLat, oldLon, lat, lon, distance)
        val turn = abs(((bearing - lastBearing + 540f) % 360f) - 180f)
        return distance[0] >= 20f || turn >= 20f
    }

    private fun request(lat: Double, lon: Double, bearing: Float) {
        inFlight = true
        val camera = CameraPosition.Builder()
            .target(LatLng(lat, lon))
            .zoom(ZOOM)
            .bearing(bearing.toDouble())
            .build()
        try {
            val width = MapViewport.width.coerceAtLeast(320)
            val height = MapViewport.height.coerceAtLeast(240)
            if (snapshotter == null || width != snapshotWidth || height != snapshotHeight) {
                snapshotter?.cancel()
                val options = MapSnapshotter.Options(width, height)
                    .withStyleBuilder(Style.Builder().fromUri(STYLE_URL))
                    .withCameraPosition(camera)
                    .withPixelRatio(1f)
                    .withLogo(false)
                    .withAttribution(false)
                snapshotter = MapSnapshotter(app, options)
                snapshotWidth = width
                snapshotHeight = height
            } else {
                snapshotter?.setCameraPosition(camera)
            }
            snapshotter?.start(
                { snapshot: MapSnapshot ->
                    MapSnapshotStore.set(snapshot.bitmap, lat, lon)
                    retryAt = 0L
                    inFlight = false
                    LiveDataStore.notifyMapUpdated()
                },
                { error: String ->
                    fail(error)
                },
            )
        } catch (error: Exception) {
            OaLog.w(TAG, "Map snapshot failed", error)
            fail(error.message)
        }
    }

    private fun fail(message: String?) {
        inFlight = false
        lastLat = null
        lastLon = null
        retryAt = SystemClock.elapsedRealtime() + RETRY_MS
        MapSnapshotStore.setError(message)
        LiveDataStore.notifyMapUpdated()
    }

    companion object {
        private const val TAG = "MapSnapshot"
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
        private const val ZOOM = 15.5
        private const val MIN_INTERVAL_MS = 2_500L
        private const val RETRY_MS = 10_000L
    }
}
