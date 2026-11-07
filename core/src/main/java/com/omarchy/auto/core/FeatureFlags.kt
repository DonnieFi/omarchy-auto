package com.omarchy.auto.core

/** Temporary product flags. Keep disabled code intact so it can return. */
object FeatureFlags {
    const val SHOW_VEHICLE_TILE = false

    /**
     * Phone-launcher WebView stages (YouTube, ChatGPT, Grok). The Android Auto
     * navigation surface never embeds these, parked or driving. A car session
     * also suppresses them until the host reports parked. See CarPlatformPolicy.
     */
    @Volatile
    var ALLOW_CAR_WEB_STAGE = true
}
