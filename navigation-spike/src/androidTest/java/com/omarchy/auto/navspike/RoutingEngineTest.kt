package com.omarchy.auto.navspike

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stadiamaps.ferrostar.core.*
import com.stadiamaps.ferrostar.core.http.*
import com.stadiamaps.ferrostar.core.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.ferrostar.*
import java.util.concurrent.atomic.AtomicInteger

/** Real native engine + recorded Valhalla response; no key or hosted request required. */
@RunWith(AndroidJUnit4::class)
class RoutingEngineTest {
    private fun fixture(): ByteArray = InstrumentationRegistry.getInstrumentation().context.assets
        .open("valhalla-route.json").use { it.readBytes() }
    private fun config() = NavigationControllerConfig(
        WaypointAdvanceMode.WaypointWithinRange(25.0),
        stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
        stepAdvanceDistanceToEndOfStep(10u, 32u),
        RouteDeviationTracking.StaticThreshold(15u, 50.0), CourseFiltering.SNAP_TO_ROUTE)
    @Test fun nativeGuidanceReachesArrivalAndTriggersSpeech() = runBlocking {
        val speechCount = AtomicInteger()
        val provider = SimulatedLocationProvider(warpFactor = 1000u)
        val bytes = fixture()
        val client = object : HttpClientProvider {
            override suspend fun call(request: RouteRequest): IResponse = object : IResponse {
                override val isSuccessful = true
                override val code = 200
                override fun bodyBytes() = bytes
            }
        }
        val engine = FerrostarCore(WellKnownRouteProvider.Valhalla("https://example.invalid/route", "auto"),
            client, provider, config())
        engine.spokenInstructionObserver = object : SpokenInstructionObserver {
            override val muteState = MutableStateFlow(false)
            override fun onSpokenInstructionTrigger(spokenInstruction: SpokenInstruction) { speechCount.incrementAndGet() }
            override fun stopAndClearQueue() { }
            override fun setMuted(isMuted: Boolean) { muteState.value = isMuted }
        }
        try {
            val start = android.location.Location("test").apply { latitude = 38.0; longitude = -122.0; accuracy = 5f }
            val route = engine.getRoutes(start.toUserLocation(), listOf(Waypoint(GeographicCoordinate(38.1,-122.1),WaypointKind.BREAK))).first()
            assertTrue(route.geometry.size > 2)
            assertTrue(route.steps.size > 1)
            provider.setRoute(route)
            engine.startNavigation(route)
            assertTrue(engine.state.value.isNavigating())
            withTimeout(60_000) { engine.state.first { it.tripState is TripState.Complete } }
            assertTrue("No spoken instruction triggers", speechCount.get() > 0)
        } finally { engine.stopNavigation() }
        assertFalse(engine.state.value.isNavigating())
    }
    @Test fun deviationRequestsReplacementRoute() = runBlocking {
        val calls = AtomicInteger()
        val replacements = AtomicInteger()
        val bytes = fixture()
        val client = object : HttpClientProvider {
            override suspend fun call(request: RouteRequest): IResponse {
                calls.incrementAndGet()
                return object : IResponse {
                    override val isSuccessful = true
                    override val code = 200
                    override fun bodyBytes() = bytes
                }
            }
        }
        val provider = SimulatedLocationProvider(warpFactor = 100u)
        val confidentFixes = object : NavigationLocationProviding {
            override suspend fun lastLocation() = provider.lastLocation()
            override fun locationUpdates(intervalMillis: Long) = provider.locationUpdates(intervalMillis)
                .map { android.location.Location(it).apply { accuracy = 5f } }
        }
        val engine = FerrostarCore(WellKnownRouteProvider.Valhalla("https://example.invalid/route", "auto"),
            client, confidentFixes, config())
        engine.alternativeRouteProcessor = AlternativeRouteProcessor { core, routes ->
            core.replaceRoute(routes.first())
            replacements.incrementAndGet()
        }
        try {
            val start = android.location.Location("test").toUserLocation()
            val route = engine.getRoutes(start, listOf(Waypoint(GeographicCoordinate(1.0,1.0),WaypointKind.BREAK))).first()
            provider.setRoute(route, LocationBias.Right(120.0))
            engine.startNavigation(route)
            withTimeout(20_000) { while (replacements.get() == 0) delay(50) }
            assertTrue("Deviation did not request a new route", calls.get() >= 2)
            assertTrue(engine.state.value.isNavigating())
        } finally { engine.stopNavigation() }
    }
    @Test fun unauthorizedProviderFailsWithoutStartingGuidance() = runBlocking {
        val client = object : HttpClientProvider {
            override suspend fun call(request: RouteRequest): IResponse = object : IResponse {
                override val isSuccessful = false
                override val code = 401
                override fun bodyBytes() = ByteArray(0)
            }
        }
        val engine = FerrostarCore(WellKnownRouteProvider.Valhalla("https://example.invalid/route", "auto"),
            client, SimulatedLocationProvider(), config())
        val start = android.location.Location("test").toUserLocation()
        try {
            engine.getRoutes(start, listOf(Waypoint(GeographicCoordinate(1.0,1.0),WaypointKind.BREAK)))
            fail("Expected authentication failure")
        } catch (_: InvalidStatusCodeException) { }
        assertFalse(engine.state.value.isNavigating())
    }
}
