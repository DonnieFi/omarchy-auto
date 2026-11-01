package com.omarchy.auto

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.MediaSnapshot
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-only layout preview. Renders the exact 1920x720 car canvas into PNGs
 * so UI changes can be reviewed without a car or a Play-installed build.
 *
 * This is not car footage and must never be presented as such.
 */
class CarPreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            try {
                renderPreviews()
            } catch (error: Exception) {
                Log.e(TAG, "Preview render failed", error)
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun renderPreviews() {
        val output = getExternalFilesDir("preview") ?: filesDir
        output.mkdirs()
        val wallpaper = AppBackground.load(this)
        val state = previewState()
        val states = listOf(
            "idle" to null,
            "drive" to TileId.DRIVE,
            "media" to TileId.MEDIA,
            "comms" to TileId.COMMS,
            "vehicle" to TileId.VEHICLE,
            "home" to TileId.HOME,
        )
        for ((name, tile) in states) {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val renderer = TileRenderer().apply { setBackground(wallpaper) }
            if (tile != null) renderer.toggle(tile)
            renderer.draw(
                canvas = canvas,
                width = WIDTH,
                height = HEIGHT,
                state = state,
                themeIndex = 0,
                mode = TileRenderer.Mode.CAR,
            )
            previewLabel(canvas)
            val file = File(output, "car-$name.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i(TAG, "Wrote ${file.absolutePath}")
        }
    }

    private fun previewLabel(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
        }
        canvas.drawText("LAYOUT PREVIEW · NOT CAR FOOTAGE", 18f, HEIGHT - 8f, paint)
    }

    private fun previewState(): DashboardState {
        val now = System.currentTimeMillis()
        return DashboardState(
            media = MediaSnapshot(
                title = "High Roller",
                artist = "The Crystal Method",
                album = "Vegas",
                playing = true,
                positionMs = 94_000L,
                durationMs = 236_000L,
                source = "YouTube Music",
            ),
            mediaAccess = true,
            notifications = listOf(
                NotificationItem("1", "Android Auto", "Connected to your vehicle", "Plugged in over USB", now - 12_000),
                NotificationItem("2", "Messages", "Alex", "Running five minutes late, start without me", now - 60_000),
                NotificationItem("3", "Calendar", "Standup at 10:30", "Meet link is ready", now - 120_000),
                NotificationItem("4", "Home Assistant", "Front door unlocked", "Unlocked by Donnie", now - 180_000),
            ),
            notificationAccess = true,
            speedKph = 42f,
            gpsActive = true,
            locationAccess = true,
            vehicleMock = true,
        )
    }

    companion object {
        private const val TAG = "CarPreview"
        private const val WIDTH = 1920
        private const val HEIGHT = 720
    }
}
