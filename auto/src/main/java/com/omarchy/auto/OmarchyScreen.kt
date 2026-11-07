package com.omarchy.auto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.DistractionLock
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MapCameraStore
import com.omarchy.auto.core.NavigateIntent
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.RouteLane
import com.omarchy.auto.core.RouteStep
import com.omarchy.auto.core.SearchCandidate
import com.omarchy.auto.core.SetupActivity
import com.omarchy.auto.core.StageWebController
import com.omarchy.auto.core.diag.OaLog
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class OmarchyScreen(carContext: CarContext) : Screen(carContext) {

    private var surfaceRenderer: CarSurfaceRenderer? = null
    private var navigationManager: NavigationManager? = null
    internal var hostNavStarted: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingNav: NavigateIntent.Request? = null
    private var templateStamp = ""
    private var tripStamp = ""
    private var alertKey = ""

    private val autoDriveTick = object : Runnable {
        override fun run() {
            if (!NavigationController.advanceAutoDrive()) return
            syncNavigationManager()
            surfaceRenderer?.requestDraw()
            refreshTemplate()
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    private val dataListener: (DashboardState) -> Unit = {
        syncNavigationManager()
        surfaceRenderer?.requestDraw()
        refreshTemplate()
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            val surface = surfaceContainer.surface ?: return
            surfaceRenderer?.stop()
            surfaceRenderer = CarSurfaceRenderer(surface, carContext).also {
                if (surfaceContainer.width > 0 && surfaceContainer.height > 0) {
                    it.setSafeArea(Rect(0, 0, surfaceContainer.width, surfaceContainer.height))
                } else {
                    it.setSafeArea(null)
                }
                it.start()
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            // CarSurfaceRenderer owns Surface.release(), including the
            // deferred release when a draw was in flight.
            surfaceRenderer?.stop()
            surfaceRenderer = null
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            // The map bitmap stays full-bleed. Speed and attribution use the
            // host stable area so they are not covered by the routing card.
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            surfaceRenderer?.setSafeArea(stableArea)
            surfaceRenderer?.requestDraw()
        }

        override fun onClick(x: Float, y: Float) {
            surfaceRenderer?.tap(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            surfaceRenderer?.scroll(-distanceX, -distanceY)
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            surfaceRenderer?.scale(scaleFactor)
        }
    }

    // Map-strip actions must stay icon-only. A title makes NavigationTemplate reject the strip.
    private val mapActionStrip: ActionStrip = ActionStrip.Builder()
        .addAction(iconAction(R.drawable.ic_zoom_in) { zoomIn() })
        .addAction(iconAction(R.drawable.ic_zoom_out) { zoomOut() })
        .addAction(iconAction(R.drawable.ic_recenter) { recenter() })
        .addAction(Action.PAN)
        .build()

    init {
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(surfaceCallback)
        } catch (error: Exception) {
            OaLog.w(TAG, "Surface access is unavailable", error)
        }
        try {
            navigationManager = carContext.getCarService(NavigationManager::class.java)?.also { mgr ->
                mgr.setNavigationManagerCallback(
                    object : NavigationManagerCallback {
                        override fun onStopNavigation() {
                            NavigationController.stopNavigation()
                            syncNavigationManager()
                            surfaceRenderer?.requestDraw()
                        }

                        override fun onAutoDriveEnabled() {
                            NavigationController.onAutoDriveEnabled()
                            mainHandler.removeCallbacks(autoDriveTick)
                            mainHandler.post(autoDriveTick)
                        }
                    },
                )
            }
        } catch (error: Exception) {
            OaLog.w(TAG, "NavigationManager access is unavailable", error)
        }
        DistractionLock.onCarSessionStarted()
        StageWebController.setActive(false)
        AaosHostUx.start(carContext) { ux -> onHostUx(ux) }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    LiveDataStore.addListener(dataListener)
                    applyPendingNavigation()
                    syncNavigationManager()
                    surfaceRenderer?.requestDraw()
                    refreshTemplate()
                }

                override fun onStop(owner: LifecycleOwner) {
                    LiveDataStore.removeListener(dataListener)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    mainHandler.removeCallbacks(autoDriveTick)
                    AaosHostUx.stop()
                    DistractionLock.onCarSessionEnded()
                    StageWebController.setActive(false)
                    NavigationAlerts.clear(carContext)
                    if (hostNavStarted) {
                        try {
                            navigationManager?.navigationEnded()
                        } catch (_: Exception) {
                        }
                        hostNavStarted = false
                    }
                    try {
                        navigationManager?.clearNavigationManagerCallback()
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }

    internal fun syncNavigationManager() {
        val mgr = navigationManager ?: return
        val state = NavigationController.navState
        when (state) {
            NavState.ROUTING -> {
                tripStamp = ""
                try {
                    if (!hostNavStarted) {
                        mgr.navigationStarted()
                        hostNavStarted = true
                    }
                    mgr.updateTrip(Trip.Builder().setLoading(true).build())
                } catch (error: Exception) {
                    OaLog.w(TAG, "NavigationManager routing sync failed", error)
                }
            }

            NavState.ACTIVE -> {
                try {
                    if (!hostNavStarted) {
                        mgr.navigationStarted()
                        hostNavStarted = true
                    }
                    val tripKey = tripKey()
                    if (tripKey == tripStamp) return
                    tripStamp = tripKey
                    val dest = NavigationController.destination
                    val step = NavigationController.steps.getOrNull(NavigationController.currentStepIndex)
                    val nowMs = System.currentTimeMillis()
                    val tz = TimeZone.getDefault()
                    val remDistMeters = NavigationController.remainingDistanceMeters.coerceAtLeast(0.0)
                    val remDurSec = NavigationController.displayedRemainingDurationSeconds().coerceAtLeast(0.0).toLong()
                    val stepDistMeters = NavigationController.nextStepDistanceMeters.coerceAtLeast(0.0)
                    val stepDurSec = (step?.durationSeconds ?: 0.0).coerceAtLeast(0.0).toLong()

                    val tripBuilder = Trip.Builder().setLoading(false)
                    if (dest != null) {
                        val carDest = Destination.Builder()
                            .setName(dest.name.ifBlank { "Destination" })
                            .setAddress(dest.displayName.ifBlank { dest.name })
                            .build()
                        val destArrival = DateTimeWithZone.create(
                            nowMs + TimeUnit.SECONDS.toMillis(remDurSec),
                            tz,
                        )
                        val destEstimate = TravelEstimate.Builder(
                            toCarDistance(remDistMeters),
                            destArrival,
                        )
                            .setRemainingTimeSeconds(remDurSec)
                            .build()
                        tripBuilder.addDestination(carDest, destEstimate)
                    }
                    if (step != null) {
                        val carStep = carStep(step)
                        val stepArrival = DateTimeWithZone.create(
                            nowMs + TimeUnit.SECONDS.toMillis(stepDurSec),
                            tz,
                        )
                        val stepEstimate = TravelEstimate.Builder(
                            toCarDistance(stepDistMeters),
                            stepArrival,
                        )
                            .setRemainingTimeSeconds(stepDurSec)
                            .build()
                        tripBuilder.addStep(carStep, stepEstimate)
                    }
                    mgr.updateTrip(tripBuilder.build())
                } catch (error: Exception) {
                    OaLog.w(TAG, "NavigationManager active trip sync failed", error)
                }
            }

            else -> {
                tripStamp = ""
                if (hostNavStarted) {
                    try {
                        mgr.navigationEnded()
                    } catch (error: Exception) {
                        OaLog.w(TAG, "NavigationManager end failed", error)
                    }
                    hostNavStarted = false
                }
            }
        }
    }

    private fun toCarDistance(meters: Double): Distance =
        if (meters >= 1000.0) {
            Distance.create(meters / 1000.0, Distance.UNIT_KILOMETERS)
        } else {
            Distance.create(meters.coerceAtLeast(1.0), Distance.UNIT_METERS)
        }

    private fun mapManeuverType(step: RouteStep): Int {
        val m = step.instruction.lowercase()
        return when {
            m.startsWith("head") -> Maneuver.TYPE_DEPART
            m.startsWith("arrive") -> Maneuver.TYPE_DESTINATION
            m.startsWith("roundabout") -> Maneuver.TYPE_ROUNDABOUT_ENTER_CW
            m.contains("uturn") -> Maneuver.TYPE_U_TURN_LEFT
            m.contains("sharp left") -> Maneuver.TYPE_TURN_SHARP_LEFT
            m.contains("slight left") -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            m.contains("left") -> Maneuver.TYPE_TURN_NORMAL_LEFT
            m.contains("sharp right") -> Maneuver.TYPE_TURN_SHARP_RIGHT
            m.contains("slight right") -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            m.contains("right") -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            else -> Maneuver.TYPE_STRAIGHT
        }
    }

    fun offerNavigateIntent(intent: Intent?) {
        pendingNav = NavigateIntent.parse(intent?.data) ?: return
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            applyPendingNavigation()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = NavigationTemplate.Builder()
            .setActionStrip(actionStrip())
            .setMapActionStrip(mapActionStrip)
        applyHostGuidance(builder)
        templateStamp = navStamp()
        return builder.build()
    }

    private fun onHostUx(ux: DistractionLock.HostUx) {
        DistractionLock.onHostUx(ux)
        if (DistractionLock.suppressesSharedDistractions()) {
            StageWebController.setActive(false)
            surfaceRenderer?.dismissDistractions()
        }
        refreshTemplate()
    }

    private fun refreshTemplate() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val stamp = navStamp()
        if (stamp == templateStamp) return
        templateStamp = stamp
        invalidate()
    }

    private fun navStamp(): String {
        val step = NavigationController.steps.getOrNull(NavigationController.currentStepIndex)
        val distanceBucket = (NavigationController.nextStepDistanceMeters / 50.0).toInt()
        val durationBucket = (NavigationController.displayedRemainingDurationSeconds() / 30.0).toInt()
        return listOf(
            NavigationController.navState.name,
            NavigationController.currentStepIndex.toString(),
            step?.instruction.orEmpty(),
            distanceBucket.toString(),
            durationBucket.toString(),
            DistractionLock.hostUx.name,
            NavigationController.offRoute.toString(),
        ).joinToString("|")
    }

    private fun tripKey(): String {
        val step = NavigationController.steps.getOrNull(NavigationController.currentStepIndex)
        val dest = NavigationController.destination
        return listOf(
            NavigationController.navState.name,
            NavigationController.currentStepIndex.toString(),
            step?.instruction.orEmpty(),
            dest?.title.orEmpty(),
            NavigationController.offRoute.toString(),
        ).joinToString("|")
    }

    private fun applyPendingNavigation() {
        val request = pendingNav ?: return
        pendingNav = null
        if (request.hasCoordinates) {
            NavigationController.selectDestination(
                SearchCandidate(
                    title = request.query?.takeIf { it.isNotBlank() } ?: "Pinned place",
                    subtitle = "",
                    latitude = request.latitude ?: return,
                    longitude = request.longitude ?: return,
                ),
                autoStartNav = true,
            )
            return
        }
        val query = request.query?.trim().orEmpty()
        if (query.isEmpty()) return
        NavigationController.searchAddress(query)
        screenManager.push(PlaceSearchScreen(carContext))
    }

    private fun applyHostGuidance(builder: NavigationTemplate.Builder) {
        val state = NavigationController.navState
        when (state) {
            NavState.ROUTING -> {
                builder.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
            }

            NavState.ACTIVE -> {
                if (NavigationController.offRoute) {
                    builder.setNavigationInfo(
                        MessageInfo.Builder("Off the route")
                            .setText("Tap Recalculate for a new route from here")
                            .build(),
                    )
                    publishAlert("off", "Off the route", "Recalculate for a new route")
                } else {
                val step = NavigationController.steps.getOrNull(NavigationController.currentStepIndex)
                val nowMs = System.currentTimeMillis()
                val tz = TimeZone.getDefault()
                if (step != null) {
                    val routing = RoutingInfo.Builder()
                        .setCurrentStep(
                            carStep(step),
                            toCarDistance(NavigationController.nextStepDistanceMeters),
                        )
                    val next = NavigationController.steps.getOrNull(NavigationController.currentStepIndex + 1)
                    if (next != null) routing.setNextStep(carStep(next))
                    builder.setNavigationInfo(routing.build())
                }
                val dest = NavigationController.destination
                if (dest != null) {
                    val remDurSec = NavigationController.displayedRemainingDurationSeconds().coerceAtLeast(0.0).toLong()
                    val arrival = DateTimeWithZone.create(
                        nowMs + TimeUnit.SECONDS.toMillis(remDurSec),
                        tz,
                    )
                    builder.setDestinationTravelEstimate(
                        TravelEstimate.Builder(
                            toCarDistance(NavigationController.remainingDistanceMeters),
                            arrival,
                        ).setRemainingTimeSeconds(remDurSec).build(),
                    )
                }
                val title = step?.instruction?.ifBlank { "Continue" } ?: "Navigation"
                val distanceBucket = (NavigationController.nextStepDistanceMeters / 50.0).toInt()
                val text = buildString {
                    append(NavigationController.formatDistance(NavigationController.remainingDistanceMeters))
                    append(" · ")
                    append(NavigationController.formatDuration(NavigationController.displayedRemainingDurationSeconds()))
                }
                publishAlert("$title|$distanceBucket", title, text)
                }
            }

            NavState.LOCATION_REQUIRED,
            NavState.ROUTE_ERROR,
            -> {
                val message = NavigationController.statusMessage ?: state.name
                builder.setNavigationInfo(
                    MessageInfo.Builder(message).setText("Navigation is paused").build(),
                )
                alertKey = ""
                NavigationAlerts.clear(carContext)
            }

            else -> {
                alertKey = ""
                NavigationAlerts.clear(carContext)
            }
        }
    }

    private fun publishAlert(key: String, title: String, text: String) {
        if (key == alertKey) return
        alertKey = key
        NavigationAlerts.publish(carContext, title, text)
    }

    private fun carStep(step: RouteStep): Step {
        val builder = Step.Builder(step.instruction.ifBlank { "Continue" })
            .setManeuver(Maneuver.Builder(mapManeuverType(step)).build())
            .setRoad(step.street.ifBlank { step.instruction })
        if (step.lanes.isNotEmpty()) {
            step.lanes.forEach { builder.addLane(toCarLane(it)) }
            builder.setLanesImage(
                CarIcon.Builder(IconCompat.createWithBitmap(laneImage(step.lanes))).build(),
            )
        }
        return builder.build()
    }

    private fun toCarLane(lane: RouteLane): Lane {
        val builder = Lane.Builder()
        val shapes = lane.indications.map { laneShape(it) }.ifEmpty { listOf(LaneDirection.SHAPE_UNKNOWN) }
        shapes.forEach { shape ->
            builder.addDirection(LaneDirection.create(shape, lane.recommended))
        }
        return builder.build()
    }

    private fun laneShape(indication: String): Int = when (indication.lowercase()) {
        "straight" -> LaneDirection.SHAPE_STRAIGHT
        "slight left" -> LaneDirection.SHAPE_SLIGHT_LEFT
        "left" -> LaneDirection.SHAPE_NORMAL_LEFT
        "sharp left" -> LaneDirection.SHAPE_SHARP_LEFT
        "slight right" -> LaneDirection.SHAPE_SLIGHT_RIGHT
        "right" -> LaneDirection.SHAPE_NORMAL_RIGHT
        "sharp right" -> LaneDirection.SHAPE_SHARP_RIGHT
        "uturn" -> LaneDirection.SHAPE_U_TURN_LEFT
        else -> LaneDirection.SHAPE_UNKNOWN
    }

    private fun laneImage(lanes: List<RouteLane>): Bitmap {
        val height = 48
        val width = 48 * lanes.size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        lanes.forEachIndexed { index, lane ->
            paint.color = if (lane.recommended) Color.WHITE else Color.argb(150, 255, 255, 255)
            paint.strokeWidth = if (lane.recommended) 4f else 2.5f
            drawLaneArrow(canvas, paint, 24f + index * 48f, 26f, lane.indications.firstOrNull())
        }
        return bitmap
    }

    private fun drawLaneArrow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, indication: String?) {
        val name = indication?.lowercase().orEmpty()
        val path = android.graphics.Path()
        when {
            name.contains("left") -> {
                path.moveTo(cx + 8f, cy + 10f)
                path.lineTo(cx + 8f, cy - 2f)
                path.lineTo(cx - 10f, cy - 2f)
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - 10f, cy - 2f, cx - 4f, cy - 8f, paint)
                canvas.drawLine(cx - 10f, cy - 2f, cx - 4f, cy + 4f, paint)
            }
            name.contains("right") -> {
                path.moveTo(cx - 8f, cy + 10f)
                path.lineTo(cx - 8f, cy - 2f)
                path.lineTo(cx + 10f, cy - 2f)
                canvas.drawPath(path, paint)
                canvas.drawLine(cx + 10f, cy - 2f, cx + 4f, cy - 8f, paint)
                canvas.drawLine(cx + 10f, cy - 2f, cx + 4f, cy + 4f, paint)
            }
            else -> {
                canvas.drawLine(cx, cy + 12f, cx, cy - 10f, paint)
                canvas.drawLine(cx, cy - 10f, cx - 6f, cy - 2f, paint)
                canvas.drawLine(cx, cy - 10f, cx + 6f, cy - 2f, paint)
            }
        }
    }

    private fun actionStrip(): ActionStrip {
        val strip = ActionStrip.Builder()
        strip.addAction(
            Action.Builder()
                .setTitle("Search")
                .setIcon(icon(R.drawable.ic_search))
                .setOnClickListener(ParkedOnlyOnClickListener.create { openSearch() })
                .build(),
        )
        if (NavigationController.navState == NavState.ACTIVE ||
            NavigationController.navState == NavState.ROUTING
        ) {
            strip.addAction(iconAction(R.drawable.ic_stop, "Stop") {
                NavigationController.stopNavigation()
                syncNavigationManager()
                refreshTemplate()
            })
        }
        if (NavigationController.offRoute) {
            strip.addAction(iconAction(R.drawable.ic_recalculate, "Recalculate") {
                NavigationController.recalculate()
                syncNavigationManager()
                refreshTemplate()
            })
        } else {
            strip.addAction(iconAction(R.drawable.ic_heading, "Heading") {
                MapCameraStore.toggleOrientation()
                surfaceRenderer?.requestDraw()
            })
        }
        strip.addAction(iconAction(R.drawable.ic_settings, "Settings") { openPhoneSetup() })
        return strip.build()
    }

    private fun openSearch() {
        screenManager.push(PlaceSearchScreen(carContext))
    }

    private fun openPhoneSetup() {
        CarToast.makeText(
            carContext,
            "Continue on your phone only when it is safe.",
            CarToast.LENGTH_LONG,
        ).show()
        try {
            carContext.startCarApp(
                Intent(carContext, SetupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (error: Exception) {
            OaLog.w(TAG, "Could not open phone setup", error)
        }
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    private fun iconAction(resId: Int, title: String? = null, onClick: () -> Unit): Action {
        val builder = Action.Builder().setIcon(icon(resId)).setOnClickListener(onClick)
        if (title != null) builder.setTitle(title)
        return builder.build()
    }

    private fun zoomIn() {
        MapCameraStore.zoomIn()
        surfaceRenderer?.requestDraw()
    }

    private fun zoomOut() {
        MapCameraStore.zoomOut()
        surfaceRenderer?.requestDraw()
    }

    private fun recenter() {
        MapCameraStore.recenter()
        surfaceRenderer?.requestDraw()
    }

    companion object {
        private const val TAG = "OmarchyScreen"
    }
}
