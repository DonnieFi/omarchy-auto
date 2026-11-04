package com.omarchy.auto.launcher

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.BackgroundStore
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LaunchTargetStore
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MapCameraStore
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.MediaWatcher
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.OmarchyNotificationListener
import com.omarchy.auto.core.RailConfigStore
import com.omarchy.auto.core.StageWebApp
import com.omarchy.auto.core.StageWebController
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer

class TileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val renderer = TileRenderer()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downHit: TileRenderer.Hit? = null
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragging = false
    private var lastBackgroundRevision = -1
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (renderer.scale(detector.scaleFactor)) {
                    downHit = null
                    dragging = true
                    invalidate()
                    return true
                }
                return false
            }
        },
    )
    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, millisToNextMinute())
        }
    }

    private val dataListener: (DashboardState) -> Unit = {
        if (BackgroundStore.revision != lastBackgroundRevision) {
            lastBackgroundRevision = BackgroundStore.revision
            refreshBackground()
        }
        postInvalidateOnAnimation()
    }

    var onTileTap: ((TileId) -> Unit)? = null
    var onSetup: (() -> Unit)? = null
    var mode: TileRenderer.Mode = TileRenderer.Mode.PHONE
        set(value) {
            field = value
            invalidate()
        }

    fun selectStage(tile: TileId?) {
        if (tile == null) {
            renderer.selected?.let { renderer.toggle(it) }
        } else {
            renderer.show(tile)
        }
        invalidate()
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Omarchy Auto launcher"
        BackgroundStore.init(context)
        LaunchTargetStore.init(context)
        RailConfigStore.init(context)
        StageWebController.init(context)
        setBackgroundColor(ThemeStore.current().background)
        refreshBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LiveDataStore.addListener(dataListener)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        LiveDataStore.removeListener(dataListener)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(
            canvas = canvas,
            width = width,
            height = height,
            state = LiveDataStore.state,
            themeIndex = ThemeStore.index,
            mode = mode,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                dragging = false
                downHit = renderer.hitTest(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (!dragging &&
                    (kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop)
                ) {
                    dragging = true
                    downHit = null
                }
                if (dragging && !scaleDetector.isInProgress) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    val state = LiveDataStore.state
                    if (renderer.scroll(event.x, event.y, dx, dy, state.latitude, state.longitude)) {
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val start = downHit
                downHit = null
                val wasDragging = dragging
                dragging = false
                if (!wasDragging && start != null) {
                    val end = renderer.hitTest(event.x, event.y)
                    if (start == end) {
                        perform(start)
                        performClick()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                downHit = null
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun perform(hit: TileRenderer.Hit) {
        when (hit.target) {
            TileRenderer.Target.THEME -> {
                renderer.show(TileId.HOME)
                invalidate()
            }

            TileRenderer.Target.THEME_CHIP -> {
                ThemeStore.select(hit.index)
                setBackgroundColor(ThemeStore.current().background)
                refreshBackground()
                invalidate()
            }

            TileRenderer.Target.SETUP -> {
                renderer.toggleHomeSetup()
                invalidate()
            }

            TileRenderer.Target.RAIL_CONFIG_TOGGLE -> {
                hit.railItem?.let { RailConfigStore.toggle(context, it) }
                invalidate()
            }

            TileRenderer.Target.MEDIA_TARGET_SELECT -> {
                val key = hit.key ?: return
                LaunchTargetStore.select(context, key)
                StageWebApp.entries.firstOrNull { it.id == key }?.let { app ->
                    StageWebController.selectApp(app)
                }
                invalidate()
            }

            TileRenderer.Target.MEDIA_SESSION_SELECT -> {
                val pkg = hit.key ?: return
                renderer.closeWebApp()
                MediaWatcher.selectSession(pkg)
                invalidate()
            }

            TileRenderer.Target.WEB_APP_OPEN -> {
                val app = hit.webApp ?: return
                renderer.openWebApp(app)
                invalidate()
            }

            TileRenderer.Target.WEB_BACK -> {
                StageWebController.goBack()
                invalidate()
            }

            TileRenderer.Target.WEB_RELOAD -> {
                StageWebController.reload()
                invalidate()
            }

            TileRenderer.Target.WEB_SEARCH -> {
                renderer.openKeyboard(TileRenderer.KeyboardMode.WEB_INPUT)
                invalidate()
            }

            TileRenderer.Target.WEB_SCROLL_UP -> {
                StageWebController.scrollBy(-260)
                invalidate()
            }

            TileRenderer.Target.WEB_SCROLL_DOWN -> {
                StageWebController.scrollBy(260)
                invalidate()
            }

            TileRenderer.Target.WEB_CLOSE -> {
                renderer.closeWebApp()
                invalidate()
            }

            TileRenderer.Target.WEB_STAGE_TAP -> {
                StageWebController.tapNormalized(hit.normX, hit.normY)
                invalidate()
            }

            TileRenderer.Target.MAP_ZOOM_IN -> {
                MapCameraStore.zoomIn()
                invalidate()
            }

            TileRenderer.Target.MAP_ZOOM_OUT -> {
                MapCameraStore.zoomOut()
                invalidate()
            }

            TileRenderer.Target.MAP_RECENTER -> {
                MapCameraStore.recenter()
                invalidate()
            }

            TileRenderer.Target.MAP_SEARCH_OPEN -> {
                renderer.openKeyboard(
                    TileRenderer.KeyboardMode.MAP_SEARCH,
                    NavigationController.searchQuery,
                )
                invalidate()
            }

            TileRenderer.Target.MAP_NAV_TOGGLE -> {
                if (NavigationController.destination == null && !NavigationController.navActive) {
                    renderer.openKeyboard(
                        TileRenderer.KeyboardMode.MAP_SEARCH,
                        NavigationController.searchQuery,
                    )
                } else {
                    NavigationController.toggleNavigation()
                }
                invalidate()
            }

            TileRenderer.Target.MAP_CANDIDATE_SELECT -> {
                val cand = NavigationController.searchResults.getOrNull(hit.index)
                if (cand != null) {
                    NavigationController.selectDestination(cand, autoStartNav = true)
                    renderer.closeKeyboard()
                    invalidate()
                }
            }

            TileRenderer.Target.KB_KEY -> {
                hit.key?.let { renderer.appendKeyboardChar(it) }
                invalidate()
            }

            TileRenderer.Target.KB_BACKSPACE -> {
                renderer.backspaceKeyboard()
                invalidate()
            }

            TileRenderer.Target.KB_SPACE -> {
                renderer.appendKeyboardChar(" ")
                invalidate()
            }

            TileRenderer.Target.KB_CLEAR -> {
                renderer.clearKeyboard()
                invalidate()
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
                invalidate()
            }

            TileRenderer.Target.KB_CLOSE -> {
                renderer.closeKeyboard()
                invalidate()
            }

            TileRenderer.Target.RAIL_SCROLL_UP -> {
                renderer.scrollRail(-1)
                invalidate()
            }

            TileRenderer.Target.RAIL_SCROLL_DOWN -> {
                renderer.scrollRail(1)
                invalidate()
            }

            TileRenderer.Target.RAIL_ITEM -> {
                val item = hit.railItem ?: return
                renderer.selectRailItem(item)
                invalidate()
            }

            TileRenderer.Target.STAGE_OPEN -> {
                val sel = renderer.selected ?: return
                if (sel == TileId.MEDIA && renderer.webStageApp == null) {
                    val target = LaunchTargetStore.selected(context)
                    val matchingWeb = StageWebApp.entries.firstOrNull { it.id == target.key }
                    if (matchingWeb != null) {
                        renderer.openWebApp(matchingWeb)
                        invalidate()
                        return
                    }
                }
                if (sel == TileId.HOME) {
                    onSetup?.invoke()
                    return
                }
                onTileTap?.invoke(sel)
            }

            TileRenderer.Target.PLAY_PAUSE -> {
                MediaTransport.toggle()
                invalidate()
            }

            TileRenderer.Target.PREV_TRACK -> {
                MediaTransport.previous()
                invalidate()
            }

            TileRenderer.Target.NEXT_TRACK -> {
                MediaTransport.next()
                invalidate()
            }

            TileRenderer.Target.NOTIF_REFRESH -> {
                OmarchyNotificationListener.refresh(context)
                invalidate()
            }

            TileRenderer.Target.NOTIF_CLEAR -> {
                OmarchyNotificationListener.dismissDisplayed(context)
                renderer.scrollNotifications(-1000)
                invalidate()
            }

            TileRenderer.Target.NOTIF_SCROLL_UP -> {
                renderer.scrollNotifications(-1)
                invalidate()
            }

            TileRenderer.Target.NOTIF_SCROLL_DOWN -> {
                renderer.scrollNotifications(1)
                invalidate()
            }

            TileRenderer.Target.NOTIFICATION -> {
                renderer.openNotification(hit.key)
                invalidate()
            }

            TileRenderer.Target.NOTIF_CLOSE,
            TileRenderer.Target.OVERLAY_DISMISS,
            -> {
                renderer.closeNotification()
                invalidate()
            }

            TileRenderer.Target.NOTIF_DISMISS_ONE -> {
                hit.key?.let { OmarchyNotificationListener.dismiss(context, it) }
                renderer.closeNotification()
                invalidate()
            }

            TileRenderer.Target.NOTIF_ROW_DISMISS -> {
                hit.key?.let { OmarchyNotificationListener.dismiss(context, it) }
                invalidate()
            }

            TileRenderer.Target.OVERLAY_SCROLL_UP -> {
                renderer.scrollOverlay(-2)
                invalidate()
            }

            TileRenderer.Target.OVERLAY_SCROLL_DOWN -> {
                renderer.scrollOverlay(2)
                invalidate()
            }

            TileRenderer.Target.TILE_DRIVE -> toggle(TileId.DRIVE)
            TileRenderer.Target.TILE_MEDIA -> toggle(TileId.MEDIA)
            TileRenderer.Target.TILE_COMMS -> toggle(TileId.COMMS)
            TileRenderer.Target.TILE_VEHICLE -> toggle(TileId.VEHICLE)
            TileRenderer.Target.TILE_HOME -> toggle(TileId.HOME)
        }
    }

    private fun refreshBackground() {
        lastBackgroundRevision = BackgroundStore.revision
        AppBackground.loadAsync(context, ThemeStore.current()) { bitmap ->
            renderer.setBackground(bitmap)
            invalidate()
        }
    }

    private fun toggle(tile: TileId) {
        renderer.toggle(tile)
        invalidate()
    }

    private fun millisToNextMinute(): Long {
        val now = System.currentTimeMillis()
        return 60_000L - (now % 60_000L) + 50L
    }
}
