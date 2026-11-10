package com.omarchy.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.omarchy.auto.core.DistractionLock
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MediaSnapshot
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.RailConfigStore
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarShellTest {

    private val commands = mutableListOf<String>()

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
    }

    @Test
    fun phoneShellStaysThemedAndStillOpensItsKeyboard() {
        val renderer = TileRenderer()
        renderer.show(TileId.HOME)
        draw(renderer, TileRenderer.Mode.PHONE)
        val frame = renderer.lastFrame
        assertEquals(TileRenderer.Mode.PHONE, frame!!.mode)
        assertTrue(frame.drawsCustomRail)
        assertTrue(frame.drawsWallpaper)
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
