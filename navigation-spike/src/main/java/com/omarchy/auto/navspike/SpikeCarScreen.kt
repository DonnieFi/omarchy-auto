package com.omarchy.auto.navspike

import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.car.app.navigation.NavigationManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.surfaceStableFractionalPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.rallista.car.app.compose.ComposableScreen
import com.stadiamaps.ferrostar.car.app.navigation.NavigationManagerBridge
import com.stadiamaps.ferrostar.car.app.navigation.TurnByTurnNotificationManager
import com.stadiamaps.ferrostar.car.app.template.NavigationTemplateBuilder
import com.stadiamaps.ferrostar.ui.maplibre.car.app.CarAppNavigationView
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SpikeCarScreen(carContext: CarContext) : ComposableScreen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker = SurfaceAreaTracker { surfaceGestureCallback = it }
    private var model by mutableStateOf(SpikeRuntime.viewModel)
    private var camera: NavigationMapState? = null
    private var bridge: NavigationManagerBridge? = null
    init {
        scope.launch {
            SpikeRuntime.status.collectLatest {
                val active = SpikeRuntime.viewModel
                if (model !== active || bridge == null) {
                    bridge?.stop()
                    model = active
                    bridge = active?.let { vm -> NavigationManagerBridge(
                        navigationManager = carContext.getCarService(NavigationManager::class.java),
                        viewModel = vm, context = carContext,
                        notificationManager = TurnByTurnNotificationManager(carContext, smallIconRes = R.drawable.ic_navigation),
                        onStopNavigation = { SpikeRuntime.stop() },
                        // Set simulation on the phone before starting; never silently simulate a live drive.
                        onAutoDriveEnabled = { },
                        isCarForeground = { lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
                    ).also { it.start(scope) } }
                }
                invalidate()
            }
        }
        scope.launch {
            // State collection also keeps the phone-created view model active with the phone screen off.
            SpikeRuntime.status.map { SpikeRuntime.viewModel }.distinctUntilChanged()
                .flatMapLatest { it?.navigationUiState ?: emptyFlow() }
                .collectLatest { invalidate() }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { bridge?.stop(); scope.cancel() }
        })
    }
    @Composable override fun content() {
        model?.let { vm ->
            val mapState = rememberNavigationMapState()
            camera = mapState
            val state by vm.navigationUiState.collectAsState()
            LaunchedEffect(state.isNavigating()) {
                mapState.recenter(isNavigating = state.isNavigating())
            }
            val area by screenSurfaceState(tracker)
            Box(Modifier.fillMaxSize()) {
                CarAppNavigationView(Modifier.fillMaxSize(), SpikeRuntime.STYLE,
                    navigationMapState = mapState, viewModel = vm, surfaceAreaTracker = tracker)
                Box(Modifier.fillMaxSize().padding(surfaceStableFractionalPadding(area?.compositeArea))) {
                    Text("© OpenStreetMap · OpenMapTiles · OpenFreeMap",
                        modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCC181825)).padding(4.dp),
                        color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
    override fun onGetTemplate(): Template {
        val vm = model
        val state = vm?.navigationUiState?.value
        if (state?.isNavigating() == true) {
            return NavigationTemplateBuilder(carContext).setTripState(state.tripState)
                .setOnStopNavigation { SpikeRuntime.stop() }
                .setOnMute(state.isMuted) { vm.toggleMute() }
                .setOnZoom({ camera?.zoomIn() }, { camera?.zoomOut() })
                .setOnCycleCamera(camera?.isTrackingUser) { camera?.recenter(true) }.build()
        }
        return MessageTemplate.Builder(SpikeRuntime.status.value + "\nConfigure and start the route on the phone while parked.\n© OpenStreetMap contributors / OpenMapTiles / OpenFreeMap")
            .setTitle("Omarchy routing alpha").setHeaderAction(Action.APP_ICON).build()
    }
}
