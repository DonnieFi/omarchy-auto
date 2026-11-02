package com.omarchy.auto.core

/**
 * The current Drive-stage pixel size, published by the renderer so the
 * snapshotter can render the map at the size it will actually be shown.
 */
object MapViewport {
    @Volatile
    var width: Int = 1920

    @Volatile
    var height: Int = 720

    @Volatile
    var active: Boolean = false
}
