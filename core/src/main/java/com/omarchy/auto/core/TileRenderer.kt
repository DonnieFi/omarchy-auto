package com.omarchy.auto.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
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

    enum class Target { THEME, SETUP, THEME_CHIP, TILE_DRIVE, TILE_MEDIA, TILE_COMMS, TILE_VEHICLE, TILE_HOME }

    data class Hit(val target: Target, val index: Int = -1)

    private data class Layout(
        val themeButton: RectF,
        val setupButton: RectF?,
        val chips: List<Pair<Int, RectF>>,
        val tiles: Map<Target, RectF>,
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
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFormat = SimpleDateFormat("EEE d MMM", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    fun setSafeArea(rect: Rect?) {
        safeArea = rect?.let { Rect(it) }
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

        val landscape = area.width() > area.height() * 1.25f
        val unit = if (landscape) {
            minOf(area.width() / 1280f, area.height() / 720f)
        } else {
            area.width() / 720f
        }.coerceAtLeast(0.7f)

        val margin = 14f * unit
        val gap = 10f * unit
        val topBarH = 56f * unit
        val footerH = 24f * unit
        val tileTop = area.top + topBarH + gap
        val tileBottom = area.bottom - footerH - gap
        val contentWidth = area.width() - margin * 2f
        val contentHeight = tileBottom - tileTop
        val driveRect: RectF
        val mediaRect: RectF
        val commsRect: RectF
        val vehicleRect: RectF
        val homeRect: RectF

        canvas.drawColor(theme.background)
        drawBackdrop(canvas, area, theme, unit)

        val themeButton = RectF(
            area.right - margin - 62f * unit,
            area.top + (topBarH - 34f * unit) / 2f,
            area.right - margin,
            area.top + (topBarH + 34f * unit) / 2f,
        )
        var setupButton: RectF? = null
        val chips = mutableListOf<Pair<Int, RectF>>()

        if (landscape) {
            val cellW = (contentWidth - gap * 3f) / 4f
            val cellH = (contentHeight - gap) / 2f
            driveRect = RectF(
                area.left + margin,
                tileTop,
                area.left + margin + cellW * 2f + gap,
                tileTop + cellH * 2f + gap,
            )
            mediaRect = RectF(
                area.left + margin + (cellW + gap) * 2f,
                tileTop,
                area.left + margin + (cellW + gap) * 2f + cellW,
                tileTop + cellH,
            )
            commsRect = RectF(
                area.left + margin + (cellW + gap) * 3f,
                tileTop,
                area.left + margin + (cellW + gap) * 3f + cellW,
                tileTop + cellH,
            )
            vehicleRect = RectF(
                area.left + margin + (cellW + gap) * 2f,
                tileTop + cellH + gap,
                area.left + margin + (cellW + gap) * 2f + cellW,
                tileTop + cellH + gap + cellH,
            )
            homeRect = RectF(
                area.left + margin + (cellW + gap) * 3f,
                tileTop + cellH + gap,
                area.left + margin + (cellW + gap) * 3f + cellW,
                tileTop + cellH + gap + cellH,
            )
        } else {
            val driveH = contentHeight * 0.36f
            val sideW = (contentWidth - gap) / 2f
            val rowH = (contentHeight - driveH - gap * 2f) / 2f
            driveRect = RectF(
                area.left + margin,
                tileTop,
                area.left + margin + contentWidth,
                tileTop + driveH,
            )
            mediaRect = RectF(
                area.left + margin,
                tileTop + driveH + gap,
                area.left + margin + sideW,
                tileTop + driveH + gap + rowH,
            )
            commsRect = RectF(
                area.left + margin + sideW + gap,
                tileTop + driveH + gap,
                area.left + margin + contentWidth,
                tileTop + driveH + gap + rowH,
            )
            vehicleRect = RectF(
                area.left + margin,
                tileTop + driveH + gap + rowH + gap,
                area.left + margin + sideW,
                tileTop + contentHeight,
            )
            homeRect = RectF(
                area.left + margin + sideW + gap,
                tileTop + driveH + gap + rowH + gap,
                area.left + margin + contentWidth,
                tileTop + contentHeight,
            )
        }

        drawTopBar(
            canvas, area, theme, unit, state, mode, themeButton,
            themeIndex,
        )

        drawTile(canvas, driveRect, TileId.DRIVE, theme, state, unit, mode)
        drawTile(canvas, mediaRect, TileId.MEDIA, theme, state, unit, mode)
        drawTile(canvas, commsRect, TileId.COMMS, theme, state, unit, mode)
        drawTile(canvas, vehicleRect, TileId.VEHICLE, theme, state, unit, mode)
        drawTile(canvas, homeRect, TileId.HOME, theme, state, unit, mode)

        setupButton = drawHomeDetails(
            canvas, homeRect, theme, state, unit, mode, themeIndex, chips,
        )
        drawFooter(canvas, area, theme, unit, state, mode, themeIndex)

        lastLayout = Layout(
            themeButton = RectF(themeButton),
            setupButton = setupButton?.let { RectF(it) },
            chips = chips.map { it.first to RectF(it.second) },
            tiles = mapOf(
                Target.TILE_DRIVE to RectF(driveRect),
                Target.TILE_MEDIA to RectF(mediaRect),
                Target.TILE_COMMS to RectF(commsRect),
                Target.TILE_VEHICLE to RectF(vehicleRect),
                Target.TILE_HOME to RectF(homeRect),
            ),
        )
    }

    fun hitTest(x: Float, y: Float): Hit? {
        val layout = lastLayout ?: return null
        if (layout.themeButton.contains(x, y)) return Hit(Target.THEME)
        layout.setupButton?.takeIf { it.contains(x, y) }?.let { return Hit(Target.SETUP) }
        layout.chips.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.THEME_CHIP, it.first)
        }
        layout.tiles.entries.firstOrNull { it.value.contains(x, y) }?.let {
            return Hit(it.key)
        }
        return null
    }

    private fun drawBackdrop(canvas: Canvas, area: RectF, theme: OmarchyTheme, unit: Float) {
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(10, 255, 255, 255)
        var x = area.left
        val step = 44f * unit
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
        val topBarH = 56f * unit
        fill.style = Paint.Style.FILL
        fill.color = theme.surface
        canvas.drawRect(area.left, area.top, area.right, area.top + topBarH, fill)
        fill.color = theme.border
        canvas.drawRect(area.left, area.top + topBarH - 1f, area.right, area.top + topBarH, fill)

        val baseline = area.top + topBarH / 2f + 5.5f * unit
        text(canvas, "OMARCHY", area.left + 18f * unit, baseline, 16f * unit, theme.accent, bold = true)
        val brandWidth = measure("OMARCHY", 16f * unit, bold = true)
        text(
            canvas, "// AUTO", area.left + 18f * unit + brandWidth + 10f * unit, baseline,
            11f * unit, theme.dim,
        )
        val modeText = if (mode == Mode.CAR) "AA" else "PHONE"
        text(
            canvas, modeText, area.left + 18f * unit + brandWidth + 75f * unit,
            baseline, 10f * unit, theme.dim,
        )

        val themeName = Themes.at(themeIndex).name
        text(
            canvas, themeName, themeButton.left - 12f * unit - measure(themeName, 10f * unit),
            baseline, 10f * unit, theme.dim,
        )
        val clock = clockFormat.format(Date())
        text(
            canvas, clock, themeButton.left - 12f * unit - measure(themeName, 10f * unit) - 20f * unit - measure(clock, 18f * unit, bold = true),
            baseline + 1f * unit, 18f * unit, theme.foreground, bold = true,
        )

        fill.style = Paint.Style.FILL
        fill.color = theme.surfaceAlt
        canvas.drawRoundRect(themeButton, 2f * unit, 2f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(themeButton, 2f * unit, 2f * unit, stroke)
        text(
            canvas, "THEME", themeButton.centerX() - measure("THEME", 9f * unit) / 2f,
            themeButton.centerY() + 3f * unit, 9f * unit, theme.accent,
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        area: RectF,
        theme: OmarchyTheme,
        unit: Float,
        state: DashboardState,
        mode: Mode,
        themeIndex: Int,
    ) {
        val footerH = 24f * unit
        val top = area.bottom - footerH
        fill.style = Paint.Style.FILL
        fill.color = theme.surface
        canvas.drawRect(area.left, top, area.right, area.bottom, fill)
        fill.color = theme.border
        canvas.drawRect(area.left, top, area.right, top + 1f, fill)

        val baseline = top + footerH / 2f + 3.5f * unit
        var x = area.left + 16f * unit
        x = status(canvas, x, baseline, "MEDIA", state.mediaAccess, theme, unit)
        x = status(canvas, x + 16f * unit, baseline, "NOTIF", state.notificationAccess, theme, unit)
        status(canvas, x + 16f * unit, baseline, "GPS", state.locationAccess, theme, unit)

        val mock = if (state.vehicleMock) "MOCK VEHICLE" else "OBD LIVE"
        text(
            canvas, mock, area.centerX() - measure(mock, 9f * unit) / 2f, baseline,
            9f * unit, if (state.vehicleMock) theme.yellow else theme.green,
        )
        val modeLabel = if (mode == Mode.CAR) "AA HOST" else "PHONE"
        val right = "$modeLabel  ·  ${Themes.at(themeIndex).name}"
        text(
            canvas, right, area.right - 16f * unit - measure(right, 9f * unit),
            baseline, 9f * unit, theme.accent,
        )
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

    private fun drawTile(
        canvas: Canvas,
        rect: RectF,
        id: TileId,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
        mode: Mode,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = theme.surface
        canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)

        val borderColor = if (id == TileId.DRIVE) theme.accent else theme.border
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2f * unit
        stroke.color = borderColor
        canvas.drawRoundRect(
            RectF(rect.left + 1f * unit, rect.top + 1f * unit, rect.right - 1f * unit, rect.bottom - 1f * unit),
            2f * unit, 2f * unit, stroke,
        )

        val legend = "[${id.number} · ${id.title}]"
        val legendSize = 10.5f * unit
        val legendWidth = measure(legend, legendSize, bold = true) + 18f * unit
        fill.color = theme.background
        canvas.drawRect(rect.left + 12f * unit, rect.top - 1f * unit, rect.left + 12f * unit + legendWidth, rect.top + 19f * unit, fill)
        text(
            canvas, legend, rect.left + 20f * unit, rect.top + 13f * unit, legendSize,
            if (id == TileId.DRIVE) theme.accent else theme.dim, bold = true,
        )

        val meta = when (id) {
            TileId.DRIVE -> if (state.gpsActive) "GPS LIVE" else "GPS WAIT"
            TileId.MEDIA -> if (state.media != null) "LIVE" else "WAIT"
            TileId.COMMS -> "${state.notifications.size} NEW"
            TileId.VEHICLE -> if (state.vehicleMock) "MOCK" else "LIVE"
            TileId.HOME -> "THEME"
        }
        val metaColor = when (id) {
            TileId.DRIVE -> if (state.gpsActive) theme.green else theme.dim
            TileId.MEDIA -> if (state.media != null) theme.green else theme.dim
            TileId.COMMS -> if (state.notifications.isNotEmpty()) theme.accent else theme.dim
            TileId.VEHICLE -> if (state.vehicleMock) theme.yellow else theme.green
            TileId.HOME -> theme.dim
        }
        text(
            canvas, meta, rect.right - 12f * unit - measure(meta, 9f * unit),
            rect.top + 13f * unit, 9f * unit, metaColor,
        )

        val content = RectF(
            rect.left + 12f * unit,
            rect.top + 27f * unit,
            rect.right - 12f * unit,
            rect.bottom - 10f * unit,
        )
        canvas.save()
        canvas.clipRect(content)
        when (id) {
            TileId.DRIVE -> drawDrive(canvas, content, theme, state, unit)
            TileId.MEDIA -> drawMedia(canvas, content, theme, state, unit)
            TileId.COMMS -> drawComms(canvas, content, theme, state, unit)
            TileId.VEHICLE -> drawVehicle(canvas, content, theme, state, unit)
            TileId.HOME -> Unit
        }
        canvas.restore()
    }

    private fun drawDrive(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = theme.surfaceAlt
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

        fill.color = Color.argb(10, 255, 255, 255)
        canvas.drawRect(
            content.left + content.width() * 0.12f,
            content.top + content.height() * 0.16f,
            content.left + content.width() * 0.38f,
            content.top + content.height() * 0.34f,
            fill,
        )
        canvas.drawRect(
            content.left + content.width() * 0.56f,
            content.top + content.height() * 0.10f,
            content.left + content.width() * 0.82f,
            content.top + content.height() * 0.26f,
            fill,
        )

        route.reset()
        route.moveTo(content.left + content.width() * 0.14f, content.top + content.height() * 0.66f)
        route.lineTo(content.left + content.width() * 0.30f, content.top + content.height() * 0.50f)
        route.lineTo(content.left + content.width() * 0.46f, content.top + content.height() * 0.56f)
        route.lineTo(content.left + content.width() * 0.60f, content.top + content.height() * 0.36f)
        route.lineTo(content.left + content.width() * 0.84f, content.top + content.height() * 0.22f)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 3f * unit
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.color = theme.accent
        canvas.drawPath(route, stroke)

        val startX = content.left + content.width() * 0.14f
        val startY = content.top + content.height() * 0.66f
        fill.color = theme.green
        canvas.drawCircle(startX, startY, 5f * unit, fill)
        stroke.strokeWidth = 2f * unit
        stroke.color = Color.argb(90, 158, 206, 106)
        canvas.drawCircle(startX, startY, 12f * unit, stroke)

        val endX = content.left + content.width() * 0.84f
        val endY = content.top + content.height() * 0.22f
        stroke.strokeWidth = 2f * unit
        stroke.color = theme.yellow
        canvas.drawCircle(endX, endY, 8f * unit, stroke)

        text(canvas, "DEMO MAP", content.left + 8f * unit, content.top + 14f * unit, 9f * unit, theme.dim)
        val speed = state.speedKph
        val speedText = if (speed != null) "%.0f".format(Locale.US, speed) else "--"
        val speedX = content.left + 10f * unit
        val speedBaseline = content.bottom - 28f * unit
        text(canvas, speedText, speedX, speedBaseline, 58f * unit, theme.foreground, bold = true)
        text(
            canvas, "km/h", speedX + measure(speedText, 58f * unit, bold = true) + 8f * unit,
            speedBaseline, 11f * unit, theme.dim,
        )
        val gps = if (state.gpsActive) "GPS LIVE" else "GPS WAIT"
        text(
            canvas, gps, content.left + 10f * unit, speedBaseline + 20f * unit, 10f * unit,
            if (state.gpsActive) theme.green else theme.dim,
        )

        val eta = "DEMO ROUTE"
        text(
            canvas, eta, content.right - 10f * unit - measure(eta, 9f * unit),
            content.bottom - 12f * unit, 9f * unit, theme.dim,
        )
    }

    private fun drawMedia(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
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
            return
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

        val progressTop = content.bottom - 34f * unit
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

        drawTransport(canvas, content.left + progressWidth / 2f, content.bottom - 8f * unit, media.playing, theme, unit)
    }

    private fun drawTransport(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        playing: Boolean,
        theme: OmarchyTheme,
        unit: Float,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = theme.accent
        canvas.drawRoundRect(
            RectF(centerX - 21f * unit, centerY - 14f * unit, centerX + 21f * unit, centerY + 14f * unit),
            2f * unit, 2f * unit, fill,
        )
        if (playing) {
            fill.color = theme.background
            canvas.drawRect(centerX - 5f * unit, centerY - 6f * unit, centerX - 2f * unit, centerY + 6f * unit, fill)
            canvas.drawRect(centerX + 2f * unit, centerY - 6f * unit, centerX + 5f * unit, centerY + 6f * unit, fill)
        } else {
            val path = Path()
            path.moveTo(centerX - 3f * unit, centerY - 7f * unit)
            path.lineTo(centerX + 7f * unit, centerY)
            path.lineTo(centerX - 3f * unit, centerY + 7f * unit)
            path.close()
            fill.color = theme.background
            canvas.drawPath(path, fill)
        }
    }

    private fun drawComms(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
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
            return
        }
        if (state.notifications.isEmpty()) {
            text(
                canvas, "NO NOTIFICATIONS", content.centerX() - measure("NO NOTIFICATIONS", 14f * unit, bold = true) / 2f,
                content.centerY(), 14f * unit, theme.dim, bold = true,
            )
            return
        }

        val maxItems = if (content.height() / unit > 360f) 4 else 3
        val items = state.notifications.take(maxItems)
        val rowH = minOf(54f * unit, content.height() / items.size)
        items.forEachIndexed { index, item ->
            val top = content.top + index * rowH
            val bottom = top + rowH - 6f * unit
            fill.style = Paint.Style.FILL
            fill.color = if (index == 0) theme.surfaceAlt else Color.TRANSPARENT
            canvas.drawRect(content.left, top, content.right, bottom, fill)
            if (index == 0) {
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1f * unit
                stroke.color = theme.border
                canvas.drawRect(content.left, top, content.right, bottom, stroke)
            }
            fill.color = theme.accent
            canvas.drawRect(content.left, top, content.left + 3f * unit, bottom, fill)

            text(
                canvas, item.app.uppercase(Locale.US), content.left + 9f * unit,
                top + 12f * unit, 8f * unit, theme.accent, bold = true,
                maxWidth = content.width() * 0.6f,
            )
            val time = timeFormat.format(Date(item.postedAt))
            text(
                canvas, time, content.right - 9f * unit - measure(time, 8f * unit),
                top + 12f * unit, 8f * unit, theme.dim,
            )
            text(
                canvas, item.title, content.left + 9f * unit, top + 24f * unit,
                10f * unit, theme.foreground, maxWidth = content.width() - 18f * unit,
            )
            text(
                canvas, item.text, content.left + 9f * unit, top + 34f * unit,
                9f * unit, theme.dim, maxWidth = content.width() - 18f * unit,
            )
        }
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
        text(canvas, "THEME", content.left, content.bottom - 52f * unit, 9f * unit, theme.dim)

        val chipTop = content.bottom - 46f * unit
        val gap = 4f * unit
        val chipWidth = (content.width() - gap * (Themes.all.size - 1)) / Themes.all.size
        Themes.all.forEachIndexed { index, item ->
            val chip = RectF(
                content.left + (chipWidth + gap) * index,
                chipTop,
                content.left + (chipWidth + gap) * index + chipWidth,
                chipTop + 24f * unit,
            )
            chips += index to chip
            fill.style = Paint.Style.FILL
            fill.color = if (index == themeIndex) theme.accent else Color.TRANSPARENT
            canvas.drawRect(chip, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * unit
            stroke.color = if (index == themeIndex) theme.accent else theme.border
            canvas.drawRect(chip, stroke)
            val label = item.name.take(5)
            text(
                canvas, label, chip.centerX() - measure(label, 7f * unit) / 2f,
                chip.centerY() + 3f * unit, 7f * unit,
                if (index == themeIndex) theme.background else theme.dim,
            )
        }

        if (mode == Mode.PHONE) {
            val setup = RectF(content.left, content.bottom - 16f * unit, content.right, content.bottom - 2f * unit)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * unit
            stroke.color = theme.border
            canvas.drawRect(setup, stroke)
            text(
                canvas, "SETUP · PERMISSIONS", setup.centerX() - measure("SETUP · PERMISSIONS", 8f * unit) / 2f,
                setup.centerY() + 3f * unit, 8f * unit, theme.dim,
            )
            canvas.restore()
            return setup
        } else {
            text(
                canvas, "AA · MAP SURFACE", content.left, content.bottom - 6f * unit,
                8f * unit, theme.dim,
            )
        }
        canvas.restore()
        return null
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
        paint.textSize = size
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
        paint.textSize = size
        return paint.measureText(value)
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(Locale.US, total / 60L, total % 60L)
    }
}
