package com.omarchy.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.MediaWatcher

/**
 * Driving-safe media screen: now playing plus play/pause, next and previous.
 * The shell's Media tile sends the same transport commands. Browsing, queue,
 * video and web stages stay off the car canvas.
 */
class CarMediaScreen(carContext: CarContext) : Screen(carContext) {

    private var lastStamp = ""

    private val listener: (DashboardState) -> Unit = { state ->
        val media = state.media
        val stamp = listOf(
            media?.title.orEmpty(),
            media?.artist.orEmpty(),
            media?.album.orEmpty(),
            media?.playing.toString(),
            media?.source.orEmpty(),
            state.mediaAccess.toString(),
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
        val media = state.media
        if (!state.mediaAccess) {
            list.addItem(
                Row.Builder()
                    .setTitle("Media access is off")
                    .addText("Grant notification access in phone setup to read the active player")
                    .build(),
            )
            list.addItem(
                row("Phone setup") {
                    CarPhoneSetup.open(carContext)
                },
            )
        } else if (media == null) {
            list.addItem(
                Row.Builder()
                    .setTitle("Nothing playing")
                    .addText("Start playback on your phone or the car media app")
                    .build(),
            )
            addSessionRows(list, state, activePackage = null)
            if (state.mediaSessions.isEmpty()) {
                list.addItem(
                    row("Phone setup") {
                        CarPhoneSetup.open(carContext)
                    },
                )
            }
        } else {
            list.addItem(
                Row.Builder()
                    .setTitle(media.title.ifBlank { "Now playing" })
                    .addText(
                        listOfNotNull(
                            media.artist.takeIf { it.isNotBlank() },
                            media.album.takeIf { it.isNotBlank() },
                            media.source.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                    )
                    .build(),
            )
            list.addItem(
                row(if (media.playing) "Pause" else "Play") {
                    MediaTransport.toggle()
                    invalidate()
                },
            )
            list.addItem(
                row("Next track") {
                    MediaTransport.next()
                    invalidate()
                },
            )
            list.addItem(
                row("Previous track") {
                    MediaTransport.previous()
                    invalidate()
                },
            )
            addSessionRows(list, state, activePackage = media.packageName)
        }

        return ListTemplate.Builder()
            .setTitle("Media")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }

    private fun addSessionRows(
        list: ItemList.Builder,
        state: DashboardState,
        activePackage: String?,
    ) {
        val others = state.mediaSessions.filter { it.packageName != activePackage }.take(3)
        if (others.isEmpty()) return
        list.addItem(
            Row.Builder()
                .setTitle("Other players")
                .addText("Tap a player to make it active")
                .build(),
        )
        others.forEach { session ->
            list.addItem(
                row("Switch to ${session.label}") {
                    MediaWatcher.selectSession(session.packageName)
                    invalidate()
                },
            )
        }
    }

    private fun row(title: String, onClick: () -> Unit): Row =
        Row.Builder()
            .setTitle(title)
            .setOnClickListener(onClick)
            .build()

    companion object {
        private const val TAG = "CarMediaScreen"
    }
}
