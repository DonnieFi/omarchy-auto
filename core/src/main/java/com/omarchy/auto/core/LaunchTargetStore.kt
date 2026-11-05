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
        val stageApp: StageWebApp? = null,
    ) {
        val id: String get() = key
    }

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
        Target("youtube", "YOUTUBE", listOf("com.google.android.youtube"), "YouTube", StageWebApp.YOUTUBE),
        Target("chatgpt", "CHATGPT", listOf("com.openai.chatgpt"), "ChatGPT", StageWebApp.CHATGPT),
        Target("grok", "GROK", listOf("ai.x.grok"), "Grok", StageWebApp.GROK),
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

    fun isInstalled(context: Context, target: Target): Boolean {
        val pm = context.packageManager
        return target.packages.any { installed(pm, it) }
    }

    /** Native installed targets plus AUTO (for phone media launching). */
    fun available(context: Context): List<Target> {
        val pm = context.packageManager
        return known.filter { target ->
            target.key == AUTO_KEY || target.packages.any { installed(pm, it) }
        }
    }

    /** Targets usable in the stage or setup (includes stage web apps and installed apps). */
    fun stageTargets(context: Context): List<Target> {
        val pm = context.packageManager
        return known.filter { target ->
            target.key == AUTO_KEY ||
                target.stageApp != null ||
                target.packages.any { installed(pm, it) }
        }
    }

    /**
     * Saved target if installed (or if it is a stage web app); otherwise falls
     * back to the first installed native target or AUTO.
     */
    fun selected(context: Context): Target {
        init(context)
        val savedKey = prefs?.getString(KEY_MEDIA, AUTO_KEY) ?: AUTO_KEY
        val saved = known.firstOrNull { it.key == savedKey }
        if (saved != null) {
            if (saved.key == AUTO_KEY || saved.stageApp != null || isInstalled(context, saved)) {
                return saved
            }
        }
        val firstInstalled = known.firstOrNull { it.key != AUTO_KEY && isInstalled(context, it) }
        return firstInstalled ?: auto
    }

    fun selectedKey(): String = prefs?.getString(KEY_MEDIA, AUTO_KEY) ?: AUTO_KEY

    fun selectedMediaId(): String = selectedKey()

    fun mediaTargets(): List<Target> = known.take(8)

    fun select(context: Context, key: String) {
        init(context)
        prefs?.edit()?.putString(KEY_MEDIA, key)?.apply()
        LiveDataStore.notifyDisplayChanged()
    }

    private fun installed(pm: PackageManager, packageName: String): Boolean =
        pm.getLaunchIntentForPackage(packageName) != null
}
