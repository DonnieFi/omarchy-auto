package com.omarchy.auto.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/** Publishes battery percentage and charging state for the status bars. */
class BatteryWatcher(
    context: Context,
    private val onUpdate: (percent: Int?, charging: Boolean) -> Unit,
) {
    private val app = context.applicationContext
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            publish(intent)
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            val sticky = app.registerReceiver(null, filter)
            publish(sticky)
            ContextCompat.registerReceiver(
                app,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: Exception) {
            started = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            app.unregisterReceiver(receiver)
        } catch (_: Exception) {
            // The receiver may already be gone during teardown.
        }
    }

    private fun publish(intent: Intent?) {
        if (intent == null) {
            onUpdate(null, false)
            return
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt().coerceIn(0, 100)
        } else {
            null
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        onUpdate(percent, charging)
    }
}
