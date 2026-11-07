package com.omarchy.auto

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import androidx.test.core.app.ApplicationProvider
import com.omarchy.auto.core.CarPlatformPolicy
import com.omarchy.auto.core.DistractionLock
import com.omarchy.auto.core.LaunchTargetStore
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MapCameraStore
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.NavigateIntent
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NotificationItem
import com.omarchy.auto.core.ParsedRoute
import com.omarchy.auto.core.RouteLane
import com.omarchy.auto.core.RouteStep
import com.omarchy.auto.core.SearchCandidate
import com.omarchy.auto.core.SetupActivity
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmarchyScreenTest {

    @Before
    fun setUp() {
        LiveDataStore.resetForTest()
        NavigationController.resetForTest()
        DistractionLock.resetForTest()
        MapCameraStore.resetForTest()
    }

    @After
    fun tearDown() {
        NavigationController.resetForTest()
        DistractionLock.resetForTest()
    }

    @Test
    fun onGetTemplate_buildsNavigationTemplateWithActionStripAndPanMapActionStrip() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = OmarchyScreen(carContext).onGetTemplate() as NavigationTemplate
        assertNotNull(template.actionStrip)
        assertEquals(
            listOf("Search", "Heading", "Settings"),
            template.actionStrip!!.actions.map { it.title?.toString() },
        )
        val mapStrip = template.mapActionStrip
        assertNotNull(mapStrip)
        assertTrue(mapStrip!!.actions.any { it.type == Action.TYPE_PAN })
        assertEquals(4, mapStrip.actions.size)
        mapStrip.actions.filter { it.type != Action.TYPE_PAN }.forEach { assertNull(it.title) }
        assertTrue(DistractionLock.carSessionActive)
        assertTrue(DistractionLock.suppressesSharedDistractions())
    }

    @Test
    fun activeNavigationPublishesHostRoutingInfoInsteadOfCanvasText() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screen = OmarchyScreen(carContext)
        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { oLat, oLon, dLat, dLon ->
            ParsedRoute(
                geometry = listOf(oLat to oLon, dLat to dLon),
                steps = listOf(
                    RouteStep(
                        "TURN LEFT",
                        "Yonge St",
                        400.0,
                        80.0,
                        dLat,
                        dLon,
                        oLat,
                        oLon,
                        lanes = listOf(
                            RouteLane(listOf("straight"), recommended = false),
                            RouteLane(listOf("left"), recommended = true),
                        ),
                    ),
                ),
                totalDistanceMeters = 400.0,
                totalDurationSeconds = 80.0,
            )
        }
        LiveDataStore.setLocationFix(30f, true, 43.6532, -79.3832, 10f, 8f, now)
        NavigationController.selectDestination(
            SearchCandidate("Yonge", "Toronto", 43.66, -79.37),
            autoStartNav = true,
            nowMs = now,
        )
        val template = screen.onGetTemplate() as NavigationTemplate
        assertTrue(template.navigationInfo is RoutingInfo)
        assertNotNull(template.destinationTravelEstimate)
        val routing = template.navigationInfo as RoutingInfo
        val step = routing.currentStep
        assertNotNull(step)
        assertEquals("TURN LEFT", step!!.cue.toString())
        assertEquals("Yonge St", step.road.toString())
        assertEquals(2, step.lanes.size)
        assertFalse(step.lanes[0].directions[0].isRecommended)
        assertTrue(step.lanes[1].directions[0].isRecommended)
        assertEquals(LaneDirection.SHAPE_NORMAL_LEFT, step.lanes[1].directions[0].shape)
        assertTrue(template.actionStrip!!.actions.map { it.title?.toString() }.contains("Heading"))
        assertFalse(template.actionStrip!!.actions.map { it.title?.toString() }.contains("Recalculate"))
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
        renderer.draw(canvas, 1280, 720, LiveDataStore.state, 0, TileRenderer.Mode.PHONE)

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

    @Test
    fun carSurfaceIsMapOnlyAndPhoneCommsLockWhileTheHostHasNotParked() {
        DistractionLock.onCarSessionStarted()
        val renderer = TileRenderer()
        renderer.show(TileId.COMMS)
        renderer.openKeyboard(TileRenderer.KeyboardMode.MAP_SEARCH, "king")
        val canvas = Canvas(Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888))
        renderer.draw(canvas, 800, 480, LiveDataStore.state, 0, TileRenderer.Mode.CAR)
        val frame = renderer.lastFrame
        assertNotNull(frame)
        assertTrue(frame!!.mapFillsCanvas)
        assertFalse(frame.drawsCustomRail)
        assertFalse(frame.drawsManeuverBanner)
        assertFalse(frame.drawsKeyboard)
        assertFalse(frame.drawsNotificationList)
        assertFalse(frame.drawsWebStage)
        assertFalse(frame.drawsWallpaper)
        assertEquals(TileId.DRIVE, renderer.selected)
        assertTrue(renderer.scroll(0f, 0f, 12f, -4f, 43.65, -79.38))
        assertFalse(MapCameraStore.followGps)

        renderer.show(TileId.COMMS)
        renderer.draw(canvas, 800, 480, LiveDataStore.state, 0, TileRenderer.Mode.PHONE)
        assertFalse(renderer.lastFrame!!.drawsNotificationList)
        assertFalse(CarPlatformPolicy.allowsWebStage(CarPlatformPolicy.Surface.ANDROID_AUTO))
    }

    @Test
    fun navigateIntentParsesCoordinatesAndSearchUsesTheHostTemplate() {
        val coords = NavigateIntent.parse(Uri.parse("geo:43.6532,-79.3832"))
        assertEquals(43.6532, coords!!.latitude!!, 0.0001)
        assertEquals(-79.3832, coords.longitude!!, 0.0001)
        val query = NavigateIntent.parse(Uri.parse("geo:0,0?q=King%20Street"))
        assertEquals("King Street", query!!.query)
        assertFalse(query.hasCoordinates)
        val assistant = NavigateIntent.parse(Uri.parse("google.navigation:q=King+Street"))
        assertEquals("King Street", assistant!!.query)
        assertFalse(assistant.hasCoordinates)

        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        NavigationController.synchronousForTest = true
        NavigationController.searchTransport = { _, _, _ ->
            listOf(SearchCandidate("King Street", "Toronto", 43.65, -79.38))
        }
        NavigationController.searchAddress("King Street")
        val template = PlaceSearchScreen(carContext).onGetTemplate() as SearchTemplate
        assertFalse(template.isLoading)
        assertEquals(1, template.itemList?.items?.size)
    }

    @Test
    fun offRoutePublishesAMessageAndReplacesHeadingWithRecalculate() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screen = OmarchyScreen(carContext)
        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { oLat, oLon, dLat, dLon ->
            ParsedRoute(
                geometry = listOf(oLat to oLon, dLat to dLon),
                steps = listOf(RouteStep("TURN LEFT", "Yonge St", 800.0, 120.0, dLat, dLon, oLat, oLon)),
                totalDistanceMeters = 800.0,
                totalDurationSeconds = 120.0,
            )
        }
        LiveDataStore.setLocationFix(30f, true, 43.6532, -79.3832, 10f, 5f, now)
        NavigationController.selectDestination(
            SearchCandidate("Yonge", "Toronto", 43.66, -79.37),
            autoStartNav = true,
            nowMs = now,
        )
        NavigationController.onLocationUpdate(43.70, -79.50, 5f, now + 1_000L)
        NavigationController.onLocationUpdate(43.70, -79.50, 5f, now + 2_000L)
        val template = screen.onGetTemplate() as NavigationTemplate
        assertTrue(template.navigationInfo is MessageInfo)
        val titles = template.actionStrip!!.actions.map { it.title?.toString() }
        assertEquals(listOf("Search", "Stop", "Recalculate", "Settings"), titles)
        assertEquals(4, template.mapActionStrip!!.actions.size)
    }

    @Test
    fun phoneGeoIntentWaitsForStartRouteAndRecreateDoesNotQueueAgain() {
        var routes = 0
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { oLat, oLon, dLat, dLon ->
            routes += 1
            ParsedRoute(
                geometry = listOf(oLat to oLon, dLat to dLon),
                steps = listOf(RouteStep("HEAD", "Queen", 400.0, 80.0, dLat, dLon, oLat, oLon)),
                totalDistanceMeters = 400.0,
                totalDurationSeconds = 80.0,
            )
        }
        val now = System.currentTimeMillis()
        LiveDataStore.setLocationFix(10f, true, 43.6532, -79.3832, 0f, 8f, now)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:43.66,-79.37"))
        val controller = Robolectric.buildActivity(SetupActivity::class.java, intent).setup()
        assertEquals(0, routes)
        assertNotEquals(NavState.ACTIVE, NavigationController.navState)
        val start = findButton(controller.get().window.decorView, "START ROUTE")
        assertNotNull(start)
        start!!.performClick()
        assertEquals(1, routes)
        assertEquals(NavState.ACTIVE, NavigationController.navState)
        controller.recreate()
        assertEquals(1, routes)
    }

    @Test
    fun lockedPhoneDoesNotScrollTheNotificationList() {
        DistractionLock.onCarSessionStarted()
        LiveDataStore.setNotificationAccess(true)
        for (i in 1..10) {
            LiveDataStore.addNotification(
                NotificationItem("k$i", "App", "Title $i", "Body $i", 1000L + i),
            )
        }
        val renderer = TileRenderer()
        renderer.show(TileId.COMMS)
        val canvas = Canvas(Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888))
        renderer.draw(canvas, 1280, 720, LiveDataStore.state, 0, TileRenderer.Mode.PHONE)
        assertEquals(0, renderer.notifScrollOffset)
        assertFalse(renderer.scroll(400f, 360f, 0f, -40f))
        assertEquals(0, renderer.notifScrollOffset)
    }

    @Test
    fun phoneKeyboardSpaceBarSpansTheCardOnAShortSurface() {
        NavigationController.synchronousForTest = true
        NavigationController.searchTransport = { _, _, _ ->
            List(3) { SearchCandidate("Place $it", "Toronto", 43.65, -79.38) }
        }
        NavigationController.searchAddress("king")
        val renderer = TileRenderer()
        renderer.show(TileId.DRIVE)
        renderer.openKeyboard(TileRenderer.KeyboardMode.MAP_SEARCH, "king")
        val width = 480
        val height = 360
        val canvas = Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        renderer.draw(canvas, width, height, LiveDataStore.state, 0, TileRenderer.Mode.PHONE)
        var minX = Int.MAX_VALUE
        var maxX = 0
        var sawSpace = false
        var sawKey = false
        for (y in 0 until height step 3) {
            for (x in 0 until width step 4) {
                val hit = renderer.hitTest(x.toFloat() + 1f, y.toFloat() + 1f) ?: continue
                if (hit.target == TileRenderer.Target.KB_SPACE) {
                    sawSpace = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
                if (hit.target == TileRenderer.Target.KB_KEY) sawKey = true
            }
        }
        assertTrue(sawSpace)
        assertTrue(sawKey)
        assertTrue(maxX - minX > (width * 0.65f).toInt())
    }

    private fun findButton(root: View, label: String): Button? {
        if (root is Button && root.text.toString() == label) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findButton(root.getChildAt(i), label)?.let { return it }
            }
        }
        return null
    }
}
