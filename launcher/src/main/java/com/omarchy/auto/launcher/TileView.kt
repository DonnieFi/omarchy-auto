package com.omarchy.auto.launcher

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.BackgroundStore
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.OmarchyNotificationListener
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
    private var lastBackgroundRevision = -1
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downHit = renderer.hitTest(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (downHit != null &&
                    (kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop)
                ) {
                    downHit = null
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val start = downHit
                downHit = null
                if (start != null) {
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

            TileRenderer.Target.SETUP -> onSetup?.invoke()
            TileRenderer.Target.STAGE_OPEN -> renderer.selected?.let { onTileTap?.invoke(it) }
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
