package com.omarchy.auto.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CarUxFeaturesTest {

    private val sampleOsrmJson = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 1450.0,
              "duration": 210.0,
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  [-79.3832, 43.6532],
                  [-79.3800, 43.6550],
                  [-79.3750, 43.6580]
                ]
              },
              "legs": [
                {
                  "steps": [
                    {
                      "distance": 450.0,
                      "duration": 60.0,
                      "name": "Queen St W",
                      "maneuver": {
                        "type": "depart",
                        "modifier": "east",
                        "location": [-79.3832, 43.6532]
                      }
                    },
                    {
                      "distance": 1000.0,
                      "duration": 150.0,
                      "name": "Yonge St",
                      "maneuver": {
                        "type": "turn",
                        "modifier": "left",
                        "location": [-79.3800, 43.6550]
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        LiveDataStore.resetForTest()
        MapCameraStore.resetForTest()
        RailConfigStore.resetForTest()
        StageWebController.resetForTest()
        OmarchyNotificationListener.clearDismissedForTest()
        NavigationController.resetForTest()
    }

    @After
    fun tearDown() {
        NavigationController.resetForTest()
        StageWebController.resetForTest()
    }

    @Test
    fun mapCameraZoomAndPanAndRecenterAndSlowPinchAccumulation() {
        assertEquals(MapCameraStore.DEFAULT_ZOOM, MapCameraStore.zoom, 0.001)
        assertTrue(MapCameraStore.followGps)

        MapCameraStore.zoomIn(1.5)
        assertEquals(MapCameraStore.DEFAULT_ZOOM + 1.5, MapCameraStore.zoom, 0.001)

        MapCameraStore.zoomOut(2.0)
        assertEquals(MapCameraStore.DEFAULT_ZOOM - 0.5, MapCameraStore.zoom, 0.001)

        // Slow pinch with small per-event scale factors (< 0.05 zoom each) accumulates
        val beforeSlowPinch = MapCameraStore.zoom
        repeat(6) {
            MapCameraStore.scaleBy(1.008f)
        }
        assertTrue(MapCameraStore.zoom > beforeSlowPinch + 0.04)

        MapCameraStore.panByPixels(120f, -80f, 43.6532, -79.3832)
        assertFalse(MapCameraStore.followGps)
        assertNotNull(MapCameraStore.panLat)
        assertNotNull(MapCameraStore.panLon)

        val (cx, cy) = MapCameraStore.projectToScreen(
            43.6532,
            -79.3832,
            43.6532,
            -79.3832,
            15.0,
            0f,
            400f,
            240f,
        )
        assertEquals(400f, cx, 0.5f)
        assertEquals(240f, cy, 0.5f)

        MapCameraStore.recenter()
        assertTrue(MapCameraStore.followGps)
    }

    @Test
    fun parseOsrmRouteJsonAndFullStepProgressionToArrival() {
        val parsed = NavigationController.parseOsrmRouteJson(sampleOsrmJson)
        assertNotNull(parsed)
        assertEquals(3, parsed!!.geometry.size)
        assertEquals(2, parsed.steps.size)
        // Depart leg announces the upcoming turn while keeping its own 450 m.
        assertEquals("TURN LEFT", parsed.steps[0].instruction)
        assertEquals(450.0, parsed.steps[0].distanceMeters, 0.001)
        assertEquals("TURN LEFT", parsed.steps[1].instruction)
        assertEquals("Yonge St", parsed.steps[1].street)
        assertEquals(1000.0, parsed.steps[1].distanceMeters, 0.001)
        assertEquals("1.5 km", NavigationController.formatDistance(parsed.totalDistanceMeters))
        assertEquals("3m", NavigationController.formatDuration(parsed.totalDurationSeconds))

        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        NavigationController.routeTransport = { _, _, _, _ -> parsed }
        fix(43.6532, -79.3832, 10f, now)

        val dest = SearchCandidate("Destination", "Toronto", 43.6580, -79.3750)
        NavigationController.selectDestination(dest, autoStartNav = true, nowMs = now)
        assertEquals(NavState.ACTIVE, NavigationController.navState)
        assertTrue(NavigationController.navActive)
        assertEquals(0, NavigationController.currentStepIndex)

        // First GPS update at origin keeps the full 1450 m, not just the 1000 m tail.
        NavigationController.onLocationUpdate(43.6532, -79.3832, 10f, now + 1_000L)
        assertEquals(1450.0, NavigationController.remainingDistanceMeters, 5.0)
        assertEquals(210.0, NavigationController.remainingDurationSeconds, 5.0)
        assertEquals(450.0, NavigationController.nextStepDistanceMeters, 5.0)

        // Halfway along leg 0: remaining decreases smoothly, never below the tail.
        NavigationController.onLocationUpdate(43.6541, -79.3816, 10f, now + 10_000L)
        assertEquals(0, NavigationController.currentStepIndex)
        assertTrue(NavigationController.remainingDistanceMeters in 1100.0..1350.0)

        // Within 35 m of the turn -> step 1 with ~1000 m left.
        NavigationController.onLocationUpdate(43.65495, -79.38005, 10f, now + 20_000L)
        assertEquals(1, NavigationController.currentStepIndex)
        assertTrue(NavigationController.remainingDistanceMeters in 950.0..1050.0)

        // Arrival.
        NavigationController.onLocationUpdate(43.6580, -79.3750, 8f, now + 90_000L)
        assertEquals(NavState.IDLE, NavigationController.navState)
        assertFalse(NavigationController.navActive)
        assertEquals("ARRIVED AT DESTINATION", NavigationController.statusMessage)
    }

    @Test
    fun navigationRequiresRecentAccurateGpsAndNeverUsesCameraOrigin() {
        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        val origins = mutableListOf<Pair<Double, Double>>()
        val parsed = NavigationController.parseOsrmRouteJson(sampleOsrmJson)
        NavigationController.routeTransport = { lat, lon, _, _ ->
            origins += lat to lon
            parsed
        }
        MapCameraStore.focusOn(51.5074, -0.1278, 12.0)
        val dest = SearchCandidate("Union Station", "Toronto", 43.6453, -79.3806)

        // Missing fix.
        NavigationController.selectDestination(dest, autoStartNav = true, nowMs = now)
        assertEquals(NavState.LOCATION_REQUIRED, NavigationController.navState)
        assertFalse(NavigationController.navActive)
        assertTrue(NavigationController.routePoints.isEmpty())

        // Stale fix.
        fix(43.6532, -79.3832, 10f, now - 60_000L)
        NavigationController.retryRoute(now)
        assertEquals(NavState.LOCATION_REQUIRED, NavigationController.navState)

        // Inaccurate fix.
        fix(43.6532, -79.3832, 250f, now)
        NavigationController.retryRoute(now)
        assertEquals(NavState.LOCATION_REQUIRED, NavigationController.navState)
        assertTrue(origins.isEmpty())

        // Valid fix routes from GPS, never from the panned camera.
        fix(43.6532, -79.3832, 12f, now)
        NavigationController.retryRoute(now)
        assertEquals(NavState.ACTIVE, NavigationController.navState)
        assertEquals(listOf(43.6532 to -79.3832), origins)
    }

    @Test
    fun routingFailureExposesRouteErrorAndRetry() {
        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        val parsed = NavigationController.parseOsrmRouteJson(sampleOsrmJson)
        var fail = true
        NavigationController.routeTransport = { _, _, _, _ ->
            if (fail) throw java.io.IOException("OSRM timeout") else parsed
        }
        fix(43.6532, -79.3832, 10f, now)
        val dest = SearchCandidate("CN Tower", "Toronto", 43.6426, -79.3871)

        NavigationController.selectDestination(dest, autoStartNav = true, nowMs = now)
        assertEquals(NavState.ROUTE_ERROR, NavigationController.navState)
        assertFalse(NavigationController.navActive)
        assertTrue(NavigationController.routePoints.isEmpty())
        assertEquals(0.0, NavigationController.remainingDistanceMeters, 0.001)
        assertEquals(0.0, NavigationController.remainingDurationSeconds, 0.001)

        fail = false
        NavigationController.retryRoute(now)
        assertEquals(NavState.ACTIVE, NavigationController.navState)
        assertEquals(3, NavigationController.routePoints.size)
    }

    @Test
    fun staleRouteAndSearchResponsesAreDiscarded() {
        val now = System.currentTimeMillis()
        NavigationController.synchronousForTest = true
        val parsed = NavigationController.parseOsrmRouteJson(sampleOsrmJson)
        fix(43.6532, -79.3832, 10f, now)
        val destA = SearchCandidate("Dest A", "", 43.6580, -79.3750)
        val destB = SearchCandidate("Dest B", "", 43.6620, -79.3900)

        NavigationController.routeTransport = { _, _, _, _ -> parsed }
        NavigationController.selectDestination(destA, autoStartNav = true, nowMs = now)
        assertEquals(3, NavigationController.routePoints.size)

        // Stop while the replacement request is in flight.
        NavigationController.routeTransport = { _, _, _, _ ->
            assertTrue(NavigationController.routePoints.isEmpty())
            NavigationController.stopNavigation()
            parsed
        }
        NavigationController.selectDestination(destB, autoStartNav = true, nowMs = now)
        assertEquals(NavState.IDLE, NavigationController.navState)
        assertTrue(NavigationController.routePoints.isEmpty())

        NavigationController.searchTransport = { _, _, _ ->
            NavigationController.clearQuery()
            listOf(destA)
        }
        NavigationController.searchAddress("Queen St")
        assertTrue(NavigationController.searchResults.isEmpty())
    }

    private fun fix(lat: Double, lon: Double, accuracy: Float, at: Long) {
        LiveDataStore.setLocationFix(
            speedKph = 20f,
            gpsActive = true,
            latitude = lat,
            longitude = lon,
            bearing = 0f,
            accuracyM = accuracy,
            updatedAtMs = at,
        )
    }
    @Test
    fun dismissedNotificationsAndClearAllCoverBeyondDisplayCap() {
        val key = "pkg|1|tag"
        OmarchyNotificationListener.markDismissed(key, 5_000L)
        assertTrue(OmarchyNotificationListener.isDismissed(key, 4_999L))
        assertTrue(OmarchyNotificationListener.isDismissed(key, 5_000L))
        assertFalse(OmarchyNotificationListener.isDismissed(key, 5_001L))

        // Populate 35 active notifications (LiveDataStore caps displayed list at 30)
        for (i in 1..35) {
            val k = "pkg|$i|tag"
            val t = 10_000L + i
            OmarchyNotificationListener.recordActiveForTest(k, t)
            LiveDataStore.addNotification(
                NotificationItem(
                    key = k,
                    app = "App",
                    title = "Title $i",
                    text = "Body $i",
                    postedAt = t,
                ),
            )
        }
        assertEquals(LiveDataStore.MAX_NOTIFICATIONS, LiveDataStore.state.notifications.size)

        OmarchyNotificationListener.dismissDisplayed(null)
        assertTrue(LiveDataStore.state.notifications.isEmpty())
        for (i in 1..35) {
            assertTrue(
                "Expected notification $i to be dismissed",
                OmarchyNotificationListener.isDismissed("pkg|$i|tag", 10_000L + i),
            )
        }
    }

    @Test
    fun mediaSessionsClearedWhenAccessLost() {
        LiveDataStore.setMediaSessions(
            listOf(
                MediaSessionItem(
                    packageName = "com.spotify.music",
                    label = "SPOTIFY",
                    title = "Song",
                    playing = true,
                ),
            ),
        )
        assertEquals(1, LiveDataStore.state.mediaSessions.size)

        LiveDataStore.setMedia(null, access = false)
        assertTrue(LiveDataStore.state.mediaSessions.isEmpty())
        assertFalse(LiveDataStore.state.mediaAccess)
    }

    @Test
    fun railConfigKeepsHomeEnabledAndExcludesHiddenVehicleFlag() {
        val enabled = RailConfigStore.enabledItems()
        assertTrue(RailItemId.DRIVE in enabled)
        assertTrue(RailItemId.MEDIA in enabled)
        assertTrue(RailItemId.COMMS in enabled)
        assertTrue(RailItemId.HOME in enabled)
        assertFalse(RailItemId.VEHICLE in enabled)
        assertFalse(RailItemId.VEHICLE in RailConfigStore.allSelectable())
        assertFalse(RailItemId.VEHICLE in RailConfigStore.configurableItems())

        // Even if persisted preferences contain "vehicle", loading clamps it out
        RailConfigStore.loadKeysForTest(setOf("drive", "vehicle", "home"))
        assertFalse(RailConfigStore.isEnabled(RailItemId.VEHICLE))
        assertFalse(RailItemId.VEHICLE in RailConfigStore.enabledItems())
        assertTrue(RailConfigStore.isEnabled(RailItemId.HOME))
    }

    @Test
    fun stageWebControllerPauseResumeAndCloseLifecycle() {
        StageWebController.open(StageWebApp.YOUTUBE)
        assertTrue(StageWebController.active)
        assertFalse(StageWebController.paused)
        assertEquals(StageWebApp.YOUTUBE, StageWebController.activeApp)

        StageWebController.pause()
        assertFalse(StageWebController.active)
        assertTrue(StageWebController.paused)

        StageWebController.resume()
        assertTrue(StageWebController.active)
        assertFalse(StageWebController.paused)

        StageWebController.close()
        assertFalse(StageWebController.active)
        assertTrue(StageWebController.paused)
        assertNull(StageWebController.bitmap)
    }
}
