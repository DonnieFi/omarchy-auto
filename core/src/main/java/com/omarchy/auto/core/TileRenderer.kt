package com.omarchy.auto.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.location.Location
import android.text.TextPaint
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One Canvas renderer for both targets. The Android Auto surface and the phone
 * launcher draw the same model, theme, and layout. No Compose or QML runtime is
 * needed on the surface.
 */
class TileRenderer {

    enum class Mode { CAR, PHONE }

    enum class Target {
        THEME,
        SETUP,
        THEME_CHIP,
        STAGE_OPEN,
        PLAY_PAUSE,
        PREV_TRACK,
        NEXT_TRACK,
        NOTIF_REFRESH,
        NOTIF_CLEAR,
        NOTIFICATION,
        NOTIF_CLOSE,
        NOTIF_DISMISS_ONE,
        OVERLAY_DISMISS,
        TILE_DRIVE,
        TILE_MEDIA,
        TILE_COMMS,
        TILE_VEHICLE,
        TILE_HOME,
    }

    data class Hit(val target: Target, val index: Int = -1, val key: String? = null)

    private data class Layout(
        val themeButton: RectF,
        val setupButton: RectF?,
        val chips: List<Pair<Int, RectF>>,
        val stageOpen: RectF?,
        val playPause: RectF?,
        val prevTrack: RectF?,
        val nextTrack: RectF?,
        val notifRefresh: RectF?,
        val notifClear: RectF?,
        val notifications: List<Pair<String, RectF>>,
        val notifCard: RectF?,
        val notifClose: RectF?,
        val notifDismissOne: RectF?,
        val tiles: Map<Target, RectF>,
    )

    private data class StageHit(
        val open: RectF?,
        val play: RectF?,
        val setup: RectF?,
        val previous: RectF? = null,
        val next: RectF? = null,
        val refresh: RectF? = null,
        val clear: RectF? = null,
        val notifications: List<Pair<String, RectF>> = emptyList(),
    )

    private data class CommsHit(
        val clear: RectF?,
        val notifications: List<Pair<String, RectF>>,
    )

    private data class OverlayHit(
        val card: RectF,
        val close: RectF,
        val dismiss: RectF,
    )

    private data class MediaHit(
        val play: RectF?,
        val previous: RectF?,
        val next: RectF?,
    )

    @Volatile
    private var safeArea: Rect? = null

