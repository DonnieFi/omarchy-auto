package com.omarchy.auto.core

/**
 * Notification rows keep a readable height and page instead of shrinking as
 * the list grows. The Android Auto navigation surface does not draw this list.
 */
object NotificationCardLayout {
    const val MAX_VISIBLE = 4

    fun visibleCount(listHeight: Float, minRow: Float, total: Int): Int {
        if (total <= 0 || minRow <= 0f || listHeight <= 0f) return 0
        val fit = (listHeight / minRow).toInt().coerceAtLeast(1)
        return minOf(total, fit, MAX_VISIBLE)
    }

    fun rowHeight(minRow: Float): Float = minRow
}
