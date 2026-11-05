package com.omarchy.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.car.app.model.Action
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.navigation.TestNavigationManager
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.omarchy.auto.core.LaunchTargetStore
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.ParsedRoute
import com.omarchy.auto.core.RouteStep
import com.omarchy.auto.core.SearchCandidate
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
class OmarchyScreenTest {

    @Before
    fun setUp() {
        LiveDataStore.resetForTest()
        NavigationController.resetForTest()
    }

    @After
    fun tearDown() {
        NavigationController.resetForTest()
    }

    @Test
    fun onGetTemplate_buildsNavigationTemplateWithActionStripAndPanMapActionStrip() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = OmarchyScreen(carContext).onGetTemplate() as NavigationTemplate
        assertNotNull(template.actionStrip)
        val mapStrip = template.mapActionStrip
        assertNotNull(mapStrip)
        assertTrue(mapStrip!!.actions.any { it.type == Action.TYPE_PAN })
    }

    @Test
    fun navigationManager_lifecycleSyncedWithNavigationController() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val carContext = TestCarContext.createCarContext(context)
        val testNavManager = carContext.getCarService(TestNavigationManager::class.java)
        val screen = OmarchyScreen(carContext)

        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { oLat, oLon, dLat, dLon ->
            ParsedRoute(
                geometry = listOf(oLat to oLon, dLat to dLon),
                steps = listOf(
                    RouteStep("TURN LEFT", "Yonge St", 1000.0, 150.0, dLat, dLon, oLat, oLon),
                ),
                totalDistanceMeters = 1000.0,
                totalDurationSeconds = 150.0,
            )
        }
        LiveDataStore.setLocationFix(
            speedKph = 30f,
            gpsActive = true,
            latitude = 43.6532,
            longitude = -79.3832,
            bearing = 0f,
            accuracyM = 8f,
            updatedAtMs = now,
        )

        val dest = SearchCandidate("Yonge", "Toronto", 43.6580, -79.3750)
        NavigationController.selectDestination(dest, autoStartNav = true, nowMs = now)
        screen.syncNavigationManager()

        assertTrue(screen.hostNavStarted)
        assertTrue(testNavManager.tripsSent.isNotEmpty())

        NavigationController.stopNavigation()
        screen.syncNavigationManager()
        assertFalse(screen.hostNavStarted)
    }

    @Test
    fun tileRendererAccumulatesSlowCommsDragAndLaunchTargetStoreFallsBackWhenUninstalled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LiveDataStore.setNotificationAccess(true)
        for (i in 1..10) {
            LiveDataStore.addNotification(
                NotificationItem("k$i", "App", "Title $i", "Body $i", 1000L + i),
            )
        }

        val renderer = TileRenderer()
        renderer.show(TileId.COMMS)
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        renderer.draw(canvas, 1280, 720, LiveDataStore.state, 0, TileRenderer.Mode.CAR)

        assertEquals(0, renderer.notifScrollOffset)
        // Three small drags of -6px each (< 16px threshold individually, -18px total)
        renderer.scroll(400f, 360f, 0f, -6f)
        renderer.scroll(400f, 360f, 0f, -6f)
        val moved = renderer.scroll(400f, 360f, 0f, -6f)
        assertTrue(moved)
        assertEquals(1, renderer.notifScrollOffset)

        // LaunchTargetStore falls back to auto when an uninstalled native package is selected,
        // while keeping stage web apps selectable
        LaunchTargetStore.select(context, "tidal")
        assertEquals("auto", LaunchTargetStore.selected(context).key)

        LaunchTargetStore.select(context, "youtube")
        assertEquals("youtube", LaunchTargetStore.selected(context).key)
    }
}
