package com.omarchy.auto

import android.content.Intent
import android.graphics.Rect
import com.omarchy.auto.core.diag.OaLog
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore

class OmarchyScreen(carContext: CarContext) : Screen(carContext) {

    private var surfaceRenderer: CarSurfaceRenderer? = null

    private val dataListener: (DashboardState) -> Unit = {
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
    }

    private val settingsAction: Action = Action.Builder()
        .setIcon(
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_settings),
            ).build(),
        )
        .setOnClickListener { surfaceRenderer?.openSettings() }
        .build()

    init {
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(surfaceCallback)
        } catch (error: Exception) {
            OaLog.w(TAG, "Surface access is unavailable", error)
        }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    LiveDataStore.addListener(dataListener)
                    surfaceRenderer?.requestDraw()
                }

                override fun onStop(owner: LifecycleOwner) {
                    LiveDataStore.removeListener(dataListener)
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        // NavigationTemplate.Builder.build() throws without an action strip.
        // Keep it to one icon-only gear that toggles the Home stage (setup and
        // themes) while the canvas renders full-bleed without a duplicate button.
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(settingsAction).build())
            .build()
    }

    companion object {
        private const val TAG = "OmarchyScreen"
    }
}
