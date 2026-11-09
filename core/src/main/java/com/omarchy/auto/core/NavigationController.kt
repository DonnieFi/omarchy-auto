package com.omarchy.auto.core

import com.omarchy.auto.core.diag.OaLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class NavState {
    IDLE,
    LOCATION_REQUIRED,
    ROUTING,
    ROUTE_ERROR,
    PREVIEW,
    ACTIVE,
}

data class SearchCandidate(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
) {
    val name: String get() = title
    val displayName: String get() = if (subtitle.isNotBlank()) "$title, $subtitle" else title
}

/** One arrow in a turn-lane diagram. Indications use OSRM names such as "left". */
data class RouteLane(
    val indications: List<String>,
    val recommended: Boolean,
)

data class RouteStep(
    val instruction: String,
    val street: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val latitude: Double,
    val longitude: Double,
    val startLatitude: Double = latitude,
    val startLongitude: Double = longitude,
    val lanes: List<RouteLane> = emptyList(),
)

data class ParsedRoute(
    val geometry: List<Pair<Double, Double>>,
    val steps: List<RouteStep>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
)

/**
 * Keyless address geocoding (Photon / Nominatim) and turn-by-turn routing
 * (OSRM) for the Drive stage.
 *
 * Safety invariants:
 * 1. Never starts navigation from a map camera center or hard-coded fallback;
 *    requires a recent, accurate GPS fix (`LOCATION_REQUIRED` otherwise).
 * 2. Never disguises routing failure as active navigation (`ROUTE_ERROR` with
 *    explicit Retry).
 * 3. Uses monotonic request tokens so stale or cancelled async responses can
 *    never overwrite newer destinations or repopulate after Stop.
 */
object NavigationController {
    private const val TAG = "NavigationController"
    const val MAX_FIX_AGE_MS = 45_000L
    const val OFF_ROUTE_METERS = 40.0
    private const val AUTO_DRIVE_MPS = 40.0 / 3.6
    const val MAX_ACCURACY_METERS = 100f

    /**
     * Polite gap between public geocoder and router calls. This used to sleep
     * 800 ms on the routing thread before every network request, which showed
     * up as search and route lag even on a fast link. Network time still
     * dominates and has to be measured on a device.
     */
    const val MIN_REQUEST_GAP_MS = 200L

    private val executor = Executors.newSingleThreadExecutor()
    private val searchGeneration = AtomicLong(0L)
    private val routeGeneration = AtomicLong(0L)

    @Volatile
    var photonBaseUrl: String = "https://photon.komoot.io/api/"

    @Volatile
    var nominatimBaseUrl: String = "https://nominatim.openstreetmap.org/search"

    @Volatile
    var osrmBaseUrl: String = "https://router.project-osrm.org/route/v1/driving"

    @Volatile
    var synchronousForTest: Boolean = false

    @Volatile
    var autoDriveEnabled: Boolean = false
        private set

    /** True after repeated fixes sit well off the active polyline. Not a reroute. */
    @Volatile
    var offRoute: Boolean = false
        private set

    @Volatile
    var lastSearchElapsedMs: Long = -1L
        private set

    private var autoDriveDistanceM: Double = 0.0
    private var autoDriveLastMs: Long = -1L
    private var offRouteHits: Int = 0
    private var heldDurationSeconds: Double = -1.0
    private var heldDurationStep: Int = -1

    @Volatile
    var routeTransport: ((Double, Double, Double, Double) -> ParsedRoute?)? = null

    @Volatile
    var searchTransport: ((String, Double?, Double?) -> List<SearchCandidate>)? = null

    @Volatile
    private var lastNetworkRequestMs: Long = 0L

    @Volatile
    var navState: NavState = NavState.IDLE
        private set

    @Volatile
    var searchQuery: String = ""
        private set

    @Volatile
    var searching: Boolean = false
        private set

    @Volatile
    var searchResults: List<SearchCandidate> = emptyList()
        private set

    @Volatile
    var destination: SearchCandidate? = null
        private set

    @Volatile
    var routeGeometry: List<Pair<Double, Double>> = emptyList()
        private set

    @Volatile
    var routeSteps: List<RouteStep> = emptyList()
        private set

    @Volatile
    var currentStepIndex: Int = 0
        private set

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var routingInFlight: Boolean = false
        private set

    @Volatile
    var remainingDistanceMeters: Double = 0.0
        private set

    @Volatile
    var remainingDurationSeconds: Double = 0.0
        private set

    @Volatile
    var distanceToNextTurnMeters: Double = 0.0
        private set

    @Volatile
    var currentLegRemainingMeters: Double = 0.0
        private set

    @Volatile
    var statusMessage: String? = null
        private set

