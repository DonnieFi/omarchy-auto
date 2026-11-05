package com.omarchy.auto

import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.RouteStep
import com.omarchy.auto.core.diag.OaLog
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class OmarchyScreen(carContext: CarContext) : Screen(carContext) {

    private var surfaceRenderer: CarSurfaceRenderer? = null
    private var navigationManager: NavigationManager? = null
    internal var hostNavStarted: Boolean = false
        private set

    private val dataListener: (DashboardState) -> Unit = {
        syncNavigationManager()
        surfaceRenderer?.requestDraw()
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
            // Ignore visibleArea insets so the transient ActionStrip never
            // pushes down the top bar or squishes the full-screen layout.
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            // Render full-bleed across the entire SurfaceContainer viewport.
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

    private val settingsAction: Action = Action.Builder()
        .setIcon(
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_settings),
            ).build(),
        )
        .setOnClickListener { surfaceRenderer?.openSettings() }
        .build()

    private val mapActionStrip: ActionStrip = ActionStrip.Builder()
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
                        }
                    },
                )
            }
        } catch (error: Exception) {
            OaLog.w(TAG, "NavigationManager access is unavailable", error)
        }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    LiveDataStore.addListener(dataListener)
                    syncNavigationManager()
                    surfaceRenderer?.requestDraw()
                }

                override fun onStop(owner: LifecycleOwner) {
                    LiveDataStore.removeListener(dataListener)
                }

                override fun onDestroy(owner: LifecycleOwner) {
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
                    val dest = NavigationController.destination
                    val step = NavigationController.steps.getOrNull(NavigationController.currentStepIndex)
                    val nowMs = System.currentTimeMillis()
                    val tz = TimeZone.getDefault()
                    val remDistMeters = NavigationController.remainingDistanceMeters.coerceAtLeast(0.0)
                    val remDurSec = NavigationController.remainingDurationSeconds.coerceAtLeast(0.0).toLong()
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
                        val maneuver = Maneuver.Builder(mapManeuverType(step)).build()
                        val carStep = Step.Builder(step.instruction.ifBlank { "Continue" })
                            .setManeuver(maneuver)
                            .setRoad(step.street.ifBlank { step.instruction })
                            .build()
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

    override fun onGetTemplate(): Template {
        // NavigationTemplate.Builder.build() throws without an action strip.
        // Supply both the settings action strip and the mapActionStrip with
        // Action.PAN so the host delivers pan/zoom callbacks to SurfaceCallback.
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(settingsAction).build())
            .setMapActionStrip(mapActionStrip)
            .build()
    }

    companion object {
        private const val TAG = "OmarchyScreen"
    }
}
