package com.omarchy.auto.core

/**
 * When a followed map snapshot is stale enough to redraw.
 *
 * The previous gate waited for 20 m or 20 degrees and at least 2.5 s. At the
 * 6–8 km/h in the field photos that is about 9–12 s before the vehicle can
 * move on the bitmap. Follow mode refreshes on a shorter interval and a
 * smaller move or turn. Panning still uses the coarser idle interval.
 */
object MapFollowPolicy {
    const val FOLLOW_MIN_INTERVAL_MS = 1_000L
    const val IDLE_MIN_INTERVAL_MS = 2_500L
    const val MOVE_METERS = 8f
    const val TURN_DEGREES = 12f

    fun shouldRequest(
        nowMs: Long,
        lastRequestAtMs: Long,
        needsInitial: Boolean,
        movedMeters: Float,
        turnedDegrees: Float,
        following: Boolean,
    ): Boolean {
        if (needsInitial) return true
        val minInterval = if (following) FOLLOW_MIN_INTERVAL_MS else IDLE_MIN_INTERVAL_MS
        if (nowMs - lastRequestAtMs < minInterval) return false
        return movedMeters >= MOVE_METERS || turnedDegrees >= TURN_DEGREES
    }
}