    val navActive: Boolean get() = active && navState == NavState.ACTIVE
    val routePoints: List<Pair<Double, Double>> get() = routeGeometry
    val steps: List<RouteStep> get() = routeSteps
    val nextStepDistanceMeters: Double get() = distanceToNextTurnMeters

    fun currentInstruction(): String? =
        if (destination != null || routingInFlight || navState != NavState.IDLE) {
            currentHeadline()
        } else {
            null
        }

    fun formatDuration(seconds: Double): String {
        val totalMins = (seconds / 60.0).toInt().coerceAtLeast(1)
        return if (totalMins >= 60) {
            "${totalMins / 60}h ${totalMins % 60}m"
        } else {
            "${totalMins}m"
        }
    }

    /**
     * Returns the validated vehicle GPS origin `(lat, lon)` if and only if
     * `LiveDataStore.state` has an active, recent, sufficiently accurate fix.
     * Never falls back to map camera or static coordinates.
     */
    fun validGpsOrigin(nowMs: Long = System.currentTimeMillis()): Pair<Double, Double>? {
        val s = LiveDataStore.state
        if (!s.gpsActive) return null
        val lat = s.latitude ?: return null
        val lon = s.longitude ?: return null
        if (s.locationUpdatedAtMs <= 0L || nowMs - s.locationUpdatedAtMs > MAX_FIX_AGE_MS) {
            return null
        }
        val acc = s.accuracyM
        if (acc != null && acc > MAX_ACCURACY_METERS) {
            return null
        }
        return lat to lon
    }

    fun setQuery(value: String) {
        searchQuery = value
        LiveDataStore.notifyDisplayChanged()
    }

    fun appendQueryChar(ch: String) {
        searchQuery += ch
        LiveDataStore.notifyDisplayChanged()
    }

    fun backspaceQuery() {
        if (searchQuery.isNotEmpty()) {
            searchQuery = searchQuery.dropLast(1)
            LiveDataStore.notifyDisplayChanged()
        }
    }

    fun clearQuery() {
        searchGeneration.incrementAndGet()
        searching = false
        searchQuery = ""
        searchResults = emptyList()
        if (navState == NavState.IDLE) {
            statusMessage = null
        }
        LiveDataStore.notifyDisplayChanged()
    }

    fun searchAddress(query: String = searchQuery) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val token = searchGeneration.incrementAndGet()
        searchQuery = query
        searching = true
        searchResults = emptyList()
        statusMessage = "SEARCHING..."
        LiveDataStore.notifyDisplayChanged()

