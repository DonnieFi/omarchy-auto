package com.omarchy.auto.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process snapshot shared by the car surface and the phone launcher.
 * Android Auto and the launcher are separate APKs/processes, so there is no
 * cross-process magic here: each app owns its own data sources.
 */
object LiveDataStore {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(DashboardState) -> Unit>()

    @Volatile
    var state: DashboardState = DashboardState()
        private set

    fun addListener(listener: (DashboardState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun removeListener(listener: (DashboardState) -> Unit) {
        listeners.remove(listener)
    }

    fun update(transform: (DashboardState) -> DashboardState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply(transform)
        } else {
            main.post { apply(transform) }
        }
    }

    private fun apply(transform: (DashboardState) -> DashboardState) {
        state = transform(state)
        listeners.forEach { it(state) }
    }

    fun setMedia(media: MediaSnapshot?, access: Boolean = true) = update {
        it.copy(media = media, mediaAccess = access)
    }

    fun setMediaAccess(access: Boolean) = update { it.copy(mediaAccess = access) }

    fun setNotificationAccess(access: Boolean) = update { it.copy(notificationAccess = access) }

    fun setLocation(access: Boolean) = update { it.copy(locationAccess = access) }

    fun setSpeed(speedKph: Float?, gpsActive: Boolean) = update {
        it.copy(speedKph = speedKph, gpsActive = gpsActive)
    }

    fun setLocationFix(
        speedKph: Float?,
        gpsActive: Boolean,
        latitude: Double?,
        longitude: Double?,
        bearing: Float,
        accuracyM: Float?,
    ) = update {
        it.copy(
            speedKph = speedKph,
            gpsActive = gpsActive,
            latitude = latitude,
            longitude = longitude,
            bearing = bearing,
            accuracyM = accuracyM,
        )
    }

    fun notifyMapUpdated() = update { it.copy(mapRevision = it.mapRevision + 1) }

    fun replaceNotifications(items: List<NotificationItem>) = update {
        it.copy(notifications = items.take(8))
    }

    fun addNotification(item: NotificationItem) = update { current ->
        val without = current.notifications.filterNot { it.key == item.key }
        current.copy(notifications = (listOf(item) + without).take(8))
    }

    fun removeNotification(key: String) = update { current ->
        current.copy(notifications = current.notifications.filterNot { it.key == key })
    }
}
