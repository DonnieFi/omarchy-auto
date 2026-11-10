package com.omarchy.auto.core

/**
 * What each surface may draw.
 *
 * The phone launcher and the Android Auto map surface share the Omarchy
 * shell (rail, stage, theme, wallpaper). The `auto` module still declares
 * only the NAVIGATION category, so the in-stage browser and the custom
 * keyboard stay off the car canvas. Search on that canvas opens the host
 * search screen instead. Notification bodies stay
 * behind [DistractionLock] until the host reports parked; while driving, the
 * shell opens the host notification list (titles only).
 *
 * Car-app quality guidance for a navigation-only app prefers a map-only
 * canvas. Drawing the shell here is a product choice, not an SDK hard block.
 */
object CarPlatformPolicy {
    enum class Surface { ANDROID_AUTO, PHONE }

    fun mapOnly(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> false
    }

    /** The drive stage draws the same guidance card the phone shell draws. */
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
    fun allowsCompactSpeed(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    fun allowsWebStage(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> false
        Surface.PHONE ->
            FeatureFlags.ALLOW_CAR_WEB_STAGE && !DistractionLock.suppressesSharedDistractions()
    }

    fun allowsCustomKeyboard(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> false
        Surface.PHONE -> !DistractionLock.suppressesSharedDistractions()
    }

    fun allowsNotificationList(surface: Surface): Boolean =
        !DistractionLock.suppressesSharedDistractions()

    fun allowsCommsBrowsing(surface: Surface): Boolean = allowsNotificationList(surface)
}