        val origin = validGpsOrigin()
        val task = Runnable {
            val started = System.currentTimeMillis()
            val custom = searchTransport
            val results = try {
                if (custom != null) {
                    custom(trimmed, origin?.first, origin?.second)
                } else {
                    throttleRequest()
                    fetchPhotonCandidates(trimmed, origin?.first, origin?.second)
                        .ifEmpty { fetchNominatimCandidates(trimmed) }
                }
            } catch (_: Exception) {
                emptyList()
            }
            lastSearchElapsedMs = (System.currentTimeMillis() - started).coerceAtLeast(0L)
            OaLog.i(TAG, "Search finished in ${lastSearchElapsedMs} ms")
            if (token != searchGeneration.get() || searchQuery.trim() != trimmed) {
                return@Runnable
            }
            searching = false
            searchResults = results
            statusMessage = if (results.isEmpty()) "NO RESULTS FOUND" else null
            LiveDataStore.notifyDisplayChanged()
        }
        if (synchronousForTest) task.run() else executor.execute(task)
    }

    fun selectDestination(
        candidate: SearchCandidate,
        autoStartNav: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val token = routeGeneration.incrementAndGet()
        destination = candidate
        searchResults = emptyList()
        // Immediately clear any previous route geometry/steps so old routes never linger.
        routeGeometry = emptyList()
        routeSteps = emptyList()
        currentStepIndex = 0
        remainingDistanceMeters = 0.0
        remainingDurationSeconds = 0.0
        distanceToNextTurnMeters = 0.0
        currentLegRemainingMeters = 0.0
        active = false

        val origin = validGpsOrigin(nowMs)
        if (origin == null) {
            routingInFlight = false
            navState = NavState.LOCATION_REQUIRED
            statusMessage = "LOCATION REQUIRED · WAIT FOR GPS FIX"
            LiveDataStore.notifyDisplayChanged()
            return
        }

        routingInFlight = true
        navState = NavState.ROUTING
        statusMessage = "ROUTING..."
        if (autoStartNav) {
            MapCameraStore.recenter()
        } else {
            MapCameraStore.focusOn(candidate.latitude, candidate.longitude, 15.0)
        }
        LiveDataStore.notifyDisplayChanged()

        val (originLat, originLon) = origin
        val task = Runnable {
            val custom = routeTransport
            val route = try {
                if (custom != null) {
                    custom(originLat, originLon, candidate.latitude, candidate.longitude)
                } else {
                    throttleRequest()
                    fetchOsrmRoute(originLat, originLon, candidate.latitude, candidate.longitude)
                }
            } catch (_: Exception) {
                null
            }
            if (token != routeGeneration.get() || destination != candidate) {
                return@Runnable
            }
            routingInFlight = false
            if (route != null && route.geometry.size >= 2 && route.steps.isNotEmpty()) {
                applyRoute(route, originLat, originLon)
                active = autoStartNav
                navState = if (autoStartNav) NavState.ACTIVE else NavState.PREVIEW
                statusMessage = null
            } else {
                active = false
                navState = NavState.ROUTE_ERROR
                routeGeometry = emptyList()
                routeSteps = emptyList()
                remainingDistanceMeters = 0.0
                remainingDurationSeconds = 0.0
                distanceToNextTurnMeters = 0.0
                currentLegRemainingMeters = 0.0
                statusMessage = "ROUTE UNAVAILABLE · TAP RETRY"
            }
            LiveDataStore.notifyDisplayChanged()
        }
        if (synchronousForTest) task.run() else executor.execute(task)
    }

    fun retryRoute(nowMs: Long = System.currentTimeMillis()) {
        val dest = destination ?: return
        selectDestination(dest, autoStartNav = true, nowMs = nowMs)
    }

    fun toggleNavigation(nowMs: Long = System.currentTimeMillis()) {
        when {
            active || navState == NavState.ROUTING -> stopNavigation()
            navState == NavState.ROUTE_ERROR || navState == NavState.LOCATION_REQUIRED -> {
                retryRoute(nowMs)
            }
            else -> {
                val dest = destination ?: return
                if (validGpsOrigin(nowMs) == null) {
                    active = false
                    navState = NavState.LOCATION_REQUIRED
                    statusMessage = "LOCATION REQUIRED · WAIT FOR GPS FIX"
                    LiveDataStore.notifyDisplayChanged()
                    return
                }
                if (routeGeometry.size >= 2 && routeSteps.isNotEmpty()) {
                    active = true
                    navState = NavState.ACTIVE
                    statusMessage = null
                    MapCameraStore.recenter()
                    LiveDataStore.notifyDisplayChanged()
                } else {
                    selectDestination(dest, autoStartNav = true, nowMs = nowMs)
                }
            }
        }
    }

    fun stopNavigation() {
        routeGeneration.incrementAndGet()
        active = false
        routingInFlight = false
        navState = NavState.IDLE
        routeGeometry = emptyList()
        routeSteps = emptyList()
        currentStepIndex = 0
        remainingDistanceMeters = 0.0
        remainingDurationSeconds = 0.0
        distanceToNextTurnMeters = 0.0
        currentLegRemainingMeters = 0.0
        destination = null
        statusMessage = null
        autoDriveEnabled = false
        autoDriveDistanceM = 0.0
        autoDriveLastMs = -1L
        offRoute = false
        offRouteHits = 0
        heldDurationSeconds = -1.0
        heldDurationStep = -1
        LiveDataStore.notifyDisplayChanged()
    }

    /**
     * DHU "simulate navigation" (quality NF-7). Steps the existing route and
     * publishes a fix. Returns true while the simulation should keep ticking.
     * Does not treat speed 0 as parked.
     */
    fun onAutoDriveEnabled() {
        autoDriveEnabled = true
        autoDriveDistanceM = 0.0
        autoDriveLastMs = -1L
    }

    /**
     * Moves about 11 m along the polyline per second at 40 km/h. A tick does
     * not jump to the next geometry vertex.
     */
    fun advanceAutoDrive(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!autoDriveEnabled) return false
        if (navState == NavState.ROUTING) return true
        val points = routeGeometry
        if (navState != NavState.ACTIVE || points.size < 2) {
            autoDriveEnabled = false
            return false
        }
        val dt = if (autoDriveLastMs < 0L) 1_000L else (nowMs - autoDriveLastMs).coerceIn(0L, 2_000L)
        autoDriveLastMs = nowMs
        val total = polylineLength(points)
        val nextDistance = (autoDriveDistanceM + AUTO_DRIVE_MPS * (dt / 1000.0)).coerceAtMost(total)
        val previous = pointAlong(points, autoDriveDistanceM)
        val point = pointAlong(points, nextDistance)
        autoDriveDistanceM = nextDistance
        val course = HeadingHold.bearingBetween(previous.first, previous.second, point.first, point.second)
        LiveDataStore.setLocationFix(
            speedKph = 40f,
            gpsActive = true,
            latitude = point.first,
            longitude = point.second,
            bearing = course,
            accuracyM = 5f,
            updatedAtMs = nowMs,
        )
        onLocationUpdate(point.first, point.second, 5f, nowMs)
        if (navState != NavState.ACTIVE || autoDriveDistanceM >= total - 0.5) {
            autoDriveEnabled = false
            return false
        }
        return true
    }

    /** Builds a new route from the current GPS fix to the same destination. */
    fun recalculate() {
        val dest = destination ?: return
        offRoute = false
        offRouteHits = 0
        autoDriveDistanceM = 0.0
        autoDriveLastMs = -1L
        selectDestination(dest, autoStartNav = true)
    }

    /**
     * Arrival time shown on the host card. Holds the previous value until the
     * step changes or the estimate moves by at least 30 seconds.
     */
    fun displayedRemainingDurationSeconds(): Double {
        val raw = remainingDurationSeconds
        if (heldDurationStep != currentStepIndex || heldDurationSeconds < 0.0) {
            heldDurationSeconds = raw
            heldDurationStep = currentStepIndex
            return heldDurationSeconds
        }
        if (kotlin.math.abs(raw - heldDurationSeconds) >= 30.0) {
            heldDurationSeconds = raw
        }
        return heldDurationSeconds
    }

    fun resetForTest() {
        synchronousForTest = false
        routeTransport = null
        searchTransport = null
        autoDriveEnabled = false
        autoDriveDistanceM = 0.0
        autoDriveLastMs = -1L
        offRoute = false
        offRouteHits = 0
        heldDurationSeconds = -1.0
        heldDurationStep = -1
        lastSearchElapsedMs = -1L
        clearQuery()
        stopNavigation()
    }

    fun onLocationUpdate(
        lat: Double?,
        lon: Double?,
        accuracyM: Float? = null,
        updatedAtMs: Long = System.currentTimeMillis(),
    ) {
        if (lat == null || lon == null) return
        if (accuracyM != null && accuracyM > MAX_ACCURACY_METERS) return
        if (!active || navState != NavState.ACTIVE) return
        val away = distanceToRouteMeters(lat, lon)
        if (away > OFF_ROUTE_METERS) {
            offRouteHits += 1
            if (offRouteHits >= 2 && !offRoute) {
                offRoute = true
                statusMessage = "OFF THE ROUTE"
                LiveDataStore.notifyDisplayChanged()
            }
            if (offRoute) return
        } else if (offRouteHits != 0 || offRoute) {
            offRouteHits = 0
            offRoute = false
            if (statusMessage == "OFF THE ROUTE") statusMessage = null
        }
        val steps = routeSteps
        val dest = destination ?: return
        if (steps.isEmpty()) return

        var idx = currentStepIndex.coerceIn(0, steps.lastIndex)
        var step = steps[idx]
        var distToManeuver = haversineMeters(lat, lon, step.latitude, step.longitude)

        while (distToManeuver < 35.0 && idx < steps.lastIndex) {
            idx += 1
            currentStepIndex = idx
            step = steps[idx]
            distToManeuver = haversineMeters(lat, lon, step.latitude, step.longitude)
        }

        val distToDest = haversineMeters(lat, lon, dest.latitude, dest.longitude)
        if (idx == steps.lastIndex && distToDest < 30.0) {
            remainingDistanceMeters = 0.0
            remainingDurationSeconds = 0.0
            distanceToNextTurnMeters = 0.0
            currentLegRemainingMeters = 0.0
            statusMessage = "ARRIVED AT ${dest.title.uppercase(Locale.US)}"
            active = false
            navState = NavState.IDLE
            LiveDataStore.notifyDisplayChanged()
            return
        }

        val directSpan = haversineMeters(
            step.startLatitude,
            step.startLongitude,
            step.latitude,
            step.longitude,
        )
        val fractionRemaining = if (directSpan > 1.0) {
            (distToManeuver / directSpan).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val legRemaining = if (step.distanceMeters > 0.0 && directSpan > 1.0) {
            step.distanceMeters * fractionRemaining
        } else {
            distToManeuver
        }
        val legDurationRemaining = if (step.durationSeconds > 0.0 && directSpan > 1.0) {
            step.durationSeconds * fractionRemaining
        } else {
            legRemaining / 13.8
        }

        val tailSteps = steps.drop(idx + 1)
        val tailDistance = tailSteps.sumOf { it.distanceMeters }
        val tailDuration = tailSteps.sumOf { it.durationSeconds }

        currentLegRemainingMeters = legRemaining
        distanceToNextTurnMeters = legRemaining
        remainingDistanceMeters = legRemaining + tailDistance
        remainingDurationSeconds = legDurationRemaining + tailDuration
        LiveDataStore.notifyDisplayChanged()
    }

    fun currentHeadline(): String {
        when (navState) {
            NavState.LOCATION_REQUIRED -> return statusMessage ?: "LOCATION REQUIRED · WAIT FOR GPS FIX"
            NavState.ROUTING -> return "CALCULATING ROUTE..."
            NavState.ROUTE_ERROR -> return statusMessage ?: "ROUTE UNAVAILABLE · TAP RETRY"
            else -> Unit
        }
        val steps = routeSteps
        if (steps.isNotEmpty()) {
            val step = steps[currentStepIndex.coerceIn(0, steps.lastIndex)]
            val streetPart = step.street.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            return "${formatDistance(distanceToNextTurnMeters)} · ${step.instruction}$streetPart"
        }
        val dest = destination
        if (dest != null) {
            return statusMessage ?: dest.title.uppercase(Locale.US)
        }
        return statusMessage ?: "READY"
    }

    fun currentEtaSummary(): String {
        if (navState == NavState.LOCATION_REQUIRED) {
            return "Accurate GPS fix required before routing"
        }
        if (navState == NavState.ROUTE_ERROR) {
            return "Tap RETRY NAV when connection is available"
        }
        val dest = destination ?: return ""
        if (remainingDistanceMeters <= 0.0) return dest.displayName
        return "${formatDistance(remainingDistanceMeters)} · ETA ${formatDuration(remainingDurationSeconds)} · ${dest.title.uppercase(Locale.US)}"
    }

    fun formatDistance(meters: Double): String =
        if (meters >= 1000.0) {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        } else {
            String.format(Locale.US, "%d m", meters.toInt().coerceAtLeast(0))
        }

    internal fun applyRoute(route: ParsedRoute, originLat: Double, originLon: Double) {
        routeGeometry = route.geometry
        routeSteps = route.steps
        currentStepIndex = 0
        remainingDistanceMeters = route.totalDistanceMeters
        remainingDurationSeconds = route.totalDurationSeconds
        val first = route.steps.firstOrNull()
        val firstSpan = if (first != null) {
            first.distanceMeters.takeIf { it > 0.0 }
                ?: haversineMeters(originLat, originLon, first.latitude, first.longitude)
        } else {
            route.totalDistanceMeters
        }
        currentLegRemainingMeters = firstSpan
        distanceToNextTurnMeters = firstSpan
        offRoute = false
        offRouteHits = 0
        heldDurationSeconds = route.totalDurationSeconds
        heldDurationStep = 0
    }

    private data class RawOsrmStep(
        val type: String,
        val modifier: String,
        val name: String,
        val distanceMeters: Double,
        val durationSeconds: Double,
        val latitude: Double,
        val longitude: Double,
        val exit: Int? = null,
        val lanes: List<RouteLane> = emptyList(),
    )

    internal fun parseOsrmRouteJson(raw: String): ParsedRoute? {
        return try {
            val root = JSONObject(raw)
            val routes = root.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val route = routes.getJSONObject(0)
            val totalDistance = route.optDouble("distance", 0.0)
            val totalDuration = route.optDouble("duration", 0.0)
            val coordsArray = route.optJSONObject("geometry")?.optJSONArray("coordinates")
            val geometry = mutableListOf<Pair<Double, Double>>()
            if (coordsArray != null) {
                for (i in 0 until coordsArray.length()) {
                    val pt = coordsArray.getJSONArray(i)
                    val lon = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    geometry += lat to lon
                }
            }
            val rawSteps = mutableListOf<RawOsrmStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val steps = legs.getJSONObject(0).optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val s = steps.getJSONObject(i)
                        val maneuver = s.optJSONObject("maneuver")
                        val type = maneuver?.optString("type").orEmpty()
                        val modifier = maneuver?.optString("modifier").orEmpty()
                        val loc = maneuver?.optJSONArray("location")
                        val lon = loc?.optDouble(0) ?: 0.0
                        val lat = loc?.optDouble(1) ?: 0.0
                        val name = s.optString("name").orEmpty()
                        val dist = s.optDouble("distance", 0.0)
                        val dur = s.optDouble("duration", 0.0)
                        rawSteps += RawOsrmStep(
                            type = type,
                            modifier = modifier,
                            name = name,
                            distanceMeters = dist,
                            durationSeconds = dur,
                            latitude = lat,
                            longitude = lon,
                            exit = maneuver?.takeIf { it.has("exit") }?.optInt("exit"),
                            lanes = parseLanes(s),
                        )
                    }
                }
            }
            buildParsedRoute(geometry, rawSteps, totalDistance, totalDuration)
                ?.takeIf { it.geometry.size >= 2 && it.steps.isNotEmpty() }
                ?: parseOsrmFallback(raw)
        } catch (_: Exception) {
            // Unit tests stub org.json and throw. A device uses the object path above.
            parseOsrmFallback(raw)
        }
    }

    private fun parseOsrmFallback(raw: String): ParsedRoute? {
        if (!raw.contains("\"routes\"")) return null
        val totalDistance = Regex("\"distance\"\\s*:\\s*([0-9.]+)").find(raw)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val totalDuration = Regex("\"duration\"\\s*:\\s*([0-9.]+)").find(raw)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val afterCoords = raw.substringAfter("\"coordinates\"", "")
        val startIdx = afterCoords.indexOf('[')
        val coordSection = if (startIdx >= 0) {
            var depth = 0
            var endIdx = -1
            for (i in startIdx until afterCoords.length) {
                if (afterCoords[i] == '[') {
                    depth++
                } else if (afterCoords[i] == ']') {
                    depth--
                    if (depth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
            if (endIdx > startIdx) afterCoords.substring(startIdx, endIdx + 1) else afterCoords
        } else {
            ""
        }
        val coordPairs = Regex("\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*]")
            .findAll(coordSection)
            .mapNotNull { m ->
                val lon = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val lat = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                lat to lon
            }.toList()
        val stepsSection = raw.substringAfter("\"steps\"", "")
        val parts = stepsSection.split("\"maneuver\"")
        val rawSteps = parts.drop(1).mapIndexed { idx, block ->
            val prevBlock = parts[idx]
            val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").findAll(prevBlock).lastOrNull()
                ?.groupValues?.get(1).orEmpty()
            val dist = Regex("\"distance\"\\s*:\\s*([0-9.]+)").findAll(prevBlock).lastOrNull()
                ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val dur = Regex("\"duration\"\\s*:\\s*([0-9.]+)").findAll(prevBlock).lastOrNull()
                ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val type = Regex("\"type\"\\s*:\\s*\"([^\"]*)\"").find(block)
                ?.groupValues?.get(1).orEmpty()
            val modifier = Regex("\"modifier\"\\s*:\\s*\"([^\"]*)\"").find(block)
                ?.groupValues?.get(1).orEmpty()
            val locMatch = Regex("\"location\"\\s*:\\s*\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*]")
                .find(block)
            val lon = locMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val lat = locMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
            val exit = Regex("\"exit\"\\s*:\\s*(\\d+)").find(block)?.groupValues?.get(1)?.toIntOrNull()
            RawOsrmStep(
                type = type,
                modifier = modifier,
                name = name,
                distanceMeters = dist,
                durationSeconds = dur,
                latitude = lat,
                longitude = lon,
                exit = exit,
                lanes = parseLanesFallback(block),
            )
        }
        return buildParsedRoute(coordPairs, rawSteps, totalDistance, totalDuration)
    }

    /**
     * Converts raw OSRM steps into navigable legs so every leg's distance and
     * duration are preserved:
     * - Each non-arrive OSRM step `i` represents driving leg `i` from
     *   `rawSteps[i].location` to `endLocation(i)` (`rawSteps[i+1].location` or
     *   `geometry.last()`).
     * - If `rawSteps[0]` is `"depart"` and a turn step `rawSteps[1]` follows,
     *   leg 0 announces the upcoming turn (`rawSteps[1]`) at `rawSteps[1].location`
     *   while retaining leg 0's distance/duration, and leg 1 continues from
     *   `rawSteps[1].location` to the next maneuver or destination.
     */
    private fun buildParsedRoute(
        geometry: List<Pair<Double, Double>>,
        rawSteps: List<RawOsrmStep>,
        totalDistance: Double,
        totalDuration: Double,
    ): ParsedRoute? {
        val nonArrive = rawSteps.filter { it.type != "arrive" || rawSteps.size == 1 }
        if (nonArrive.isEmpty() && geometry.size < 2) return null
        val routeEndLat = rawSteps.lastOrNull { it.type == "arrive" }?.latitude
            ?: geometry.lastOrNull()?.first
            ?: nonArrive.lastOrNull()?.latitude
            ?: 0.0
        val routeEndLon = rawSteps.lastOrNull { it.type == "arrive" }?.longitude
            ?: geometry.lastOrNull()?.second
            ?: nonArrive.lastOrNull()?.longitude
            ?: 0.0

        val builtSteps = nonArrive.mapIndexed { idx, current ->
            val next = nonArrive.getOrNull(idx + 1)
            val endLat = next?.latitude ?: routeEndLat
            val endLon = next?.longitude ?: routeEndLon
            // When on the initial depart leg and a turn maneuver follows, announce
            // that upcoming turn at the end of leg 0.
            val announce = if (current.type == "depart" && next != null) next else current
            RouteStep(
                instruction = formatManeuver(announce.type, announce.modifier, announce.exit),
                street = announce.name.ifBlank { current.name },
                distanceMeters = current.distanceMeters,
                durationSeconds = current.durationSeconds,
                latitude = endLat,
                longitude = endLon,
                startLatitude = current.latitude,
                startLongitude = current.longitude,
                lanes = announce.lanes,
            )
        }
        val computedTotalDist = if (totalDistance > 0.0) {
            totalDistance
        } else {
            builtSteps.sumOf { it.distanceMeters }
        }
        val computedTotalDur = if (totalDuration > 0.0) {
            totalDuration
        } else {
            builtSteps.sumOf { it.durationSeconds }
        }
        return ParsedRoute(
            geometry = geometry,
            steps = builtSteps,
            totalDistanceMeters = computedTotalDist,
            totalDurationSeconds = computedTotalDur,
        )
    }

    private fun formatManeuver(type: String, modifier: String, exit: Int? = null): String {
        val mod = modifier.replace('-', ' ').uppercase(Locale.US)
        return when (type) {
            "turn" -> if (mod.isNotBlank()) "TURN $mod" else "TURN"
            "new name", "continue" -> if (mod.isNotBlank()) "KEEP $mod" else "CONTINUE"
            "merge" -> "MERGE $mod".trim()
            "on ramp", "off ramp" -> "RAMP $mod".trim()
            "fork" -> "FORK $mod".trim()
            "roundabout", "rotary" -> {
                if (exit != null && exit > 0) "ROUNDABOUT, ${ordinal(exit)} EXIT" else "ROUNDABOUT"
            }
            "arrive" -> "ARRIVE"
            "depart" -> "HEAD $mod".trim()
            else -> if (mod.isNotBlank()) mod else "CONTINUE"
        }
    }

    private fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "TH"
            n % 10 == 1 -> "ST"
            n % 10 == 2 -> "ND"
            n % 10 == 3 -> "RD"
            else -> "TH"
        }
        return "$n$suffix"
    }

    /**
     * Used when [org.json.JSONObject] is the unit-test stub. Reads the first
     * `lanes` array in this maneuver's block.
     */
    private fun parseLanesFallback(block: String): List<RouteLane> {
        val key = block.indexOf("\"lanes\"")
        if (key < 0) return emptyList()
        val start = block.indexOf('[', key)
        if (start < 0) return emptyList()
        val array = sliceMatching(block, start, '[', ']') ?: return emptyList()
        val lanes = mutableListOf<RouteLane>()
        var cursor = 0
        while (cursor < array.length) {
            val objStart = array.indexOf('{', cursor)
            if (objStart < 0) break
            val obj = sliceMatching(array, objStart, '{', '}') ?: break
            val indications = Regex("\"indications\"\\s*:\\s*\\[([^\\]]*)]").find(obj)
                ?.groupValues?.get(1)
                ?.split(',')
                ?.map { it.trim().trim('"') }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            lanes += RouteLane(indications, Regex("\"valid\"\\s*:\\s*true").containsMatchIn(obj))
            cursor = objStart + obj.length
        }
        return lanes
    }

    private fun sliceMatching(text: String, openIndex: Int, open: Char, close: Char): String? {
        if (openIndex !in text.indices || text[openIndex] != open) return null
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex, i + 1)
                }
            }
        }
        return null
    }

    private fun parseLanes(step: JSONObject): List<RouteLane> {
        val intersections = step.optJSONArray("intersections") ?: return emptyList()
        for (i in 0 until intersections.length()) {
            val lanes = intersections.optJSONObject(i)?.optJSONArray("lanes") ?: continue
            if (lanes.length() == 0) continue
            return (0 until lanes.length()).map { index ->
                val lane = lanes.optJSONObject(index) ?: return@map RouteLane(emptyList(), false)
                val indications = lane.optJSONArray("indications")
                val names = if (indications == null) {
                    emptyList()
                } else {
                    (0 until indications.length()).map { indications.optString(it) }
                }
                RouteLane(names, lane.optBoolean("valid", false))
            }
        }
        return emptyList()
    }

    private fun distanceToRouteMeters(lat: Double, lon: Double): Double {
        val points = routeGeometry
        if (points.size < 2) return Double.MAX_VALUE
        var best = Double.MAX_VALUE
        for (i in 0 until points.lastIndex) {
            val span = distanceToSegmentMeters(lat, lon, points[i], points[i + 1])
            if (span < best) best = span
        }
        return best
    }

    private fun distanceToSegmentMeters(
        lat: Double,
        lon: Double,
        start: Pair<Double, Double>,
        end: Pair<Double, Double>,
    ): Double {
        val meanLat = Math.toRadians((start.first + end.first) / 2.0)
        val kx = 111_320.0 * cos(meanLat)
        val ky = 110_540.0
        val ax = (start.second - lon) * kx
        val ay = (start.first - lat) * ky
        val bx = (end.second - lon) * kx
        val by = (end.first - lat) * ky
        val abx = bx - ax
        val aby = by - ay
        val ab2 = abx * abx + aby * aby
        val t = if (ab2 <= 1.0) 0.0 else ((-ax) * abx + (-ay) * aby) / ab2
        val clamped = t.coerceIn(0.0, 1.0)
        val px = ax + abx * clamped
        val py = ay + aby * clamped
        return sqrt(px * px + py * py)
    }

    private fun polylineLength(points: List<Pair<Double, Double>>): Double {
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += haversineMeters(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second)
        }
        return total
    }

    private fun pointAlong(points: List<Pair<Double, Double>>, distanceM: Double): Pair<Double, Double> {
        if (points.isEmpty()) return 0.0 to 0.0
        var remaining = distanceM.coerceAtLeast(0.0)
        for (i in 0 until points.lastIndex) {
            val start = points[i]
            val end = points[i + 1]
            val span = haversineMeters(start.first, start.second, end.first, end.second)
            if (span < 0.5 || remaining <= span) {
                val fraction = if (span < 0.5) 1.0 else (remaining / span).coerceIn(0.0, 1.0)
                return start.first + (end.first - start.first) * fraction to
                    start.second + (end.second - start.second) * fraction
            }
            remaining -= span
        }
        return points.last()
    }

    private fun throttleRequest() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastNetworkRequestMs
        if (elapsed in 0 until MIN_REQUEST_GAP_MS) {
            try {
                Thread.sleep(MIN_REQUEST_GAP_MS - elapsed)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        lastNetworkRequestMs = System.currentTimeMillis()
    }

    private fun fetchPhotonCandidates(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
    ): List<SearchCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val biasParams = if (biasLat != null && biasLon != null) {
                "&lat=$biasLat&lon=$biasLon"
            } else {
                ""
            }
            val url = "$photonBaseUrl?q=$encoded$biasParams&limit=5"
            val raw = httpGet(url) ?: return emptyList()
            val features = JSONObject(raw).optJSONArray("features") ?: return emptyList()
            val results = mutableListOf<SearchCandidate>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val geom = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val lon = geom.getDouble(0)
                val lat = geom.getDouble(1)
                val props = f.optJSONObject("properties")
                val name = props?.optString("name").orEmpty()
                    .ifBlank { props?.optString("street").orEmpty() }
                    .ifBlank { query }
                val street = props?.optString("street").orEmpty()
                val city = props?.optString("city").orEmpty()
                    .ifBlank { props?.optString("state").orEmpty() }
                val sub = listOf(street, city).filter { it.isNotBlank() && it != name }.joinToString(", ")
                results += SearchCandidate(
                    title = name,
                    subtitle = sub,
                    latitude = lat,
                    longitude = lon,
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchNominatimCandidates(query: String): List<SearchCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$nominatimBaseUrl?format=json&limit=5&q=$encoded"
            val raw = httpGet(url) ?: return emptyList()
            val arr = JSONArray(raw)
            val results = mutableListOf<SearchCandidate>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val lat = obj.getString("lat").toDoubleOrNull() ?: continue
                val lon = obj.getString("lon").toDoubleOrNull() ?: continue
                val display = obj.optString("display_name", query)
                val parts = display.split(",").map { it.trim() }
                val title = parts.firstOrNull().orEmpty().ifBlank { query }
                val subtitle = parts.drop(1).take(2).joinToString(", ")
                results += SearchCandidate(
                    title = title,
                    subtitle = subtitle,
                    latitude = lat,
                    longitude = lon,
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchOsrmRoute(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): ParsedRoute? {
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", lon1, lat1, lon2, lat2)
        val url = "$osrmBaseUrl/$coords?overview=full&geometries=geojson&steps=true"
        val raw = httpGet(url) ?: return null
        return parseOsrmRouteJson(raw)
    }

    private fun httpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 6000
                setRequestProperty("User-Agent", "OmarchyAuto/0.1.11 (https://omarchyauto.com)")
            }
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2.0 * r * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
