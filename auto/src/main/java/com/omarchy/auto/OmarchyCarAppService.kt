package com.omarchy.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.omarchy.auto.core.LiveDataController
import com.omarchy.auto.core.SetupActivity
import com.omarchy.auto.core.ThemeStore

class OmarchyCarAppService : CarAppService() {

    private lateinit var controller: LiveDataController
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        ThemeStore.init(this)
        controller = LiveDataController(this).also { it.start() }
        startLiveNotification()
    }

    override fun createHostValidator(): HostValidator {
        // Sideloaded debug builds need to accept the AA host. A release build
        // deliberately has no allow-all validator; configure the real host
        // allow-list before shipping one.
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this).build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = OmarchySession()

    override fun onDestroy() {
        controller.stop()
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        super.onDestroy()
    }

    private fun startLiveNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Omarchy Auto live data",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                },
            )
        }

        val intent = Intent(this, SetupActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
            .setSmallIcon(com.omarchy.auto.core.R.drawable.ic_stat_omarchy)
            .setContentTitle("Omarchy Auto")
            .setContentText("Car display live data")
            .setContentIntent(pending)
            .setOngoing(true)
            .build()

        try {
            val type = if (Build.VERSION.SDK_INT >= 29) {
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (error: Exception) {
            // A blocked foreground start must not stop the car app; it only
            // makes background location less reliable.
            Log.w(TAG, "Could not enter foreground mode", error)
        }
    }

    companion object {
        private const val TAG = "OmarchyCarAppService"
        private const val CHANNEL_ID = "omarchy-auto-live"
        private const val NOTIFICATION_ID = 41
    }
}
