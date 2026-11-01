package com.omarchy.auto

import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileRenderer

/**
 * Draws the shared five-tile renderer into the Android Auto map surface.
 * The host owns the surrounding template and action strip.
 */
class CarSurfaceRenderer(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
) {
    private val thread = HandlerThread("omarchy-car-surface").apply { start() }
    private val handler = Handler(thread.looper)
    private val renderer = TileRenderer()

    private val tick = object : Runnable {
        override fun run() {
            draw()
            handler.postDelayed(this, 1000L)
        }
    }

    fun start() {
        handler.post(tick)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        try {
            // Do not let the host destroy/reuse the Surface while a draw is
            // still in flight.
            thread.join(1000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun requestDraw() {
        handler.removeCallbacks(tick)
        handler.post {
            draw()
            handler.postDelayed(tick, 1000L)
        }
    }

    fun setSafeArea(rect: Rect) {
        renderer.setSafeArea(rect)
    }

    fun tap(x: Float, y: Float) {
        handler.post {
            val hit = renderer.hitTest(x, y) ?: return@post
            when (hit.target) {
                TileRenderer.Target.THEME -> {
                    ThemeStore.next()
                    draw()
                }

                TileRenderer.Target.THEME_CHIP -> {
                    ThemeStore.select(hit.index)
                    draw()
                }

                else -> Unit
            }
        }
    }

    private fun draw() {
        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null) ?: return
            renderer.draw(
                canvas = canvas,
                width = canvas.width,
                height = canvas.height,
                state = LiveDataStore.state,
                themeIndex = ThemeStore.index,
                mode = TileRenderer.Mode.CAR,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Surface draw failed", error)
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (error: Exception) {
                    Log.w(TAG, "Surface post failed", error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CarSurfaceRenderer"
    }
}
