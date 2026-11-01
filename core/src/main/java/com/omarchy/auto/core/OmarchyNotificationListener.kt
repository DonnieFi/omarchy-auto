package com.omarchy.auto.core

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads notifications for the Comms tile. This is the same component in both
 * APKs; Android grants notification access per app.
 */
class OmarchyNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        LiveDataStore.setNotificationAccess(true)
        val existing = try {
            activeNotifications.orEmpty()
        } catch (_: Exception) {
            emptyArray()
        }
        LiveDataStore.replaceNotifications(
            existing
                .filter(::shouldShow)
                .mapNotNull(::toItem)
                .sortedByDescending { it.postedAt }
                .take(8),
        )
    }

    override fun onListenerDisconnected() {
        LiveDataStore.setNotificationAccess(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        LiveDataStore.setNotificationAccess(true)
        if (!shouldShow(sbn)) return
        toItem(sbn)?.let(LiveDataStore::addNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        LiveDataStore.removeNotification(sbn.key)
    }

    private fun toItem(sbn: StatusBarNotification): NotificationItem? {
        val extras = sbn.notification.extras
        val title = textOf(extras.getCharSequence(Notification.EXTRA_TITLE))
        val body = textOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
        )
        if (title.isBlank() && body.isBlank()) return null
        return NotificationItem(
            key = sbn.key,
            app = appLabel(sbn.packageName),
            title = title,
            text = body,
            postedAt = sbn.postTime,
        )
    }

    private fun shouldShow(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName) return false
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category == Notification.CATEGORY_TRANSPORT) return false
        if (n.category == Notification.CATEGORY_PROGRESS) return false
        if (n.category == Notification.CATEGORY_SERVICE) return false
        return true
    }

    private fun textOf(value: CharSequence?): String =
        value?.toString()?.trim()?.replace(Regex("\\s+"), " ") ?: ""

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(
                pkg,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, OmarchyNotificationListener::class.java)
    }
}
