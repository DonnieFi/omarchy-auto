package com.omarchy.auto.core

/** Temporary product flags. Keep disabled code intact so it can return. */
object FeatureFlags {
    const val SHOW_VEHICLE_TILE = false

    /**
     * Enables interactive offscreen WebView stages (YouTube, ChatGPT, Grok) on
     * the car surface for sideloaded/parked use. Disable before submitting a
     * NAVIGATION-category build to Google Play.
     */
    @Volatile
    var ALLOW_CAR_WEB_STAGE = true
}
