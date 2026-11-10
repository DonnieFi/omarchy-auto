package com.omarchy.auto.core

/**
 * What each surface may draw.
 *
 * The `auto` module declares only the NAVIGATION category. Car app quality
 * guidelines (PC-1, NF-2, IU-1, SA-1, MF-5) prefer a map-only navigation
 * surface, with search, media, and messages on host templates. That preference
 * is not an SDK block. The owner accepted the guideline risk of drawing the
 * Omarchy shell — rail, wallpaper, theme, media, notification list, and the
 * map-search keyboard — on the Android Auto canvas anyway.
 *
 * Video and browser stages stay off Android Auto. The phone launcher keeps
 * the same shell. While a car session is active, [DistractionLock] still hides
 * that phone shell's distracting paths until the host reports parked.
 *
 * [mapOnly] remains the switch for the full-bleed map canvas. It is off.
 */
object CarPlatformPolicy {
    enum class Surface { ANDROID_AUTO, PHONE }

    fun mapOnly(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> false
    }

    /** The drive stage draws the turn card. The host routing card still does too. */
    fun allowsCanvasManeuverText(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    fun allowsCustomRail(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    fun allowsDecorativeWallpaper(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    /** Speed and the vehicle marker are drive-relevant map content. */
    fun allowsCompactSpeed(surface: Surface): Boolean = true

    fun allowsWebStage(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> false
        Surface.PHONE ->
            FeatureFlags.ALLOW_CAR_WEB_STAGE && !DistractionLock.suppressesSharedDistractions()
    }

    fun allowsCustomKeyboard(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> true
        Surface.PHONE -> !DistractionLock.suppressesSharedDistractions()
    }

    fun allowsNotificationList(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> true
        Surface.PHONE -> !DistractionLock.suppressesSharedDistractions()
    }

    fun allowsCommsBrowsing(surface: Surface): Boolean = allowsNotificationList(surface)
}