    @Volatile
    private var lastLayout: Layout? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val route = Path()
    private var fallbackShader: Shader? = null
    private var fallbackShaderKey: Long = 0L
    private var vignetteShader: Shader? = null
    private var vignetteShaderKey: Long = 0L
    private val wallpaperMatrix = Matrix()
    private val wallpaperPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFormat = SimpleDateFormat("EEE d MMM", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val fontScale = 1.06f

    @Volatile
    var selected: TileId? = null
        private set

    @Volatile
    private var background: Bitmap? = null

    @Volatile
    private var overlayKey: String? = null

    fun setSafeArea(rect: Rect?) {
        safeArea = rect?.let { Rect(it) }
    }

    fun setBackground(bitmap: Bitmap?) {
        background = bitmap
    }

    fun toggle(tile: TileId) {
        selected = if (selected == tile) null else tile
        overlayKey = null
    }

    fun show(tile: TileId) {
        selected = tile
        overlayKey = null
    }

    fun openNotification(key: String?) {
        overlayKey = key
    }

    fun closeNotification() {
        overlayKey = null
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: DashboardState,
        themeIndex: Int,
        mode: Mode,
    ) {
        val theme = Themes.at(themeIndex)
        val area = safeArea?.let { raw ->
            RectF(
                raw.left.coerceIn(0, width).toFloat(),
                raw.top.coerceIn(0, height).toFloat(),
                raw.right.coerceIn(0, width).toFloat(),
                raw.bottom.coerceIn(0, height).toFloat(),
            )
        }?.takeIf { it.width() > 100f && it.height() > 100f } ?: RectF(0f, 0f, width.toFloat(), height.toFloat())

        val car = mode == Mode.CAR
        if (!FeatureFlags.SHOW_VEHICLE_TILE && selected == TileId.VEHICLE) {
            selected = null
        }
        MapViewport.active = selected == TileId.DRIVE
        val landscape = area.width() > area.height() * 1.25f
        val rawUnit = if (landscape) {
            minOf(area.width() / 1280f, area.height() / 720f)
        } else {
            area.width() / 720f
        }
        val unit = if (car) rawUnit else rawUnit.coerceAtLeast(0.7f)

        val margin = (if (car) 18f else 14f) * unit
        val gap = (if (car) 12f else 10f) * unit
        val topBarH = topBarHeight(car, unit)
        val footerH = footerHeight(car, unit)
        val tileTop = area.top + topBarH + gap
        val tileBottom = area.bottom - footerH - gap
        val contentHeight = tileBottom - tileTop
        val barScale = topBarScale(car)

        canvas.drawColor(theme.background)
        drawWallpaper(canvas, width, height, area, theme, unit)

        val themeButton = RectF(
            area.right - margin - 62f * unit * barScale,
            area.top + (topBarH - 34f * unit * barScale) / 2f,
            area.right - margin,
            area.top + (topBarH + 34f * unit * barScale) / 2f,
        )
        val chips = mutableListOf<Pair<Int, RectF>>()

        // A narrow rail of small tiles on the right, top-down. In the car the
        // rail and its type are deliberately larger: the AA surface is often
        // 1920x720 and the driver is much farther from the panel than a phone.
        val railWidth = minOf(
            (if (car) 200f else 104f) * unit,
            area.width() * (if (car) 0.26f else 0.23f),
        ).coerceAtLeast((if (car) 150f else 74f) * unit)
        val railX = area.right - margin - railWidth
        val stage = RectF(
            area.left + margin,
            tileTop,
            railX - gap,
            tileBottom,
        )
        val railIds = buildList {
            add(TileId.DRIVE)
            add(TileId.MEDIA)
            add(TileId.COMMS)
            if (FeatureFlags.SHOW_VEHICLE_TILE) add(TileId.VEHICLE)
            add(TileId.HOME)
        }
        val tileHeight = minOf(
            (if (car) 112f else 84f) * unit,
            (contentHeight - gap * (railIds.size - 1)) / railIds.size,
        )
        val railRects = railIds.mapIndexed { index, id ->
            id to RectF(
                railX,
                tileTop + (tileHeight + gap) * index,
                railX + railWidth,
                tileTop + (tileHeight + gap) * index + tileHeight,
            )
        }
        val railUnit = unit * (if (car) 1.9f else 1f)

        drawTopBar(canvas, area, theme, unit, state, mode, themeButton, themeIndex)
        val stageHit = if (selected != null) {
            drawStage(canvas, stage, theme, state, unit, mode, themeIndex, chips)
        } else {
            drawIdleStage(canvas, area, tileBottom, theme, unit, mode)
            StageHit(open = null, play = null, setup = null)
        }
        railRects.forEach { (id, rect) ->
            drawRailTile(canvas, rect, id, theme, state, railUnit)
        }
        drawFooter(canvas, area, theme, unit, state, mode, themeIndex)

        val overlay = overlayKey?.let { key -> state.notifications.firstOrNull { it.key == key } }
        if (overlayKey != null && overlay == null) overlayKey = null
        val overlayHit = overlay?.let {
            drawNotificationOverlay(canvas, area, theme, it, unit, mode)
        }

        lastLayout = Layout(
            themeButton = RectF(themeButton),
            setupButton = stageHit.setup?.let { RectF(it) },
            chips = chips.map { it.first to RectF(it.second) },
            stageOpen = stageHit.open?.let { RectF(it) },
            playPause = stageHit.play?.let { RectF(it) },
            prevTrack = stageHit.previous?.let { RectF(it) },
            nextTrack = stageHit.next?.let { RectF(it) },
            notifRefresh = stageHit.refresh?.let { RectF(it) },
            notifClear = stageHit.clear?.let { RectF(it) },
            notifications = stageHit.notifications.map { it.first to RectF(it.second) },
            notifCard = overlayHit?.card?.let { RectF(it) },
            notifClose = overlayHit?.close?.let { RectF(it) },
            notifDismissOne = overlayHit?.dismiss?.let { RectF(it) },
            tiles = railRects.associate { (id, rect) ->
                when (id) {
                    TileId.DRIVE -> Target.TILE_DRIVE
                    TileId.MEDIA -> Target.TILE_MEDIA
                    TileId.COMMS -> Target.TILE_COMMS
                    TileId.VEHICLE -> Target.TILE_VEHICLE
                    TileId.HOME -> Target.TILE_HOME
                } to RectF(rect)
            },
        )
    }

    fun hitTest(x: Float, y: Float): Hit? {
        val layout = lastLayout ?: return null
        if (overlayKey != null) {
            layout.notifClose?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_CLOSE) }
            layout.notifDismissOne?.takeIf { it.contains(x, y) }?.let {
                return Hit(Target.NOTIF_DISMISS_ONE, key = overlayKey)
            }
            if (layout.notifCard?.contains(x, y) == true) return null
            return Hit(Target.OVERLAY_DISMISS)
        }
        if (layout.themeButton.contains(x, y)) return Hit(Target.THEME)
        layout.setupButton?.takeIf { it.contains(x, y) }?.let { return Hit(Target.SETUP) }
        layout.chips.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.THEME_CHIP, it.first)
        }
        layout.stageOpen?.takeIf { it.contains(x, y) }?.let { return Hit(Target.STAGE_OPEN) }
        layout.playPause?.takeIf { it.contains(x, y) }?.let { return Hit(Target.PLAY_PAUSE) }
        layout.prevTrack?.takeIf { it.contains(x, y) }?.let { return Hit(Target.PREV_TRACK) }
        layout.nextTrack?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NEXT_TRACK) }
        layout.notifRefresh?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_REFRESH) }
        layout.notifClear?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_CLEAR) }
        layout.notifications.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.NOTIFICATION, key = it.first)
        }
        layout.tiles.entries.firstOrNull { it.value.contains(x, y) }?.let {
            return Hit(it.key)
        }
        return null
    }

    private fun tileNumber(id: TileId): String =
        if (!FeatureFlags.SHOW_VEHICLE_TILE && id == TileId.HOME) "04" else id.number

    private fun drawWallpaper(
        canvas: Canvas,
        width: Int,
        height: Int,
        area: RectF,
        theme: OmarchyTheme,
        unit: Float,
    ) {
        val bitmap = background
        if (bitmap != null) {
            val portrait = height > width * 1.25f
            val scale = maxOf(
                width.toFloat() / bitmap.width,
                height.toFloat() / bitmap.height,
            )
            val dx = (width - bitmap.width * scale) / 2f
            val dy = (height - bitmap.height * scale) / 2f
            wallpaperMatrix.reset()
            wallpaperMatrix.setScale(scale, scale)
            val shift = if (portrait) -width * 0.08f else 0f
            wallpaperMatrix.postTranslate(dx + shift, dy)
            wallpaperPaint.alpha = 255
            canvas.drawBitmap(bitmap, wallpaperMatrix, wallpaperPaint)

            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.background, theme.scrimAlpha)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        } else {
            val key = (width.toLong() shl 32) xor height.toLong() xor theme.id.hashCode().toLong()
            if (fallbackShader == null || fallbackShaderKey != key) {
                fallbackShader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(theme.background, theme.surfaceAlt, theme.background),
                    null,
                    Shader.TileMode.CLAMP,
                )
                fallbackShaderKey = key
            }
            fill.style = Paint.Style.FILL
            fill.shader = fallbackShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            fill.shader = null

            // Original fallback: a neon curve and a low roadster silhouette.
            route.reset()
            route.moveTo(area.left - area.width() * 0.05f, area.bottom * 0.72f)
            route.cubicTo(
                area.width() * 0.25f, area.height() * 0.48f,
                area.width() * 0.62f, area.height() * 0.74f,
                area.right + area.width() * 0.08f, area.height() * 0.38f,
            )
            stroke.style = Paint.Style.STROKE
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeWidth = 58f * unit
            stroke.color = withAlpha(theme.background, 190)
            canvas.drawPath(route, stroke)
            stroke.strokeWidth = 4f * unit
            stroke.color = withAlpha(theme.accent, 220)
            canvas.drawPath(route, stroke)
            stroke.strokeWidth = 2f * unit
            stroke.color = withAlpha(theme.foreground, 120)
            canvas.drawPath(route, stroke)

            val cx = area.left + area.width() * 0.49f
            val cy = area.height() * 0.52f
            val carWidth = area.width() * 0.16f
            val carHeight = area.height() * 0.055f
            route.reset()
            route.moveTo(cx - carWidth * 0.5f, cy)
            route.lineTo(cx - carWidth * 0.36f, cy - carHeight * 0.5f)
            route.lineTo(cx - carWidth * 0.16f, cy - carHeight * 0.85f)
            route.lineTo(cx + carWidth * 0.18f, cy - carHeight * 0.85f)
            route.lineTo(cx + carWidth * 0.38f, cy - carHeight * 0.5f)
            route.lineTo(cx + carWidth * 0.5f, cy)
            route.close()
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.foreground, 210)
            canvas.drawPath(route, fill)
            fill.color = withAlpha(theme.background, 230)
            canvas.drawCircle(cx - carWidth * 0.28f, cy, carHeight * 0.32f, fill)
            canvas.drawCircle(cx + carWidth * 0.28f, cy, carHeight * 0.32f, fill)
            fill.color = withAlpha(theme.accent, 220)
            canvas.drawRect(cx - carWidth * 0.30f, cy - carHeight * 0.92f, cx + carWidth * 0.30f, cy - carHeight * 0.78f, fill)

            fill.color = withAlpha(theme.foreground, 12)
            val step = 44f * unit
            var x = area.left
            while (x <= area.right) {
                canvas.drawRect(x, area.top, x + 1f, area.bottom, fill)
                x += step
            }
            var y = area.top
            while (y <= area.bottom) {
                canvas.drawRect(area.left, y, area.right, y + 1f, fill)
                y += step
            }
        }

        val vignetteKey = height.toLong() xor theme.id.hashCode().toLong()
        if (vignetteShader == null || vignetteShaderKey != vignetteKey) {
            vignetteShader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    withAlpha(theme.background, 120),
                    withAlpha(theme.background, 24),
                    withAlpha(theme.background, 120),
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            vignetteShaderKey = vignetteKey
        }
        fill.style = Paint.Style.FILL
        fill.shader = vignetteShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
    }

    private fun drawRailTile(
        canvas: Canvas,
        rect: RectF,
        id: TileId,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
        val isSelected = selected == id
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surface, 226)
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2f * unit
        stroke.color = if (isSelected) theme.accent else theme.border
        canvas.drawRoundRect(
            RectF(
                rect.left + 1f * unit,
                rect.top + 1f * unit,
                rect.right - 1f * unit,
                rect.bottom - 1f * unit,
            ),
            2f * unit, 2f * unit, stroke,
        )

        if (isSelected) {
            fill.color = theme.accent
            canvas.drawRect(
                rect.left, rect.top + 8f * unit,
                rect.left + 4f * unit, rect.bottom - 8f * unit,
                fill,
            )
        }

        val pad = minOf(10f * unit, rect.width() * 0.08f)
        val numberSize = rect.height() * 0.16f
        val titleSize = rect.height() * 0.21f
        val metaSize = rect.height() * 0.13f
        val numberBaseline = rect.top + rect.height() * 0.27f
        val titleBaseline = rect.top + rect.height() * 0.58f
        val metaBaseline = rect.bottom - rect.height() * 0.16f

        val number = "[${tileNumber(id)}]"
        text(
            canvas, number, rect.left + pad, numberBaseline,
            numberSize, if (isSelected) theme.accent else theme.dim, bold = true,
        )
        text(
            canvas, id.title, rect.left + pad, titleBaseline,
            titleSize, if (isSelected) theme.foreground else theme.dim, bold = true,
            maxWidth = rect.width() - pad * 2f,
        )

        val meta = when (id) {
            TileId.DRIVE -> if (state.gpsActive) "GPS" else "WAIT"
            TileId.MEDIA -> if (state.media != null) "LIVE" else "WAIT"
            TileId.COMMS -> if (state.notifications.isNotEmpty()) "${state.notifications.size} NEW" else "0"
            TileId.VEHICLE -> "MOCK"
            TileId.HOME -> "THEME"
        }
        val metaColor = when (id) {
            TileId.DRIVE -> if (state.gpsActive) theme.green else theme.dim
            TileId.MEDIA -> if (state.media != null) theme.green else theme.dim
            TileId.COMMS -> if (state.notifications.isNotEmpty()) theme.accent else theme.dim
            TileId.VEHICLE -> theme.yellow
            TileId.HOME -> theme.dim
        }
        text(
            canvas, meta, rect.left + pad, metaBaseline,
            metaSize, metaColor, maxWidth = rect.width() - pad * 2f,
        )
    }

    private fun drawIdleStage(
        canvas: Canvas,
        area: RectF,
        tileBottom: Float,
        theme: OmarchyTheme,
        unit: Float,
        mode: Mode,
    ) {
        val textUnit = unit * if (mode == Mode.CAR) 2f else 1f
        val message = "ALL TILES MINIMIZED  ·  SELECT ONE FROM THE RIGHT RAIL"
        val x = area.left + 18f * textUnit
        val baseline = tileBottom - 12f * textUnit
        if (mode == Mode.CAR) {
            val width = minOf(measure(message, 10f * textUnit), area.width() - 36f * textUnit)
            val plate = RectF(
                x - 10f * textUnit,
                baseline - 10f * textUnit,
                x + width + 10f * textUnit,
                baseline + 6f * textUnit,
            )
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.background, 210)
            canvas.drawRoundRect(plate, 2f * textUnit, 2f * textUnit, fill)
        }
        text(
            canvas,
            message,
            x,
            baseline,
            10f * textUnit,
            theme.dim,
            maxWidth = area.width() - 36f * textUnit,
        )
    }

    private fun drawStage(
        canvas: Canvas,
        stage: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
        mode: Mode,
        themeIndex: Int,
        chips: MutableList<Pair<Int, RectF>>,
    ): StageHit {
        val id = selected ?: return StageHit(open = null, play = null, setup = null)
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surface, theme.stageAlpha)
        canvas.drawRoundRect(stage, 2f * unit, 2f * unit, fill)

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2f * unit
        stroke.color = theme.accent
        canvas.drawRoundRect(
            RectF(
                stage.left + 1f * unit,
                stage.top + 1f * unit,
                stage.right - 1f * unit,
                stage.bottom - 1f * unit,
            ),
            2f * unit, 2f * unit, stroke,
        )

        val stageUnit = unit * if (mode == Mode.CAR) 1.9f else 1.15f
        val stageChrome = if (mode == Mode.CAR) stageUnit else unit
        val legend = "[${tileNumber(id)} · ${id.title}]"
        val legendSize = 11f * stageChrome
        val legendWidth = measure(legend, legendSize, bold = true) + 18f * stageChrome
        fill.color = withAlpha(theme.background, 235)
        canvas.drawRect(
            stage.left + 14f * stageChrome, stage.top - 1f * unit,
            stage.left + 14f * stageChrome + legendWidth, stage.top + 20f * stageChrome,
            fill,
        )
        text(
            canvas, legend, stage.left + 22f * stageChrome, stage.top + 14f * stageChrome,
            legendSize, theme.accent, bold = true,
        )

        val content = RectF(
            stage.left + 16f * stageChrome,
            stage.top + 44f * stageChrome,
            stage.right - 16f * stageChrome,
            stage.bottom - 14f * stageChrome,
        )
        var play: RectF? = null
        var previous: RectF? = null
        var next: RectF? = null
        var clear: RectF? = null
        var notifications: List<Pair<String, RectF>> = emptyList()
        var setup: RectF? = null

        when (id) {
            TileId.DRIVE -> drawDrive(canvas, content, theme, state, stageUnit)
            TileId.MEDIA -> {
                val hit = drawMedia(canvas, content, theme, state, stageUnit)
                play = hit.play
                previous = hit.previous
                next = hit.next
            }

            TileId.COMMS -> {
                val hit = drawComms(canvas, content, theme, state, stageUnit)
                clear = hit.clear
                notifications = hit.notifications
            }
            TileId.VEHICLE -> drawVehicle(canvas, content, theme, state, stageUnit)
            TileId.HOME -> {
                setup = drawHomeDetails(
                    canvas, stage, theme, state, stageUnit, mode, themeIndex, chips,
                )
            }
        }

        val open = if (mode == Mode.PHONE && id != TileId.VEHICLE) {
            val label = when (id) {
                TileId.DRIVE -> "OPEN MAPS"
                TileId.MEDIA -> "OPEN PLAYER"
                TileId.COMMS -> "OPEN MESSAGES"
                TileId.HOME -> "OPEN SETTINGS"
                TileId.VEHICLE -> ""
            }
            val rect = RectF(
                stage.right - 16f * stageChrome - 142f * stageChrome,
                stage.top + 8f * stageChrome,
                stage.right - 16f * stageChrome,
                stage.top + 36f * stageChrome,
            )
            fill.color = withAlpha(theme.surfaceAlt, 235)
            canvas.drawRoundRect(rect, 2f * stageChrome, 2f * stageChrome, fill)
            stroke.strokeWidth = 1f * stageChrome
            stroke.color = theme.border
            canvas.drawRoundRect(rect, 2f * stageChrome, 2f * stageChrome, stroke)
            text(
                canvas, label, rect.centerX() - measure(label, 8f * stageChrome) / 2f,
                rect.centerY() + 3f * stageChrome, 8f * stageChrome, theme.accent,
            )
            rect
        } else {
            null
        }

        var refresh: RectF? = null
        if (id == TileId.COMMS) {
            val label = "REFRESH"
            val textSize = 9f * stageChrome
            val buttonWidth = measure(label, textSize, bold = true) + 24f * stageChrome
            val buttonHeight = 28f * stageChrome
            val right = open?.left?.minus(8f * stageChrome) ?: (stage.right - 16f * stageChrome)
            refresh = RectF(
                right - buttonWidth,
                stage.top + 6f * stageChrome,
                right,
                stage.top + 6f * stageChrome + buttonHeight,
            )
            drawTextButton(canvas, refresh, label, theme, stageChrome)
        }

        return StageHit(
            open = open,
            play = play,
            setup = setup,
            previous = previous,
            next = next,
            refresh = refresh,
            clear = clear,
            notifications = notifications,
        )
    }

    private fun drawTextButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        theme: OmarchyTheme,
        unit: Float,
        color: Int? = null,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surfaceAlt, 235)
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, stroke)
        val textSize = 9f * unit
        text(
            canvas,
            label,
            rect.centerX() - measure(label, textSize, bold = true) / 2f,
            rect.centerY() + 3f * unit,
            textSize,
            color ?: theme.accent,
            bold = true,
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun drawTopBar(
        canvas: Canvas,
        area: RectF,
        theme: OmarchyTheme,
        unit: Float,
        state: DashboardState,
        mode: Mode,
        themeButton: RectF,
        themeIndex: Int,
    ) {
        val car = mode == Mode.CAR
        val barUnit = unit * topBarScale(car)
        val topBarH = topBarHeight(car, unit)
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surface, 228)
        canvas.drawRect(area.left, area.top, area.right, area.top + topBarH, fill)
        fill.color = theme.border
        canvas.drawRect(area.left, area.top + topBarH - 1f, area.right, area.top + topBarH, fill)

        val baseline = area.top + topBarH / 2f + 5.5f * barUnit
        text(canvas, "OMARCHY", area.left + 18f * barUnit, baseline, 16f * barUnit, theme.accent, bold = true)
        val brandWidth = measure("OMARCHY", 16f * barUnit, bold = true)
        text(
            canvas, "// AUTO", area.left + 18f * barUnit + brandWidth + 10f * barUnit, baseline,
            11f * barUnit, theme.dim,
        )
        val modeText = if (mode == Mode.CAR) "AA" else "PHONE"
        text(
            canvas, modeText, area.left + 18f * barUnit + brandWidth + 75f * barUnit,
            baseline, 10f * barUnit, theme.dim,
        )

        val themeName = Themes.at(themeIndex).name
        text(
            canvas, themeName, themeButton.left - 12f * barUnit - measure(themeName, 10f * barUnit),
            baseline, 10f * barUnit, theme.dim,
        )
        val clock = clockFormat.format(Date())
        val clockSize = (if (car) 20f else 18f) * barUnit
        text(
            canvas, clock, themeButton.left - 12f * barUnit - measure(themeName, 10f * barUnit) - 20f * barUnit - measure(clock, clockSize, bold = true),
            baseline + 1f * barUnit, clockSize, theme.foreground, bold = true,
        )

        fill.style = Paint.Style.FILL
        fill.color = theme.surfaceAlt
        canvas.drawRoundRect(themeButton, 2f * unit, 2f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(themeButton, 2f * unit, 2f * unit, stroke)
        text(
            canvas, "THEME", themeButton.centerX() - measure("THEME", 9f * barUnit) / 2f,
            themeButton.centerY() + 3f * barUnit, 9f * barUnit, theme.accent,
        )
    }

    private fun topBarHeight(car: Boolean, unit: Float): Float =
        (if (car) 128f else 56f) * unit

    private fun footerHeight(car: Boolean, unit: Float): Float =
        (if (car) 60f else 24f) * unit

    private fun topBarScale(car: Boolean): Float = if (car) 3f else 1f

    private fun footerScale(car: Boolean): Float = if (car) 3.1f else 1f

    private fun drawFooter(
        canvas: Canvas,
        area: RectF,
        theme: OmarchyTheme,
        unit: Float,
        state: DashboardState,
        mode: Mode,
        themeIndex: Int,
    ) {
        val car = mode == Mode.CAR
        val footerH = footerHeight(car, unit)
        val textUnit = unit * footerScale(car)
        val top = area.bottom - footerH
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surface, 236)
        canvas.drawRect(area.left, top, area.right, area.bottom, fill)
        fill.color = theme.border
        canvas.drawRect(area.left, top, area.right, top + 1f, fill)

        val baseline = top + footerH / 2f + 3.5f * textUnit
        var x = area.left + 16f * textUnit
        x = status(canvas, x, baseline, "MEDIA", state.mediaAccess, theme, textUnit)
        x = status(canvas, x + 16f * textUnit, baseline, "NOTIF", state.notificationAccess, theme, textUnit)
        x = status(canvas, x + 16f * textUnit, baseline, "GPS", state.locationAccess, theme, textUnit)

        if (FeatureFlags.SHOW_VEHICLE_TILE) {
            val mock = if (state.vehicleMock) "MOCK VEHICLE" else "OBD LIVE"
            val mockSize = 9f * textUnit
            text(
                canvas, mock, area.right - 16f * textUnit - measure(mock, mockSize),
                baseline, mockSize, if (state.vehicleMock) theme.yellow else theme.green,
            )
        }

        val media = state.media
        val hasMedia = media != null && media.title.isNotBlank()
        val center = when {
            hasMedia -> {
                val artist = media.artist.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                "▶ ${media.title}$artist"
            }
            state.speedKph != null -> "%.0f km/h".format(Locale.US, state.speedKph)
            else -> "OMARCHY AUTO"
        }
        val centerSize = (if (car) 12f else 9f) * textUnit
        val speed = state.speedKph
        val speedText = if (car && hasMedia && speed != null) {
            "%.0f km/h".format(Locale.US, speed)
        } else {
            null
        }
        val speedSize = 11f * textUnit
        val speedWidth = speedText?.let { measure(it, speedSize, bold = true) } ?: 0f
        val leftBound = x + 18f * textUnit
        val rightBound = area.right - 16f * textUnit - speedWidth -
            (if (speedText != null) 18f * textUnit else 0f)
        val available = (rightBound - leftBound).coerceAtLeast(40f * textUnit)
        val maxCenter = minOf(available, area.width() * (if (car) 0.52f else 0.36f))
        val centerWidth = minOf(measure(center, centerSize, bold = car), maxCenter)
        text(
            canvas, center, leftBound + (available - centerWidth) / 2f, baseline,
            centerSize, theme.foreground, maxWidth = maxCenter, bold = car,
        )

        // Speed stays visible in the footer when media is the centre item.
        if (speedText != null) {
            text(
                canvas, speedText,
                area.right - 16f * textUnit - speedWidth,
                baseline, speedSize, theme.accent, bold = true,
            )
        }
    }

    private fun status(
        canvas: Canvas,
        x: Float,
        baseline: Float,
        name: String,
        live: Boolean,
        theme: OmarchyTheme,
        unit: Float,
    ): Float {
        val color = if (live) theme.green else theme.dim
        text(canvas, name, x, baseline, 9f * unit, color)
        val width = measure(name, 9f * unit)
        text(canvas, if (live) "+" else "-", x + width + 3f * unit, baseline, 9f * unit, color)
        return x + width + 9f * unit
    }

    private fun drawDrive(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
        publishMapViewport(content)
        val map = MapSnapshotStore.bitmap
        val mapAgeMs = if (map != null) {
            (System.currentTimeMillis() - MapSnapshotStore.updatedAt).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        val snapshotLat = MapSnapshotStore.latitude
        val snapshotLon = MapSnapshotStore.longitude
        val currentLat = state.latitude
        val currentLon = state.longitude
        val movedMeters = if (snapshotLat != null && snapshotLon != null &&
            currentLat != null && currentLon != null
        ) {
            val distance = FloatArray(1)
            Location.distanceBetween(snapshotLat, snapshotLon, currentLat, currentLon, distance)
            distance[0]
        } else {
            0f
        }
        val stale = map != null && mapAgeMs > STALE_AFTER_MS && movedMeters > 30f
        if (map != null) {
            drawCenterCrop(canvas, map, content)
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.background, (theme.scrimAlpha * 0.45f).toInt().coerceIn(20, 80))
            canvas.drawRect(content, fill)
        } else {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.surfaceAlt, 118)
            canvas.drawRect(content, fill)

            fill.color = Color.argb(12, 255, 255, 255)
            val step = 34f * unit
            var x = content.left
            while (x < content.right) {
                canvas.drawRect(x, content.top, x + 1f, content.bottom, fill)
                x += step
            }
            var y = content.top
            while (y < content.bottom) {
                canvas.drawRect(content.left, y, content.right, y + 1f, fill)
                y += step
            }

            val message = when {
                !state.gpsActive -> "WAIT FOR GPS"
                MapSnapshotStore.error != null -> "MAP OFFLINE"
                else -> "MAP LOADING"
            }
            text(
                canvas,
                message,
                content.centerX() - measure(message, 16f * unit, bold = true) / 2f,
                content.centerY() + 5f * unit,
                16f * unit,
                if (message == "MAP OFFLINE") theme.yellow else theme.dim,
                bold = true,
            )
        }

        if (stale) {
            val label = "MAP STALE"
            val size = 10f * unit
            val badge = RectF(
                content.right - 12f * unit - measure(label, size, bold = true) - 16f * unit,
                content.top + 10f * unit,
                content.right - 12f * unit,
                content.top + 32f * unit,
            )
            fill.color = withAlpha(theme.background, 215)
            canvas.drawRoundRect(badge, 2f * unit, 2f * unit, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * unit
            stroke.color = theme.yellow
            canvas.drawRoundRect(badge, 2f * unit, 2f * unit, stroke)
            text(
                canvas,
                label,
                badge.left + 8f * unit,
                badge.centerY() + 3f * unit,
                size,
                theme.yellow,
                bold = true,
            )
        }

        if (state.gpsActive && map != null && !stale) {
            val cx = content.centerX()
            val cy = content.centerY()
            fill.color = Color.argb(90, 0, 0, 0)
            canvas.drawCircle(cx, cy, 19f * unit, fill)
            fill.color = theme.green
            canvas.drawCircle(cx, cy, 11f * unit, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 2f * unit
            stroke.color = theme.foreground
            canvas.drawCircle(cx, cy, 11f * unit, stroke)
            route.reset()
            route.moveTo(cx, cy - 19f * unit)
            route.lineTo(cx - 5f * unit, cy - 10f * unit)
            route.lineTo(cx + 5f * unit, cy - 10f * unit)
            route.close()
            fill.color = theme.foreground
            canvas.drawPath(route, fill)
        }

        val panel = RectF(
            content.left + 12f * unit,
            content.bottom - 102f * unit,
            content.left + 300f * unit,
            content.bottom - 12f * unit,
        )
        fill.color = withAlpha(theme.background, 205)
        canvas.drawRoundRect(panel, 3f * unit, 3f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(panel, 3f * unit, 3f * unit, stroke)

        val speed = state.speedKph
        val speedText = if (speed != null) "%.0f".format(Locale.US, speed) else "--"
        val speedX = panel.left + 14f * unit
        val speedBaseline = panel.bottom - 34f * unit
        text(canvas, speedText, speedX, speedBaseline, 54f * unit, theme.foreground, bold = true)
        text(
            canvas, "km/h", speedX + measure(speedText, 54f * unit, bold = true) + 8f * unit,
            speedBaseline, 10f * unit, theme.dim,
        )
        val gps = if (state.gpsActive) "GPS LIVE" else "GPS WAIT"
        text(
            canvas, gps, panel.left + 14f * unit, panel.bottom - 12f * unit, 9f * unit,
            if (state.gpsActive) theme.green else theme.dim,
        )

        val attribution = "© OpenStreetMap · OpenFreeMap"
        val attributionSize = 8f * unit
        val attributionMax = (content.width() - 312f * unit).coerceAtLeast(80f * unit)
        val attributionWidth = minOf(measure(attribution, attributionSize), attributionMax)
        text(
            canvas,
            attribution,
            content.right - 8f * unit - attributionWidth,
            content.bottom - 8f * unit,
            attributionSize,
            theme.dim,
            maxWidth = attributionMax,
        )
    }

    private fun publishMapViewport(content: RectF) {
        var width = content.width().toInt().coerceAtLeast(320)
        var height = content.height().toInt().coerceAtLeast(240)
        val pixels = width.toLong() * height
        if (pixels > 2_000_000L) {
            val scale = kotlin.math.sqrt(2_000_000.0 / pixels.toDouble())
            width = (width * scale).toInt().coerceAtLeast(320)
            height = (height * scale).toInt().coerceAtLeast(240)
        }
        MapViewport.width = width
        MapViewport.height = height
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, content: RectF) {
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height
        val contentAspect = content.width() / content.height()
        val src = Rect(0, 0, bitmap.width, bitmap.height)
        if (bitmapAspect > contentAspect) {
            val cropWidth = (bitmap.height * contentAspect).toInt()
            src.left = (bitmap.width - cropWidth) / 2
            src.right = src.left + cropWidth
        } else {
            val cropHeight = (bitmap.width / contentAspect).toInt()
            src.top = (bitmap.height - cropHeight) / 2
            src.bottom = src.top + cropHeight
        }
        canvas.drawBitmap(bitmap, src, content, wallpaperPaint)
    }

    private fun drawMedia(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ): MediaHit {
        val media = state.media
        if (media == null) {
            val title = if (state.mediaAccess) "NO ACTIVE MEDIA" else "MEDIA ACCESS NEEDED"
            val detail = if (state.mediaAccess) {
                "Start playback on the phone"
            } else {
                "Grant notification access in Setup"
            }
            text(
                canvas, title, content.centerX() - measure(title, 16f * unit, bold = true) / 2f,
                content.centerY() - 4f * unit, 16f * unit,
                if (state.mediaAccess) theme.dim else theme.yellow, bold = true,
            )
            text(
                canvas, detail, content.centerX() - measure(detail, 10f * unit) / 2f,
                content.centerY() + 18f * unit, 10f * unit, theme.dim,
            )
            return MediaHit(play = null, previous = null, next = null)
        }

        val artSize = minOf(content.width() * 0.32f, content.height() * 0.62f)
        val art = RectF(content.left, content.top, content.left + artSize, content.top + artSize)
        fill.style = Paint.Style.FILL
        if (media.art != null) {
            canvas.drawBitmap(media.art, null, art, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            fill.color = theme.surfaceAlt
            canvas.drawRect(art, fill)
            text(
                canvas, "NO ART", art.centerX() - measure("NO ART", 9f * unit) / 2f,
                art.centerY() + 3f * unit, 9f * unit, theme.dim,
            )
        }
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRect(art, stroke)

        val textLeft = art.right + 14f * unit
        val textWidth = content.right - textLeft
        var baseline = content.top + 20f * unit
        text(
            canvas, media.title.ifBlank { "UNKNOWN TRACK" }, textLeft, baseline,
            18f * unit, theme.foreground, bold = true, maxWidth = textWidth,
        )
        baseline += 20f * unit
        text(
            canvas, media.artist.ifBlank { "UNKNOWN ARTIST" }, textLeft, baseline,
            11f * unit, theme.dim, maxWidth = textWidth,
        )
        baseline += 16f * unit
        text(
            canvas, media.album.ifBlank { "" }, textLeft, baseline,
            9f * unit, theme.dim, maxWidth = textWidth,
        )
        baseline += 15f * unit
        text(
            canvas, media.source.uppercase(Locale.US), textLeft, baseline,
            8f * unit, theme.dim, maxWidth = textWidth,
        )

        val progressTop = content.bottom - 108f * unit
        val progressWidth = content.width()
        fill.color = theme.border
        canvas.drawRect(content.left, progressTop, content.left + progressWidth, progressTop + 4f * unit, fill)
        if (media.durationMs > 0L) {
            val fraction = (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f)
            fill.color = theme.accent
            canvas.drawRect(
                content.left, progressTop,
                content.left + progressWidth * fraction, progressTop + 4f * unit, fill,
            )
        }
        text(
            canvas, formatTime(media.positionMs), content.left,
            progressTop + 18f * unit, 8f * unit, theme.dim,
        )
        val duration = "-" + formatTime((media.durationMs - media.positionMs).coerceAtLeast(0L))
        text(
            canvas, duration, content.right - measure(duration, 8f * unit),
            progressTop + 18f * unit, 8f * unit, theme.dim,
        )

        val playWidth = 88f * unit
        val sideWidth = 64f * unit
        val gap = 16f * unit
        val transportHeight = 64f * unit
        val totalWidth = playWidth + (sideWidth + gap) * 2f
        val transportTop = content.bottom - 8f * unit - transportHeight
        val transportLeft = content.centerX() - totalWidth / 2f
        val previous = RectF(
            transportLeft,
            transportTop,
            transportLeft + sideWidth,
            transportTop + transportHeight,
        )
        val transport = RectF(
            previous.right + gap,
            transportTop,
            previous.right + gap + playWidth,
            transportTop + transportHeight,
        )
        val next = RectF(
            transport.right + gap,
            transportTop,
            transport.right + gap + sideWidth,
            transportTop + transportHeight,
        )
        drawSideTransport(canvas, previous, previous = true, theme, unit)
        drawTransport(canvas, transport, media.playing, theme, unit)
        drawSideTransport(canvas, next, previous = false, theme, unit)
        return MediaHit(play = transport, previous = previous, next = next)
    }

    private fun drawSideTransport(
        canvas: Canvas,
        rect: RectF,
        previous: Boolean,
        theme: OmarchyTheme,
        unit: Float,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surfaceAlt, 235)
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, stroke)

        val cx = rect.centerX()
        val cy = rect.centerY()
        val half = 10f * unit
        val inset = 6f * unit
        fill.color = theme.accent
        if (previous) {
            canvas.drawRect(cx - 14f * unit, cy - half, cx - 10f * unit, cy + half, fill)
            route.reset()
            route.moveTo(cx - inset, cy)
            route.lineTo(cx + inset, cy - half)
            route.lineTo(cx + inset, cy + half)
            route.close()
            canvas.drawPath(route, fill)
        } else {
            canvas.drawRect(cx + 10f * unit, cy - half, cx + 14f * unit, cy + half, fill)
            route.reset()
            route.moveTo(cx + inset, cy)
            route.lineTo(cx - inset, cy - half)
            route.lineTo(cx - inset, cy + half)
            route.close()
            canvas.drawPath(route, fill)
        }
    }

    private fun drawTransport(
        canvas: Canvas,
        rect: RectF,
        playing: Boolean,
        theme: OmarchyTheme,
        unit: Float,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = theme.accent
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)
        val cx = rect.centerX()
        val cy = rect.centerY()
        if (playing) {
            fill.color = theme.background
            val barW = 9f * unit
            val barH = 30f * unit
            canvas.drawRect(cx - 15f * unit, cy - barH / 2, cx - 15f * unit + barW, cy + barH / 2, fill)
            canvas.drawRect(cx + 6f * unit, cy - barH / 2, cx + 6f * unit + barW, cy + barH / 2, fill)
        } else {
            route.reset()
            route.moveTo(cx - 11f * unit, cy - 16f * unit)
            route.lineTo(cx + 17f * unit, cy)
            route.lineTo(cx - 11f * unit, cy + 16f * unit)
            route.close()
            fill.color = theme.background
            canvas.drawPath(route, fill)
        }
    }

    private fun drawComms(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ): CommsHit {
        if (!state.notificationAccess) {
            text(
                canvas, "NOTIFICATION ACCESS NEEDED", content.centerX() - measure("NOTIFICATION ACCESS NEEDED", 14f * unit, bold = true) / 2f,
                content.centerY() - 2f * unit, 14f * unit, theme.yellow, bold = true,
            )
            val hint = "Open Setup and grant access"
            text(
                canvas, hint, content.centerX() - measure(hint, 10f * unit) / 2f,
                content.centerY() + 18f * unit, 10f * unit, theme.dim,
            )
            return CommsHit(clear = null, notifications = emptyList())
        }
        if (state.notifications.isEmpty()) {
            text(
                canvas, "NO NOTIFICATIONS", content.centerX() - measure("NO NOTIFICATIONS", 14f * unit, bold = true) / 2f,
                content.centerY(), 14f * unit, theme.dim, bold = true,
            )
            return CommsHit(clear = null, notifications = emptyList())
        }

        val headerH = 34f * unit
        val count = "${state.notifications.size} NEW"
        text(
            canvas,
            count,
            content.left + 2f * unit,
            content.top + 20f * unit,
            9f * unit,
            theme.accent,
            bold = true,
        )
        val clearLabel = "CLEAR"
        val clearTextSize = 9f * unit
        val clearWidth = measure(clearLabel, clearTextSize, bold = true) + 20f * unit
        val clearHeight = 28f * unit
        val clear = RectF(
            content.right - clearWidth,
            content.top + 2f * unit,
            content.right,
            content.top + 2f * unit + clearHeight,
        )
        drawTextButton(canvas, clear, clearLabel, theme, unit)
        fill.color = theme.border
        canvas.drawRect(content.left, content.top + headerH - 1f, content.right, content.top + headerH, fill)

        val listTop = content.top + headerH
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 175)
        canvas.drawRect(content.left, listTop, content.right, content.bottom, fill)
        val listHeight = content.bottom - listTop
        val textScale = DisplaySettings.notificationTextScale
        val baseRow = 54f * unit * textScale
        val maxItems = if (listHeight > 4f * baseRow) 4 else 3
        val items = state.notifications.take(maxItems)
        val rowH = minOf(baseRow, listHeight / items.size)
        val rows = mutableListOf<Pair<String, RectF>>()
        items.forEachIndexed { index, item ->
            val top = listTop + index * rowH
            val bottom = top + rowH - 6f * unit
            val row = RectF(content.left, top, content.right, bottom)
            rows += item.key to row
            fill.style = Paint.Style.FILL
            fill.color = if (index == 0) withAlpha(theme.surfaceAlt, 226) else Color.TRANSPARENT
            canvas.drawRect(row, fill)
            if (index == 0) {
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1f * unit
                stroke.color = theme.border
                canvas.drawRect(row, stroke)
            }
            fill.color = theme.accent
            canvas.drawRect(content.left, top, content.left + 3f * unit, bottom, fill)

            text(
                canvas, item.app.uppercase(Locale.US), content.left + 9f * unit,
                top + 12f * unit, 8f * unit * textScale, theme.accent, bold = true,
                maxWidth = content.width() * 0.6f,
            )
            val time = timeFormat.format(Date(item.postedAt))
            text(
                canvas, time, content.right - 9f * unit - measure(time, 8f * unit * textScale),
                top + 12f * unit, 8f * unit * textScale, theme.dim,
            )
            text(
                canvas, item.title, content.left + 9f * unit, top + 24f * unit,
                10f * unit * textScale, theme.foreground, maxWidth = content.width() - 18f * unit,
            )
            text(
                canvas, item.text, content.left + 9f * unit, top + 34f * unit,
                9f * unit * textScale, theme.dim, maxWidth = content.width() - 18f * unit,
            )
        }
        return CommsHit(clear = clear, notifications = rows)
    }

    private fun drawNotificationOverlay(
        canvas: Canvas,
        area: RectF,
        theme: OmarchyTheme,
        item: NotificationItem,
        unit: Float,
        mode: Mode,
    ): OverlayHit {
        val overlayUnit = unit * if (mode == Mode.CAR) 1.8f else 1.1f
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(190, 4, 6, 14)
        canvas.drawRect(area, fill)

        val width = minOf(area.width() * 0.72f, 1180f * unit)
        val height = minOf(area.height() * 0.72f, 470f * unit)
        val card = RectF(
            area.centerX() - width / 2f,
            area.centerY() - height / 2f,
            area.centerX() + width / 2f,
            area.centerY() + height / 2f,
        )
        fill.color = withAlpha(theme.surface, 252)
        canvas.drawRoundRect(card, 4f * overlayUnit, 4f * overlayUnit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2f * overlayUnit
        stroke.color = theme.accent
        canvas.drawRoundRect(card, 4f * overlayUnit, 4f * overlayUnit, stroke)
        fill.color = theme.accent
        canvas.drawRect(
            card.left + 4f * overlayUnit,
            card.top,
            card.right - 4f * overlayUnit,
            card.top + 3f * overlayUnit,
            fill,
        )

        val pad = 24f * overlayUnit
        val textScale = DisplaySettings.notificationTextScale
        val appSize = 10f * overlayUnit * textScale
        val titleSize = 20f * overlayUnit * textScale
        val bodySize = 12f * overlayUnit * textScale
        val lineHeight = 18f * overlayUnit * textScale
        val contentWidth = card.width() - pad * 2f

        text(
            canvas,
            item.app.uppercase(Locale.US),
            card.left + pad,
            card.top + pad + appSize,
            appSize,
            theme.accent,
            bold = true,
            maxWidth = contentWidth * 0.62f,
        )
        val time = timeFormat.format(Date(item.postedAt))
        text(
            canvas,
            time,
            card.right - pad - measure(time, appSize),
            card.top + pad + appSize,
            appSize,
            theme.dim,
        )
        var baseline = card.top + pad + appSize + 16f * overlayUnit + titleSize
        text(
            canvas,
            item.title.ifBlank { "NOTIFICATION" },
            card.left + pad,
            baseline,
            titleSize,
            theme.foreground,
            bold = true,
            maxWidth = contentWidth,
        )
        baseline += 18f * overlayUnit

        val closeLabel = "CLOSE"
        val dismissLabel = "DISMISS"
        val buttonTextSize = 9f * overlayUnit
        val closeWidth = measure(closeLabel, buttonTextSize, bold = true) + 24f * overlayUnit
        val dismissWidth = measure(dismissLabel, buttonTextSize, bold = true) + 24f * overlayUnit
        val buttonHeight = 30f * overlayUnit
        val gap = 10f * overlayUnit
        val close = RectF(
            card.right - pad - closeWidth,
            card.bottom - pad - buttonHeight,
            card.right - pad,
            card.bottom - pad,
        )
        val dismiss = RectF(
            close.left - gap - dismissWidth,
            close.top,
            close.left - gap,
            close.bottom,
        )
        val bodyBottom = close.top - 18f * overlayUnit
        val maxLines = ((bodyBottom - baseline) / lineHeight).toInt().coerceAtLeast(1)
        val lines = wrappedLines(item.text.ifBlank { item.title }, bodySize, contentWidth, maxLines)
        lines.forEachIndexed { index, line ->
            text(
                canvas,
                line,
                card.left + pad,
                baseline + index * lineHeight,
                bodySize,
                theme.foreground,
                maxWidth = contentWidth,
            )
        }

        text(
            canvas,
            "TAP OUTSIDE TO CLOSE",
            card.left + pad,
            card.bottom - pad - 6f * overlayUnit,
            7f * overlayUnit,
            theme.dim,
        )
        drawTextButton(canvas, close, closeLabel, theme, overlayUnit)
        drawTextButton(canvas, dismiss, dismissLabel, theme, overlayUnit, color = theme.red)
        return OverlayHit(card = card, close = close, dismiss = dismiss)
    }

    private fun drawVehicle(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
        text(canvas, "MOCK", content.left, content.top + 12f * unit, 9f * unit, theme.yellow, bold = true)
        text(canvas, "OBD PENDING", content.right - measure("OBD PENDING", 9f * unit), content.top + 12f * unit, 9f * unit, theme.dim)
        text(canvas, "0", content.left, content.top + 75f * unit, 58f * unit, theme.foreground, bold = true)
        text(canvas, "km/h", content.left + 40f * unit, content.top + 75f * unit, 10f * unit, theme.dim)
        text(canvas, "GEAR N", content.left + 40f * unit, content.top + 92f * unit, 9f * unit, theme.accent)
        val range = "412 km"
        text(canvas, "RANGE", content.right - measure("RANGE", 8f * unit), content.top + 34f * unit, 8f * unit, theme.dim)
        text(canvas, range, content.right - measure(range, 16f * unit, bold = true), content.top + 54f * unit, 16f * unit, theme.green, bold = true)
        fill.color = theme.border
        canvas.drawRect(content.left, content.top + 112f * unit, content.right, content.top + 117f * unit, fill)
        fill.color = theme.accent
        canvas.drawRect(content.left, content.top + 112f * unit, content.left + content.width() * 0.28f, content.top + 117f * unit, fill)
        text(canvas, "COOLANT 88°", content.left, content.top + 134f * unit, 9f * unit, theme.foreground)
        text(canvas, "FUEL 62%", content.left + 90f * unit, content.top + 134f * unit, 9f * unit, theme.foreground)
        text(canvas, "12.4 V", content.right - measure("12.4 V", 9f * unit), content.top + 134f * unit, 9f * unit, theme.foreground)
        text(canvas, "MOCK VEHICLE · ELM327 LATER", content.left, content.bottom - 4f * unit, 8f * unit, theme.yellow)
    }

    private fun drawHomeDetails(
        canvas: Canvas,
        tile: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
        mode: Mode,
        themeIndex: Int,
        chips: MutableList<Pair<Int, RectF>>,
    ): RectF? {
        val content = RectF(
            tile.left + 12f * unit,
            tile.top + 27f * unit,
            tile.right - 12f * unit,
            tile.bottom - 10f * unit,
        )
        canvas.save()
        canvas.clipRect(content)
        text(canvas, clockFormat.format(Date()), content.left, content.top + 34f * unit, 34f * unit, theme.foreground, bold = true)
        text(canvas, dateFormat.format(Date()).uppercase(Locale.US), content.left, content.top + 52f * unit, 9f * unit, theme.dim)
        text(canvas, "OMARCHY AUTO", content.left, content.top + 74f * unit, 9f * unit, theme.accent)
        val columns = 5
        val rows = (Themes.all.size + columns - 1) / columns
        val gap = 4f * unit
        val chipHeight = 28f * unit
        val chipBlockHeight = rows * chipHeight + (rows - 1) * gap
        val bottomReserve = if (mode == Mode.PHONE) 32f * unit else 8f * unit
        val chipTop = content.bottom - bottomReserve - chipBlockHeight
        text(canvas, "THEME", content.left, chipTop - 10f * unit, 9f * unit, theme.dim)
        val chipWidth = (content.width() - gap * (columns - 1)) / columns
        Themes.all.forEachIndexed { index, item ->
            val row = index / columns
            val column = index % columns
            val left = content.left + (chipWidth + gap) * column
            val top = chipTop + (chipHeight + gap) * row
            val chip = RectF(left, top, left + chipWidth, top + chipHeight)
            chips += index to chip
            fill.style = Paint.Style.FILL
            fill.color = if (index == themeIndex) theme.accent else Color.TRANSPARENT
            canvas.drawRect(chip, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * unit
            stroke.color = if (index == themeIndex) theme.accent else theme.border
            canvas.drawRect(chip, stroke)
            val label = item.shortName
            text(
                canvas, label, chip.centerX() - measure(label, 8f * unit) / 2f,
                chip.centerY() + 3f * unit, 8f * unit,
                if (index == themeIndex) theme.background else theme.dim,
            )
        }

        if (mode == Mode.PHONE) {
            val setup = RectF(content.left, content.bottom - 28f * unit, content.right, content.bottom - 2f * unit)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * unit
            stroke.color = theme.border
            canvas.drawRect(setup, stroke)
            text(
                canvas, "SETUP · PERMISSIONS", setup.centerX() - measure("SETUP · PERMISSIONS", 9f * unit) / 2f,
                setup.centerY() + 3f * unit, 9f * unit, theme.dim,
            )
            canvas.restore()
            return setup
        }
        canvas.restore()
        return null
    }

    private fun wrappedLines(
        value: String,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
    ): List<String> {
        val paint = textPaint
        paint.textSize = size * fontScale
        val all = mutableListOf<String>()
        for (paragraph in value.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ')) {
                if (word.isEmpty()) continue
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                    line = candidate
                } else {
                    all += line
                    line = word
                }
            }
            all += line
        }
        if (all.size <= maxLines) return all
        val trimmed = all.take(maxLines).toMutableList()
        if (trimmed.isNotEmpty()) {
            trimmed[trimmed.lastIndex] = TextUtils.ellipsize(
                trimmed.last() + " …",
                paint,
                maxWidth,
                TextUtils.TruncateAt.END,
            ).toString()
        }
        return trimmed
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        maxWidth: Float = Float.MAX_VALUE,
    ) {
        if (value.isEmpty()) return
        val paint = if (bold) boldPaint else textPaint
        paint.textSize = size * fontScale
        paint.color = color
        paint.letterSpacing = 0f
        val rendered = if (maxWidth < Float.MAX_VALUE && measure(value, size, bold) > maxWidth) {
            TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        } else {
            value
        }
        canvas.drawText(rendered, x, baseline, paint)
    }

    private fun measure(value: String, size: Float, bold: Boolean = false): Float {
        val paint = if (bold) boldPaint else textPaint
        paint.textSize = size * fontScale
        return paint.measureText(value)
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(Locale.US, total / 60L, total % 60L)
    }

    companion object {
        private const val STALE_AFTER_MS = 6_000L
    }
}
