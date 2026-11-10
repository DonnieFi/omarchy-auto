package com.omarchy.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MapCameraStore
import com.omarchy.auto.core.MapSnapshotStore
import com.omarchy.auto.core.ThemeStore

/**
 * In-car settings. This is a host-rendered ListTemplate so every row works
 * while driving; the old gear action tried to launch the phone-only setup
 * activity, which the host blocks (or silently ignores), so the button looked
 * dead. Navigation controls stay on the map strip, and Search is in the hub,
 * so this screen does not duplicate them. Phone-only tasks (background picker,
 * notification access, rail apps) stay on the "Phone setup" row.
 */
class CarSettingsScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val theme = ThemeStore.current()
        val list = ItemList.Builder()
            .addItem(
                row("Theme", theme.name) {
                    ThemeStore.next()
                    LiveDataStore.notifyDisplayChanged()
                    invalidate()
                },
            )
            .addItem(
                row("Map orientation", mapSummary()) {
                    MapCameraStore.toggleOrientation()
                    invalidate()
                },
            )
            .addItem(
                row(
                    "Recenter map",
                    if (MapCameraStore.followGps) "Following GPS" else "Map is panned",
                ) {
                    MapCameraStore.recenter()
                    invalidate()
                },
            )
            .addItem(
                row("Phone setup", "Permissions, background, rail apps") {
                    CarPhoneSetup.open(carContext)
                },
            )
            .addItem(
                Row.Builder()
                    .setTitle("About")
                    .addText("Omarchy Auto ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    .addText(statusSummary())
                    .build(),
            )

        return ListTemplate.Builder()
            .setTitle("Settings")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }

    private fun row(title: String, detail: String, onClick: () -> Unit): Row =
        Row.Builder()
            .setTitle(title)
            .addText(detail)
            .setOnClickListener(onClick)
            .build()

    private fun mapSummary(): String {
        val orientation = if (MapCameraStore.orientation == MapCameraStore.Orientation.NORTH_UP) {
            "North up"
        } else {
            "Heading up"
        }
        return if (MapCameraStore.followGps) "$orientation · following GPS" else "$orientation · panned"
    }

    private fun statusSummary(): String {
        val state = LiveDataStore.state
        val gps = if (state.gpsActive) "GPS live" else "No GPS fix"
        val map = when {
            MapSnapshotStore.error != null -> "map offline"
            MapSnapshotStore.bitmap != null -> "map ready"
            else -> "map loading"
        }
        return "$gps · $map"
    }
}
