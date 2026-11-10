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
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.SearchTemplate
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import androidx.lifecycle.Lifecycle
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

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
            listOf("Search", "Menu", "Heading"),
            template.actionStrip!!.actions.map { it.title?.toString() },
        )
        // Search must stay tappable while driving. A parked-only listener is
        // disabled by the host, which previously trapped the driver on the map.
        val searchAction = template.actionStrip!!.actions.first {
            it.title?.toString() == "Search"
        }
        assertNotNull(searchAction.onClickDelegate)
        assertFalse(searchAction.onClickDelegate!!.isParkedOnly)
        val menuAction = template.actionStrip!!.actions.first {
            it.title?.toString() == "Menu"
        }
        assertNotNull(menuAction.onClickDelegate)
        assertFalse(menuAction.onClickDelegate!!.isParkedOnly)
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
        assertFalse(template.actionStrip!!.actions.map { it.title?.toString() }.contains("Reroute"))
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
    fun carSurfaceDrawsTheShellAndPhoneCommsStayLockedWhileTheHostHasNotParked() {
        DistractionLock.onCarSessionStarted()
        val renderer = TileRenderer()
        renderer.show(TileId.COMMS)
        assertFalse(renderer.openKeyboard(TileRenderer.KeyboardMode.MAP_SEARCH, "king"))
        val canvas = Canvas(Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888))
        renderer.draw(canvas, 800, 480, LiveDataStore.state, 0, TileRenderer.Mode.CAR)
        val frame = renderer.lastFrame
        assertNotNull(frame)
        assertEquals(TileRenderer.Mode.CAR, frame!!.mode)
        assertFalse(frame.mapFillsCanvas)
        assertTrue(frame.drawsCustomRail)
        assertTrue(frame.drawsWallpaper)
        assertFalse(frame.drawsKeyboard)
        assertFalse(frame.drawsNotificationList)
        assertFalse(frame.drawsWebStage)
        assertEquals(TileId.COMMS, renderer.selected)
        val hits = hitTargets(renderer, 800, 480)
        assertTrue(hits.contains(TileRenderer.Target.THEME))
        assertTrue(hits.contains(TileRenderer.Target.TILE_DRIVE))
        assertTrue(hits.contains(TileRenderer.Target.TILE_MEDIA))
        assertTrue(hits.contains(TileRenderer.Target.TILE_COMMS))
        assertTrue(hits.contains(TileRenderer.Target.TILE_HOME))

        renderer.show(TileId.DRIVE)
        renderer.draw(canvas, 800, 480, LiveDataStore.state, 0, TileRenderer.Mode.CAR)
        assertTrue(renderer.scroll(0f, 0f, 12f, -4f, 43.65, -79.38))
        assertFalse(MapCameraStore.followGps)
        assertTrue(hitTargets(renderer, 800, 480).contains(TileRenderer.Target.MAP_SEARCH_OPEN))

        renderer.show(TileId.COMMS)
        renderer.draw(canvas, 800, 480, LiveDataStore.state, 0, TileRenderer.Mode.PHONE)
        assertFalse(renderer.lastFrame!!.drawsNotificationList)
        assertFalse(CarPlatformPolicy.allowsWebStage(CarPlatformPolicy.Surface.ANDROID_AUTO))
        assertFalse(CarPlatformPolicy.mapOnly(CarPlatformPolicy.Surface.ANDROID_AUTO))
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
    fun searchAddressPreservesTrailingSpaceAndCarGuidanceShowsWhileActive() {
        NavigationController.synchronousForTest = true
        NavigationController.searchTransport = { _, _, _ ->
            listOf(SearchCandidate("King Street", "Toronto", 43.65, -79.38))
        }
        NavigationController.searchAddress("king ")
        assertEquals("king ", NavigationController.searchQuery)
        assertEquals(
            listOf("King Street"),
            NavigationController.searchResults.map { it.title },
        )
    }

    @Test
    fun searchTemplateKeepsTheLastNonBlankVoiceTranscript() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        NavigationController.setQuery("")
        val screen = PlaceSearchScreen(carContext)

        val first = screen.onGetTemplate() as SearchTemplate
        assertNull(first.initialSearchText)
        assertTrue(first.isShowKeyboardByDefault)

        val callback = first.searchCallbackDelegate
        val done = object : androidx.car.app.OnDoneCallback {}
        callback.sendSearchTextChanged("123 Main Street", done)
        assertEquals("123 Main Street", NavigationController.searchQuery)

        // Hosts clear the box around a voice session. A blank update must not
        // erase the transcript or cancel the pending/running search.
        callback.sendSearchTextChanged("", done)
        assertEquals("123 Main Street", NavigationController.searchQuery)

        // The refresh that delivers results restores the transcript.
        val second = screen.onGetTemplate() as SearchTemplate
        assertEquals("123 Main Street", second.initialSearchText)
    }

    @Test
    fun blankHostUpdateDoesNotCancelTheTranscriptSearch() {
        NavigationController.synchronousForTest = true
        NavigationController.searchTransport = { _, _, _ ->
            listOf(SearchCandidate("123 Main Street", "Toronto", 43.65, -79.38))
        }
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        NavigationController.setQuery("")
        val screen = PlaceSearchScreen(carContext)
        val template = screen.onGetTemplate() as SearchTemplate
        val callback = template.searchCallbackDelegate
        val done = object : androidx.car.app.OnDoneCallback {}
        callback.sendSearchTextChanged("123 Main Street", done)
        // The host clears the box and submits an empty term after the voice
        // session. The pending transcript search must still run.
        callback.sendSearchSubmitted("", done)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(800, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(
            listOf("123 Main Street"),
            NavigationController.searchResults.map { it.title },
        )
    }

    @Test
    fun prefilledSearchKeepsTheQueryInTheFirstTemplate() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        NavigationController.setQuery("Queen Street")
        val template = PlaceSearchScreen(carContext).onGetTemplate() as SearchTemplate
        assertEquals("Queen Street", template.initialSearchText)
        assertFalse(template.isShowKeyboardByDefault)
    }

    @Test
    fun carSurfaceDrawsAGuidanceCardWhileNavigating() {
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { oLat, oLon, dLat, dLon ->
            ParsedRoute(
                geometry = listOf(oLat to oLon, dLat to dLon),
                steps = listOf(
                    RouteStep("TURN LEFT", "Yonge St", 400.0, 80.0, dLat, dLon, oLat, oLon),
                ),
                totalDistanceMeters = 400.0,
                totalDurationSeconds = 80.0,
            )
        }
        val now = System.currentTimeMillis()
        LiveDataStore.setLocationFix(30f, true, 43.6532, -79.3832, 0f, 5f, now)
        NavigationController.selectDestination(
            SearchCandidate("Yonge", "Toronto", 43.66, -79.37),
            autoStartNav = true,
            nowMs = now,
        )
        val renderer = TileRenderer()
        renderer.show(TileId.DRIVE)
        val canvas = Canvas(Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888))
        renderer.draw(canvas, 1280, 720, LiveDataStore.state, 0, TileRenderer.Mode.CAR)
        val frame = renderer.lastFrame
        assertNotNull(frame)
        assertFalse(frame!!.mapFillsCanvas)
        assertTrue(frame.drawsCustomRail)
        assertTrue(frame.drawsManeuverBanner)
        assertFalse(frame.drawsKeyboard)
        assertTrue(hitTargets(renderer, 1280, 720).contains(TileRenderer.Target.MAP_SEARCH_OPEN))
    }

    @Test
    fun coldIdleStartStaysOnTheMapTemplateUntilMenuOpensTheHub() {
        val screen = startedScreen()
        assertEquals(NavState.IDLE, NavigationController.navState)
        assertTrue(screen.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val template = screen.onGetTemplate()
        assertTrue(template is NavigationTemplate)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val manager = screen.screenManager
        assertEquals(1, manager.stackSize)
        assertTrue(manager.top is OmarchyScreen)

        val menu = (template as NavigationTemplate).actionStrip!!.actions.first {
            it.title?.toString() == "Menu"
        }
        menu.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, manager.stackSize)
        assertTrue(manager.top is CarHomeScreen)
    }

    @Test
    fun navigateIntentLandsOnTheMapAndDoesNotOpenTheHub() {
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { oLat, oLon, dLat, dLon ->
            ParsedRoute(
                geometry = listOf(oLat to oLon, dLat to dLon),
                steps = listOf(RouteStep("HEAD", "King", 400.0, 80.0, dLat, dLon, oLat, oLon)),
                totalDistanceMeters = 400.0,
                totalDurationSeconds = 80.0,
            )
        }
        val now = System.currentTimeMillis()
        LiveDataStore.setLocationFix(20f, true, 43.6532, -79.3832, 0f, 8f, now)
        val screen = startedScreen()
        screen.offerNavigateIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:43.66,-79.37")))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(NavState.ACTIVE, NavigationController.navState)
        assertTrue(screen.onGetTemplate() is NavigationTemplate)
        assertTrue(screen.screenManager.top is OmarchyScreen)
        assertFalse(screen.screenManager.top is CarHomeScreen)

        val queryScreen = startedScreen()
        queryScreen.offerNavigateIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=King%20Street")),
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(queryScreen.screenManager.top is PlaceSearchScreen)
        assertFalse(queryScreen.screenManager.top is CarHomeScreen)
    }

    @Test
    fun carSettingsThemeRowIsClickable() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screen = CarSettingsScreen(carContext)
        val template = screen.onGetTemplate() as ListTemplate
        val themeRow = template.singleList!!.items
            .filterIsInstance<androidx.car.app.model.Row>()
            .first { it.title?.toString() == "Theme" }
        val before = com.omarchy.auto.core.ThemeStore.index
        val done = object : androidx.car.app.OnDoneCallback {}
        themeRow.onClickDelegate!!.sendClick(done)
        assertNotEquals(before, com.omarchy.auto.core.ThemeStore.index)
        com.omarchy.auto.core.ThemeStore.select(before)
    }

    @Test
    fun carSettingsRowsAreTappableAndNoneAreParkedOnly() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = CarSettingsScreen(carContext).onGetTemplate() as ListTemplate
        assertEquals("Settings", template.title.toString())
        val rows = template.singleList!!.items.filterIsInstance<androidx.car.app.model.Row>()
        val titles = rows.map { it.title?.toString() }
        assertTrue(titles.contains("Theme"))
        assertTrue(titles.contains("Map orientation"))
        assertTrue(titles.contains("Phone setup"))
        assertTrue(titles.contains("About"))
        rows.forEach { row ->
            row.onClickDelegate?.let { assertFalse(it.isParkedOnly) }
        }
    }

    @Test
    fun geoIntentStillReturnsTheMapTemplate() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screen = OmarchyScreen(carContext)
        screen.offerNavigateIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:43.6532,-79.3832")),
        )
        assertTrue(screen.onGetTemplate() is NavigationTemplate)
    }

    @Test
    fun carHomeIsTheScreenHubWithSafeNotificationTitles() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = CarHomeScreen(carContext).onGetTemplate() as ListTemplate
        assertEquals("OMARCHY", template.title.toString())
        val rows = template.singleList!!.items.filterIsInstance<androidx.car.app.model.Row>()
        val titles = rows.map { it.title?.toString() }
        assertEquals(
            listOf("Map", "Search", "Media", "Notifications", "Settings"),
            titles,
        )
        val mapRow = rows.first { it.title?.toString() == "Map" }
        assertNotNull(mapRow.onClickDelegate)
        assertFalse(mapRow.onClickDelegate!!.isParkedOnly)
        val notifications = rows.first { it.title?.toString() == "Notifications" }
        assertNotNull(notifications.onClickDelegate)
        assertFalse(notifications.onClickDelegate!!.isParkedOnly)
        val media = rows.first { it.title?.toString() == "Media" }
        assertFalse(media.onClickDelegate!!.isParkedOnly)
    }

    @Test
    fun carMediaTemplateOffersSafeTransportControls() {
        LiveDataStore.setMediaAccess(true)
        LiveDataStore.setMedia(
            com.omarchy.auto.core.MediaSnapshot(
                title = "Nightcall",
                artist = "Kavinsky",
                album = "OutRun",
                playing = true,
                source = "Spotify",
            ),
        )
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = CarMediaScreen(carContext).onGetTemplate() as ListTemplate
        assertEquals("Media", template.title.toString())
        val titles = template.singleList!!.items
            .filterIsInstance<androidx.car.app.model.Row>()
            .map { it.title?.toString() }
        assertTrue(titles.contains("Pause"))
        assertTrue(titles.contains("Next track"))
        assertTrue(titles.contains("Previous track"))
        LiveDataStore.resetForTest()
    }

    @Test
    fun carCommsTemplateShowsTitlesButNotBodies() {
        LiveDataStore.setNotificationAccess(true)
        LiveDataStore.addNotification(
            NotificationItem("k1", "Messages", "Alex", "Secret body text", 1_000L),
        )
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = CarCommsScreen(carContext).onGetTemplate() as ListTemplate
        assertEquals("Notifications", template.title.toString())
        val rows = template.singleList!!.items.filterIsInstance<androidx.car.app.model.Row>()
        val titles = rows.map { it.title?.toString() }
        assertTrue(titles.contains("Alex"))
        assertFalse(rows.any { row -> row.texts.any { it.toString().contains("Secret body") } })
        LiveDataStore.resetForTest()
    }

    @Test
    fun offRoutePublishesAMessageAndReplacesHeadingWithReroute() {
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
        assertEquals(listOf("Search", "Menu", "Reroute", "End"), titles)
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

    private fun startedScreen(): OmarchyScreen {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screen = OmarchyScreen(carContext)
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        assertTrue(screen.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        assertTrue(screen.screenManager.top is OmarchyScreen)
        return screen
    }

    private fun hitTargets(renderer: TileRenderer, width: Int, height: Int): Set<TileRenderer.Target> {
        val found = mutableSetOf<TileRenderer.Target>()
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                renderer.hitTest(x + 1f, y + 1f)?.let { found += it.target }
                x += 6
            }
            y += 6
        }
        return found
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
