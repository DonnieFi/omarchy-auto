package com.omarchy.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.diag.OaLog

/**
 * The car's screen hub. It opens only from the Menu action, never on a cold
 * start. The map stays alive underneath, so navigation and guidance continue
 * while Media, Notifications or Settings are open. The Map row pops back to
 * the live map.
 *
 * Labels avoid "Home": in a navigation app a house/Home reads as "navigate to
 * my home address". The map action / Menu is the hub's entry point.
 */
class CarHomeScreen(carContext: CarContext) : Screen(carContext) {

    private var lastStamp = ""

    private val listener: (DashboardState) -> Unit = { state ->
        val media = state.media
        val stamp = listOf(
            NavigationController.navState.name,
            NavigationController.destination?.title.orEmpty(),
            NavigationController.remainingDistanceMeters.toInt().toString(),
            media?.title.orEmpty(),
            media?.artist.orEmpty(),
            media?.playing.toString(),
            state.notifications.size.toString(),
            state.notificationAccess.toString(),
        ).joinToString("|")
        if (stamp != lastStamp) {
            lastStamp = stamp
            invalidate()
        }
    }

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    lastStamp = ""
                    LiveDataStore.addListener(listener)
                }

                override fun onStop(owner: LifecycleOwner) {
                    LiveDataStore.removeListener(listener)
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val state = LiveDataStore.state
        val list = ItemList.Builder()
            .addItem(
                row("Map", mapDetail()) {
                    OaLog.i(TAG, "Leaving the hub for the live map")
                    screenManager.pop()
                },
            )
            .addItem(
                row("Search", "Find a place or address") {
                    screenManager.push(PlaceSearchScreen(carContext))
                },
            )
            .addItem(
                row("Media", mediaDetail(state)) {
                    screenManager.push(CarMediaScreen(carContext))
                },
            )
            .addItem(
                row("Notifications", notificationsDetail(state)) {
                    // Titles only here; the body detail is a parked-only
                    // MessageTemplate. The list itself stays reachable so the
                    // driver can see that alerts exist without reading bodies.
                    screenManager.push(CarCommsScreen(carContext))
                },
            )
            .addItem(
                row("Settings", "Theme, map and phone setup") {
                    screenManager.push(CarSettingsScreen(carContext))
                },
            )

        val mapAction = Action.Builder()
            .setIcon(icon(R.drawable.ic_map))
            .setOnClickListener {
                screenManager.pop()
            }
            .build()
        return ListTemplate.Builder()
            .setTitle("OMARCHY")
            .setActionStrip(ActionStrip.Builder().addAction(mapAction).build())
            .setSingleList(list.build())
            .build()
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    private fun row(title: String, detail: String, onClick: () -> Unit): Row =
        Row.Builder()
            .setTitle(title)
            .addText(detail)
            .setOnClickListener(onClick)
            .build()

    private fun mapDetail(): String {
        val nav = NavigationController
        val dest = nav.destination
        return when (nav.navState) {
            NavState.ACTIVE -> {
                val name = dest?.title.orEmpty().ifBlank { "destination" }
                val eta = nav.formatDuration(nav.displayedRemainingDurationSeconds())
                "Route to $name · ETA $eta"
            }
            NavState.ROUTING -> "Building route…"
            NavState.ROUTE_ERROR -> "Route error · open the map"
            NavState.LOCATION_REQUIRED -> "Waiting for GPS · open the map"
            else -> "Map, speed and route"
        }
    }

    private fun mediaDetail(state: DashboardState): String {
        if (!state.mediaAccess) return "Media access is off · see Settings"
        val media = state.media ?: return "Nothing playing"
        val track = media.title.ifBlank { "Unknown track" }
        val artist = media.artist.takeIf { it.isNotBlank() }
        val verb = if (media.playing) "Playing" else "Paused"
        return if (artist != null) "$verb · $track — $artist" else "$verb · $track"
    }

    private fun notificationsDetail(state: DashboardState): String {
        if (!state.notificationAccess) return "Notification access is off · see Settings"
        val count = state.notifications.size
        return when {
            count == 0 -> "No recent notifications"
            count == 1 -> "1 recent notification · titles only"
            else -> "$count recent notifications · titles only"
        }
    }

    companion object {
        private const val TAG = "CarHomeScreen"
    }
}
