package com.omarchy.auto.core

/**
 * What each surface may draw.
 *
 * The phone launcher and the Android Auto surface share the Omarchy canvas
 * shell (top bar, central stage, 4-tile right rail, footer, theme, wallpaper).
 * The `auto` module declares only the NAVIGATION category, so the in-stage
 * web browser, web rail items, and custom keyboard stay off the car canvas.
 * Address search on the car canvas opens the host SearchTemplate screen.
 * Turn-by-turn guidance, media transport, notifications, and car settings all
 * render inside the Omarchy canvas stage rather than pushing stock host lists
 * or duplicate host routing cards.
 */
object CarPlatformPolicy {
    enum class Surface { ANDROID_AUTO, PHONE }

    private const val MOVING_SPEED_KPH = 2f

    fun mapOnly(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> false
    }

    /** The drive stage draws the Omarchy guidance card on both surfaces. */
    fun allowsCanvasManeuverText(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    /**
     * Host NavigationTemplate RoutingInfo and TravelEstimate overlay cards are
     * omitted so the Omarchy Drive stage is the single turn-by-turn UI.
     */
    fun allowsHostRoutingCards(surface: Surface): Boolean = false

    fun allowsCustomRail(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    /**
     * Only the 4 core car tiles (DRIVE, MEDIA, COMMS, HOME) belong in the
     * Android Auto rail; web stage apps stay phone-only.
     */
    fun allowsRailItem(surface: Surface, item: RailItemId): Boolean {
        if (item == RailItemId.VEHICLE && !FeatureFlags.SHOW_VEHICLE_TILE) return false
        return when (surface) {
            Surface.ANDROID_AUTO -> item.webApp == null
            Surface.PHONE -> item.webApp == null || FeatureFlags.ALLOW_CAR_WEB_STAGE
        }
    }

    fun allowsDecorativeWallpaper(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO, Surface.PHONE -> true
    }

    /**
     * On the car surface, the bottom footer already displays live speed, so
     * the large stage speed box is omitted to keep the Drive map unobstructed.
     */
    fun allowsCompactSpeed(surface: Surface): Boolean = when (surface) {
        Surface.ANDROID_AUTO -> false
        Surface.PHONE -> true
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

    fun allowsNotificationList(surface: Surface): Boolean {
        if (!DistractionLock.carSessionActive) return true
        return when (DistractionLock.hostUx) {
            DistractionLock.HostUx.PARKED -> true
            DistractionLock.HostUx.DRIVING -> false
            DistractionLock.HostUx.UNKNOWN -> when (surface) {
                Surface.ANDROID_AUTO -> {
                    val speed = LiveDataStore.state.speedKph ?: 0f
                    speed <= MOVING_SPEED_KPH && !NavigationController.autoDriveEnabled
                }
                Surface.PHONE -> false
            }
        }
    }

    fun allowsCommsBrowsing(surface: Surface): Boolean = allowsNotificationList(surface)
}
