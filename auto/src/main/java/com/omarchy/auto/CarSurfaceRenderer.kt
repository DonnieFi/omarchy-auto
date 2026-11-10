package com.omarchy.auto

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import com.omarchy.auto.core.diag.OaLog
import android.view.Surface
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.BackgroundStore
import com.omarchy.auto.core.DisplaySettings
import com.omarchy.auto.core.LaunchTargetStore
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.RailConfigStore
import com.omarchy.auto.core.SetupActivity
import com.omarchy.auto.core.StageWebApp
import com.omarchy.auto.core.StageWebController
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
    private val host: CarShellHost,
) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("omarchy-car-surface").apply { start() }
    private val handler = Handler(thread.looper)
    private val renderer = TileRenderer()
    private val dispatch by lazy {
        CarShellDispatch(
            renderer = renderer,
            appContext = appContext,
            host = host,
            refreshBackground = ::refreshBackground,
            launchStage = ::launchStageTarget,
        )
    }
    private val drawLock = ReentrantLock()
    private val released = AtomicBoolean(false)
    @Volatile
    private var backgroundKey = ""

    @Volatile
    private var stopped = false

    init {
        BackgroundStore.init(appContext)
        DisplaySettings.init(appContext)
        LaunchTargetStore.init(appContext)
        RailConfigStore.init(appContext)
        StageWebController.init(appContext)
        if (NavigationController.navState != NavState.IDLE) {
            renderer.show(TileId.DRIVE)
        }
        refreshBackground()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            draw()
            if (!stopped) handler.postDelayed(this, millisToNextMinute())
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
        StageWebController.setActive(false)
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

    fun openSettings() {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            renderer.toggle(TileId.HOME)
            draw()
        }
    }

    fun showTile(tile: TileId) {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            renderer.show(tile)
            draw()
        }
    }

    fun setSafeArea(rect: Rect?) {
        renderer.setSafeArea(rect)
    }

    fun dismissDistractions() {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            renderer.closeKeyboard()
            renderer.closeWebApp()
            renderer.closeNotification()
            StageWebController.setActive(false)
            draw()
        }
    }

    fun scroll(dx: Float, dy: Float) {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            val state = LiveDataStore.state
            if (renderer.scroll(0f, 0f, dx, dy, state.latitude, state.longitude)) {
                draw()
            }
        }
    }

    fun scale(scaleFactor: Float) {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            if (renderer.scale(scaleFactor)) {
                draw()
            }
        }
    }

    fun tap(x: Float, y: Float) {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            val hit = renderer.hitTest(x, y) ?: return@post
            if (dispatch.apply(hit)) draw()
        }
    }

    /** Puts the drive map in the shell stage. Used for NAVIGATE and geo intents. */
    fun showMap() {
        if (stopped) return
        handler.post {
            if (stopped) return@post
            renderer.show(TileId.DRIVE)
            draw()
        }
    }

    private fun launchStageTarget(tile: TileId) {
        when (tile) {
            TileId.DRIVE -> openTarget(
                listOf("com.google.android.apps.maps"),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
            )

            TileId.MEDIA -> {
                val activeWeb = renderer.webStageApp
                if (activeWeb != null) {
                    openTarget(
                        listOf(activeWeb.packageName),
                        Intent(Intent.ACTION_VIEW, Uri.parse(activeWeb.homeUrl)),
                    )
                    return
                }
                val target = LaunchTargetStore.selected(appContext)
                val matchingWeb = StageWebApp.entries.firstOrNull { it.id == target.key }
                if (matchingWeb != null) {
                    renderer.openWebApp(matchingWeb)
                    draw()
                    return
                }
                openTarget(target.packages, null)
            }

            TileId.COMMS -> openTarget(
                emptyList(),
                Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            )

            TileId.VEHICLE -> Unit

            TileId.HOME -> startSafely(Intent(appContext, SetupActivity::class.java))
        }
    }

    private fun openTarget(packages: List<String>, fallback: Intent?) {
        val pm = appContext.packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null && startSafely(intent)) return
        }
        if (fallback != null && fallback.resolveActivity(pm) != null) {
            startSafely(fallback)
        }
    }

    private fun startSafely(intent: Intent): Boolean = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun currentBackgroundKey(): String =
        "${ThemeStore.current().id}:${BackgroundStore.revision}"

    private fun millisToNextMinute(): Long {
        val now = System.currentTimeMillis()
        return 60_000L - (now % 60_000L) + 50L
    }

    private fun refreshBackground() {
        backgroundKey = currentBackgroundKey()
        AppBackground.loadAsync(appContext, ThemeStore.current()) { bitmap ->
            handler.post {
                if (!stopped) {
                    renderer.setBackground(bitmap)
                    draw()
                }
            }
        }
    }

    private fun draw() {
        if (stopped) {
            releaseSurface()
            return
        }
        if (backgroundKey != currentBackgroundKey()) {
            refreshBackground()
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
                    OaLog.w(TAG, "Surface draw failed", error)
                } finally {
                    if (canvas != null) {
                        try {
                            surface.unlockCanvasAndPost(canvas)
                        } catch (error: Exception) {
                            OaLog.w(TAG, "Surface post failed", error)
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
            OaLog.w(TAG, "Surface release failed", error)
        }
    }

    companion object {
        private const val TAG = "CarSurfaceRenderer"
    }
}
