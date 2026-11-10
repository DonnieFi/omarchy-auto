package com.omarchy.auto

import android.content.Context
import com.omarchy.auto.core.CarPlatformPolicy
import com.omarchy.auto.core.LaunchTargetStore
import com.omarchy.auto.core.MapCameraStore
import com.omarchy.auto.core.MediaTransport
import com.omarchy.auto.core.MediaWatcher
import com.omarchy.auto.core.NavigationController
import com.omarchy.auto.core.NavState
import com.omarchy.auto.core.OmarchyNotificationListener
import com.omarchy.auto.core.RailConfigStore
import com.omarchy.auto.core.StageWebApp
import com.omarchy.auto.core.StageWebController
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileId
import com.omarchy.auto.core.TileRenderer

/**
 * Turns a shell hit into a real action. Search, settings, and locked
 * notifications leave the canvas for a host template so they stay usable
 * while driving. Theme chips, media transport, and the parked notification
 * list run on the canvas.
 */
interface CarShellHost {
    fun openSearch()
    fun openNotifications()
    fun openSettings()
}

internal class CarShellDispatch(
    private val renderer: TileRenderer,
    private val appContext: Context,
    private val host: CarShellHost,
    private val refreshBackground: () -> Unit,
    private val launchStage: (TileId) -> Unit,
) {
    /** @return true when the canvas should be redrawn */
    fun apply(hit: TileRenderer.Hit): Boolean {
        when (hit.target) {
            TileRenderer.Target.THEME -> renderer.show(TileId.HOME)

            TileRenderer.Target.THEME_CHIP -> {
                ThemeStore.select(hit.index)
                refreshBackground()
            }

            TileRenderer.Target.PLAY_PAUSE -> MediaTransport.toggle()
            TileRenderer.Target.PREV_TRACK -> MediaTransport.previous()
            TileRenderer.Target.NEXT_TRACK -> MediaTransport.next()

            TileRenderer.Target.NOTIF_REFRESH ->
                OmarchyNotificationListener.refresh(appContext)

            TileRenderer.Target.NOTIF_CLEAR -> {
                OmarchyNotificationListener.dismissDisplayed(appContext)
                renderer.scrollNotifications(-1000)
            }

            TileRenderer.Target.NOTIF_SCROLL_UP -> renderer.scrollNotifications(-1)
            TileRenderer.Target.NOTIF_SCROLL_DOWN -> renderer.scrollNotifications(1)
            TileRenderer.Target.NOTIFICATION -> renderer.openNotification(hit.key)

            TileRenderer.Target.NOTIF_CLOSE,
            TileRenderer.Target.OVERLAY_DISMISS,
            -> renderer.closeNotification()

            TileRenderer.Target.NOTIF_DISMISS_ONE -> {
                hit.key?.let { OmarchyNotificationListener.dismiss(appContext, it) }
                renderer.closeNotification()
            }

            TileRenderer.Target.NOTIF_ROW_DISMISS -> {
                hit.key?.let { OmarchyNotificationListener.dismiss(appContext, it) }
            }

            TileRenderer.Target.OVERLAY_SCROLL_UP -> renderer.scrollOverlay(-2)
            TileRenderer.Target.OVERLAY_SCROLL_DOWN -> renderer.scrollOverlay(2)
            TileRenderer.Target.SETUP -> renderer.toggleHomeSetup()

            TileRenderer.Target.RAIL_CONFIG_TOGGLE -> {
                hit.railItem?.let { RailConfigStore.toggle(appContext, it) }
            }

            TileRenderer.Target.MEDIA_TARGET_SELECT -> {
                val key = hit.key ?: return false
                LaunchTargetStore.select(appContext, key)
                StageWebApp.entries.firstOrNull { it.id == key }?.let { app ->
                    StageWebController.selectApp(app)
                }
            }

            TileRenderer.Target.MEDIA_SESSION_SELECT -> {
                val pkg = hit.key ?: return false
                renderer.closeWebApp()
                MediaWatcher.selectSession(pkg)
            }

            TileRenderer.Target.WEB_APP_OPEN -> {
                val app = hit.webApp ?: return false
                renderer.openWebApp(app)
            }

            TileRenderer.Target.WEB_BACK -> StageWebController.goBack()
            TileRenderer.Target.WEB_RELOAD -> StageWebController.reload()
            TileRenderer.Target.WEB_SEARCH ->
                renderer.openKeyboard(TileRenderer.KeyboardMode.WEB_INPUT)
            TileRenderer.Target.WEB_SCROLL_UP -> StageWebController.scrollBy(-260)
            TileRenderer.Target.WEB_SCROLL_DOWN -> StageWebController.scrollBy(260)
            TileRenderer.Target.WEB_CLOSE -> renderer.closeWebApp()
            TileRenderer.Target.WEB_STAGE_TAP ->
                StageWebController.tapNormalized(hit.normX, hit.normY)

            TileRenderer.Target.MAP_ZOOM_IN -> MapCameraStore.zoomIn()
            TileRenderer.Target.MAP_ZOOM_OUT -> MapCameraStore.zoomOut()
            TileRenderer.Target.MAP_RECENTER -> MapCameraStore.recenter()

            // The car canvas does not use the custom keyboard. The host search
            // screen accepts voice while driving and the host keyboard when parked.
            TileRenderer.Target.MAP_SEARCH_OPEN -> host.openSearch()

            TileRenderer.Target.MAP_NAV_TOGGLE -> {
                when (NavigationController.navState) {
                    NavState.ROUTE_ERROR,
                    NavState.LOCATION_REQUIRED,
                    -> NavigationController.retryRoute()
                    else -> {
                        if (NavigationController.destination == null && !NavigationController.navActive) {
                            host.openSearch()
                        } else {
                            NavigationController.toggleNavigation()
                        }
                    }
                }
            }

            TileRenderer.Target.MAP_CANDIDATE_SELECT -> {
                val cand = NavigationController.searchResults.getOrNull(hit.index) ?: return false
                NavigationController.selectDestination(cand, autoStartNav = true)
                renderer.closeKeyboard()
            }

            TileRenderer.Target.KB_KEY -> hit.key?.let { renderer.appendKeyboardChar(it) }
            TileRenderer.Target.KB_BACKSPACE -> renderer.backspaceKeyboard()
            TileRenderer.Target.KB_SPACE -> renderer.appendKeyboardChar(" ")
            TileRenderer.Target.KB_CLEAR -> renderer.clearKeyboard()

            TileRenderer.Target.KB_SUBMIT -> {
                when (renderer.keyboardMode) {
                    TileRenderer.KeyboardMode.MAP_SEARCH -> {
                        NavigationController.setQuery(renderer.keyboardQuery)
                        NavigationController.searchAddress(renderer.keyboardQuery)
                    }
                    TileRenderer.KeyboardMode.WEB_INPUT -> {
                        StageWebController.submitQuery(renderer.keyboardQuery)
                        renderer.closeKeyboard()
                    }
                    TileRenderer.KeyboardMode.NONE -> Unit
                }
            }

            TileRenderer.Target.KB_CLOSE -> renderer.closeKeyboard()
            TileRenderer.Target.RAIL_SCROLL_UP -> renderer.scrollRail(-1)
            TileRenderer.Target.RAIL_SCROLL_DOWN -> renderer.scrollRail(1)

            TileRenderer.Target.RAIL_ITEM -> {
                val item = hit.railItem ?: return false
                renderer.selectRailItem(item)
            }

            TileRenderer.Target.STAGE_OPEN -> {
                val tile = renderer.selected ?: return false
                if (tile == TileId.HOME) {
                    host.openSettings()
                } else {
                    launchStage(tile)
                }
                return false
            }

            TileRenderer.Target.TILE_DRIVE -> renderer.toggle(TileId.DRIVE)
            TileRenderer.Target.TILE_MEDIA -> renderer.toggle(TileId.MEDIA)
            TileRenderer.Target.TILE_COMMS -> {
                renderer.toggle(TileId.COMMS)
                if (renderer.selected == TileId.COMMS &&
                    !CarPlatformPolicy.allowsNotificationList(CarPlatformPolicy.Surface.ANDROID_AUTO)
                ) {
                    host.openNotifications()
                }
            }
            TileRenderer.Target.TILE_VEHICLE -> renderer.toggle(TileId.VEHICLE)
            TileRenderer.Target.TILE_HOME -> renderer.toggle(TileId.HOME)
        }
        return true
    }
}
