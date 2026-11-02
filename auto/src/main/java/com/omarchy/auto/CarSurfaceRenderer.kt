package com.omarchy.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.OmarchyNotificationListener
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * Draws the shared five-tile renderer into the Android Auto map surface.
 * The host owns the surrounding template and action strip.
 *
 * Car App Library requires the app to release every Surface it receives. This
 * class is the single owner of that release so a host teardown cannot race a
 * draw: if stop() cannot quiesce the draw thread, the draw thread releases the
 * Surface after its final unlock.
 */
class CarSurfaceRenderer(
    private val surface: Surface,
    context: Context,
) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("omarchy-car-surface").apply { start() }
    private val handler = Handler(thread.looper)
    private val renderer = TileRenderer().apply {
        setBackground(AppBackground.load(context))
    }
    private val drawLock = ReentrantLock()
    private val released = AtomicBoolean(false)

    @Volatile
    private var stopped = false

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            draw()
            if (!stopped) handler.postDelayed(this, 1000L)
        }
    }

    private val immediateDraw = Runnable {
        if (!stopped) draw()
    }

    fun start() {
        if (stopped) return
        handler.post(tick)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()

        val quiesced = try {
            drawLock.tryLock(250L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (quiesced) {
            drawLock.unlock()
            releaseSurface()
        }
        // If not quiesced, the in-flight draw releases the Surface in draw().
    }

    fun requestDraw() {
        if (stopped) return
        handler.removeCallbacks(immediateDraw)
        handler.post(immediateDraw)
    }

    fun setSafeArea(rect: Rect) {
        renderer.setSafeArea(rect)
    }

    fun tap(x: Float, y: Float) {
        if (stopped) return
        handler.post {
            if (stopped) return@post
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

                TileRenderer.Target.PLAY_PAUSE -> {
                    MediaTransport.toggle()
                    draw()
                }

                TileRenderer.Target.PREV_TRACK -> {
                    MediaTransport.previous()
                    draw()
                }

                TileRenderer.Target.NEXT_TRACK -> {
                    MediaTransport.next()
                    draw()
                }

                TileRenderer.Target.NOTIF_REFRESH -> {
                    OmarchyNotificationListener.refresh(appContext)
                    draw()
                }

                TileRenderer.Target.NOTIF_CLEAR -> {
                    OmarchyNotificationListener.dismissDisplayed(appContext)
                    draw()
                }

                TileRenderer.Target.NOTIFICATION -> {
                    renderer.openNotification(hit.key)
                    draw()
                }

                TileRenderer.Target.NOTIF_CLOSE,
                TileRenderer.Target.OVERLAY_DISMISS,
                -> {
                    renderer.closeNotification()
                    draw()
                }

                TileRenderer.Target.NOTIF_DISMISS_ONE -> {
                    hit.key?.let { OmarchyNotificationListener.dismiss(appContext, it) }
                    renderer.closeNotification()
                    draw()
                }

                TileRenderer.Target.TILE_DRIVE -> toggle(TileId.DRIVE)
                TileRenderer.Target.TILE_MEDIA -> toggle(TileId.MEDIA)
                TileRenderer.Target.TILE_COMMS -> toggle(TileId.COMMS)
                TileRenderer.Target.TILE_VEHICLE -> toggle(TileId.VEHICLE)
                TileRenderer.Target.TILE_HOME -> toggle(TileId.HOME)
                else -> Unit
            }
        }
    }

    private fun toggle(tile: TileId) {
        renderer.toggle(tile)
        draw()
    }

    private fun draw() {
        if (stopped) {
            releaseSurface()
            return
        }
        drawLock.lock()
        try {
            if (!stopped) {
                var canvas: Canvas? = null
                try {
                    canvas = surface.lockCanvas(null)
                    if (canvas != null) {
                        renderer.draw(
                            canvas = canvas,
                            width = canvas.width,
                            height = canvas.height,
                            state = LiveDataStore.state,
                            themeIndex = ThemeStore.index,
                            mode = TileRenderer.Mode.CAR,
                        )
                    }
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
        } finally {
            drawLock.unlock()
        }
        if (stopped) releaseSurface()
    }

    private fun releaseSurface() {
        if (!released.compareAndSet(false, true)) return
        try {
            surface.release()
        } catch (error: Exception) {
            Log.w(TAG, "Surface release failed", error)
        }
    }

    companion object {
        private const val TAG = "CarSurfaceRenderer"
    }
}
