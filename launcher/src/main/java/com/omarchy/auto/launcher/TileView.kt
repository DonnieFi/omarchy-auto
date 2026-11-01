package com.omarchy.auto.launcher

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer

class TileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val renderer = TileRenderer()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1000L)
        }
    }

    private val dataListener: (DashboardState) -> Unit = {
        postInvalidateOnAnimation()
    }

    var onTileTap: ((TileId) -> Unit)? = null
    var onSetup: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(ThemeStore.current().background)
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
            mode = TileRenderer.Mode.PHONE,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val hit = renderer.hitTest(event.x, event.y) ?: return true
                when (hit.target) {
                    TileRenderer.Target.THEME -> {
                        ThemeStore.next()
                        setBackgroundColor(ThemeStore.current().background)
                        invalidate()
                    }

                    TileRenderer.Target.THEME_CHIP -> {
                        ThemeStore.select(hit.index)
                        setBackgroundColor(ThemeStore.current().background)
                        invalidate()
                    }

                    TileRenderer.Target.SETUP -> onSetup?.invoke()
                    TileRenderer.Target.TILE_DRIVE -> onTileTap?.invoke(TileId.DRIVE)
                    TileRenderer.Target.TILE_MEDIA -> onTileTap?.invoke(TileId.MEDIA)
                    TileRenderer.Target.TILE_COMMS -> onTileTap?.invoke(TileId.COMMS)
                    TileRenderer.Target.TILE_VEHICLE -> onTileTap?.invoke(TileId.VEHICLE)
                    TileRenderer.Target.TILE_HOME -> onTileTap?.invoke(TileId.HOME)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
