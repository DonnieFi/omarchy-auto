package com.omarchy.auto.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationAccess {
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val expected = ComponentName(context, OmarchyNotificationListener::class.java)
        return enabled.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    fun settingsIntent() = android.content.Intent(
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
    )
}
