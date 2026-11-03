package com.omarchy.auto

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.BackgroundStore
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.DisplaySettings
import com.omarchy.auto.core.FeatureFlags
import com.omarchy.auto.core.MediaSnapshot
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.Themes
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
        DisplaySettings.init(this)
        BackgroundStore.init(this)
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
        val wallpaper = AppBackground.load(this, Themes.at(0))
        val state = previewState(wallpaper)
        val states = buildList {
            add("idle" to null)
            add("drive" to TileId.DRIVE)
            add("media" to TileId.MEDIA)
            add("comms" to TileId.COMMS)
            if (FeatureFlags.SHOW_VEHICLE_TILE) add("vehicle" to TileId.VEHICLE)
            add("home" to TileId.HOME)
        }
        for ((name, tile) in states) {
            render(wallpaper, state, output, "car-$name.png", tile, overlayKey = null)
        }
        render(wallpaper, state, output, "car-comms-overlay.png", TileId.COMMS, overlayKey = "2")
        for ((name, tile) in listOf("comms" to TileId.COMMS, "home" to TileId.HOME, "media" to TileId.MEDIA)) {
            render(wallpaper, state, output, "car-800-$name.png", tile, overlayKey = null, width = 800, height = 480)
        }
        render(wallpaper, state, output, "car-1280-home.png", TileId.HOME, overlayKey = null, width = 1280, height = 720)
        render(
            wallpaper, state, output, "car-1280-everforest.png", TileId.HOME,
            overlayKey = null, width = 1280, height = 720, themeIndex = 5,
        )
        render(wallpaper, state, output, "car-1280-media.png", TileId.MEDIA, overlayKey = null, width = 1280, height = 720)
        render(
            wallpaper, state, output, "car-960-everforest.png", TileId.HOME,
            overlayKey = null, width = 960, height = 540, themeIndex = 5,
        )
        render(
            wallpaper, state, output, "car-800-everforest.png", TileId.HOME,
            overlayKey = null, width = 800, height = 480, themeIndex = 5,
        )
        DisplaySettings.cycle()
        render(
            wallpaper, state, output, "car-800-comms-large.png", TileId.COMMS,
            overlayKey = null, width = 800, height = 480,
        )
        render(
            wallpaper, state, output, "car-800-comms-overlay-large.png", TileId.COMMS,
            overlayKey = "2", width = 800, height = 480,
        )
        render(
            wallpaper, state, output, "car-1280-comms-large.png", TileId.COMMS,
            overlayKey = null, width = 1280, height = 720,
        )
        val full = fullCommsState(state)
        render(wallpaper, full, output, "car-800-comms-large-full.png", TileId.COMMS, overlayKey = null, width = 800, height = 480)
        render(wallpaper, full, output, "car-1280-comms-large-full.png", TileId.COMMS, overlayKey = null, width = 1280, height = 720)
        render(wallpaper, full, output, "car-comms-large-full.png", TileId.COMMS, overlayKey = null)
        DisplaySettings.cycle()
        render(
            wallpaper, state, output, "car-800-comms-small.png", TileId.COMMS,
            overlayKey = null, width = 800, height = 480,
        )
        render(
            wallpaper, state, output, "car-800-comms-overlay-small.png", TileId.COMMS,
            overlayKey = "2", width = 800, height = 480,
        )
        DisplaySettings.cycle() // back to normal for the theme renders
        Themes.all.forEachIndexed { index, theme ->
            val themeWallpaper = AppBackground.load(this, theme)
            render(themeWallpaper, state, output, "theme-${theme.id}.png", null, overlayKey = null, themeIndex = index)
        }
    }

    private fun render(
        wallpaper: Bitmap?,
        state: DashboardState,
        output: File,
        name: String,
        tile: TileId?,
        overlayKey: String?,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        themeIndex: Int = 0,
    ) {
        val bitmap = Bitmap.createBitmap(width, height + CAPTION_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = TileRenderer().apply { setBackground(wallpaper) }
        if (tile != null) renderer.toggle(tile)
        if (overlayKey != null) renderer.openNotification(overlayKey)
        renderer.draw(
            canvas = canvas,
            width = width,
            height = height,
            state = state,
            themeIndex = themeIndex,
            mode = TileRenderer.Mode.CAR,
        )
        previewLabel(canvas, width, height)
        val file = File(output, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Wrote ${file.absolutePath}")
    }

    private fun previewLabel(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 150, 150)
            textSize = 13f
        }
        canvas.drawRect(0f, height.toFloat(), width.toFloat(), (height + CAPTION_HEIGHT).toFloat(), Paint().apply {
            color = Color.rgb(12, 12, 12)
        })
        canvas.drawText(
            "LAYOUT PREVIEW · NOT CAR FOOTAGE · ${width}×${height}",
            18f,
            height + 17f,
            paint,
        )
    }

    private fun fullCommsState(base: DashboardState): DashboardState {
        val now = System.currentTimeMillis()
        val extra = listOf(
            NotificationItem("5", "Signal", "Sam", "Grabbing coffee, want anything?", now - 240_000),
            NotificationItem("6", "Gmail", "Play Console", "Internal testing release is live", now - 300_000),
            NotificationItem("7", "Slack", "#omarchy", "New build passed review", now - 360_000),
            NotificationItem("8", "Weather", "Rain at 4 PM", "Bring a jacket", now - 420_000),
        )
        return base.copy(notifications = base.notifications + extra)
    }

    private fun previewState(art: Bitmap?): DashboardState {
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
                art = art,
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
            batteryPercent = 82,
            batteryCharging = false,
            vehicleMock = true,
        )
    }

    companion object {
        private const val TAG = "CarPreview"
        private const val WIDTH = 1920
        private const val HEIGHT = 720
        private const val CAPTION_HEIGHT = 26
    }
}
