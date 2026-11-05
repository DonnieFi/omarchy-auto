package com.omarchy.auto.core

import android.graphics.Bitmap

enum class TileId(val number: String, val title: String) {
    DRIVE("01", "DRIVE"),
    MEDIA("02", "MEDIA"),
    COMMS("03", "COMMS"),
    VEHICLE("04", "VEHICLE"),
    HOME("05", "HOME"),
}

data class MediaSnapshot(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val source: String = "",
    val packageName: String = "",
    val art: Bitmap? = null,
)

data class MediaSessionItem(
    val packageName: String,
    val label: String,
    val title: String = "",
    val playing: Boolean = false,
) {
    val source: String get() = label
}

data class NotificationItem(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)

data class DashboardState(
    val media: MediaSnapshot? = null,
    val mediaSessions: List<MediaSessionItem> = emptyList(),
    val mediaAccess: Boolean = false,
    val notifications: List<NotificationItem> = emptyList(),
    val notificationAccess: Boolean = false,
    val speedKph: Float? = null,
    val gpsActive: Boolean = false,
    val locationAccess: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Float = 0f,
    val accuracyM: Float? = null,
    val locationUpdatedAtMs: Long = 0L,
    val mapRevision: Int = 0,
    val displayRevision: Int = 0,
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val vehicleMock: Boolean = true,
)
