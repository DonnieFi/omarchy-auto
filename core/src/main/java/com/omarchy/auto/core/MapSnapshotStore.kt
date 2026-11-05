package com.omarchy.auto.core

import android.graphics.Bitmap

/**
 * The latest real map snapshot rendered by MapLibre/OpenFreeMap. The shared
 * canvas renderer only draws this when it exists; it never invents a map.
 */
object MapSnapshotStore {
    @Volatile
    var bitmap: Bitmap? = null
        private set

    @Volatile
    var error: String? = null
        private set

    @Volatile
    var updatedAt: Long = 0L
        private set

    @Volatile
    var latitude: Double? = null
        private set

    @Volatile
    var longitude: Double? = null
        private set

    @Volatile
    var zoom: Double = MapCameraStore.DEFAULT_ZOOM
        private set

    @Volatile
    var bearing: Float = 0f
        private set

    fun set(
        bitmap: Bitmap,
        lat: Double,
        lon: Double,
        zoom: Double = MapCameraStore.zoom,
        bearing: Float = 0f,
    ) {
        this.bitmap = bitmap
        latitude = lat
        longitude = lon
        this.zoom = zoom
        this.bearing = bearing
        error = null
        updatedAt = System.currentTimeMillis()
    }

    fun setError(message: String?) {
        error = message ?: "Map unavailable"
    }
}
