package com.omarchy.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import com.omarchy.auto.core.diag.OaLog

/**
 * Turn-by-turn notification required by car quality NF-3. The host suppresses
 * the heads-up while RoutingInfo is already on screen. Importance stays low
 * so distance ticks do not flash a banner.
 */
internal object NavigationAlerts {
    private const val TAG = "NavigationAlerts"
    private const val CHANNEL_ID = "omarchy-auto-navigation"
    private const val NOTIFICATION_ID = 42

    fun publish(context: Context, title: String, text: String) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Turn-by-turn",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.omarchy.auto.core.R.drawable.ic_stat_omarchy)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentTitle(title)
                .setContentText(text)
                .extend(
                    CarAppExtender.Builder()
                        .setSmallIcon(com.omarchy.auto.core.R.drawable.ic_stat_omarchy)
                        .setImportance(NotificationManager.IMPORTANCE_LOW)
                        .setContentTitle(title)
                        .setContentText(text)
                        .build(),
                )
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (error: Exception) {
            OaLog.w(TAG, "Navigation notification was not posted", error)
        }
    }

    fun clear(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (error: Exception) {
            OaLog.w(TAG, "Navigation notification was not cleared", error)
        }
    }
}
