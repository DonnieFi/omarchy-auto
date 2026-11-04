package com.omarchy.auto.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class SearchCandidate(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
) {
    val name: String get() = title
    val displayName: String get() = if (subtitle.isNotBlank()) "$title, $subtitle" else title
}

data class RouteStep(
    val instruction: String,
    val street: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val latitude: Double,
    val longitude: Double,
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
 */
object NavigationController {
    private val executor = Executors.newSingleThreadExecutor()

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
    var statusMessage: String? = null
        private set

    val navActive: Boolean get() = active
    val routePoints: List<Pair<Double, Double>> get() = routeGeometry
    val steps: List<RouteStep> get() = routeSteps
    val nextStepDistanceMeters: Double get() = distanceToNextTurnMeters

    fun currentInstruction(): String? =
        if (destination != null || routingInFlight) currentHeadline() else null

    fun formatDuration(seconds: Double): String {
        val totalMins = (seconds / 60.0).toInt().coerceAtLeast(1)
        return if (totalMins >= 60) {
            "${totalMins / 60}h ${totalMins % 60}m"
        } else {
            "${totalMins}m"
        }
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
        searchQuery = ""
        searchResults = emptyList()
        statusMessage = null
        LiveDataStore.notifyDisplayChanged()
    }

    fun searchAddress(query: String = searchQuery) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchQuery = trimmed
        searching = true
        statusMessage = "SEARCHING..."
        LiveDataStore.notifyDisplayChanged()

        val biasLat = LiveDataStore.state.latitude ?: MapSnapshotStore.latitude ?: 43.6532
        val biasLon = LiveDataStore.state.longitude ?: MapSnapshotStore.longitude ?: -79.3832

        executor.execute {
            val results = fetchPhotonCandidates(trimmed, biasLat, biasLon)
                .ifEmpty { fetchNominatimCandidates(trimmed) }
            searching = false
            searchResults = results
            statusMessage = if (results.isEmpty()) "NO RESULTS FOUND" else null
            LiveDataStore.notifyDisplayChanged()
        }
    }

    fun selectDestination(candidate: SearchCandidate, autoStartNav: Boolean = true) {
        destination = candidate
        searchResults = emptyList()
        routingInFlight = true
        statusMessage = "ROUTING..."
        val state = LiveDataStore.state
        val originLat = state.latitude ?: MapSnapshotStore.latitude ?: 43.6532
        val originLon = state.longitude ?: MapSnapshotStore.longitude ?: -79.3832
        if (autoStartNav) {
            active = true
            MapCameraStore.recenter()
        } else {
            MapCameraStore.focusOn(candidate.latitude, candidate.longitude, 15.0)
        }
        LiveDataStore.notifyDisplayChanged()

        executor.execute {
            val route = fetchOsrmRoute(originLat, originLon, candidate.latitude, candidate.longitude)
            routingInFlight = false
            if (route != null && route.geometry.isNotEmpty()) {
                applyRoute(route, originLat, originLon)
                statusMessage = null
            } else {
                // Direct line fallback if offline
                routeGeometry = listOf(
                    originLat to originLon,
                    candidate.latitude to candidate.longitude,
                )
                val dist = haversineMeters(originLat, originLon, candidate.latitude, candidate.longitude)
                routeSteps = listOf(
                    RouteStep(
                        instruction = "PROCEED TO ${candidate.title.uppercase(Locale.US)}",
                        street = candidate.subtitle,
                        distanceMeters = dist,
                        durationSeconds = dist / 13.8,
                        latitude = candidate.latitude,
                        longitude = candidate.longitude,
                    ),
                )
                currentStepIndex = 0
                remainingDistanceMeters = dist
                remainingDurationSeconds = dist / 13.8
                distanceToNextTurnMeters = dist
                statusMessage = null
            }
            LiveDataStore.notifyDisplayChanged()
        }
    }

    fun toggleNavigation() {
        if (active) {
            stopNavigation()
        } else {
            val dest = destination
            if (dest != null) {
                active = true
                MapCameraStore.recenter()
                if (routeGeometry.isEmpty()) {
                    selectDestination(dest, autoStartNav = true)
                } else {
                    LiveDataStore.notifyDisplayChanged()
                }
            }
        }
    }

    fun stopNavigation() {
        active = false
        routingInFlight = false
        routeGeometry = emptyList()
        routeSteps = emptyList()
        currentStepIndex = 0
        remainingDistanceMeters = 0.0
        remainingDurationSeconds = 0.0
        distanceToNextTurnMeters = 0.0
        destination = null
        statusMessage = null
        LiveDataStore.notifyDisplayChanged()
    }

    fun onLocationUpdate(lat: Double?, lon: Double?) {
        if (!active || lat == null || lon == null) return
        val steps = routeSteps
        val dest = destination ?: return
        if (steps.isEmpty()) return

        val idx = currentStepIndex.coerceIn(0, steps.lastIndex)
        val step = steps[idx]
        val distToStep = haversineMeters(lat, lon, step.latitude, step.longitude)
        distanceToNextTurnMeters = distToStep

        if (distToStep < 35.0 && idx < steps.lastIndex) {
            currentStepIndex = idx + 1
            val next = steps[currentStepIndex]
            distanceToNextTurnMeters = haversineMeters(lat, lon, next.latitude, next.longitude)
        }

        val distToDest = haversineMeters(lat, lon, dest.latitude, dest.longitude)
        if (distToDest < 30.0) {
            remainingDistanceMeters = 0.0
            remainingDurationSeconds = 0.0
            statusMessage = "ARRIVED AT ${dest.title.uppercase(Locale.US)}"
            active = false
        } else {
            val tailSteps = steps.drop(currentStepIndex + 1)
            remainingDistanceMeters = distanceToNextTurnMeters + tailSteps.sumOf { it.distanceMeters }
            remainingDurationSeconds = (remainingDistanceMeters / 13.8)
                .coerceAtLeast(tailSteps.sumOf { it.durationSeconds })
        }
    }

    fun currentHeadline(): String {
        val dest = destination
        if (routingInFlight) return "CALCULATING ROUTE..."
        val steps = routeSteps
        if (steps.isNotEmpty()) {
            val step = steps[currentStepIndex.coerceIn(0, steps.lastIndex)]
            val streetPart = step.street.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            return "${formatDistance(distanceToNextTurnMeters)} · ${step.instruction}$streetPart"
        }
        if (dest != null) {
            return "${formatDistance(remainingDistanceMeters)} · ${dest.title.uppercase(Locale.US)}"
        }
        return statusMessage ?: "READY"
    }

    fun currentEtaSummary(): String {
        val dest = destination ?: return ""
        val mins = (remainingDurationSeconds / 60.0).toInt().coerceAtLeast(1)
        return "${formatDistance(remainingDistanceMeters)} · ${mins}m · ${dest.title.uppercase(Locale.US)}"
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
        distanceToNextTurnMeters = if (first != null) {
            haversineMeters(originLat, originLon, first.latitude, first.longitude)
        } else {
            route.totalDistanceMeters
        }
    }

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
            val stepsList = mutableListOf<RouteStep>()
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
                        val instruction = formatManeuver(type, modifier)
                        stepsList += RouteStep(
                            instruction = instruction,
                            street = name,
                            distanceMeters = dist,
                            durationSeconds = dur,
                            latitude = lat,
                            longitude = lon,
                        )
                    }
                }
            }
            // Drop the initial "depart" step if a turn step follows
            val filteredSteps = if (stepsList.size > 1) stepsList.drop(1) else stepsList
            ParsedRoute(
                geometry = geometry,
                steps = filteredSteps,
                totalDistanceMeters = totalDistance,
                totalDurationSeconds = totalDuration,
            )
        } catch (_: Exception) {
            parseOsrmFallback(raw)
        }
    }

    private fun parseOsrmFallback(raw: String): ParsedRoute? {
        if (!raw.contains("\"routes\"")) return null
        val totalDistance = Regex("\"distance\"\\s*:\\s*([0-9.]+)").find(raw)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val totalDuration = Regex("\"duration\"\\s*:\\s*([0-9.]+)").find(raw)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val coordSection = raw.substringAfter("\"coordinates\"", "").substringBefore("]", "")
        val coordPairs = Regex("\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*]")
            .findAll(coordSection + "]")
            .mapNotNull { m ->
                val lon = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val lat = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                lat to lon
            }.toList()
        val stepsSection = raw.substringAfter("\"steps\"", "")
        val stepBlocks = stepsSection.split("\"maneuver\"").drop(1)
        val stepsList = stepBlocks.mapIndexed { idx, block ->
            val prevBlock = stepsSection.split("\"maneuver\"")[idx]
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
            RouteStep(
                instruction = formatManeuver(type, modifier),
                street = name,
                distanceMeters = dist,
                durationSeconds = dur,
                latitude = lat,
                longitude = lon,
            )
        }
        val filteredSteps = if (stepsList.size > 1) stepsList.drop(1) else stepsList
        return ParsedRoute(
            geometry = coordPairs,
            steps = filteredSteps,
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration,
        )
    }

    private fun formatManeuver(type: String, modifier: String): String {
        val mod = modifier.replace('-', ' ').uppercase(Locale.US)
        return when (type) {
            "turn" -> if (mod.isNotBlank()) "TURN $mod" else "TURN"
            "new name", "continue" -> if (mod.isNotBlank()) "KEEP $mod" else "CONTINUE"
            "merge" -> "MERGE $mod".trim()
            "on ramp", "off ramp" -> "RAMP $mod".trim()
            "fork" -> "FORK $mod".trim()
            "roundabout", "rotary" -> "ROUNDABOUT"
            "arrive" -> "ARRIVE"
            "depart" -> "HEAD $mod".trim()
            else -> if (mod.isNotBlank()) mod else "CONTINUE"
        }
    }

    private fun fetchPhotonCandidates(
        query: String,
        biasLat: Double,
        biasLon: Double,
    ): List<SearchCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://photon.komoot.io/api/?q=$encoded&lat=$biasLat&lon=$biasLon&limit=5"
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
            val url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&q=$encoded"
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
        val url = "https://router.project-osrm.org/route/v1/driving/$coords" +
            "?overview=full&geometries=geojson&steps=true"
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
