package com.omarchy.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.OmarchyNotificationListener

/**
 * Parked-only notification list. The notification body is only shown on the
 * detail screen, which is a parked-only [MessageTemplate]; sensitive/OTP
 * notifications are already filtered by the shared listener. The hub gates
 * this screen with a ParkedOnlyOnClickListener.
 */
class CarCommsScreen(carContext: CarContext) : Screen(carContext) {

    private var lastStamp = ""

    private val listener: (DashboardState) -> Unit = { state ->
        val stamp = listOf(
            state.notifications.size.toString(),
            state.notifications.firstOrNull()?.key.orEmpty(),
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
        if (!state.notificationAccess) {
            list.addItem(
                Row.Builder()
                    .setTitle("Notification access is off")
                    .addText("Grant access in phone setup")
                    .build(),
            )
            list.addItem(
                Row.Builder()
                    .setTitle("Phone setup")
                    .addText("Open Omarchy Auto setup on your phone")
                    .setOnClickListener {
                        CarPhoneSetup.open(carContext)
                    }
                    .build(),
            )
        } else if (state.notifications.isEmpty()) {
            list.setNoItemsMessage("No recent notifications")
        } else {
            state.notifications.take(8).forEach { item ->
                list.addItem(
                    Row.Builder()
                        .setTitle(item.title.ifBlank { item.app })
                        .addText(item.app)
                        .setOnClickListener {
                            screenManager.push(CarNotificationScreen(carContext, item))
                        }
                        .build(),
                )
            }
        }
        list.addItem(
            Row.Builder()
                .setTitle("Refresh")
                .addText("Reload existing notifications")
                .setOnClickListener {
                    OmarchyNotificationListener.refresh(carContext)
                    invalidate()
                }
                .build(),
        )

        return ListTemplate.Builder()
            .setTitle("Notifications")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }

    private class CarNotificationScreen(
        carContext: CarContext,
        private val item: NotificationItem,
    ) : Screen(carContext) {
        override fun onGetTemplate(): Template =
            MessageTemplate.Builder(item.text.ifBlank { "No further details" })
                .setTitle(item.title.ifBlank { item.app })
                .setHeaderAction(Action.BACK)
                .build()
    }
}
