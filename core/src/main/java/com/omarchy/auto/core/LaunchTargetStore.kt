package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Phone-launcher targets for the Media tile's OPEN action.
 *
 * The Android Auto host owns car app launching, so this selection only changes
 * the phone launcher. The car Media stage still follows the active
 * MediaSession.
 */
object LaunchTargetStore {
    private const val PREFS = "omarchy_launch"
    private const val KEY_MEDIA = "media_target"
    private const val AUTO_KEY = "auto"

    data class Target(
        val key: String,
        val label: String,
        val packages: List<String>,
        val installHint: String,
    )

    private val spotify = Target(
        key = "spotify",
        label = "SPOTIFY",
        packages = listOf("com.spotify.music"),
        installHint = "Spotify",
    )
    private val youtubeMusic = Target(
        key = "youtube-music",
        label = "YOUTUBE MUSIC",
        packages = listOf("com.google.android.apps.youtube.music"),
        installHint = "YouTube Music",
    )
    private val auto = Target(
        key = AUTO_KEY,
        label = "AUTO · SPOTIFY → YOUTUBE MUSIC",
        packages = listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        installHint = "Spotify or YouTube Music",
    )

    val known: List<Target> = listOf(
        auto,
        spotify,
        youtubeMusic,
        Target("youtube", "YOUTUBE", listOf("com.google.android.youtube"), "YouTube"),
        Target("chatgpt", "CHATGPT", listOf("com.openai.chatgpt"), "ChatGPT"),
        Target("grok", "GROK", listOf("ai.x.grok"), "Grok"),
        Target("gemini", "GEMINI", listOf("com.google.android.apps.bard"), "Gemini"),
        Target(
            "google-assistant",
            "GOOGLE ASSISTANT",
            listOf("com.google.android.googlequicksearchbox"),
            "Google Assistant",
        ),
        Target("amazon-music", "AMAZON MUSIC", listOf("com.amazon.mp3"), "Amazon Music"),
        Target("tidal", "TIDAL", listOf("com.aspiro.tidal"), "Tidal"),
        Target("deezer", "DEEZER", listOf("deezer.android.app"), "Deezer"),
        Target("vlc", "VLC", listOf("org.videolan.vlc"), "VLC"),
        Target(
            "pocket-casts",
            "POCKET CASTS",
            listOf("au.com.shiftyjelly.pocketcasts"),
            "Pocket Casts",
        ),
        Target("antennapod", "ANTENNAPOD", listOf("de.danoeh.antennapod"), "AntennaPod"),
    )

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun available(context: Context): List<Target> {
        val pm = context.packageManager
        val hasAuto = auto.packages.any { installed(pm, it) }
        return known.filter { target ->
            if (target.key == AUTO_KEY) {
                hasAuto
            } else {
                target.packages.any { installed(pm, it) }
            }
        }
    }

    /** Saved target if still installed, otherwise the first installed target. */
    fun selected(context: Context): Target {
        val savedKey = prefs?.getString(KEY_MEDIA, AUTO_KEY) ?: AUTO_KEY
        val installedTargets = available(context)
        return installedTargets.firstOrNull { it.key == savedKey }
            ?: installedTargets.firstOrNull { it.key != AUTO_KEY }
            ?: auto
    }

    fun select(context: Context, key: String) {
        init(context)
        prefs?.edit()?.putString(KEY_MEDIA, key)?.apply()
    }

    private fun installed(pm: PackageManager, packageName: String): Boolean =
        pm.getLaunchIntentForPackage(packageName) != null
}
