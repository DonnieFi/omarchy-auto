package com.omarchy.auto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CarUxFeaturesTest {

    @Before
    fun setUp() {
        MapCameraStore.resetForTest()
        RailConfigStore.resetForTest()
        OmarchyNotificationListener.clearDismissedForTest()
        NavigationController.stopNavigation()
    }

    @Test
    fun mapCameraZoomAndPanAndRecenter() {
        assertEquals(MapCameraStore.DEFAULT_ZOOM, MapCameraStore.zoom, 0.001)
        assertTrue(MapCameraStore.followGps)

        MapCameraStore.zoomIn(1.5)
        assertEquals(MapCameraStore.DEFAULT_ZOOM + 1.5, MapCameraStore.zoom, 0.001)

        MapCameraStore.zoomOut(2.0)
        assertEquals(MapCameraStore.DEFAULT_ZOOM - 0.5, MapCameraStore.zoom, 0.001)

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
    fun parseOsrmRouteJsonAndStepProgression() {
        val sampleJson = """
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

        val parsed = NavigationController.parseOsrmRouteJson(sampleJson)
        assertNotNull(parsed)
        assertTrue(parsed!!.geometry.isNotEmpty())
        assertEquals(1, parsed.steps.size)
        assertEquals("TURN LEFT", parsed.steps[0].instruction)
        assertEquals("Yonge St", parsed.steps[0].street)
        assertEquals("1.5 km", NavigationController.formatDistance(parsed.totalDistanceMeters))
        assertEquals("3m", NavigationController.formatDuration(parsed.totalDurationSeconds))
    }

    @Test
    fun dismissedNotificationsStayFilteredUntilNewerTimestamp() {
        val key = "pkg|1|tag"
        OmarchyNotificationListener.markDismissed(key, 5_000L)
        assertTrue(OmarchyNotificationListener.isDismissed(key, 4_999L))
        assertTrue(OmarchyNotificationListener.isDismissed(key, 5_000L))
        assertFalse(OmarchyNotificationListener.isDismissed(key, 5_001L))
    }

    @Test
    fun railConfigKeepsHomeEnabled() {
        val enabled = RailConfigStore.enabledItems()
        assertTrue(RailItemId.DRIVE in enabled)
        assertTrue(RailItemId.MEDIA in enabled)
        assertTrue(RailItemId.COMMS in enabled)
        assertTrue(RailItemId.HOME in enabled)
        assertTrue(RailConfigStore.isEnabled(RailItemId.HOME))
        assertEquals("YOUTUBE", StageWebApp.YOUTUBE.title)
        assertEquals("CHATGPT", StageWebApp.CHATGPT.title)
        assertEquals("GROK", StageWebApp.GROK.title)
    }
}
