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

    enum class KeyboardMode { NONE, MAP_SEARCH, WEB_INPUT }

    enum class Target {
        THEME,
        SETUP,
        THEME_CHIP,
        STAGE_OPEN,
        PLAY_PAUSE,
        PREV_TRACK,
        NEXT_TRACK,
        MEDIA_SESSION_SELECT,
        WEB_APP_OPEN,
        WEB_BACK,
        WEB_RELOAD,
        WEB_SEARCH,
        WEB_SCROLL_UP,
        WEB_SCROLL_DOWN,
        WEB_CLOSE,
        WEB_STAGE_TAP,
        NOTIF_REFRESH,
        NOTIF_CLEAR,
        NOTIF_SCROLL_UP,
        NOTIF_SCROLL_DOWN,
        NOTIF_ROW_DISMISS,
        NOTIFICATION,
        NOTIF_CLOSE,
        NOTIF_DISMISS_ONE,
        OVERLAY_SCROLL_UP,
        OVERLAY_SCROLL_DOWN,
        OVERLAY_DISMISS,
        MAP_ZOOM_IN,
        MAP_ZOOM_OUT,
        MAP_RECENTER,
        MAP_SEARCH_OPEN,
        MAP_NAV_TOGGLE,
        MAP_CANDIDATE_SELECT,
        RAIL_CONFIG_TOGGLE,
        MEDIA_TARGET_SELECT,
        RAIL_SCROLL_UP,
        RAIL_SCROLL_DOWN,
        RAIL_ITEM,
        KB_KEY,
        KB_SPACE,
        KB_BACKSPACE,
        KB_CLEAR,
        KB_SUBMIT,
        KB_CLOSE,
        TILE_DRIVE,
        TILE_MEDIA,
        TILE_COMMS,
        TILE_VEHICLE,
        TILE_HOME,
    }

    data class FrameInfo(
        val mode: Mode,
        val mapFillsCanvas: Boolean,
        val drawsCustomRail: Boolean,
        val drawsManeuverBanner: Boolean,
        val drawsKeyboard: Boolean,
        val drawsNotificationList: Boolean,
        val drawsWebStage: Boolean,
        val drawsWallpaper: Boolean,
    )

    @Volatile
    var lastFrame: FrameInfo? = null
        private set

    data class Hit(
        val target: Target,
        val index: Int = -1,
        val key: String? = null,
        val railItem: RailItemId? = null,
        val webApp: StageWebApp? = null,
        val normX: Float = 0f,
        val normY: Float = 0f,
    )

    private data class Layout(
        val stageRect: RectF,
        val railRect: RectF,
        val themeButton: RectF?,
        val setupButton: RectF?,
        val chips: List<Pair<Int, RectF>>,
        val railToggles: List<Pair<RailItemId, RectF>>,
        val mediaTargets: List<Pair<String, RectF>>,
        val stageOpen: RectF?,
        val playPause: RectF?,
        val prevTrack: RectF?,
        val nextTrack: RectF?,
        val mediaSessions: List<Pair<String, RectF>>,
        val webAppButtons: List<Pair<StageWebApp, RectF>>,
        val webBack: RectF?,
        val webReload: RectF?,
        val webSearch: RectF?,
        val webScrollUp: RectF?,
        val webScrollDown: RectF?,
        val webClose: RectF?,
        val webStageRect: RectF?,
        val notifRefresh: RectF?,
        val notifClear: RectF?,
        val notifScrollUp: RectF?,
        val notifScrollDown: RectF?,
        val notifDismissRows: List<Pair<String, RectF>>,
        val notifications: List<Pair<String, RectF>>,
        val notifCard: RectF?,
        val notifClose: RectF?,
        val notifDismissOne: RectF?,
        val overlayScrollUp: RectF?,
        val overlayScrollDown: RectF?,
        val mapZoomIn: RectF?,
        val mapZoomOut: RectF?,
        val mapRecenter: RectF?,
        val mapSearchOpen: RectF?,
        val mapNavToggle: RectF?,
        val railScrollUp: RectF?,
        val railScrollDown: RectF?,
        val railItems: List<Pair<RailItemId, RectF>>,
        val tiles: Map<Target, RectF>,
        val keyboardHit: KeyboardHit?,
    )

    private data class StageHit(
        val open: RectF?,
        val play: RectF?,
        val setup: RectF?,
        val railToggles: List<Pair<RailItemId, RectF>> = emptyList(),
        val mediaTargets: List<Pair<String, RectF>> = emptyList(),
        val previous: RectF? = null,
        val next: RectF? = null,
        val mediaSessions: List<Pair<String, RectF>> = emptyList(),
        val webAppButtons: List<Pair<StageWebApp, RectF>> = emptyList(),
        val webBack: RectF? = null,
        val webReload: RectF? = null,
        val webSearch: RectF? = null,
        val webScrollUp: RectF? = null,
        val webScrollDown: RectF? = null,
        val webClose: RectF? = null,
        val webStageRect: RectF? = null,
        val refresh: RectF? = null,
        val clear: RectF? = null,
        val notifScrollUp: RectF? = null,
        val notifScrollDown: RectF? = null,
        val notifDismissRows: List<Pair<String, RectF>> = emptyList(),
        val notifications: List<Pair<String, RectF>> = emptyList(),
        val mapZoomIn: RectF? = null,
        val mapZoomOut: RectF? = null,
        val mapRecenter: RectF? = null,
        val mapSearchOpen: RectF? = null,
        val mapNavToggle: RectF? = null,
    )

    private data class DriveHit(
        val zoomIn: RectF? = null,
        val zoomOut: RectF? = null,
        val recenter: RectF? = null,
        val searchOpen: RectF? = null,
        val navToggle: RectF? = null,
    )

    private data class CommsHit(
        val clear: RectF?,
        val notifications: List<Pair<String, RectF>>,
        val dismissRows: List<Pair<String, RectF>> = emptyList(),
        val scrollUp: RectF? = null,
        val scrollDown: RectF? = null,
    )

    private data class OverlayHit(
        val card: RectF,
        val close: RectF,
        val dismiss: RectF,
        val scrollUp: RectF? = null,
        val scrollDown: RectF? = null,
    )

    private data class MediaHit(
        val play: RectF? = null,
        val previous: RectF? = null,
        val next: RectF? = null,
        val sessions: List<Pair<String, RectF>> = emptyList(),
        val webApps: List<Pair<StageWebApp, RectF>> = emptyList(),
        val webBack: RectF? = null,
        val webReload: RectF? = null,
        val webSearch: RectF? = null,
        val webScrollUp: RectF? = null,
        val webScrollDown: RectF? = null,
        val webClose: RectF? = null,
        val webStageRect: RectF? = null,
    )

    private data class HomeHit(
        val setup: RectF? = null,
        val railToggles: List<Pair<RailItemId, RectF>> = emptyList(),
        val mediaTargets: List<Pair<String, RectF>> = emptyList(),
    )

    private data class KeyboardHit(
        val card: RectF,
        val keys: List<Pair<String, RectF>>,
        val space: RectF?,
        val backspace: RectF,
        val clear: RectF,
        val submit: RectF,
        val close: RectF,
        val candidates: List<Pair<Int, RectF>> = emptyList(),
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
    var webStageApp: StageWebApp? = null
        private set

    @Volatile
    var homeSetupOpen: Boolean = false
        private set

    @Volatile
    var keyboardMode: KeyboardMode = KeyboardMode.NONE
        private set

    @Volatile
    var keyboardQuery: String = ""
        private set

    @Volatile
    var notifScrollOffset: Int = 0
        private set

    @Volatile
    var overlayScrollOffset: Int = 0
        private set

    @Volatile
    var railScrollOffset: Int = 0
        private set

    private var notifDragAccum: Float = 0f
    private var railDragAccum: Float = 0f
    private var overlayDragAccum: Float = 0f

    @Volatile
    private var background: Bitmap? = null

    @Volatile
    private var overlayKey: String? = null

    /** Surface of the most recent [draw]. Input policy follows that surface. */
    private var renderMode: Mode = Mode.PHONE

    private fun policySurface(): CarPlatformPolicy.Surface =
        if (renderMode == Mode.CAR) CarPlatformPolicy.Surface.ANDROID_AUTO
        else CarPlatformPolicy.Surface.PHONE

    fun resetGestureAccumulators() {
        notifDragAccum = 0f
        railDragAccum = 0f
        overlayDragAccum = 0f
    }

    fun setSafeArea(rect: Rect?) {
        safeArea = rect?.let { Rect(it) }
    }

    fun setBackground(bitmap: Bitmap?) {
        background = bitmap
    }

    fun toggle(tile: TileId) {
        if (selected == tile && webStageApp == null) {
            selected = null
        } else {
            selected = tile
            webStageApp = null
        }
        StageWebController.setActive(false)
        overlayKey = null
        keyboardMode = KeyboardMode.NONE
    }

    fun show(tile: TileId) {
        selected = tile
        webStageApp = null
        StageWebController.setActive(false)
        overlayKey = null
        keyboardMode = KeyboardMode.NONE
    }

    fun openWebApp(app: StageWebApp) {
        if (!CarPlatformPolicy.allowsWebStage(policySurface())) return
        if (selected == TileId.MEDIA && webStageApp == app) {
            selected = null
            webStageApp = null
            StageWebController.setActive(false)
        } else {
            selected = TileId.MEDIA
            webStageApp = app
            StageWebController.open(app)
        }
        overlayKey = null
        keyboardMode = KeyboardMode.NONE
    }

    fun closeWebApp() {
        webStageApp = null
        StageWebController.setActive(false)
        keyboardMode = KeyboardMode.NONE
    }

    fun selectRailItem(item: RailItemId) {
        val web = item.webApp
        if (web != null) {
            openWebApp(web)
        } else if (item.tileId != null) {
            toggle(item.tileId)
        }
    }

    fun toggleHomeSetup() {
        selected = TileId.HOME
        homeSetupOpen = !homeSetupOpen
        overlayKey = null
    }

    fun openKeyboard(mode: KeyboardMode, initialQuery: String = ""): Boolean {
        if (!CarPlatformPolicy.allowsCustomKeyboard(policySurface())) return false
        keyboardMode = mode
        keyboardQuery = initialQuery
        return true
    }

    fun closeKeyboard() {
        keyboardMode = KeyboardMode.NONE
    }

    fun appendKeyboardChar(ch: String) {
        if (keyboardQuery.length < 160) {
            keyboardQuery += ch
        }
    }

    fun backspaceKeyboard() {
        if (keyboardQuery.isNotEmpty()) {
            keyboardQuery = keyboardQuery.dropLast(1)
        }
    }

    fun clearKeyboard() {
        keyboardQuery = ""
        if (keyboardMode == KeyboardMode.MAP_SEARCH) {
            NavigationController.clearQuery()
        }
    }

    fun scrollNotifications(delta: Int) {
        val total = LiveDataStore.state.notifications.size
        notifScrollOffset = (notifScrollOffset + delta).coerceIn(0, (total - 1).coerceAtLeast(0))
    }

    fun scrollOverlay(delta: Int) {
        overlayScrollOffset = (overlayScrollOffset + delta).coerceAtLeast(0)
    }

    fun scrollRail(delta: Int) {
        val enabled = RailConfigStore.enabledItems()
        val maxOffset = (enabled.size - MAX_VISIBLE_RAIL).coerceAtLeast(0)
        railScrollOffset = (railScrollOffset + delta).coerceIn(0, maxOffset)
    }

    fun openNotification(key: String?) {
        overlayKey = key
        overlayScrollOffset = 0
    }

    fun closeNotification() {
        overlayKey = null
        overlayScrollOffset = 0
    }

    /**
     * Handles drag/scroll gestures across the canvas (Drive map pan, Comms list
     * scroll, Web stage scroll, or right-rail scroll).
     */
    fun scroll(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        stateLat: Double? = null,
        stateLon: Double? = null,
    ): Boolean {
        val layout = lastLayout ?: return false
        if (overlayKey != null) {
            overlayDragAccum += dy
            var moved = false
            while (kotlin.math.abs(overlayDragAccum) >= 12f) {
                val step = if (overlayDragAccum < 0f) 1 else -1
                overlayDragAccum -= if (overlayDragAccum < 0f) -12f else 12f
                scrollOverlay(step)
                moved = true
            }
            return moved
        }
        if (keyboardMode != KeyboardMode.NONE) return false
        if (x > 0f && layout.railRect.contains(x, y)) {
            railDragAccum += dy
            var moved = false
            while (kotlin.math.abs(railDragAccum) >= 18f) {
                val step = if (railDragAccum < 0f) 1 else -1
                railDragAccum -= if (railDragAccum < 0f) -18f else 18f
                scrollRail(step)
                moved = true
            }
            return moved
        }
        if (x > 0f && !layout.stageRect.contains(x, y)) return false
        return when (selected) {
            TileId.DRIVE -> {
                if (dx == 0f && dy == 0f) return false
                val baseLat = stateLat ?: LiveDataStore.state.latitude ?: MapSnapshotStore.latitude ?: 43.6532
                val baseLon = stateLon ?: LiveDataStore.state.longitude ?: MapSnapshotStore.longitude ?: -79.3832
                val bearing = MapCameraStore.cameraBearing(LiveDataStore.state.bearing)
                MapCameraStore.panByPixels(-dx, -dy, baseLat, baseLon, bearing)
                true
            }
            TileId.COMMS -> {
                if (!CarPlatformPolicy.allowsCommsBrowsing(policySurface())) return false
                notifDragAccum += dy
                var moved = false
                while (kotlin.math.abs(notifDragAccum) >= 16f) {
                    val step = if (notifDragAccum < 0f) 1 else -1
                    notifDragAccum -= if (notifDragAccum < 0f) -16f else 16f
                    scrollNotifications(step)
                    moved = true
                }
                moved
            }
            TileId.MEDIA -> {
                if (webStageApp != null) {
                    StageWebController.scrollBy((-dy).toInt())
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    fun scale(scaleFactor: Float): Boolean {
        if (selected == TileId.DRIVE && overlayKey == null && keyboardMode == KeyboardMode.NONE) {
            MapCameraStore.scaleBy(scaleFactor)
            return true
        }
        return false
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: DashboardState,
        themeIndex: Int,
        mode: Mode,
    ) {
        renderMode = mode
        if (CarPlatformPolicy.mapOnly(policySurface())) {
            drawCarNavigationSurface(canvas, width, height, state, themeIndex)
            return
        }
        if (DistractionLock.suppressesSharedDistractions()) {
            if (keyboardMode != KeyboardMode.NONE) closeKeyboard()
            if (overlayKey != null) closeNotification()
            if (webStageApp != null) closeWebApp()
        }
        val theme = Themes.at(themeIndex)
        val car = mode == Mode.CAR
        // Both surfaces keep the shell inside the reported safe area so host
        // chrome (or the phone status bar) does not cover the rail or buttons.
        val area = safeArea?.let { raw ->
            RectF(
                raw.left.coerceIn(0, width).toFloat(),
                raw.top.coerceIn(0, height).toFloat(),
                raw.right.coerceIn(0, width).toFloat(),
                raw.bottom.coerceIn(0, height).toFloat(),
            )
        }?.takeIf { it.width() > 100f && it.height() > 100f }
            ?: RectF(0f, 0f, width.toFloat(), height.toFloat())
        val wasMapActive = MapViewport.active
        MapViewport.active = selected == TileId.DRIVE
        val webActive = selected == TileId.MEDIA && webStageApp != null
        StageWebController.setActive(webActive, webStageApp)

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
        val railContainer = RectF(railX, tileTop, railX + railWidth, tileBottom)
        val allRailItems = RailConfigStore.enabledItems()
        val hasRailScroll = allRailItems.size > MAX_VISIBLE_RAIL
        val maxRailOffset = (allRailItems.size - MAX_VISIBLE_RAIL).coerceAtLeast(0)
        if (railScrollOffset > maxRailOffset) railScrollOffset = maxRailOffset
        val visibleRailItems = if (hasRailScroll) {
            allRailItems.drop(railScrollOffset).take(MAX_VISIBLE_RAIL)
        } else {
            allRailItems
        }

        val scrollBtnH = if (hasRailScroll) (if (car) 48f else 48f) * unit else 0f
        val railScrollUpRect = if (hasRailScroll) {
            RectF(railX, tileTop, railX + railWidth, tileTop + scrollBtnH)
        } else {
            null
        }
        val railScrollDownRect = if (hasRailScroll) {
            RectF(railX, tileBottom - scrollBtnH, railX + railWidth, tileBottom)
        } else {
            null
        }
        val railListTop = if (hasRailScroll) tileTop + scrollBtnH + gap * 0.5f else tileTop
        val railListBottom = if (hasRailScroll) tileBottom - scrollBtnH - gap * 0.5f else tileBottom
        val railListHeight = (railListBottom - railListTop).coerceAtLeast(60f * unit)
        val countVisible = visibleRailItems.size.coerceAtLeast(1)
        val tileHeight = minOf(
            (if (car) 112f else 84f) * unit,
            (railListHeight - gap * (countVisible - 1)) / countVisible,
        )
        val railRects = visibleRailItems.mapIndexed { index, item ->
            item to RectF(
                railX,
                railListTop + (tileHeight + gap) * index,
                railX + railWidth,
                railListTop + (tileHeight + gap) * index + tileHeight,
            )
        }
        val railUnit = unit * (if (car) 1.9f else 1f)

        drawTopBar(canvas, area, theme, unit, margin, state, mode, themeButton, themeIndex)
        val stageHit = if (selected != null) {
            drawStage(canvas, stage, theme, state, unit, mode, themeIndex, chips)
        } else {
            drawIdleStage(canvas, area, tileBottom, theme, unit, mode)
            StageHit(open = null, play = null, setup = null)
        }
        if (railScrollUpRect != null) {
            drawTextButton(
                canvas,
                railScrollUpRect,
                if (railScrollOffset > 0) "▲ RAIL (${railScrollOffset + 1}/${allRailItems.size})" else "▲ TOP",
                theme,
                unit * if (car) 1.4f else 0.9f,
                color = if (railScrollOffset > 0) theme.accent else theme.dim,
            )
        }
        railRects.forEachIndexed { visibleIdx, (item, rect) ->
            drawRailItem(
                canvas = canvas,
                rect = rect,
                item = item,
                numberIndex = railScrollOffset + visibleIdx + 1,
                theme = theme,
                state = state,
                unit = railUnit,
            )
        }
        if (railScrollDownRect != null) {
            val moreBelow = railScrollOffset < maxRailOffset
            drawTextButton(
                canvas,
                railScrollDownRect,
                if (moreBelow) "▼ MORE (${allRailItems.size - railScrollOffset - visibleRailItems.size})" else "▼ END",
                theme,
                unit * if (car) 1.4f else 0.9f,
                color = if (moreBelow) theme.accent else theme.dim,
            )
        }
        drawFooter(canvas, area, theme, unit, state, mode, themeIndex)

        val overlay = overlayKey?.let { key -> state.notifications.firstOrNull { it.key == key } }
        if (overlayKey != null && overlay == null) overlayKey = null
        val overlayHit = overlay?.let {
            drawNotificationOverlay(canvas, area, theme, it, unit, mode)
        }

        val keyboardHit = if (keyboardMode != KeyboardMode.NONE && overlayHit == null) {
            drawKeyboardOverlay(canvas, area, theme, unit, mode)
        } else {
            null
        }

        if (wasMapActive != MapViewport.active) {
            LiveDataStore.notifyDisplayChanged()
        }

        val legacyTiles = mutableMapOf<Target, RectF>()
        railRects.forEach { (item, rect) ->
            val target = when (item) {
                RailItemId.DRIVE -> Target.TILE_DRIVE
                RailItemId.MEDIA -> Target.TILE_MEDIA
                RailItemId.COMMS -> Target.TILE_COMMS
                RailItemId.VEHICLE -> Target.TILE_VEHICLE
                RailItemId.HOME -> Target.TILE_HOME
                else -> null
            }
            if (target != null) legacyTiles[target] = RectF(rect)
        }

        lastLayout = Layout(
            stageRect = RectF(stage),
            railRect = RectF(railContainer),
            themeButton = themeButton?.let { RectF(it) },
            setupButton = stageHit.setup?.let { RectF(it) },
            chips = chips.map { it.first to RectF(it.second) },
            railToggles = stageHit.railToggles.map { it.first to RectF(it.second) },
            mediaTargets = stageHit.mediaTargets.map { it.first to RectF(it.second) },
            stageOpen = stageHit.open?.let { RectF(it) },
            playPause = stageHit.play?.let { RectF(it) },
            prevTrack = stageHit.previous?.let { RectF(it) },
            nextTrack = stageHit.next?.let { RectF(it) },
            mediaSessions = stageHit.mediaSessions.map { it.first to RectF(it.second) },
            webAppButtons = stageHit.webAppButtons.map { it.first to RectF(it.second) },
            webBack = stageHit.webBack?.let { RectF(it) },
            webReload = stageHit.webReload?.let { RectF(it) },
            webSearch = stageHit.webSearch?.let { RectF(it) },
            webScrollUp = stageHit.webScrollUp?.let { RectF(it) },
            webScrollDown = stageHit.webScrollDown?.let { RectF(it) },
            webClose = stageHit.webClose?.let { RectF(it) },
            webStageRect = stageHit.webStageRect?.let { RectF(it) },
            notifRefresh = stageHit.refresh?.let { RectF(it) },
            notifClear = stageHit.clear?.let { RectF(it) },
            notifScrollUp = stageHit.notifScrollUp?.let { RectF(it) },
            notifScrollDown = stageHit.notifScrollDown?.let { RectF(it) },
            notifDismissRows = stageHit.notifDismissRows.map { it.first to RectF(it.second) },
            notifications = stageHit.notifications.map { it.first to RectF(it.second) },
            notifCard = overlayHit?.card?.let { RectF(it) },
            notifClose = overlayHit?.close?.let { RectF(it) },
            notifDismissOne = overlayHit?.dismiss?.let { RectF(it) },
            overlayScrollUp = overlayHit?.scrollUp?.let { RectF(it) },
            overlayScrollDown = overlayHit?.scrollDown?.let { RectF(it) },
            mapZoomIn = stageHit.mapZoomIn?.let { RectF(it) },
            mapZoomOut = stageHit.mapZoomOut?.let { RectF(it) },
            mapRecenter = stageHit.mapRecenter?.let { RectF(it) },
            mapSearchOpen = stageHit.mapSearchOpen?.let { RectF(it) },
            mapNavToggle = stageHit.mapNavToggle?.let { RectF(it) },
            railScrollUp = railScrollUpRect?.let { RectF(it) },
            railScrollDown = railScrollDownRect?.let { RectF(it) },
            railItems = railRects.map { it.first to RectF(it.second) },
            tiles = legacyTiles,
            keyboardHit = keyboardHit,
        )
        lastFrame = FrameInfo(
            mode = mode,
            mapFillsCanvas = false,
            drawsCustomRail = true,
            drawsManeuverBanner = selected == TileId.DRIVE &&
                CarPlatformPolicy.allowsCanvasManeuverText(policySurface()),
            drawsKeyboard = keyboardHit != null,
            drawsNotificationList = selected == TileId.COMMS && stageHit.notifications.isNotEmpty(),
            drawsWebStage = webStageApp != null,
            drawsWallpaper = true,
        )
    }

    fun hitTest(x: Float, y: Float): Hit? {
        val layout = lastLayout ?: return null
        if (overlayKey != null) {
            layout.notifClose?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_CLOSE) }
            layout.notifDismissOne?.takeIf { it.contains(x, y) }?.let {
                return Hit(Target.NOTIF_DISMISS_ONE, key = overlayKey)
            }
            layout.overlayScrollUp?.takeIf { it.contains(x, y) }?.let {
                return Hit(Target.OVERLAY_SCROLL_UP)
            }
            layout.overlayScrollDown?.takeIf { it.contains(x, y) }?.let {
                return Hit(Target.OVERLAY_SCROLL_DOWN)
            }
            if (layout.notifCard?.contains(x, y) == true) return null
            return Hit(Target.OVERLAY_DISMISS)
        }
        val kb = layout.keyboardHit
        if (kb != null) {
            kb.close.takeIf { it.contains(x, y) }?.let { return Hit(Target.KB_CLOSE) }
            kb.submit.takeIf { it.contains(x, y) }?.let { return Hit(Target.KB_SUBMIT) }
            kb.backspace.takeIf { it.contains(x, y) }?.let { return Hit(Target.KB_BACKSPACE) }
            kb.clear.takeIf { it.contains(x, y) }?.let { return Hit(Target.KB_CLEAR) }
            kb.space?.takeIf { it.contains(x, y) }?.let { return Hit(Target.KB_SPACE) }
            kb.candidates.firstOrNull { it.second.contains(x, y) }?.let {
                return Hit(Target.MAP_CANDIDATE_SELECT, index = it.first)
            }
            kb.keys.firstOrNull { it.second.contains(x, y) }?.let {
                return Hit(Target.KB_KEY, key = it.first)
            }
            if (kb.card.contains(x, y)) return null
            return Hit(Target.KB_CLOSE)
        }
        layout.themeButton?.takeIf { it.contains(x, y) }?.let { return Hit(Target.THEME) }
        layout.setupButton?.takeIf { it.contains(x, y) }?.let { return Hit(Target.SETUP) }
        layout.railToggles.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.RAIL_CONFIG_TOGGLE, railItem = it.first)
        }
        layout.mediaTargets.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.MEDIA_TARGET_SELECT, key = it.first)
        }
        layout.chips.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.THEME_CHIP, it.first)
        }
        layout.stageOpen?.takeIf { it.contains(x, y) }?.let { return Hit(Target.STAGE_OPEN) }
        layout.playPause?.takeIf { it.contains(x, y) }?.let { return Hit(Target.PLAY_PAUSE) }
        layout.prevTrack?.takeIf { it.contains(x, y) }?.let { return Hit(Target.PREV_TRACK) }
        layout.nextTrack?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NEXT_TRACK) }
        layout.mediaSessions.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.MEDIA_SESSION_SELECT, key = it.first)
        }
        layout.webAppButtons.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.WEB_APP_OPEN, webApp = it.first)
        }
        layout.webBack?.takeIf { it.contains(x, y) }?.let { return Hit(Target.WEB_BACK) }
        layout.webReload?.takeIf { it.contains(x, y) }?.let { return Hit(Target.WEB_RELOAD) }
        layout.webSearch?.takeIf { it.contains(x, y) }?.let { return Hit(Target.WEB_SEARCH) }
        layout.webScrollUp?.takeIf { it.contains(x, y) }?.let { return Hit(Target.WEB_SCROLL_UP) }
        layout.webScrollDown?.takeIf { it.contains(x, y) }?.let { return Hit(Target.WEB_SCROLL_DOWN) }
        layout.webClose?.takeIf { it.contains(x, y) }?.let { return Hit(Target.WEB_CLOSE) }
        layout.webStageRect?.takeIf { it.contains(x, y) && it.width() > 0f && it.height() > 0f }?.let { rect ->
            return Hit(
                target = Target.WEB_STAGE_TAP,
                normX = ((x - rect.left) / rect.width()).coerceIn(0f, 1f),
                normY = ((y - rect.top) / rect.height()).coerceIn(0f, 1f),
            )
        }
        layout.mapZoomIn?.takeIf { it.contains(x, y) }?.let { return Hit(Target.MAP_ZOOM_IN) }
        layout.mapZoomOut?.takeIf { it.contains(x, y) }?.let { return Hit(Target.MAP_ZOOM_OUT) }
        layout.mapRecenter?.takeIf { it.contains(x, y) }?.let { return Hit(Target.MAP_RECENTER) }
        layout.mapSearchOpen?.takeIf { it.contains(x, y) }?.let { return Hit(Target.MAP_SEARCH_OPEN) }
        layout.mapNavToggle?.takeIf { it.contains(x, y) }?.let { return Hit(Target.MAP_NAV_TOGGLE) }
        layout.notifRefresh?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_REFRESH) }
        layout.notifClear?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_CLEAR) }
        layout.notifScrollUp?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_SCROLL_UP) }
        layout.notifScrollDown?.takeIf { it.contains(x, y) }?.let { return Hit(Target.NOTIF_SCROLL_DOWN) }
        layout.notifDismissRows.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.NOTIF_ROW_DISMISS, key = it.first)
        }
        layout.notifications.firstOrNull { it.second.contains(x, y) }?.let {
            return Hit(Target.NOTIFICATION, key = it.first)
        }
        layout.railScrollUp?.takeIf { it.contains(x, y) }?.let { return Hit(Target.RAIL_SCROLL_UP) }
        layout.railScrollDown?.takeIf { it.contains(x, y) }?.let { return Hit(Target.RAIL_SCROLL_DOWN) }
        layout.railItems.firstOrNull { it.second.contains(x, y) }?.let { (item, _) ->
            return when (item) {
                RailItemId.DRIVE -> Hit(Target.TILE_DRIVE, railItem = item)
                RailItemId.MEDIA -> Hit(Target.TILE_MEDIA, railItem = item)
                RailItemId.COMMS -> Hit(Target.TILE_COMMS, railItem = item)
                RailItemId.VEHICLE -> Hit(Target.TILE_VEHICLE, railItem = item)
                RailItemId.HOME -> Hit(Target.TILE_HOME, railItem = item)
                else -> Hit(Target.RAIL_ITEM, railItem = item)
            }
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

    private fun drawRailItem(
        canvas: Canvas,
        rect: RectF,
        item: RailItemId,
        numberIndex: Int,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ) {
        val isSelected = when {
            item.webApp != null -> selected == TileId.MEDIA && webStageApp == item.webApp
            item == RailItemId.MEDIA -> selected == TileId.MEDIA && webStageApp == null
            else -> selected == item.tileId
        }
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

        val number = "[$numberIndex]"
        text(
            canvas, number, rect.left + pad, numberBaseline,
            numberSize, if (isSelected) theme.accent else theme.dim, bold = true,
        )
        text(
            canvas, item.title, rect.left + pad, titleBaseline,
            titleSize, if (isSelected) theme.foreground else theme.dim, bold = true,
            maxWidth = rect.width() - pad * 2f,
        )

        val meta = when (item) {
            RailItemId.DRIVE -> when {
                NavigationController.navActive -> "NAV"
                state.gpsActive -> "GPS"
                else -> "WAIT"
            }
            RailItemId.MEDIA -> if (state.media != null) "LIVE" else "WAIT"
            RailItemId.COMMS -> if (state.notifications.isNotEmpty()) "${state.notifications.size} NEW" else "0"
            RailItemId.YOUTUBE, RailItemId.CHATGPT, RailItemId.GROK ->
                if (StageWebController.activeApp == item.webApp) "ACTIVE" else "STAGE"
            RailItemId.VEHICLE -> "MOCK"
            RailItemId.HOME -> if (homeSetupOpen) "SETUP" else "THEME"
        }
        val metaColor = when (item) {
            RailItemId.DRIVE -> if (NavigationController.navActive || state.gpsActive) theme.green else theme.dim
            RailItemId.MEDIA -> if (state.media != null) theme.green else theme.dim
            RailItemId.COMMS -> if (state.notifications.isNotEmpty()) theme.accent else theme.dim
            RailItemId.YOUTUBE, RailItemId.CHATGPT, RailItemId.GROK ->
                if (StageWebController.activeApp == item.webApp) theme.green else theme.accent
            RailItemId.VEHICLE -> theme.yellow
            RailItemId.HOME -> if (homeSetupOpen) theme.accent else theme.dim
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
        val stageTitle = when {
            id == TileId.MEDIA && webStageApp != null -> "${id.title} · ${webStageApp!!.title}"
            id == TileId.HOME && homeSetupOpen -> "${id.title} · SETUP"
            else -> id.title
        }
        val legend = "[${tileNumber(id)} · $stageTitle]"
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
        var driveHit = DriveHit()
        var mediaHit = MediaHit(play = null, previous = null, next = null)
        var commsHit = CommsHit(clear = null, notifications = emptyList())
        var homeHit = HomeHit()

        when (id) {
            TileId.DRIVE -> {
                driveHit = drawDrive(canvas, content, theme, state, stageUnit)
            }
            TileId.MEDIA -> {
                if (webStageApp != null &&
                    !CarPlatformPolicy.allowsWebStage(policySurface())
                ) {
                    closeWebApp()
                }
                mediaHit = drawMedia(canvas, content, theme, state, stageUnit)
            }
            TileId.COMMS -> {
                if (!CarPlatformPolicy.allowsCommsBrowsing(policySurface())) {
                    drawLockedPlate(canvas, content, theme, stageUnit, "NOTIFICATIONS LOCKED WHILE DRIVING")
                } else {
                    commsHit = drawComms(canvas, content, theme, state, stageUnit)
                }
            }
            TileId.VEHICLE -> drawVehicle(canvas, content, theme, state, stageUnit)
            TileId.HOME -> {
                homeHit = drawHomeDetails(
                    canvas, stage, theme, state, stageUnit, mode, themeIndex, chips,
                )
            }
        }

        val open = if (id != TileId.VEHICLE) {
            val label = when (id) {
                TileId.DRIVE -> "OPEN MAPS"
                TileId.MEDIA -> if (webStageApp != null) "OPEN ON PHONE" else "OPEN PLAYER"
                TileId.COMMS -> "OPEN MESSAGES"
                TileId.HOME -> "OPEN SETTINGS"
                TileId.VEHICLE -> ""
            }
            val textSize = 9f * stageChrome
            val buttonWidth = maxOf(
                142f * stageChrome,
                measure(label, textSize, bold = true) + 24f * stageChrome,
            )
            val buttonHeight = 28f * stageChrome
            val rect = RectF(
                stage.right - 16f * stageChrome - buttonWidth,
                stage.top + 6f * stageChrome,
                stage.right - 16f * stageChrome,
                stage.top + 6f * stageChrome + buttonHeight,
            )
            drawTextButton(canvas, rect, label, theme, stageChrome)
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
            play = mediaHit.play,
            setup = homeHit.setup,
            previous = mediaHit.previous,
            next = mediaHit.next,
            refresh = refresh,
            clear = commsHit.clear,
            notifications = commsHit.notifications,
            notifDismissRows = commsHit.dismissRows,
            notifScrollUp = commsHit.scrollUp,
            notifScrollDown = commsHit.scrollDown,
            mapZoomIn = driveHit.zoomIn,
            mapZoomOut = driveHit.zoomOut,
            mapRecenter = driveHit.recenter,
            mapSearchOpen = driveHit.searchOpen,
            mapNavToggle = driveHit.navToggle,
            railToggles = homeHit.railToggles,
            mediaTargets = homeHit.mediaTargets,
            mediaSessions = mediaHit.sessions,
            webAppButtons = mediaHit.webApps,
            webStageRect = mediaHit.webStageRect,
            webBack = mediaHit.webBack,
            webReload = mediaHit.webReload,
            webSearch = mediaHit.webSearch,
            webScrollUp = mediaHit.webScrollUp,
            webScrollDown = mediaHit.webScrollDown,
            webClose = mediaHit.webClose,
        )
    }

    private fun drawLockedPlate(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        unit: Float,
        message: String,
    ) {
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 230)
        canvas.drawRoundRect(content, 4f * unit, 4f * unit, fill)
        text(
            canvas,
            message,
            content.centerX() - measure(message, 14f * unit, bold = true) / 2f,
            content.centerY(),
            14f * unit,
            theme.yellow,
            bold = true,
            maxWidth = content.width() - 24f * unit,
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
        margin: Float,
        state: DashboardState,
        mode: Mode,
        themeButton: RectF?,
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
        val brandX = area.left + margin
        val brandWidth = measure("OMARCHY", 16f * barUnit, bold = true)
        val autoX = brandX + brandWidth + 10f * barUnit
        val autoWidth = measure("// AUTO", 11f * barUnit)
        val modeText = if (mode == Mode.CAR) "AA" else "PHONE"
        val modeX = autoX + autoWidth + 12f * barUnit
        val modeWidth = measure(modeText, 10f * barUnit)
        val leftWithMode = modeX + modeWidth
        val leftWithoutMode = autoX + autoWidth

        // Keep the clock and theme name clear of the theme control. On the car
        // the host action strip can also sit in that corner; the stable area
        // inset is what keeps the control tappable.
        val nameRight = if (themeButton != null) {
            themeButton.left - 12f * barUnit
        } else {
            area.right - margin - 88f * unit
        }
        val clusterGap = 16f * barUnit
        val minGap = 14f * barUnit
        val nameSize = 10f * barUnit
        val clock = clockFormat.format(Date())
        val clockSize = 18f * barUnit
        val clockWidth = measure(clock, clockSize, bold = true)
        val fullName = Themes.at(themeIndex).name
        val shortName = Themes.at(themeIndex).shortName

        fun nameFits(name: String, left: Float): Boolean {
            val nameWidth = measure(name, nameSize)
            return nameRight - nameWidth - clusterGap - clockWidth >= left + minGap
        }

        var leftEnd = leftWithMode
        var showMode = true
        var themeName: String? = when {
            nameFits(fullName, leftEnd) -> fullName
            nameFits(shortName, leftEnd) -> shortName
            else -> null
        }
        if (!nameFits(themeName ?: "", leftEnd) && showMode) {
            // Drop the mode badge before letting the clock or theme name collide.
            showMode = false
            leftEnd = leftWithoutMode
            themeName = when {
                nameFits(fullName, leftEnd) -> fullName
                nameFits(shortName, leftEnd) -> shortName
                else -> null
            }
        }

        text(canvas, "OMARCHY", brandX, baseline, 16f * barUnit, theme.accent, bold = true)
        text(canvas, "// AUTO", autoX, baseline, 11f * barUnit, theme.dim)
        if (showMode) {
            text(canvas, modeText, modeX, baseline, 10f * barUnit, theme.dim)
        }

        val nameWidth = themeName?.let { measure(it, nameSize) } ?: 0f
        if (themeName != null) {
            text(
                canvas, themeName, nameRight - nameWidth, baseline,
                nameSize, theme.dim, maxWidth = nameWidth,
            )
        }
        val clockRight = nameRight - (if (themeName != null) nameWidth + clusterGap else 0f)
        val clockLeft = maxOf(clockRight - clockWidth, leftEnd + minGap)
        text(
            canvas, clock, clockLeft, baseline + 1f * barUnit, clockSize,
            theme.foreground, bold = true, maxWidth = (clockRight - clockLeft).coerceAtLeast(1f),
        )

        if (themeButton != null) {
            val buttonChrome = unit * topBarScale(car)
            fill.style = Paint.Style.FILL
            fill.color = theme.surfaceAlt
            canvas.drawRoundRect(themeButton, 2f * buttonChrome, 2f * buttonChrome, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * buttonChrome
            stroke.color = theme.border
            canvas.drawRoundRect(themeButton, 2f * buttonChrome, 2f * buttonChrome, stroke)
            text(
                canvas, "THEME", themeButton.centerX() - measure("THEME", 9f * barUnit) / 2f,
                themeButton.centerY() + 3f * barUnit, 9f * barUnit, theme.accent,
            )
        }
    }

    private fun topBarHeight(car: Boolean, unit: Float): Float =
        (if (car) 92f else 56f) * unit

    private fun footerHeight(car: Boolean, unit: Float): Float =
        (if (car) 52f else 24f) * unit

    private fun topBarScale(car: Boolean): Float = if (car) 2.2f else 1f

    private fun footerScale(car: Boolean): Float = if (car) 2.2f else 1f

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
        val margin = (if (car) 18f else 14f) * unit
        val footerH = footerHeight(car, unit)
        val textUnit = unit * footerScale(car)
        val top = area.bottom - footerH
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.surface, 236)
        canvas.drawRect(area.left, top, area.right, area.bottom, fill)
        fill.color = theme.border
        canvas.drawRect(area.left, top, area.right, top + 1f, fill)

        val baseline = top + footerH / 2f + 3.5f * textUnit
        var x = area.left + margin
        x = status(canvas, x, baseline, "MEDIA", state.mediaAccess, theme, textUnit)
        x = status(canvas, x + 14f * textUnit, baseline, "NOTIF", state.notificationAccess, theme, textUnit)
        x = status(canvas, x + 14f * textUnit, baseline, "GPS", state.locationAccess, theme, textUnit)

        val batteryPercent = state.batteryPercent
        val batteryText = batteryPercent?.let { "$it%" }
        val batteryTextSize = 8.5f * textUnit
        val batteryIconWidth = 13f * textUnit
        val batteryGap = 4f * textUnit
        val batteryTextWidth = batteryText?.let { measure(it, batteryTextSize) } ?: 0f
        val batteryWidth = if (batteryText != null) {
            batteryIconWidth + batteryGap + batteryTextWidth
        } else {
            0f
        }
        val batteryRight = area.right - margin
        val batteryLeft = batteryRight - batteryWidth
        if (batteryPercent != null) {
            drawBattery(
                canvas = canvas,
                left = batteryLeft,
                baseline = baseline,
                percent = batteryPercent,
                charging = state.batteryCharging,
                theme = theme,
                textUnit = textUnit,
            )
            text(
                canvas, batteryText.orEmpty(),
                batteryLeft + batteryIconWidth + batteryGap,
                baseline, batteryTextSize,
                if (state.batteryCharging) theme.accent else theme.dim,
            )
        }

        val mock = if (FeatureFlags.SHOW_VEHICLE_TILE) {
            if (state.vehicleMock) "MOCK VEHICLE" else "OBD LIVE"
        } else {
            null
        }
        val mockSize = 9f * textUnit
        val mockWidth = mock?.let { measure(it, mockSize) } ?: 0f
        val mockRight = if (batteryPercent != null) {
            batteryLeft - 16f * textUnit
        } else {
            batteryRight
        }
        if (mock != null) {
            text(
                canvas, mock, mockRight - mockWidth,
                baseline, mockSize, if (state.vehicleMock) theme.yellow else theme.green,
            )
        }
        val speedRight = mockRight -
            (if (mock != null) mockWidth + 16f * textUnit else 0f)

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
        val rightBound = speedRight - speedWidth -
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
                speedRight - speedWidth,
                baseline, speedSize, theme.accent, bold = true,
            )
        }
    }

    private fun drawBattery(
        canvas: Canvas,
        left: Float,
        baseline: Float,
        percent: Int,
        charging: Boolean,
        theme: OmarchyTheme,
        textUnit: Float,
    ) {
        val width = 13f * textUnit
        val height = 6.5f * textUnit
        val top = baseline - height - 1f * textUnit
        val body = RectF(left, top, left + width, top + height)
        val nub = RectF(
            body.right,
            body.top + height * 0.28f,
            body.right + 2f * textUnit,
            body.bottom - height * 0.28f,
        )
        fill.style = Paint.Style.FILL
        fill.color = theme.border
        canvas.drawRoundRect(body, 1.5f * textUnit, 1.5f * textUnit, fill)
        val inner = RectF(
            body.left + 1.5f * textUnit,
            body.top + 1.5f * textUnit,
            body.right - 1.5f * textUnit,
            body.bottom - 1.5f * textUnit,
        )
        val fraction = (percent / 100f).coerceIn(0f, 1f)
        fill.color = when {
            charging -> theme.accent
            percent <= 15 -> theme.red
            percent <= 30 -> theme.yellow
            else -> theme.green
        }
        canvas.save()
        canvas.clipRect(
            inner.left,
            inner.bottom - inner.height() * fraction,
            inner.right,
            inner.bottom,
        )
        canvas.drawRect(inner, fill)
        canvas.restore()
        fill.color = theme.border
        canvas.drawRect(nub, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * textUnit
        stroke.color = theme.dim
        canvas.drawRoundRect(body, 1.5f * textUnit, 1.5f * textUnit, stroke)
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

    private data class MapLayerResult(val stale: Boolean, val zoom: Double)

    private fun drawCarNavigationSurface(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: DashboardState,
        themeIndex: Int,
    ) {
        val theme = Themes.at(themeIndex)
        // This renderer instance is the car surface. Clear distractions it owns
        // once, not on every frame. StageWebController is process-global and is
        // also the phone WebView, so a quiet car frame must not stop it.
        if (webStageApp != null) closeWebApp()
        if (keyboardMode != KeyboardMode.NONE) closeKeyboard()
        if (overlayKey != null) closeNotification()
        selected = TileId.DRIVE
        homeSetupOpen = false
        val wasMapActive = MapViewport.active
        MapViewport.active = true
        val area = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawColor(theme.background)
        val landscape = area.width() > area.height() * 1.25f
        val rawUnit = if (landscape) {
            minOf(area.width() / 1280f, area.height() / 720f)
        } else {
            area.width() / 720f
        }
        val unit = rawUnit.coerceAtLeast(0.45f)
        drawMapLayer(canvas, area, theme, state, unit)
        val overlay = carOverlayRect(area, unit)
        val hasGuidance = drawCarGuidance(canvas, overlay, theme, state, unit)
        drawCompactSpeed(canvas, overlay, theme, state, unit, alignBottom = true)
        val credit = "OpenFreeMap · OSM"
        val creditSize = 11f * unit
        val speedPanelH = 76f * unit
        text(
            canvas,
            credit,
            overlay.right - measure(credit, creditSize) - 4f * unit,
            overlay.bottom - speedPanelH - 8f * unit,
            creditSize,
            withAlpha(theme.foreground, 200),
        )
        if (wasMapActive != MapViewport.active) {
            LiveDataStore.notifyDisplayChanged()
        }
        lastLayout = Layout(
            stageRect = RectF(area),
            railRect = RectF(),
            themeButton = null,
            setupButton = null,
            chips = emptyList(),
            railToggles = emptyList(),
            mediaTargets = emptyList(),
            stageOpen = null,
            playPause = null,
            prevTrack = null,
            nextTrack = null,
            mediaSessions = emptyList(),
            webAppButtons = emptyList(),
            webBack = null,
            webReload = null,
            webSearch = null,
            webScrollUp = null,
            webScrollDown = null,
            webClose = null,
            webStageRect = null,
            notifRefresh = null,
            notifClear = null,
            notifScrollUp = null,
            notifScrollDown = null,
            notifDismissRows = emptyList(),
            notifications = emptyList(),
            notifCard = null,
            notifClose = null,
            notifDismissOne = null,
            overlayScrollUp = null,
            overlayScrollDown = null,
            mapZoomIn = null,
            mapZoomOut = null,
            mapRecenter = null,
            mapSearchOpen = null,
            mapNavToggle = null,
            railScrollUp = null,
            railScrollDown = null,
            railItems = emptyList(),
            tiles = emptyMap(),
            keyboardHit = null,
        )
        lastFrame = FrameInfo(
            mode = Mode.CAR,
            mapFillsCanvas = true,
            drawsCustomRail = false,
            drawsManeuverBanner = hasGuidance,
            drawsKeyboard = false,
            drawsNotificationList = false,
            drawsWebStage = false,
            drawsWallpaper = false,
        )
    }

    /**
     * Speed and attribution sit in the host stable area. Callers pass that
     * rect via [setSafeArea] from onStableAreaChanged. Until the host reports
     * one, the overlay uses the full surface.
     */
    private fun carOverlayRect(area: RectF, unit: Float): RectF {
        val raw = safeArea
        val base = if (raw != null && raw.width() > 80 && raw.height() > 80) {
            RectF(
                raw.left.toFloat().coerceIn(area.left, area.right),
                raw.top.toFloat().coerceIn(area.top, area.bottom),
                raw.right.toFloat().coerceIn(area.left, area.right),
                raw.bottom.toFloat().coerceIn(area.top, area.bottom),
            )
        } else {
            area
        }
        val inset = 16f * unit
        val inner = RectF(base.left + inset, base.top + inset, base.right - inset, base.bottom - inset)
        return if (inner.width() > 48f && inner.height() > 48f) inner else area
    }

    /**
     * Canvas fallback for turn prompts. The host routing card is authoritative,
     * but this card guarantees a visible instruction when the host card is
     * hidden or suppressed. Drawn only while routing is active.
     */
    private fun drawCarGuidance(
        canvas: Canvas,
        overlay: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ): Boolean {
        val instruction = NavigationController.currentInstruction()
        val status = NavigationController.statusMessage
        val dest = NavigationController.destination
        val navState = NavigationController.navState
        if (instruction == null && status == null && dest == null) return false
        val primary = (instruction ?: status ?: dest?.name.orEmpty()).ifBlank { return false }
        val bannerWidth = minOf(overlay.width() * 0.52f, 340f * unit).coerceAtLeast(180f * unit)
        val banner = RectF(
            overlay.left,
            overlay.top,
            (overlay.left + bannerWidth).coerceAtMost(overlay.right),
            overlay.top + 64f * unit,
        )
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 225)
        canvas.drawRoundRect(banner, 3f * unit, 3f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1.5f * unit
        stroke.color = when (navState) {
            NavState.ACTIVE -> theme.green
            NavState.ROUTE_ERROR,
            NavState.LOCATION_REQUIRED -> theme.red
            else -> theme.accent
        }
        canvas.drawRoundRect(banner, 3f * unit, 3f * unit, stroke)
        text(
            canvas,
            primary,
            banner.left + 10f * unit,
            banner.top + 24f * unit,
            12f * unit,
            theme.foreground,
            bold = true,
            maxWidth = banner.width() - 20f * unit,
        )
        val subline = if (navState == NavState.ACTIVE && NavigationController.remainingDistanceMeters > 0.0) {
            val dist = NavigationController.formatDistance(NavigationController.remainingDistanceMeters)
            val eta = NavigationController.formatDuration(NavigationController.displayedRemainingDurationSeconds())
            val target = dest?.name ?: ""
            "$dist · ETA $eta · $target"
        } else {
            status ?: dest?.displayName.orEmpty()
        }
        if (subline.isNotBlank()) {
            text(
                canvas,
                subline,
                banner.left + 10f * unit,
                banner.bottom - 10f * unit,
                9f * unit,
                theme.dim,
                maxWidth = banner.width() - 20f * unit,
            )
        }
        return true
    }

    private fun drawCompactSpeed(
        canvas: Canvas,
        overlay: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
        alignBottom: Boolean,
    ) {
        val panelH = 76f * unit
        val panelW = 190f * unit
        val top = if (alignBottom) overlay.bottom - panelH else overlay.top
        val panel = RectF(overlay.left, top, overlay.left + panelW, top + panelH)
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 225)
        canvas.drawRoundRect(panel, 4f * unit, 4f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1.5f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(panel, 4f * unit, 4f * unit, stroke)
        val speedText = state.speedKph?.let { "%.0f".format(Locale.US, it) } ?: "--"
        val baseline = panel.bottom - 26f * unit
        text(canvas, speedText, panel.left + 10f * unit, baseline, 42f * unit, theme.foreground, bold = true)
        text(
            canvas,
            "km/h",
            panel.left + 12f * unit + measure(speedText, 42f * unit, bold = true),
            baseline,
            10f * unit,
            theme.dim,
        )
        val label = when {
            !MapCameraStore.followGps -> "PAN"
            MapCameraStore.orientation == MapCameraStore.Orientation.NORTH_UP -> "NORTH"
            state.gpsActive -> "FOLLOW"
            else -> "NO FIX"
        }
        text(canvas, label, panel.left + 10f * unit, panel.bottom - 8f * unit, 11f * unit, theme.accent, bold = true)
    }

    private fun drawMapLayer(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ): MapLayerResult {
        publishMapViewport(content)
        val map = MapSnapshotStore.bitmap
        val (camLat, camLon) = MapCameraStore.effectiveCenter(state.latitude, state.longitude)
        val camZoom = MapCameraStore.zoom
        val snapshotLat = MapSnapshotStore.latitude
        val snapshotLon = MapSnapshotStore.longitude
        val movedMeters = if (snapshotLat != null && snapshotLon != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(snapshotLat, snapshotLon, camLat, camLon, distance)
            distance[0]
        } else {
            0f
        }
        val zoomDelta = kotlin.math.abs(MapSnapshotStore.zoom - camZoom)
        val stale = map != null && (
            movedMeters >= MapFollowPolicy.MOVE_METERS || zoomDelta >= 0.45
            )
        if (map != null) {
            drawCenterCrop(canvas, map, content)
        } else {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.surfaceAlt, 140)
            canvas.drawRect(content, fill)
            val message = when {
                MapSnapshotStore.error != null -> "MAP OFFLINE"
                else -> "MAP LOADING"
            }
            text(
                canvas,
                message,
                content.centerX() - measure(message, 16f * unit, bold = true) / 2f,
                content.centerY(),
                16f * unit,
                if (MapSnapshotStore.error != null) theme.yellow else theme.dim,
                bold = true,
            )
        }

        val projLat = snapshotLat ?: camLat
        val projLon = snapshotLon ?: camLon
        val projZoom = if (map != null) MapSnapshotStore.zoom else camZoom
        canvas.save()
        canvas.clipRect(content)
        val routePts = NavigationController.routePoints
        if (routePts.size >= 2) {
            route.reset()
            var started = false
            for (pt in routePts) {
                val (sx, sy) = MapCameraStore.projectToScreen(
                    pt.first, pt.second, projLat, projLon, projZoom,
                    content.centerX(), content.centerY(),
                )
                if (!started) {
                    route.moveTo(sx, sy)
                    started = true
                } else {
                    route.lineTo(sx, sy)
                }
            }
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 7f * unit
            stroke.color = withAlpha(theme.background, 210)
            canvas.drawPath(route, stroke)
            stroke.strokeWidth = 4.5f * unit
            stroke.color = theme.accent
            canvas.drawPath(route, stroke)
        }
        val dest = NavigationController.destination
        if (dest != null) {
            val (dx, dy) = MapCameraStore.projectToScreen(
                dest.latitude, dest.longitude, projLat, projLon, projZoom,
                content.centerX(), content.centerY(),
            )
            fill.style = Paint.Style.FILL
            fill.color = theme.red
            canvas.drawCircle(dx, dy, 8f * unit, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 2f * unit
            stroke.color = theme.foreground
            canvas.drawCircle(dx, dy, 8f * unit, stroke)
        }
        if (!MapCameraStore.followGps) {
            val cx = content.centerX()
            val cy = content.centerY()
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1.5f * unit
            stroke.color = withAlpha(theme.accent, 180)
            canvas.drawCircle(cx, cy, 8f * unit, stroke)
            canvas.drawLine(cx - 14f * unit, cy, cx + 14f * unit, cy, stroke)
            canvas.drawLine(cx, cy - 14f * unit, cx, cy + 14f * unit, stroke)
        }
        if (state.gpsActive && state.latitude != null && state.longitude != null) {
            val (cx, cy) = MapCameraStore.projectToScreen(
                state.latitude, state.longitude, projLat, projLon, projZoom,
                content.centerX(), content.centerY(),
            )
            val cameraBearing = MapCameraStore.cameraBearing(state.bearing)
            val relative = state.bearing - cameraBearing
            fill.style = Paint.Style.FILL
            fill.color = Color.argb(110, 0, 0, 0)
            canvas.drawCircle(cx, cy, 16f * unit, fill)
            canvas.save()
            canvas.rotate(relative, cx, cy)
            route.reset()
            route.moveTo(cx, cy - 22f * unit)
            route.lineTo(cx - 8f * unit, cy + 10f * unit)
            route.lineTo(cx, cy + 4f * unit)
            route.lineTo(cx + 8f * unit, cy + 10f * unit)
            route.close()
            fill.color = theme.foreground
            canvas.drawPath(route, fill)
            fill.color = theme.green
            canvas.drawCircle(cx, cy, 5f * unit, fill)
            canvas.restore()
        }
        canvas.restore()
        return MapLayerResult(stale = stale, zoom = camZoom)
    }

    private fun drawDrive(
        canvas: Canvas,
        content: RectF,
        theme: OmarchyTheme,
        state: DashboardState,
        unit: Float,
    ): DriveHit {
        val layer = drawMapLayer(canvas, content, theme, state, unit)
        val stale = layer.stale
        val camZoom = layer.zoom
        val dest = NavigationController.destination

        // Same guidance card as the phone shell. The host routing card is still
        // the Android Auto maneuver UI; this card keeps the instruction on the
        // drive stage when the host card is hidden.
        val navInstruction = NavigationController.currentInstruction()
        val navStatus = NavigationController.statusMessage
        val navError: String? = null
        val navState = NavigationController.navState
        var guidanceBottom = content.top + 8f * unit
        if (dest != null || navInstruction != null || navStatus != null || navError != null) {
            val bannerWidth = minOf(content.width() * 0.46f, 300f * unit).coerceAtLeast(180f * unit)
            val banner = RectF(
                content.left + 10f * unit,
                content.top + 8f * unit,
                content.left + 10f * unit + bannerWidth,
                content.top + 78f * unit,
            )
            guidanceBottom = banner.bottom
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.background, 225)
            canvas.drawRoundRect(banner, 3f * unit, 3f * unit, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1.5f * unit
            stroke.color = when (navState) {
                NavState.ACTIVE -> theme.green
                NavState.ROUTE_ERROR,
                NavState.LOCATION_REQUIRED -> theme.red
                else -> theme.accent
            }
            canvas.drawRoundRect(banner, 3f * unit, 3f * unit, stroke)

            val primary = navInstruction ?: navStatus ?: dest?.name.orEmpty()
            text(
                canvas,
                primary,
                banner.left + 10f * unit,
                banner.top + 22f * unit,
                11f * unit,
                when (navState) {
                    NavState.ROUTE_ERROR,
                    NavState.LOCATION_REQUIRED -> theme.yellow
                    else -> theme.foreground
                },
                bold = true,
                maxWidth = banner.width() - 20f * unit,
            )
            val subline = if (navState == NavState.ACTIVE &&
                NavigationController.remainingDistanceMeters > 0.0
            ) {
                val dist = NavigationController.formatDistance(NavigationController.remainingDistanceMeters)
                val eta = NavigationController.formatDuration(NavigationController.remainingDurationSeconds)
                val targetName = dest?.name ?: ""
                "$dist  ·  ETA $eta  ·  $targetName"
            } else {
                navError ?: navStatus ?: dest?.displayName.orEmpty()
            }
            text(
                canvas,
                subline,
                banner.left + 10f * unit,
                banner.bottom - 10f * unit,
                8.5f * unit,
                when (navState) {
                    NavState.ACTIVE -> theme.green
                    NavState.ROUTE_ERROR,
                    NavState.LOCATION_REQUIRED -> theme.red
                    else -> theme.dim
                },
                maxWidth = banner.width() - 20f * unit,
            )
        } else if (stale) {
            val label = "UPDATING MAP…"
            val size = 9f * unit
            val badge = RectF(
                content.left + 10f * unit,
                content.top + 8f * unit,
                content.left + 18f * unit + measure(label, size, bold = true) + 12f * unit,
                content.top + 30f * unit,
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

        // Right-side map zoom & recenter controls
        val ctrlW = 42f * unit
        val ctrlH = 34f * unit
        val ctrlGap = 6f * unit
        val ctrlRight = content.right - 10f * unit
        val ctrlLeft = ctrlRight - ctrlW
        val zoomIn = RectF(ctrlLeft, content.top + 8f * unit, ctrlRight, content.top + 8f * unit + ctrlH)
        val zoomOut = RectF(ctrlLeft, zoomIn.bottom + ctrlGap, ctrlRight, zoomIn.bottom + ctrlGap + ctrlH)
        val recenter = RectF(ctrlLeft, zoomOut.bottom + ctrlGap, ctrlRight, zoomOut.bottom + ctrlGap + ctrlH)

        drawTextButton(canvas, zoomIn, "+", theme, unit)
        drawTextButton(canvas, zoomOut, "−", theme, unit)
        drawTextButton(
            canvas,
            recenter,
            if (MapCameraStore.followGps) "GPS" else "◎",
            theme,
            unit,
            color = if (MapCameraStore.followGps) theme.green else theme.yellow,
        )

        // Zoom level badge under controls
        val zoomText = "Z%.0f".format(Locale.US, camZoom)
        text(
            canvas,
            zoomText,
            ctrlLeft + (ctrlW - measure(zoomText, 7.5f * unit)) / 2f,
            recenter.bottom + 12f * unit,
            7.5f * unit,
            theme.dim,
        )

        // Compact speed directly under the left guidance card.
        val panel = RectF(
            content.left + 10f * unit,
            guidanceBottom + 8f * unit,
            content.left + 200f * unit,
            guidanceBottom + 84f * unit,
        )
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 220)
        canvas.drawRoundRect(panel, 3f * unit, 3f * unit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRoundRect(panel, 3f * unit, 3f * unit, stroke)

        val speed = state.speedKph
        val speedText = if (speed != null) "%.0f".format(Locale.US, speed) else "--"
        val speedX = panel.left + 10f * unit
        val speedBaseline = panel.bottom - 26f * unit
        text(canvas, speedText, speedX, speedBaseline, 42f * unit, theme.foreground, bold = true)
        text(
            canvas, "km/h", speedX + measure(speedText, 42f * unit, bold = true) + 6f * unit,
            speedBaseline, 9f * unit, theme.dim,
        )
        val gps = when {
            navState == NavState.ACTIVE -> "NAV"
            navState == NavState.ROUTING -> "ROUTING"
            navState == NavState.LOCATION_REQUIRED -> "NO FIX"
            navState == NavState.ROUTE_ERROR -> "ROUTE ERROR"
            !MapCameraStore.followGps -> "PAN"
            MapCameraStore.orientation == MapCameraStore.Orientation.NORTH_UP -> "NORTH"
            state.gpsActive -> "FOLLOW"
            else -> "GPS WAIT"
        }
        text(
            canvas, gps, panel.left + 12f * unit, panel.bottom - 9f * unit, 8f * unit,
            when {
                navState == NavState.ROUTE_ERROR ||
                    navState == NavState.LOCATION_REQUIRED -> theme.red
                state.gpsActive || navState == NavState.ACTIVE -> theme.green
                else -> theme.yellow
            },
        )

        // Bottom-right Search Address & Start/Stop/Retry Navigation buttons
        val btnH = 34f * unit
        val btnTop = content.bottom - 10f * unit - btnH
        val navLabel = when (navState) {
            NavState.ACTIVE,
            NavState.ROUTING -> "STOP NAV"
            NavState.ROUTE_ERROR,
            NavState.LOCATION_REQUIRED -> "RETRY NAV"
            else -> "START NAV"
        }
        val navW = maxOf(96f * unit, measure(navLabel, 9f * unit, bold = true) + 22f * unit)
        val navToggle = RectF(
            content.right - 10f * unit - navW,
            btnTop,
            content.right - 10f * unit,
            btnTop + btnH,
        )
        val searchRight = navToggle.left - 8f * unit
        val searchLeft = maxOf(panel.right + 8f * unit, searchRight - 180f * unit)
        val searchOpen = RectF(searchLeft, btnTop, searchRight, btnTop + btnH)
        val searchLabel = if (dest != null) "⌕ ${dest.name}" else "⌕ SEARCH ADDRESS"
        drawTextButton(canvas, searchOpen, searchLabel, theme, unit)
        drawTextButton(
            canvas,
            navToggle,
            navLabel,
            theme,
            unit,
            color = when (navState) {
                NavState.ACTIVE,
                NavState.ROUTING -> theme.red
                NavState.ROUTE_ERROR,
                NavState.LOCATION_REQUIRED -> theme.yellow
                else -> theme.green
            },
        )

        val credit = "OpenFreeMap · OSM"
        val creditSize = 8f * unit
        text(
            canvas,
            credit,
            content.left + 10f * unit,
            (btnTop - 8f * unit).coerceAtLeast(content.top + 14f * unit),
            creditSize,
            withAlpha(theme.foreground, 200),
            maxWidth = (searchLeft - content.left - 16f * unit).coerceAtLeast(48f * unit),
        )

        return DriveHit(
            zoomIn = zoomIn,
            zoomOut = zoomOut,
            recenter = recenter,
            searchOpen = searchOpen,
            navToggle = navToggle,
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
        val sessions = mutableListOf<Pair<String, RectF>>()
        val webApps = mutableListOf<Pair<StageWebApp, RectF>>()

        // Top source & stage-app bar
        val barH = 26f * unit
        val gap = 6f * unit
        var x = content.left

        // Stage web app pills on the right side of the bar
        var rightX = content.right
        if (CarPlatformPolicy.allowsWebStage(policySurface())) {
            StageWebApp.entries.reversed().forEach { app ->
                val label = app.title
                val w = measure(label, 8f * unit, bold = true) + 16f * unit
                val rect = RectF(rightX - w, content.top, rightX, content.top + barH)
                rightX = rect.left - gap
                webApps += app to rect
                val active = webStageApp == app
                fill.style = Paint.Style.FILL
                fill.color = if (active) theme.accent else withAlpha(theme.surfaceAlt, 225)
                canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1f * unit
                stroke.color = if (active) theme.accent else theme.border
                canvas.drawRoundRect(rect, 2f * unit, 2f * unit, stroke)
                text(
                    canvas,
                    label,
                    rect.centerX() - measure(label, 8f * unit, bold = true) / 2f,
                    rect.centerY() + 3f * unit,
                    8f * unit,
                    if (active) theme.background else theme.accent,
                    bold = true,
                )
            }
        }

        // Active media session switcher pills on the left side of the bar
        val sessionList = state.mediaSessions
        if (sessionList.isNotEmpty()) {
            for (session in sessionList.take(3)) {
                val label = session.source.uppercase(Locale.US).take(10)
                val w = measure(label, 8f * unit, bold = true) + 16f * unit
                if (x + w > rightX - gap) break
                val rect = RectF(x, content.top, x + w, content.top + barH)
                x = rect.right + gap
                sessions += session.packageName to rect
                val active = webStageApp == null && state.media?.packageName == session.packageName
                fill.style = Paint.Style.FILL
                fill.color = if (active) theme.accent else withAlpha(theme.surfaceAlt, 225)
                canvas.drawRoundRect(rect, 2f * unit, 2f * unit, fill)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1f * unit
                stroke.color = if (active) theme.accent else theme.border
                canvas.drawRoundRect(rect, 2f * unit, 2f * unit, stroke)
                text(
                    canvas,
                    label,
                    rect.centerX() - measure(label, 8f * unit, bold = true) / 2f,
                    rect.centerY() + 3f * unit,
                    8f * unit,
                    if (active) theme.background else theme.foreground,
                    bold = true,
                )
            }
        }

        val body = RectF(content.left, content.top + barH + 8f * unit, content.right, content.bottom)
        val activeWeb = webStageApp
        if (activeWeb != null) {
            return drawWebStage(canvas, body, activeWeb, theme, unit, sessions, webApps)
        }

        val media = state.media
        if (media == null) {
            val title = if (state.mediaAccess) "NO ACTIVE AUDIO SESSION" else "MEDIA ACCESS NEEDED"
            val detail = if (state.mediaAccess) {
                "Tap YOUTUBE / CHATGPT / GROK above or start audio playback"
            } else {
                "Grant notification access in Setup, or tap YOUTUBE / CHATGPT / GROK above"
            }
            text(
                canvas, title, body.centerX() - measure(title, 15f * unit, bold = true) / 2f,
                body.centerY() - 4f * unit, 15f * unit,
                if (state.mediaAccess) theme.dim else theme.yellow, bold = true,
            )
            text(
                canvas, detail, body.centerX() - measure(detail, 9.5f * unit) / 2f,
                body.centerY() + 18f * unit, 9.5f * unit, theme.dim,
            )
            return MediaHit(play = null, previous = null, next = null, sessions = sessions, webApps = webApps)
        }

        val progressTop = body.bottom - 96f * unit
        val artSize = minOf(body.width() * 0.28f, body.height() * 0.56f)
        val art = RectF(body.left, body.top, body.left + artSize, body.top + artSize)
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
        val textWidth = body.right - textLeft
        var baseline = body.top + 18f * unit
        text(
            canvas, media.title.ifBlank { "UNKNOWN TRACK" }, textLeft, baseline,
            17f * unit, theme.foreground, bold = true, maxWidth = textWidth,
        )
        baseline += 19f * unit
        text(
            canvas, media.artist.ifBlank { "UNKNOWN ARTIST" }, textLeft, baseline,
            11f * unit, theme.dim, maxWidth = textWidth,
        )
        baseline += 15f * unit
        text(
            canvas, media.album.ifBlank { "" }, textLeft, baseline,
            9f * unit, theme.dim, maxWidth = textWidth,
        )
        baseline += 14f * unit
        text(
            canvas, media.source.uppercase(Locale.US), textLeft, baseline,
            8f * unit, theme.accent, maxWidth = textWidth,
        )

        val progressLeft = if (progressTop < art.bottom + 10f * unit) textLeft else body.left
        val progressWidth = body.right - progressLeft
        fill.color = theme.border
        canvas.drawRect(progressLeft, progressTop, progressLeft + progressWidth, progressTop + 4f * unit, fill)
        if (media.durationMs > 0L) {
            val fraction = (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f)
            fill.color = theme.accent
            canvas.drawRect(
                progressLeft, progressTop,
                progressLeft + progressWidth * fraction, progressTop + 4f * unit, fill,
            )
        }
        text(
            canvas, formatTime(media.positionMs), progressLeft,
            progressTop + 16f * unit, 8f * unit, theme.dim,
        )
        val duration = "-" + formatTime((media.durationMs - media.positionMs).coerceAtLeast(0L))
        text(
            canvas, duration, body.right - measure(duration, 8f * unit),
            progressTop + 16f * unit, 8f * unit, theme.dim,
        )

        val playWidth = 84f * unit
        val sideWidth = 60f * unit
        val btnGap = 14f * unit
        val transportHeight = 56f * unit
        val totalWidth = playWidth + (sideWidth + btnGap) * 2f
        val transportTop = body.bottom - 6f * unit - transportHeight
        val transportLeft = body.centerX() - totalWidth / 2f
        val previous = RectF(
            transportLeft,
            transportTop,
            transportLeft + sideWidth,
            transportTop + transportHeight,
        )
        val transport = RectF(
            previous.right + btnGap,
            transportTop,
            previous.right + btnGap + playWidth,
            transportTop + transportHeight,
        )
        val next = RectF(
            transport.right + btnGap,
            transportTop,
            transport.right + btnGap + sideWidth,
            transportTop + transportHeight,
        )
        drawSideTransport(canvas, previous, previous = true, theme, unit)
        drawTransport(canvas, transport, media.playing, theme, unit)
        drawSideTransport(canvas, next, previous = false, theme, unit)
        return MediaHit(
            play = transport,
            previous = previous,
            next = next,
            sessions = sessions,
            webApps = webApps,
        )
    }

    private fun drawWebStage(
        canvas: Canvas,
        body: RectF,
        app: StageWebApp,
        theme: OmarchyTheme,
        unit: Float,
        sessions: List<Pair<String, RectF>>,
        webApps: List<Pair<StageWebApp, RectF>>,
    ): MediaHit {
        val toolbarH = 28f * unit
        val gap = 6f * unit
        val btnW = 36f * unit
        val back = RectF(body.left, body.top, body.left + btnW, body.top + toolbarH)
        val reload = RectF(back.right + gap, body.top, back.right + gap + btnW, body.top + toolbarH)
        val closeW = measure("PLAYER", 8f * unit, bold = true) + 18f * unit
        val close = RectF(body.right - closeW, body.top, body.right, body.top + toolbarH)
        val scrollDown = RectF(close.left - gap - btnW, body.top, close.left - gap, body.top + toolbarH)
        val scrollUp = RectF(scrollDown.left - gap - btnW, body.top, scrollDown.left - gap, body.top + toolbarH)
        val search = RectF(reload.right + gap, body.top, scrollUp.left - gap, body.top + toolbarH)

        drawTextButton(canvas, back, "◀", theme, unit)
        drawTextButton(canvas, reload, "↻", theme, unit)
        val searchLabel = when (app) {
            StageWebApp.YOUTUBE -> "⌕ SEARCH YOUTUBE OR URL"
            StageWebApp.CHATGPT -> "⌕ ASK CHATGPT OR TAP PAGE"
            StageWebApp.GROK -> "⌕ ASK GROK OR TAP PAGE"
        }
        drawTextButton(canvas, search, searchLabel, theme, unit)
        drawTextButton(canvas, scrollUp, "▲", theme, unit)
        drawTextButton(canvas, scrollDown, "▼", theme, unit)
        drawTextButton(canvas, close, "PLAYER", theme, unit, color = theme.dim)

        val viewport = RectF(body.left, body.top + toolbarH + 6f * unit, body.right, body.bottom)
        StageWebController.ensureActive(app, viewport.width().toInt(), viewport.height().toInt())
        val bmp = StageWebController.bitmap
        val webErr = StageWebController.errorMessage
        if (webErr != null) {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.background, 230)
            canvas.drawRect(viewport, fill)
            val msg = "${app.title} LOAD FAILED · TAP ↻ TO RETRY"
            text(
                canvas,
                msg,
                viewport.centerX() - measure(msg, 12f * unit, bold = true) / 2f,
                viewport.centerY() - 4f * unit,
                12f * unit,
                theme.yellow,
                bold = true,
            )
            text(
                canvas,
                webErr,
                viewport.centerX() - measure(webErr, 9f * unit) / 2f,
                viewport.centerY() + 16f * unit,
                9f * unit,
                theme.dim,
                maxWidth = viewport.width() - 24f * unit,
            )
        } else if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null, viewport, wallpaperPaint)
        } else {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.background, 220)
            canvas.drawRect(viewport, fill)
            val msg = "LOADING ${app.title} IN STAGE…"
            text(
                canvas,
                msg,
                viewport.centerX() - measure(msg, 12f * unit, bold = true) / 2f,
                viewport.centerY() + 4f * unit,
                12f * unit,
                theme.accent,
                bold = true,
            )
        }
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1f * unit
        stroke.color = theme.border
        canvas.drawRect(viewport, stroke)

        return MediaHit(
            play = null,
            previous = null,
            next = null,
            sessions = sessions,
            webApps = webApps,
            webStageRect = viewport,
            webBack = back,
            webReload = reload,
            webSearch = search,
            webScrollUp = scrollUp,
            webScrollDown = scrollDown,
            webClose = close,
        )
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
            notifScrollOffset = 0
            text(
                canvas, "NO NOTIFICATIONS", content.centerX() - measure("NO NOTIFICATIONS", 14f * unit, bold = true) / 2f,
                content.centerY(), 14f * unit, theme.dim, bold = true,
            )
            return CommsHit(clear = null, notifications = emptyList())
        }

        val headerH = 34f * unit
        val listTop = content.top + headerH
        val listHeight = content.bottom - listTop
        val textScale = DisplaySettings.notificationTextScale
        val baseRow = 64f * unit * textScale
        val total = state.notifications.size
        val maxItems = NotificationCardLayout.visibleCount(listHeight, baseRow, total).coerceAtLeast(1)
        val maxOffset = (total - maxItems).coerceAtLeast(0)
        notifScrollOffset = notifScrollOffset.coerceIn(0, maxOffset)
        val items = state.notifications.drop(notifScrollOffset).take(maxItems)

        val rangeText = if (total > maxItems) {
            "${total} NEW  ·  SHOWING ${notifScrollOffset + 1}–${notifScrollOffset + items.size}"
        } else {
            "$total NEW"
        }
        text(
            canvas,
            rangeText,
            content.left + 2f * unit,
            content.top + 20f * unit,
            9f * unit,
            theme.accent,
            bold = true,
        )

        val btnH = 28f * unit
        val btnTop = content.top + 2f * unit
        val clearLabel = "CLEAR ALL"
        val clearWidth = measure(clearLabel, 9f * unit, bold = true) + 20f * unit
        val clear = RectF(
            content.right - clearWidth,
            btnTop,
            content.right,
            btnTop + btnH,
        )
        drawTextButton(canvas, clear, clearLabel, theme, unit, color = theme.red)

        var scrollUp: RectF? = null
        var scrollDown: RectF? = null
        if (total > maxItems) {
            val navW = 36f * unit
            val gap = 6f * unit
            scrollDown = RectF(clear.left - gap - navW, btnTop, clear.left - gap, btnTop + btnH)
            scrollUp = RectF(scrollDown.left - gap - navW, btnTop, scrollDown.left - gap, btnTop + btnH)
            drawTextButton(
                canvas,
                scrollUp,
                "▲",
                theme,
                unit,
                color = if (notifScrollOffset > 0) theme.accent else theme.dim,
            )
            drawTextButton(
                canvas,
                scrollDown,
                "▼",
                theme,
                unit,
                color = if (notifScrollOffset < maxOffset) theme.accent else theme.dim,
            )
        }

        fill.color = theme.border
        canvas.drawRect(content.left, content.top + headerH - 1f, content.right, content.top + headerH, fill)

        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 175)
        canvas.drawRect(content.left, listTop, content.right, content.bottom, fill)

        val scrollTrackW = if (total > maxItems) 6f * unit else 0f
        val rowRight = content.right - (if (scrollTrackW > 0f) scrollTrackW + 6f * unit else 0f)
        val rowH = NotificationCardLayout.rowHeight(baseRow)
        val rows = mutableListOf<Pair<String, RectF>>()
        val dismissRows = mutableListOf<Pair<String, RectF>>()

        items.forEachIndexed { index, item ->
            val top = listTop + index * rowH
            val bottom = top + rowH - 5f * unit
            val row = RectF(content.left, top, rowRight, bottom)
            rows += item.key to row
            fill.style = Paint.Style.FILL
            fill.color = if (index == 0 && notifScrollOffset == 0) {
                withAlpha(theme.surfaceAlt, 226)
            } else {
                withAlpha(theme.surface, 150)
            }
            canvas.drawRect(row, fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 1f * unit
            stroke.color = theme.border
            canvas.drawRect(row, stroke)
            fill.style = Paint.Style.FILL
            fill.color = theme.accent
            canvas.drawRect(content.left, top, content.left + 3f * unit, bottom, fill)

            val dismissW = 28f * unit
            val dismissRect = RectF(
                row.right - dismissW - 4f * unit,
                top + 4f * unit,
                row.right - 4f * unit,
                bottom - 4f * unit,
            )
            dismissRows += item.key to dismissRect
            drawTextButton(canvas, dismissRect, "×", theme, unit, color = theme.red)

            val textRight = dismissRect.left - 8f * unit
            val availWidth = (textRight - (content.left + 9f * unit)).coerceAtLeast(40f * unit)

            text(
                canvas, item.app.uppercase(Locale.US), content.left + 9f * unit,
                top + 12f * unit * textScale, 8f * unit * textScale, theme.accent, bold = true,
                maxWidth = availWidth * 0.65f,
            )
            val time = timeFormat.format(Date(item.postedAt))
            text(
                canvas, time, textRight - measure(time, 8f * unit * textScale),
                top + 12f * unit * textScale, 8f * unit * textScale, theme.dim,
            )
            text(
                canvas, item.title, content.left + 9f * unit, top + 24f * unit * textScale,
                10f * unit * textScale, theme.foreground, maxWidth = availWidth,
            )
            text(
                canvas, item.text, content.left + 9f * unit, top + 35f * unit * textScale,
                9f * unit * textScale, theme.dim, maxWidth = availWidth,
            )
        }

        if (total > maxItems) {
            val track = RectF(content.right - scrollTrackW, listTop + 2f * unit, content.right, content.bottom - 2f * unit)
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(theme.surfaceAlt, 200)
            canvas.drawRoundRect(track, 2f * unit, 2f * unit, fill)
            val thumbH = (track.height() * (items.size.toFloat() / total)).coerceAtLeast(18f * unit)
            val thumbTop = track.top + (track.height() - thumbH) * (notifScrollOffset.toFloat() / maxOffset.coerceAtLeast(1))
            val thumb = RectF(track.left, thumbTop, track.right, thumbTop + thumbH)
            fill.color = theme.accent
            canvas.drawRoundRect(thumb, 2f * unit, 2f * unit, fill)
        }

        return CommsHit(
            clear = clear,
            notifications = rows,
            dismissRows = dismissRows,
            scrollUp = scrollUp,
            scrollDown = scrollDown,
        )
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

        val width = minOf(area.width() * 0.76f, 1180f * unit)
        val height = minOf(area.height() * 0.76f, 490f * unit)
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

        val pad = 22f * overlayUnit
        val textScale = DisplaySettings.notificationTextScale
        val appSize = 10f * overlayUnit * textScale
        val titleSize = 18f * overlayUnit * textScale
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
        var baseline = card.top + pad + appSize + 14f * overlayUnit + titleSize
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
        baseline += 18f * overlayUnit * textScale

        val closeLabel = "BACK · KEEP"
        val dismissLabel = "DELETE · DISMISS"
        val buttonTextSize = 9f * overlayUnit
        val closeWidth = measure(closeLabel, buttonTextSize, bold = true) + 22f * overlayUnit
        val dismissWidth = measure(dismissLabel, buttonTextSize, bold = true) + 22f * overlayUnit
        val buttonHeight = 30f * overlayUnit
        val gap = 8f * overlayUnit
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

        val bodyBottom = close.top - 16f * overlayUnit
        val maxLines = ((bodyBottom - baseline) / lineHeight).toInt().coerceAtLeast(1)
        val allLines = wrappedAllLines(item.text.ifBlank { item.title }, bodySize, contentWidth)
        val maxLineOffset = (allLines.size - maxLines).coerceAtLeast(0)
        overlayScrollOffset = overlayScrollOffset.coerceIn(0, maxLineOffset)
        val visibleLines = allLines.drop(overlayScrollOffset).take(maxLines)
        visibleLines.forEachIndexed { index, line ->
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

        var scrollUp: RectF? = null
        var scrollDown: RectF? = null
        if (allLines.size > maxLines) {
            val navW = 34f * overlayUnit
            scrollDown = RectF(dismiss.left - gap - navW, close.top, dismiss.left - gap, close.bottom)
            scrollUp = RectF(scrollDown.left - gap - navW, close.top, scrollDown.left - gap, close.bottom)
            drawTextButton(
                canvas,
                scrollUp,
                "▲",
                theme,
                overlayUnit,
                color = if (overlayScrollOffset > 0) theme.accent else theme.dim,
            )
            drawTextButton(
                canvas,
                scrollDown,
                "▼",
                theme,
                overlayUnit,
                color = if (overlayScrollOffset < maxLineOffset) theme.accent else theme.dim,
            )
        }

        text(
            canvas,
            "BACK KEEPS MESSAGE  ·  DELETE DISMISSES IT",
            card.left + pad,
            card.bottom - pad - 6f * overlayUnit,
            7.5f * overlayUnit,
            theme.dim,
            maxWidth = ( (scrollUp?.left ?: dismiss.left) - card.left - pad - 8f * overlayUnit ).coerceAtLeast(60f),
        )
        drawTextButton(canvas, close, closeLabel, theme, overlayUnit)
        drawTextButton(canvas, dismiss, dismissLabel, theme, overlayUnit, color = theme.red)
        return OverlayHit(
            card = card,
            close = close,
            dismiss = dismiss,
            scrollUp = scrollUp,
            scrollDown = scrollDown,
        )
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
    ): HomeHit {
        val content = RectF(
            tile.left + 12f * unit,
            tile.top + 27f * unit,
            tile.right - 12f * unit,
            tile.bottom - 10f * unit,
        )
        canvas.save()
        canvas.clipRect(content)

        val setupButton = RectF(
            content.left,
            content.bottom - 28f * unit,
            content.right,
            content.bottom - 2f * unit,
        )

        if (homeSetupOpen) {
            val railToggles = mutableListOf<Pair<RailItemId, RectF>>()
            val mediaTargets = mutableListOf<Pair<String, RectF>>()

            val colGap = 12f * unit
            val colW = (content.width() - colGap) / 2f
            val leftCol = RectF(content.left, content.top + 6f * unit, content.left + colW, setupButton.top - 8f * unit)
            val rightCol = RectF(leftCol.right + colGap, leftCol.top, content.right, leftCol.bottom)

            text(
                canvas,
                "RIGHT RAIL TILES & APPS (CHECK TO SHOW)",
                leftCol.left,
                leftCol.top + 10f * unit,
                8.5f * unit,
                theme.accent,
                bold = true,
                maxWidth = leftCol.width(),
            )
            val railItems = RailConfigStore.configurableItems()
            val railTop = leftCol.top + 16f * unit
            val railGap = 3f * unit
            val railRowH = ((leftCol.bottom - railTop) / railItems.size.coerceAtLeast(1))
                .coerceIn(18f * unit, 26f * unit)
            railItems.forEachIndexed { idx, item ->
                val top = railTop + idx * railRowH
                val row = RectF(leftCol.left, top, leftCol.right, top + railRowH - railGap)
                railToggles += item to row
                val enabled = RailConfigStore.isEnabled(item)
                fill.style = Paint.Style.FILL
                fill.color = if (enabled) withAlpha(theme.surfaceAlt, 235) else withAlpha(theme.background, 160)
                canvas.drawRoundRect(row, 2f * unit, 2f * unit, fill)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1f * unit
                stroke.color = if (enabled) theme.accent else theme.border
                canvas.drawRoundRect(row, 2f * unit, 2f * unit, stroke)
                val check = if (enabled) "[✓]" else "[ ]"
                text(
                    canvas,
                    "$check  ${item.title}",
                    row.left + 8f * unit,
                    row.centerY() + 3f * unit,
                    8.5f * unit,
                    if (enabled) theme.foreground else theme.dim,
                    bold = enabled,
                    maxWidth = row.width() - 16f * unit,
                )
            }

            text(
                canvas,
                "DEFAULT MEDIA / STAGE APP (RADIO)",
                rightCol.left,
                rightCol.top + 10f * unit,
                8.5f * unit,
                theme.accent,
                bold = true,
                maxWidth = rightCol.width(),
            )
            val targets = LaunchTargetStore.mediaTargets()
            val selectedTargetId = LaunchTargetStore.selectedMediaId()
            val targetTop = rightCol.top + 16f * unit
            val targetRowH = ((rightCol.bottom - targetTop) / targets.size.coerceAtLeast(1))
                .coerceIn(18f * unit, 26f * unit)
            targets.forEachIndexed { idx, target ->
                val top = targetTop + idx * targetRowH
                val row = RectF(rightCol.left, top, rightCol.right, top + targetRowH - railGap)
                mediaTargets += target.id to row
                val active = target.id == selectedTargetId
                fill.style = Paint.Style.FILL
                fill.color = if (active) withAlpha(theme.surfaceAlt, 235) else withAlpha(theme.background, 160)
                canvas.drawRoundRect(row, 2f * unit, 2f * unit, fill)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = 1f * unit
                stroke.color = if (active) theme.accent else theme.border
                canvas.drawRoundRect(row, 2f * unit, 2f * unit, stroke)
                val radio = if (active) "(●)" else "( )"
                text(
                    canvas,
                    "$radio  ${target.label.uppercase(Locale.US)}",
                    row.left + 8f * unit,
                    row.centerY() + 3f * unit,
                    8.5f * unit,
                    if (active) theme.foreground else theme.dim,
                    bold = active,
                    maxWidth = row.width() - 16f * unit,
                )
            }

            drawTextButton(canvas, setupButton, "DONE · BACK TO THEMES", theme, unit)
            canvas.restore()
            return HomeHit(
                setup = setupButton,
                railToggles = railToggles,
                mediaTargets = mediaTargets,
            )
        }

        text(canvas, clockFormat.format(Date()), content.left, content.top + 34f * unit, 34f * unit, theme.foreground, bold = true)
        text(canvas, dateFormat.format(Date()).uppercase(Locale.US), content.left, content.top + 52f * unit, 9f * unit, theme.dim)
        text(canvas, "OMARCHY AUTO", content.left, content.top + 74f * unit, 9f * unit, theme.accent)
        val columns = 5
        val rows = (Themes.all.size + columns - 1) / columns
        val gap = 4f * unit
        val chipHeight = 26f * unit
        val chipBlockHeight = rows * chipHeight + (rows - 1) * gap
        val bottomReserve = 34f * unit
        val chipTop = content.bottom - bottomReserve - chipBlockHeight
        text(canvas, "THEME", content.left, chipTop - 8f * unit, 9f * unit, theme.dim)
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

        drawTextButton(canvas, setupButton, "SETUP · RIGHT RAIL & APPS", theme, unit)
        canvas.restore()
        return HomeHit(setup = setupButton)
    }

    private fun drawKeyboardOverlay(
        canvas: Canvas,
        area: RectF,
        theme: OmarchyTheme,
        unit: Float,
        mode: Mode,
    ): KeyboardHit {
        val kbUnit = unit * if (mode == Mode.CAR) 1.55f else 1.05f
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(205, 4, 6, 14)
        canvas.drawRect(area, fill)

        val width = minOf(area.width() * 0.88f, 1140f * unit)
        val height = minOf(area.height() * 0.86f, 520f * unit)
        val card = RectF(
            area.centerX() - width / 2f,
            area.centerY() - height / 2f,
            area.centerX() + width / 2f,
            area.centerY() + height / 2f,
        )
        fill.color = withAlpha(theme.surface, 252)
        canvas.drawRoundRect(card, 4f * kbUnit, 4f * kbUnit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2f * kbUnit
        stroke.color = theme.accent
        canvas.drawRoundRect(card, 4f * kbUnit, 4f * kbUnit, stroke)

        val pad = 14f * kbUnit
        val title = when (keyboardMode) {
            KeyboardMode.MAP_SEARCH -> "ENTER DESTINATION ADDRESS OR PLACE"
            KeyboardMode.WEB_INPUT -> "SEARCH OR PROMPT ${webStageApp?.title ?: "WEB"}"
            KeyboardMode.NONE -> "INPUT"
        }
        text(
            canvas,
            title,
            card.left + pad,
            card.top + pad + 10f * kbUnit,
            9.5f * kbUnit,
            theme.accent,
            bold = true,
        )

        val closeW = 68f * kbUnit
        val close = RectF(
            card.right - pad - closeW,
            card.top + 8f * kbUnit,
            card.right - pad,
            card.top + 32f * kbUnit,
        )
        drawTextButton(canvas, close, "CLOSE", theme, kbUnit, color = theme.dim)

        val inputTop = card.top + 36f * kbUnit
        val inputH = 34f * kbUnit
        val submitW = 86f * kbUnit
        val clearW = 48f * kbUnit
        val backW = 48f * kbUnit
        val gap = 6f * kbUnit

        val submit = RectF(card.right - pad - submitW, inputTop, card.right - pad, inputTop + inputH)
        val clear = RectF(submit.left - gap - clearW, inputTop, submit.left - gap, inputTop + inputH)
        val backspace = RectF(clear.left - gap - backW, inputTop, clear.left - gap, inputTop + inputH)
        val inputBox = RectF(card.left + pad, inputTop, backspace.left - gap, inputTop + inputH)

        fill.style = Paint.Style.FILL
        fill.color = withAlpha(theme.background, 235)
        canvas.drawRoundRect(inputBox, 2f * kbUnit, 2f * kbUnit, fill)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1.5f * kbUnit
        stroke.color = theme.accent
        canvas.drawRoundRect(inputBox, 2f * kbUnit, 2f * kbUnit, stroke)

        val displayQuery = if (keyboardQuery.isEmpty()) "Type query…" else "${keyboardQuery}_"
        text(
            canvas,
            displayQuery,
            inputBox.left + 10f * kbUnit,
            inputBox.centerY() + 4f * kbUnit,
            11f * kbUnit,
            if (keyboardQuery.isEmpty()) theme.dim else theme.foreground,
            bold = keyboardQuery.isNotEmpty(),
            maxWidth = inputBox.width() - 20f * kbUnit,
        )

        drawTextButton(canvas, backspace, "⌫", theme, kbUnit)
        drawTextButton(canvas, clear, "CLR", theme, kbUnit, color = theme.yellow)
        val submitLabel = if (keyboardMode == KeyboardMode.MAP_SEARCH) "SEARCH" else "GO"
        drawTextButton(canvas, submit, submitLabel, theme, kbUnit, color = theme.green)

        val candidates = mutableListOf<Pair<Int, RectF>>()
        var keysTop = inputBox.bottom + 8f * kbUnit

        val rows = QwertyLayout.rows()
        val rowGap = 4f * kbUnit
        val minKeyH = 28f * kbUnit
        val keyBlock = rows.size * minKeyH + rowGap * (rows.size - 1)
        if (keyboardMode == KeyboardMode.MAP_SEARCH) {
            val results = NavigationController.searchResults.take(3)
            val candBudget = (card.bottom - pad - keyBlock - 8f * kbUnit - keysTop).coerceAtLeast(0f)
            val rowH = if (results.isEmpty()) {
                0f
            } else {
                (candBudget / results.size).coerceAtMost(36f * kbUnit).coerceAtLeast(0f)
            }
            val visible = if (rowH >= 20f * kbUnit) results.size else 0
            val candAreaH = rowH * visible
            if (visible > 0) {
                results.forEachIndexed { idx, cand ->
                    val rTop = keysTop + idx * rowH
                    val row = RectF(card.left + pad, rTop, card.right - pad, rTop + rowH - 3f * kbUnit)
                    candidates += idx to row
                    fill.style = Paint.Style.FILL
                    fill.color = withAlpha(theme.surfaceAlt, 235)
                    canvas.drawRoundRect(row, 2f * kbUnit, 2f * kbUnit, fill)
                    stroke.style = Paint.Style.STROKE
                    stroke.strokeWidth = 1f * kbUnit
                    stroke.color = theme.accent
                    canvas.drawRoundRect(row, 2f * kbUnit, 2f * kbUnit, stroke)
                    text(
                        canvas,
                        "▶ ${cand.name}  ·  ${cand.displayName}",
                        row.left + 8f * kbUnit,
                        row.centerY() + 3f * kbUnit,
                        12f * kbUnit,
                        theme.foreground,
                        bold = idx == 0,
                        maxWidth = row.width() - 16f * kbUnit,
                    )
                }
                keysTop += candAreaH + 4f * kbUnit
            } else if (candBudget >= 22f * kbUnit) {
                val hint = NavigationController.statusMessage
                    ?: "Type an address or place and tap SEARCH, then tap a result to start route"
                text(
                    canvas,
                    hint,
                    card.left + pad,
                    keysTop + 14f * kbUnit,
                    8.5f * kbUnit,
                    theme.dim,
                    maxWidth = card.width() - pad * 2f,
                )
                keysTop += 22f * kbUnit
            }
        }

        val keysBottom = card.bottom - pad
        val availH = (keysBottom - keysTop).coerceAtLeast(rows.size * 8f)
        val keyH = ((availH - rowGap * (rows.size - 1)) / rows.size).coerceAtLeast(8f)
        val widest = rows.maxOf { row ->
            row.indentKeys + row.keys.sumOf { it.weight.toDouble() }.toFloat()
        }
        val unitW = (card.width() - pad * 2f) / widest
        val keys = mutableListOf<Pair<String, RectF>>()
        var spaceRect: RectF? = null

        rows.forEachIndexed { rIdx, row ->
            val top = keysTop + rIdx * (keyH + rowGap)
            var x = card.left + pad + row.indentKeys * unitW
            for (key in row.keys) {
                val rect = if (key.label == "SPACE") {
                    RectF(card.left + pad, top, card.right - pad, top + keyH)
                } else {
                    val w = unitW * key.weight - gap
                    RectF(x, top, x + w.coerceAtLeast(8f), top + keyH)
                }
                x += unitW * key.weight
                val value = QwertyLayout.valueOf(key.label)
                keys += value to rect
                if (key.label == "SPACE") spaceRect = rect
                drawTextButton(canvas, rect, key.label, theme, kbUnit, color = theme.foreground)
            }
        }

        return KeyboardHit(
            card = card,
            keys = keys,
            backspace = backspace,
            space = spaceRect,
            clear = clear,
            submit = submit,
            close = close,
            candidates = candidates,
        )
    }

    private fun wrappedAllLines(
        value: String,
        size: Float,
        maxWidth: Float,
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
        return if (all.isEmpty()) listOf("") else all
    }

    private fun wrappedLines(
        value: String,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
    ): List<String> {
        val paint = textPaint
        paint.textSize = size * fontScale
        val all = wrappedAllLines(value, size, maxWidth)
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
        private const val MAX_VISIBLE_RAIL = 4
    }
}
