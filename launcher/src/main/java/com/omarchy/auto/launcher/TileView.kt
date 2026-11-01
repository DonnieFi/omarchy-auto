package com.omarchy.auto.launcher

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.omarchy.auto.core.AppBackground
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.MediaTransport
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
        contentDescription = "Omarchy Auto launcher"
        setBackgroundColor(ThemeStore.current().background)
        renderer.setBackground(AppBackground.load(context))
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
                    TileRenderer.Target.STAGE_OPEN -> renderer.selected?.let { onTileTap?.invoke(it) }
                    TileRenderer.Target.PLAY_PAUSE -> {
                        MediaTransport.toggle()
                        invalidate()
                    }

                    TileRenderer.Target.TILE_DRIVE -> toggle(TileId.DRIVE)
                    TileRenderer.Target.TILE_MEDIA -> toggle(TileId.MEDIA)
                    TileRenderer.Target.TILE_COMMS -> toggle(TileId.COMMS)
                    TileRenderer.Target.TILE_VEHICLE -> toggle(TileId.VEHICLE)
                    TileRenderer.Target.TILE_HOME -> toggle(TileId.HOME)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toggle(tile: TileId) {
        renderer.toggle(tile)
        invalidate()
    }
}
