package com.omarchy.auto.navspike

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.*
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.*
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.time.Duration
import uniffi.ferrostar.*

/** Process-scoped session: car surface reconnects attach to the existing navigation state. */
object SpikeRuntime {
    const val STYLE = "https://tiles.openfreemap.org/styles/dark"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val status = MutableStateFlow("Enter a Stadia API key and destination on the phone.")
    var core: FerrostarCore? = null
        private set
    var viewModel: DefaultNavigationViewModel? = null
        private set
    private val models = ViewModelStore()
    private var activeRoute: Route? = null
    private var provider: NavigationLocationProvider? = null
    private var tts: AndroidTtsObserver? = null
    private var request: Job? = null
    private var stateJob: Job? = null
    var simulated = false
        private set

    fun start(context: Context, key: String, destination: RoutePoint, simulate: Boolean,
              origin: RoutePoint, replayClient: HttpClientProvider? = null,
              onStarted: () -> Unit) {
        if (request?.isActive == true) return
        require(replayClient != null || key.isNotBlank()) { "Enter your Stadia Maps API key." }
        stop()
        simulated = simulate
        val app = context.applicationContext
        status.value = if (simulate) "Calculating simulated route…" else "Waiting for a fresh GPS fix…"
        request = scope.launch {
            try {
                val initial = Location("spike-simulation").apply {
                    latitude = origin.latitude; longitude = origin.longitude
                    accuracy = 5f; time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                val live = AndroidLocationProvider(app)
                val start = if (simulate) initial else withTimeout(30_000) {
                    live.locationUpdates(1000).first {
                        val age = (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
                        age in 0..30_000 && it.hasAccuracy() && it.accuracy <= 100f
                    }
                }
                val locations = NavigationLocationProvider(live,
                    SimulatedLocationProvider(warpFactor = if (replayClient != null) 12u else 2u, initialLocation = start))
                provider = locations
                val speech = AndroidTtsObserver(app).also { it.start() }
                tts = speech
                val client = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build()
                // Never log this URL: the key is entered at runtime and is not packaged in the APK.
                val endpoint = "https://api.stadiamaps.com/route/v1?api_key=" +
                    java.net.URLEncoder.encode(key.trim(), "UTF-8")
                val engine = FerrostarCore(
                    WellKnownRouteProvider.Valhalla(endpoint, "auto")
                        .withJsonOptions(mapOf("units" to "kilometers", "language" to "en-CA")),
                    httpClient = replayClient ?: client.toOkHttpClientProvider(),
                    locationProvider = object : NavigationLocationProviding {
                        override suspend fun lastLocation(): Location? = locations.lastLocation()
                        override fun locationUpdates(intervalMillis: Long): Flow<Location> =
                            locations.locationUpdates(intervalMillis).map { fix ->
                                // Biased simulation otherwise reports the bias as GPS uncertainty,
                                // which correctly suppresses deviation detection. This test control
                                // deliberately models a confident fix on the wrong road.
                                if (simulate) Location(fix).apply { accuracy = 5f } else fix
                            }
                    },
                    foregroundServiceManager = FerrostarForegroundServiceManager(app,
                        DefaultForegroundNotificationBuilder(app)),
                    navigationControllerConfig = NavigationControllerConfig(
                        WaypointAdvanceMode.WaypointWithinRange(25.0),
                        stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                        stepAdvanceDistanceToEndOfStep(10u, 32u),
                        RouteDeviationTracking.StaticThreshold(15u, 50.0),
                        CourseFiltering.SNAP_TO_ROUTE),
                )
                engine.spokenInstructionObserver = speech
                core = engine
                viewModel = ViewModelProvider.create(models, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        DefaultNavigationViewModel(engine) as T
                })[DefaultNavigationViewModel::class.java]
                engine.alternativeRouteProcessor = AlternativeRouteProcessor { navigation, routes ->
                    routes.firstOrNull()?.let { replacement ->
                        activeRoute = replacement
                        if (simulated) locations.enableSimulationOn(replacement)
                        navigation.replaceRoute(replacement)
                    }
                }
                viewModel?.setDestination("${destination.latitude}, ${destination.longitude}")
                status.value = "Calculating route…"
                val route = withContext(Dispatchers.IO) {
                    engine.getRoutes(start.toUserLocation(), listOf(Waypoint(
                        GeographicCoordinate(destination.latitude, destination.longitude), WaypointKind.BREAK)))
                        .firstOrNull() ?: error("No route")
                }
                activeRoute = route
                if (simulate) locations.enableSimulationOn(route)
                engine.startNavigation(route)
                stateJob = scope.launch {
                    engine.state.collect { state ->
                        status.value = when {
                            state.tripState is TripState.Complete -> "Arrived."
                            state.isCalculatingNewRoute -> "Recalculating route…"
                            state.isNavigating() -> if (replayClient != null) "RECORDED REPLAY · 12× · no live routing" else if (simulate) "SIMULATION · guidance active" else "GPS · guidance active"
                            else -> "Navigation stopped."
                        }
                        if (state.tripState is TripState.Complete) {
                            engine.stopNavigation()
                            speech.shutdown()
                            status.value = "Arrived."
                            cancel()
                        }
                    }
                }
                onStarted()
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) {
                    status.value = "No fresh GPS fix. Enable precise location and try outdoors, or use simulation."
                } else throw error
            } catch (_: Exception) {
                // Avoid exposing provider URLs, keys, or raw responses in UI/logs.
                core?.stopNavigation()
                tts?.shutdown()
                status.value = "Routing failed. Check your key, connection, credit allowance, and road coordinates."
            }
        }
    }

    fun simulateWrongTurn() {
        val route = activeRoute ?: return
        if (!simulated || core?.state?.value?.isNavigating() != true) return
        provider?.enableSimulationOn(route, LocationBias.Right(120.0))
        status.value = "SIMULATION · offset 120 m to test rerouting…"
    }

    fun stop() {
        request?.cancel(); request = null
        stateJob?.cancel(); stateJob = null
        core?.stopNavigation()
        provider?.disableSimulation()
        activeRoute = null
        models.clear()
        viewModel = null
        core = null
        tts?.shutdown(); tts = null
        status.value = "Navigation stopped."
    }
}
