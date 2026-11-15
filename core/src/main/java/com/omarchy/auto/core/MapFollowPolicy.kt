package com.omarchy.auto.core

/**
 * When a followed map snapshot is stale enough to redraw.
 *
 * The previous gate waited for 20 m or 20 degrees and at least 2.5 s. Follow
 * mode now refreshes on a 1.5 s cadence after 10 m or 12 degrees. Snapshotting
 * runs MapLibre on the head unit, so this is the main CPU throttle while
 * driving. Panning still uses the coarser idle interval.
 */
object MapFollowPolicy {
    const val FOLLOW_MIN_INTERVAL_MS = 1_500L
    const val IDLE_MIN_INTERVAL_MS = 2_500L
    const val MOVE_METERS = 10f
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
