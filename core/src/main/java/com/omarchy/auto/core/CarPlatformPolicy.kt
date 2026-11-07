package com.omarchy.auto.core

/**
 * What the Android Auto navigation surface is allowed to draw.
 *
 * Decision for omarchy-8wl.12, checked against the car app quality guidelines
 * (PC-1, NF-2, IU-1, SA-1, MF-5). The `auto` module declares only the
 * NAVIGATION category. A parked lock does not add video, browser, or messaging
 * categories, and it does not allow non-map text on the navigation surface.
 *
 * The phone launcher may keep the custom shell. While a car session is active,
 * [DistractionLock] still hides that shell's distracting paths until the host
 * reports parked.
 */
object CarPlatformPolicy {
    enum class Surface { ANDROID_AUTO, PHONE }

    fun mapOnly(surface: Surface): Boolean = surface == Surface.ANDROID_AUTO

    /** Turn instructions and ETA belong on NavigationTemplate, not the canvas. */
    fun allowsCanvasManeuverText(surface: Surface): Boolean = surface == Surface.PHONE

    fun allowsCustomRail(surface: Surface): Boolean = surface == Surface.PHONE

    fun allowsDecorativeWallpaper(surface: Surface): Boolean = surface == Surface.PHONE

    /** Speed and the vehicle marker are drive-relevant map-safe-area content. */
    fun allowsCompactSpeed(surface: Surface): Boolean = true

    fun allowsWebStage(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> false
        Surface.PHONE ->
            FeatureFlags.ALLOW_CAR_WEB_STAGE && !DistractionLock.suppressesSharedDistractions()
    }

    fun allowsCustomKeyboard(surface: Surface): Boolean =
        surface == Surface.PHONE && !DistractionLock.suppressesSharedDistractions()

    fun allowsNotificationList(surface: Surface): Boolean =
        surface == Surface.PHONE && !DistractionLock.suppressesSharedDistractions()

    fun allowsCommsBrowsing(surface: Surface): Boolean = allowsNotificationList(surface)
}
