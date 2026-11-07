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
import com.omarchy.auto.core.LaunchTargetStore
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MapCameraStore
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.MediaWatcher
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.OmarchyNotificationListener
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
) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("omarchy-car-surface").apply { start() }
    private val handler = Handler(thread.looper)
    private val renderer = TileRenderer()
    private val drawLock = ReentrantLock()
    private val released = AtomicBoolean(false)
    @Volatile
    private var backgroundKey = ""

    @Volatile
    private var stopped = false

    init {
        BackgroundStore.init(appContext)
        LaunchTargetStore.init(appContext)
        RailConfigStore.init(appContext)
        StageWebController.init(appContext)
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
            when (hit.target) {
                TileRenderer.Target.THEME -> {
                    renderer.show(TileId.HOME)
                    draw()
                }

                TileRenderer.Target.THEME_CHIP -> {
                    ThemeStore.select(hit.index)
                    refreshBackground()
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
                    renderer.scrollNotifications(-1000)
                    draw()
                }

                TileRenderer.Target.NOTIF_SCROLL_UP -> {
                    renderer.scrollNotifications(-1)
                    draw()
                }

                TileRenderer.Target.NOTIF_SCROLL_DOWN -> {
                    renderer.scrollNotifications(1)
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

                TileRenderer.Target.NOTIF_ROW_DISMISS -> {
                    hit.key?.let { OmarchyNotificationListener.dismiss(appContext, it) }
                    draw()
                }

                TileRenderer.Target.OVERLAY_SCROLL_UP -> {
                    renderer.scrollOverlay(-2)
                    draw()
                }

                TileRenderer.Target.OVERLAY_SCROLL_DOWN -> {
                    renderer.scrollOverlay(2)
                    draw()
                }

                TileRenderer.Target.SETUP -> {
                    renderer.toggleHomeSetup()
                    draw()
                }

                TileRenderer.Target.RAIL_CONFIG_TOGGLE -> {
                    hit.railItem?.let { RailConfigStore.toggle(appContext, it) }
                    draw()
                }

                TileRenderer.Target.MEDIA_TARGET_SELECT -> {
                    val key = hit.key ?: return@post
                    LaunchTargetStore.select(appContext, key)
                    StageWebApp.entries.firstOrNull { it.id == key }?.let { app ->
                        StageWebController.selectApp(app)
                    }
                    draw()
                }

                TileRenderer.Target.MEDIA_SESSION_SELECT -> {
                    val pkg = hit.key ?: return@post
                    renderer.closeWebApp()
                    MediaWatcher.selectSession(pkg)
                    draw()
                }

                TileRenderer.Target.WEB_APP_OPEN -> {
                    val app = hit.webApp ?: return@post
                    renderer.openWebApp(app)
                    draw()
                }

                TileRenderer.Target.WEB_BACK -> {
                    StageWebController.goBack()
                    draw()
                }

                TileRenderer.Target.WEB_RELOAD -> {
                    StageWebController.reload()
                    draw()
                }

                TileRenderer.Target.WEB_SEARCH -> {
                    renderer.openKeyboard(TileRenderer.KeyboardMode.WEB_INPUT)
                    draw()
                }

                TileRenderer.Target.WEB_SCROLL_UP -> {
                    StageWebController.scrollBy(-260)
                    draw()
                }

                TileRenderer.Target.WEB_SCROLL_DOWN -> {
                    StageWebController.scrollBy(260)
                    draw()
                }

                TileRenderer.Target.WEB_CLOSE -> {
                    renderer.closeWebApp()
                    draw()
                }

                TileRenderer.Target.WEB_STAGE_TAP -> {
                    StageWebController.tapNormalized(hit.normX, hit.normY)
                    draw()
                }

                TileRenderer.Target.MAP_ZOOM_IN -> {
                    MapCameraStore.zoomIn()
                    draw()
                }

                TileRenderer.Target.MAP_ZOOM_OUT -> {
                    MapCameraStore.zoomOut()
                    draw()
                }

                TileRenderer.Target.MAP_RECENTER -> {
                    MapCameraStore.recenter()
                    draw()
                }

                TileRenderer.Target.MAP_SEARCH_OPEN -> {
                    renderer.openKeyboard(
                        TileRenderer.KeyboardMode.MAP_SEARCH,
                        NavigationController.searchQuery,
                    )
                    draw()
                }

                TileRenderer.Target.MAP_NAV_TOGGLE -> {
                    when (NavigationController.navState) {
                        NavState.ROUTE_ERROR,
                        NavState.LOCATION_REQUIRED -> {
                            NavigationController.retryRoute()
                        }
                        else -> {
                            if (NavigationController.destination == null && !NavigationController.navActive) {
                                renderer.openKeyboard(
                                    TileRenderer.KeyboardMode.MAP_SEARCH,
                                    NavigationController.searchQuery,
                                )
                            } else {
                                NavigationController.toggleNavigation()
                            }
                        }
                    }
                    draw()
                }

                TileRenderer.Target.MAP_CANDIDATE_SELECT -> {
                    val cand = NavigationController.searchResults.getOrNull(hit.index)
                    if (cand != null) {
                        NavigationController.selectDestination(cand, autoStartNav = true)
                        renderer.closeKeyboard()
                        draw()
                    }
                }

                TileRenderer.Target.KB_KEY -> {
                    hit.key?.let { renderer.appendKeyboardChar(it) }
                    draw()
                }

                TileRenderer.Target.KB_BACKSPACE -> {
                    renderer.backspaceKeyboard()
                    draw()
                }

                TileRenderer.Target.KB_SPACE -> {
                    renderer.appendKeyboardChar(" ")
                    draw()
                }

                TileRenderer.Target.KB_CLEAR -> {
                    renderer.clearKeyboard()
                    draw()
                }

                TileRenderer.Target.KB_SUBMIT -> {
                    when (renderer.keyboardMode) {
                        TileRenderer.KeyboardMode.MAP_SEARCH -> {
                            NavigationController.searchAddress(renderer.keyboardQuery)
                        }
                        TileRenderer.KeyboardMode.WEB_INPUT -> {
                            StageWebController.submitQuery(renderer.keyboardQuery)
                            renderer.closeKeyboard()
                        }
                        TileRenderer.KeyboardMode.NONE -> Unit
                    }
                    draw()
                }

                TileRenderer.Target.KB_CLOSE -> {
                    renderer.closeKeyboard()
                    draw()
                }

                TileRenderer.Target.RAIL_SCROLL_UP -> {
                    renderer.scrollRail(-1)
                    draw()
                }

                TileRenderer.Target.RAIL_SCROLL_DOWN -> {
                    renderer.scrollRail(1)
                    draw()
                }

                TileRenderer.Target.RAIL_ITEM -> {
                    val item = hit.railItem ?: return@post
                    renderer.selectRailItem(item)
                    draw()
                }

                TileRenderer.Target.STAGE_OPEN -> {
                    renderer.selected?.let(::launchStageTarget)
                }

                TileRenderer.Target.TILE_DRIVE -> toggle(TileId.DRIVE)
                TileRenderer.Target.TILE_MEDIA -> toggle(TileId.MEDIA)
                TileRenderer.Target.TILE_COMMS -> toggle(TileId.COMMS)
                TileRenderer.Target.TILE_VEHICLE -> toggle(TileId.VEHICLE)
                TileRenderer.Target.TILE_HOME -> toggle(TileId.HOME)
            }
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

    private fun toggle(tile: TileId) {
        renderer.toggle(tile)
        draw()
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
