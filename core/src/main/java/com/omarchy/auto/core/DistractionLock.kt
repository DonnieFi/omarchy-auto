package com.omarchy.auto.core

/**
 * Host-driven distraction gate. GPS speed is intentionally not an input:
 * a stop at a light must not unlock text, video, or message browsing.
 *
 * The car session starts locked. It stays locked until a host UX callback
 * reports parked. If that callback never arrives, distracting features stay
 * locked for the whole session. Ending the session clears the gate so the
 * phone launcher is usable again at the desk.
 *
 * Android Auto's navigation surface additionally refuses video, browsers,
 * and custom text chrome even when parked. See [CarPlatformPolicy].
 */
object DistractionLock {
    enum class HostUx {
        /** No host report yet. Car-session distractions stay locked. */
        UNKNOWN,

        /** Host requires distraction optimization (driving or restricted). */
        DRIVING,

        /** Host explicitly permits parked interaction. */
        PARKED,
    }

    @Volatile
    var carSessionActive: Boolean = false
        private set

    @Volatile
    var hostUx: HostUx = HostUx.UNKNOWN
        private set

    fun onCarSessionStarted() {
        carSessionActive = true
        hostUx = HostUx.UNKNOWN
        LiveDataStore.notifyDisplayChanged()
    }

    fun onCarSessionEnded() {
        carSessionActive = false
        hostUx = HostUx.UNKNOWN
        LiveDataStore.notifyDisplayChanged()
    }

    /**
     * Authoritative host transition. Callers must not derive this from speed.
     * A callback that arrives after the session has ended does not start a new
     * session or lock the phone launcher.
     */
    fun onHostUx(ux: HostUx) {
        if (!carSessionActive) return
        hostUx = ux
        LiveDataStore.notifyDisplayChanged()
    }

    /**
     * True while a car session is connected and the host has not permitted
     * parked interaction. Covers unknown, stale, and driving states.
     */
    fun suppressesSharedDistractions(): Boolean =
        carSessionActive && hostUx != HostUx.PARKED

    fun resetForTest() {
        carSessionActive = false
        hostUx = HostUx.UNKNOWN
    }
}
