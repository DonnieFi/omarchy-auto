package com.omarchy.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.BackgroundStore
import com.omarchy.auto.core.DistractionLock
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MediaSnapshot
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.OmarchyTheme
import com.omarchy.auto.core.RailConfigStore
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.Themes
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarShellTest {

    private val commands = mutableListOf<String>()

    private companion object {
        const val CANVAS_W = 1280
        const val CANVAS_H = 720

        /** White door on the Roadster, inside the stage on a 1280×720 car shell. */
        const val DOOR_X = 467
        const val DOOR_Y = 396

        /** Left margin over the blue studio light. */
        const val MARGIN_X = 8
        const val MARGIN_Y = 390

        /** Gap between the stage and the rail, over the blue floor light. */
        const val GAP_X = 1055
        const val GAP_Y = 480
    }

    @Before
    fun setUp() {
        LiveDataStore.resetForTest()
        DistractionLock.resetForTest()
        RailConfigStore.resetForTest()
        ThemeStore.select(0)
        commands.clear()
        MediaTransport.commandSinkForTest = { commands += it }
    }

    @After
    fun tearDown() {
        MediaTransport.commandSinkForTest = null
        DistractionLock.resetForTest()
        ThemeStore.select(0)
        LiveDataStore.resetForTest()
        BackgroundStore.resetForTest()
        AppBackground.invalidate()
    }

    @Test
    fun phoneShellStaysThemedAndStillOpensItsKeyboard() {
        val renderer = TileRenderer()
        renderer.show(TileId.HOME)
        draw(renderer, TileRenderer.Mode.PHONE)
        val frame = renderer.lastFrame
        assertEquals(TileRenderer.Mode.PHONE, frame!!.mode)
        assertTrue(frame.drawsCustomRail)
        assertFalse(frame.drawsWallpaper)
        assertNotNull(firstHit(renderer, TileRenderer.Target.THEME))
        assertNotNull(firstHit(renderer, TileRenderer.Target.THEME_CHIP) { it.index == 3 })
        assertTrue(renderer.openKeyboard(TileRenderer.KeyboardMode.MAP_SEARCH, "king"))
        assertEquals(TileRenderer.KeyboardMode.MAP_SEARCH, renderer.keyboardMode)
    }

    @Test
    fun shellActionsReachThemeMediaSearchNotificationsAndSettings() {
        val host = RecordingHost()
        val renderer = TileRenderer()
        val dispatch = dispatcher(renderer, host)

        renderer.show(TileId.HOME)
        draw(renderer, TileRenderer.Mode.CAR)
        val chip = firstHit(renderer, TileRenderer.Target.THEME_CHIP) { it.index == 2 }
        assertNotNull(chip)
        assertTrue(dispatch.apply(chip!!))
        assertEquals(2, ThemeStore.index)

        val settings = firstHit(renderer, TileRenderer.Target.STAGE_OPEN)
        assertNotNull(settings)
        dispatch.apply(settings!!)
        assertEquals(1, host.settings)

        LiveDataStore.setMediaAccess(true)
        LiveDataStore.setMedia(
            MediaSnapshot(
                title = "Nightcall",
                artist = "Kavinsky",
                playing = true,
                source = "Spotify",
            ),
        )
        renderer.show(TileId.MEDIA)
        draw(renderer, TileRenderer.Mode.CAR)
        assertNotNull(firstHit(renderer, TileRenderer.Target.PLAY_PAUSE))
        assertNotNull(firstHit(renderer, TileRenderer.Target.PREV_TRACK))
        assertNotNull(firstHit(renderer, TileRenderer.Target.NEXT_TRACK))
        assertTrue(dispatch.apply(firstHit(renderer, TileRenderer.Target.PLAY_PAUSE)!!))
        assertTrue(dispatch.apply(firstHit(renderer, TileRenderer.Target.NEXT_TRACK)!!))
        assertTrue(dispatch.apply(firstHit(renderer, TileRenderer.Target.PREV_TRACK)!!))
        assertEquals(listOf("toggle", "next", "previous"), commands)

        DistractionLock.onCarSessionStarted()
        renderer.show(TileId.DRIVE)
        draw(renderer, TileRenderer.Mode.CAR)
        val search = firstHit(renderer, TileRenderer.Target.MAP_SEARCH_OPEN)
        assertNotNull(search)
        assertTrue(dispatch.apply(search!!))
        assertEquals(1, host.search)
        assertEquals(TileRenderer.KeyboardMode.NONE, renderer.keyboardMode)
        assertFalse(renderer.openKeyboard(TileRenderer.KeyboardMode.MAP_SEARCH, "king"))

        val comms = firstHit(renderer, TileRenderer.Target.TILE_COMMS)
        assertNotNull(comms)
        assertTrue(dispatch.apply(comms!!))
        assertEquals(TileId.COMMS, renderer.selected)
        assertEquals(1, host.notifications)
        draw(renderer, TileRenderer.Mode.CAR)
        assertFalse(renderer.lastFrame!!.drawsNotificationList)

        DistractionLock.onHostUx(DistractionLock.HostUx.PARKED)
        LiveDataStore.setNotificationAccess(true)
        LiveDataStore.addNotification(
            NotificationItem("k1", "Messages", "Alex", "On my way", 1_000L),
        )
        renderer.show(TileId.COMMS)
        draw(renderer, TileRenderer.Mode.CAR)
        assertTrue(renderer.lastFrame!!.drawsNotificationList)
        val row = firstHit(renderer, TileRenderer.Target.NOTIFICATION)
        assertNotNull(row)
        assertTrue(dispatch.apply(row!!))
        draw(renderer, TileRenderer.Mode.CAR)
        assertNotNull(firstHit(renderer, TileRenderer.Target.NOTIF_CLOSE))
        assertEquals(1, host.notifications)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun carShellPaintsTheBundledTokyoStudioRoadster() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roadster = context.assets.open("themes/tokyo-night.webp").use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        assertNotNull(roadster)
        assertEquals(1600, roadster!!.width)
        assertEquals(900, roadster.height)
        assertEquals(0, ThemeStore.index)
        assertEquals("tokyo-night", Themes.at(ThemeStore.index).id)
        val theme = Themes.at(0)

        // White door, doors up, in the blue studio. The body is near-white with a blue cast.
        val doorSource = sourceColor(roadster, CANVAS_W, CANVAS_H, DOOR_X, DOOR_Y)
        assertTrue(
            "tokyo-night.webp door sample is not the white Roadster body: ${hex(doorSource)}",
            Color.red(doorSource) > 200 && Color.green(doorSource) > 200 && Color.blue(doorSource) > 200,
        )
        // Left margin and the stage/rail gap land on the blue studio light, not the dark sky.
        val marginBlue = sourceColor(roadster, CANVAS_W, CANVAS_H, MARGIN_X, MARGIN_Y)
        val gapBlue = sourceColor(roadster, CANVAS_W, CANVAS_H, GAP_X, GAP_Y)
        assertTrue(Color.blue(marginBlue) > 140)
        assertTrue(Color.blue(gapBlue) > 180)

        val bare = renderCar(background = null, tile = null)
        assertFalse(bare.frame.drawsWallpaper)
        assertFalse(bare.frame.mapFillsCanvas)

        val idle = renderCar(roadster, tile = null)
        assertTrue(idle.frame.drawsWallpaper)
        assertFalse(idle.frame.mapFillsCanvas)
        assertTrue(idle.frame.drawsCustomRail)
        assertEquals(null, idle.renderer.selected)
        assertRoadsterPixel(idle.pixels, roadster, theme, DOOR_X, DOOR_Y, coverStage = false)
        assertRoadsterPixel(idle.pixels, roadster, theme, MARGIN_X, MARGIN_Y, coverStage = false)
        assertRoadsterPixel(idle.pixels, roadster, theme, GAP_X, GAP_Y, coverStage = false)
        assertNotPalette(idle.pixels, bare.pixels, DOOR_X, DOOR_Y)
        assertNotPalette(idle.pixels, bare.pixels, MARGIN_X, MARGIN_Y)

        val home = renderCar(roadster, tile = TileId.HOME)
        assertTrue(home.frame.drawsWallpaper)
        assertEquals(TileId.HOME, home.renderer.selected)
        assertRoadsterPixel(home.pixels, roadster, theme, DOOR_X, DOOR_Y, coverStage = true)
        assertRoadsterPixel(home.pixels, roadster, theme, MARGIN_X, MARGIN_Y, coverStage = false)
        assertRoadsterPixel(home.pixels, roadster, theme, GAP_X, GAP_Y, coverStage = false)
        assertNotPalette(home.pixels, bare.pixels, DOOR_X, DOOR_Y)

        val drive = renderCar(roadster, tile = TileId.DRIVE)
        assertTrue(drive.frame.drawsWallpaper)
        assertFalse(drive.frame.mapFillsCanvas)
        assertTrue(drive.frame.drawsCustomRail)
        assertEquals(TileId.DRIVE, drive.renderer.selected)
        assertRoadsterPixel(drive.pixels, roadster, theme, MARGIN_X, MARGIN_Y, coverStage = false)
        assertRoadsterPixel(drive.pixels, roadster, theme, GAP_X, GAP_Y, coverStage = false)
        val driveDoor = drive.pixels.getPixel(DOOR_X, DOOR_Y)
        val uncoveredDoor = composited(roadster, theme, DOOR_X, DOOR_Y, coverStage = false)
        assertTrue(
            "Drive stage still shows the Roadster instead of the map: ${hex(driveDoor)}",
            channelDelta(driveDoor, uncoveredDoor) > 40,
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun useTomBackgroundWinsOverAThemeChange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BackgroundStore.resetForTest()
        BackgroundStore.init(context)
        assertTrue(BackgroundStore.useBundledTom(context))
        assertTrue(BackgroundStore.enabled)
        AppBackground.invalidate()

        val asset = context.assets.open("themes/tokyo-night.webp").use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        val onTokyo = AppBackground.load(context, Themes.at(0))
        val onOsaka = AppBackground.load(context, Themes.at(1))
        assertNotNull(onTokyo)
        assertNotNull(onOsaka)
        assertEquals(asset!!.getPixel(584, 496), onTokyo!!.getPixel(584, 496))
        assertEquals(onTokyo.getPixel(584, 496), onOsaka!!.getPixel(584, 496))
        assertEquals(onTokyo.getPixel(200, 450), onOsaka.getPixel(200, 450))

        BackgroundStore.clear(context)
        AppBackground.invalidate()
        val osakaTheme = AppBackground.load(context, Themes.at(1))
        assertNotNull(osakaTheme)
        assertTrue(channelDelta(onTokyo.getPixel(584, 496), osakaTheme!!.getPixel(584, 496)) > 40)
    }

    private fun renderCar(background: Bitmap?, tile: TileId?): Rendered {
        val renderer = TileRenderer()
        renderer.setBackground(background)
        if (tile != null) renderer.show(tile)
        val pixels = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(pixels), CANVAS_W, CANVAS_H, LiveDataStore.state, 0, TileRenderer.Mode.CAR)
        return Rendered(renderer, pixels, renderer.lastFrame!!)
    }

    private fun assertRoadsterPixel(
        canvas: Bitmap,
        source: Bitmap,
        theme: OmarchyTheme,
        x: Int,
        y: Int,
        coverStage: Boolean,
    ) {
        val actual = canvas.getPixel(x, y)
        val expected = composited(source, theme, x, y, coverStage)
        val delta = channelDelta(actual, expected)
        assertTrue(
            "($x,$y) is not the Tokyo Studio bitmap after the scrim: actual=${hex(actual)} expected=${hex(expected)} delta=$delta",
            delta <= 24,
        )
    }

    private fun assertNotPalette(withImage: Bitmap, fallback: Bitmap, x: Int, y: Int) {
        val delta = channelDelta(withImage.getPixel(x, y), fallback.getPixel(x, y))
        assertTrue(
            "($x,$y) matches the palette fallback (delta=$delta)",
            delta > 30,
        )
    }

    private fun composited(source: Bitmap, theme: OmarchyTheme, x: Int, y: Int, coverStage: Boolean): Int {
        var color = sourceColor(source, CANVAS_W, CANVAS_H, x, y)
        color = srcOver(color, withAlpha(theme.background, theme.scrimAlpha))
        color = srcOver(color, vignette(theme, CANVAS_H, y))
        if (coverStage) {
            color = srcOver(color, withAlpha(theme.surface, theme.stageAlpha))
        }
        return color
    }

    private fun sourceColor(source: Bitmap, width: Int, height: Int, x: Int, y: Int): Int {
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val dx = (width - source.width * scale) / 2f
        val dy = (height - source.height * scale) / 2f
        val sx = (x + 0.5f - dx) / scale
        val sy = (y + 0.5f - dy) / scale
        return bilinear(source, sx, sy)
    }

    private fun bilinear(bitmap: Bitmap, sx: Float, sy: Float): Int {
        val x = sx.coerceIn(0f, (bitmap.width - 1).toFloat())
        val y = sy.coerceIn(0f, (bitmap.height - 1).toFloat())
        val x0 = x.toInt().coerceIn(0, bitmap.width - 1)
        val y0 = y.toInt().coerceIn(0, bitmap.height - 1)
        val x1 = (x0 + 1).coerceAtMost(bitmap.width - 1)
        val y1 = (y0 + 1).coerceAtMost(bitmap.height - 1)
        val tx = x - x0
        val ty = y - y0
        val top = lerp(bitmap.getPixel(x0, y0), bitmap.getPixel(x1, y0), tx)
        val bottom = lerp(bitmap.getPixel(x0, y1), bitmap.getPixel(x1, y1), tx)
        return lerp(top, bottom, ty)
    }

    private fun vignette(theme: OmarchyTheme, height: Int, y: Int): Int {
        val fraction = ((y + 0.5f) / height).coerceIn(0f, 1f)
        val alpha = if (fraction <= 0.45f) {
            120f + (24f - 120f) * (fraction / 0.45f)
        } else {
            24f + (120f - 24f) * ((fraction - 0.45f) / 0.55f)
        }
        return withAlpha(theme.background, alpha.roundToInt())
    }

    private fun srcOver(dst: Int, src: Int): Int {
        val sa = Color.alpha(src) / 255f
        val inv = 1f - sa
        fun channel(s: Int, d: Int) = (s * sa + d * inv).roundToInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(src), Color.red(dst)),
            channel(Color.green(src), Color.green(dst)),
            channel(Color.blue(src), Color.blue(dst)),
        )
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun channel(from: Int, to: Int) = (from + (to - from) * t).roundToInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(a), Color.red(b)),
            channel(Color.green(a), Color.green(b)),
            channel(Color.blue(a), Color.blue(b)),
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun channelDelta(a: Int, b: Int): Int =
        maxOf(abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))

    private fun hex(color: Int): String = "#%08X".format(color)

    private data class Rendered(
        val renderer: TileRenderer,
        val pixels: Bitmap,
        val frame: TileRenderer.FrameInfo,
    )

    private fun dispatcher(renderer: TileRenderer, host: CarShellHost): CarShellDispatch =
        CarShellDispatch(
            renderer = renderer,
            appContext = ApplicationProvider.getApplicationContext(),
            host = host,
            refreshBackground = {},
            launchStage = {},
        )

    private fun draw(renderer: TileRenderer, mode: TileRenderer.Mode) {
        val canvas = Canvas(Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888))
        renderer.draw(canvas, 1280, 720, LiveDataStore.state, ThemeStore.index, mode)
    }

    private fun firstHit(
        renderer: TileRenderer,
        target: TileRenderer.Target,
        match: (TileRenderer.Hit) -> Boolean = { true },
    ): TileRenderer.Hit? {
        var y = 0
        while (y < 720) {
            var x = 0
            while (x < 1280) {
                val hit = renderer.hitTest(x + 1f, y + 1f)
                if (hit != null && hit.target == target && match(hit)) return hit
                x += 4
            }
            y += 4
        }
        return null
    }

    private class RecordingHost : CarShellHost {
        var search = 0
        var notifications = 0
        var settings = 0

        override fun openSearch() {
            search += 1
        }

        override fun openNotifications() {
            notifications += 1
        }

        override fun openSettings() {
            settings += 1
        }
    }
}
