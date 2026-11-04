package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences

enum class RailItemId(
    val key: String,
    val title: String,
    val subtitle: String,
    val tileId: TileId?,
    val webApp: StageWebApp? = null,
    val defaultEnabled: Boolean = false,
) {
    DRIVE("drive", "DRIVE", "MAP · GPS", TileId.DRIVE, defaultEnabled = true),
    MEDIA("media", "MEDIA", "NOW PLAYING", TileId.MEDIA, defaultEnabled = true),
    COMMS("comms", "COMMS", "ALERTS", TileId.COMMS, defaultEnabled = true),
    YOUTUBE("youtube", "YOUTUBE", "VIDEO · STAGE", null, StageWebApp.YOUTUBE, defaultEnabled = false),
    CHATGPT("chatgpt", "CHATGPT", "AI · STAGE", null, StageWebApp.CHATGPT, defaultEnabled = false),
    GROK("grok", "GROK", "AI · STAGE", null, StageWebApp.GROK, defaultEnabled = false),
    VEHICLE("vehicle", "VEHICLE", "OBD · MOCK", TileId.VEHICLE, defaultEnabled = false),
    HOME("home", "HOME", "THEME · SETUP", TileId.HOME, defaultEnabled = true),
}

enum class StageWebApp(
    val id: String,
    val label: String,
    val homeUrl: String,
    val searchTemplate: String,
    val packageName: String,
) {
    YOUTUBE(
        id = "youtube",
        label = "YOUTUBE",
        homeUrl = "https://m.youtube.com",
        searchTemplate = "https://m.youtube.com/results?search_query=%s",
        packageName = "com.google.android.youtube",
    ),
    CHATGPT(
        id = "chatgpt",
        label = "CHATGPT",
        homeUrl = "https://chatgpt.com",
        searchTemplate = "https://chatgpt.com/?q=%s",
        packageName = "com.openai.chatgpt",
    ),
    GROK(
        id = "grok",
        label = "GROK",
        homeUrl = "https://grok.com",
        searchTemplate = "https://grok.com/?q=%s",
        packageName = "ai.x.grok",
    );

    val title: String get() = label
}

/**
 * Persists which tiles/apps appear in the right-side rail.
 * HOME is always kept enabled so the user can always reach Setup/Theme.
 */
object RailConfigStore {
    private const val PREFS = "omarchy_rail"
    private const val KEY_ENABLED = "enabled_rail_items"

    private var prefs: SharedPreferences? = null

    @Volatile
    private var enabledSet: Set<String> = defaultKeys()

    @Volatile
    var revision: Int = 0
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val stored = p.getStringSet(KEY_ENABLED, null)
        enabledSet = if (stored.isNullOrEmpty()) {
            defaultKeys()
        } else {
            (stored + RailItemId.HOME.key).toSet()
        }
    }

    fun allSelectable(): List<RailItemId> = RailItemId.entries

    fun configurableItems(): List<RailItemId> = RailItemId.entries

    fun enabledItems(): List<RailItemId> =
        RailItemId.entries.filter { it.key in enabledSet || it == RailItemId.HOME }

    fun isEnabled(item: RailItemId): Boolean =
        item == RailItemId.HOME || item.key in enabledSet

    fun setEnabled(context: Context, item: RailItemId, enabled: Boolean) {
        init(context)
        if (item == RailItemId.HOME) return
        val next = enabledSet.toMutableSet()
        if (enabled) {
            next.add(item.key)
        } else {
            next.remove(item.key)
        }
        next.add(RailItemId.HOME.key)
        enabledSet = next
        prefs?.edit()?.putStringSet(KEY_ENABLED, next)?.apply()
        revision++
        LiveDataStore.notifyDisplayChanged()
    }

    fun toggle(context: Context, item: RailItemId) {
        setEnabled(context, item, !isEnabled(item))
    }

    fun resetForTest() {
        enabledSet = defaultKeys()
        revision++
    }

    private fun defaultKeys(): Set<String> =
        RailItemId.entries.filter { it.defaultEnabled }.map { it.key }.toSet()
}
