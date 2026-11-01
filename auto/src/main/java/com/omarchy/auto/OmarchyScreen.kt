package com.omarchy.auto

import android.content.Intent
import android.graphics.Rect
import android.util.Log
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
import com.omarchy.auto.core.ThemeStore

class OmarchyScreen(carContext: CarContext) : Screen(carContext) {

    private var surfaceRenderer: CarSurfaceRenderer? = null
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null

    private val dataListener: (DashboardState) -> Unit = {
        surfaceRenderer?.requestDraw()
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            val surface = surfaceContainer.surface ?: return
            surfaceRenderer?.stop()
            surfaceRenderer = CarSurfaceRenderer(surface).also {
                applySafeArea(it)
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
            this@OmarchyScreen.visibleArea = Rect(visibleArea)
            surfaceRenderer?.let(::applySafeArea)
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            this@OmarchyScreen.stableArea = Rect(stableArea)
            surfaceRenderer?.let(::applySafeArea)
        }

        override fun onClick(x: Float, y: Float) {
            surfaceRenderer?.tap(x, y)
        }
    }

    private val themeAction: Action = Action.Builder()
        .setTitle("Theme")
        .setIcon(
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_theme),
            ).build(),
        )
        .setOnClickListener {
            ThemeStore.next()
            surfaceRenderer?.requestDraw()
        }
        .build()

    init {
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(surfaceCallback)
        } catch (error: Exception) {
            Log.w(TAG, "Surface access is unavailable", error)
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

    private fun applySafeArea(renderer: CarSurfaceRenderer) {
        // Stable is the persistent area; visible may be dynamic host chrome.
        // Prefer stable, and intersect if both have been provided.
        val stable = stableArea
        val visible = visibleArea
        val area = when {
            stable != null && !stable.isEmpty && visible != null && !visible.isEmpty -> {
                Rect(stable).apply { intersect(visible) }
            }

            stable != null && !stable.isEmpty -> stable
            visible != null && !visible.isEmpty -> visible
            else -> null
        }
        if (area != null) renderer.setSafeArea(area)
    }

    override fun onGetTemplate(): Template {
        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(themeAction)
                    .build(),
            )
            .build()
    }

    companion object {
        private const val TAG = "OmarchyScreen"
    }
}
