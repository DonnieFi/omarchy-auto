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
    private val main: Handler? by lazy {
        try {
            Looper.getMainLooper()?.let { Handler(it) }
        } catch (_: Throwable) {
            null
        }
    }
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
        val mainLooper = try {
            Looper.getMainLooper()
        } catch (_: Throwable) {
            null
        }
        val myLooper = try {
            Looper.myLooper()
        } catch (_: Throwable) {
            null
        }
        if (mainLooper == null || myLooper == mainLooper) {
            apply(transform)
        } else {
            val h = main
            if (h != null) {
                h.post { apply(transform) }
            } else {
                apply(transform)
            }
        }
    }

    private fun apply(transform: (DashboardState) -> DashboardState) {
        val next = transform(state)
        if (next == state) return
        state = next
        listeners.forEach { it(state) }
    }

    fun setMedia(
        media: MediaSnapshot?,
        access: Boolean = true,
        sessions: List<MediaSessionItem> = if (access) state.mediaSessions else emptyList(),
    ) = update {
        it.copy(
            media = media,
            mediaAccess = access,
            mediaSessions = if (access) sessions else emptyList(),
        )
    }

    fun setMediaSessions(sessions: List<MediaSessionItem>) = update {
        it.copy(mediaSessions = sessions)
    }

    fun setMediaAccess(access: Boolean) = update {
        it.copy(
            mediaAccess = access,
            media = if (access) it.media else null,
            mediaSessions = if (access) it.mediaSessions else emptyList(),
        )
    }

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
        updatedAtMs: Long = if (gpsActive) System.currentTimeMillis() else 0L,
    ) = update {
        it.copy(
            speedKph = speedKph,
            gpsActive = gpsActive,
            latitude = latitude,
            longitude = longitude,
            bearing = bearing,
            accuracyM = accuracyM,
            locationUpdatedAtMs = updatedAtMs,
        )
    }

    fun resetForTest() {
        state = DashboardState()
    }

    fun notifyMapUpdated() = update { it.copy(mapRevision = it.mapRevision + 1) }

    fun notifyDisplayChanged() = update { it.copy(displayRevision = it.displayRevision + 1) }

    fun setBattery(percent: Int?, charging: Boolean) = update {
        it.copy(batteryPercent = percent, batteryCharging = charging)
    }

    fun replaceNotifications(items: List<NotificationItem>) = update {
        it.copy(notifications = items.take(MAX_NOTIFICATIONS))
    }

    fun addNotification(item: NotificationItem) = update { current ->
        val without = current.notifications.filterNot { it.key == item.key }
        current.copy(notifications = (listOf(item) + without).take(MAX_NOTIFICATIONS))
    }

    fun removeNotification(key: String) = update { current ->
        current.copy(notifications = current.notifications.filterNot { it.key == key })
    }

    const val MAX_NOTIFICATIONS = 30
}
