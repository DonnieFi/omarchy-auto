package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences

/** Small, per-app display preferences. */
object DisplaySettings {
    private const val PREFS = "omarchy_display"
    private const val KEY_NOTIFICATION_SCALE = "notification_text_scale"

    private var prefs: SharedPreferences? = null

    @Volatile
    var notificationTextScale: Float = 1f
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        notificationTextScale = normalize(
            prefs?.getFloat(KEY_NOTIFICATION_SCALE, 1f) ?: 1f,
        )
    }

    fun cycle() {
        notificationTextScale = when {
            notificationTextScale < 0.95f -> 1f
            notificationTextScale < 1.15f -> 1.25f
            else -> 0.85f
        }
        prefs?.edit()?.putFloat(KEY_NOTIFICATION_SCALE, notificationTextScale)?.apply()
        LiveDataStore.notifyDisplayChanged()
    }

    fun label(): String = when {
        notificationTextScale < 0.95f -> "SMALL"
        notificationTextScale < 1.15f -> "NORMAL"
        else -> "LARGE"
    }

    private fun normalize(value: Float): Float = when {
        value < 0.95f -> 0.85f
        value < 1.15f -> 1f
        else -> 1.25f
    }
}
