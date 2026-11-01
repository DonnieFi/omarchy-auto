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

    fun set(bitmap: Bitmap) {
        this.bitmap = bitmap
        error = null
        updatedAt = System.currentTimeMillis()
    }

    fun setError(message: String?) {
        error = message ?: "Map unavailable"
    }
}
