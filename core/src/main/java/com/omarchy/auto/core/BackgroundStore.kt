package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * A user-selected background that survives theme changes. Precedence in
 * AppBackground is: custom background, theme background, generated fallback.
 */
object BackgroundStore {
    private const val PREFS = "omarchy_background"
    private const val KEY_PATH = "custom_path"
    private const val KEY_ENABLED = "custom_enabled"
    private const val KEY_SKIP_LOCAL = "skip_local_overrides"

    val BUNDLED_WALLPAPER_NAMES = listOf(
        "wallpaper.jpg",
        "wallpaper.png",
        "roadster.jpg",
        "roadster.png",
    )

    private var prefs: SharedPreferences? = null

    @Volatile
    var revision: Int = 0
        private set

    @Volatile
    var customPath: String? = null
        private set

    @Volatile
    var enabled: Boolean = false
        private set

    /**
     * Set when the user explicitly asks for theme backgrounds. It suppresses
     * legacy local files and the private bundled wallpaper so the bundled
     * Omarchy theme image can win.
     */
    @Volatile
    var skipLocalOverrides: Boolean = false
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs?.getString(KEY_PATH, null)
        enabled = prefs?.getBoolean(KEY_ENABLED, false) == true &&
            path != null && File(path).isFile
        customPath = path.takeIf { enabled }
        skipLocalOverrides = prefs?.getBoolean(KEY_SKIP_LOCAL, false) == true
        if (!enabled) {
            prefs?.edit()?.putBoolean(KEY_ENABLED, false)?.apply()
        }
    }

    fun bundledWallpaperName(context: Context): String? =
        BUNDLED_WALLPAPER_NAMES.firstOrNull { name ->
            try {
                context.assets.open(name).close()
                true
            } catch (_: Exception) {
                false
            }
        }

    fun hasBundledTom(context: Context): Boolean = bundledWallpaperName(context) != null

    fun useBundledTom(context: Context): Boolean {
        val app = context.applicationContext
        val name = bundledWallpaperName(app) ?: return false
        val extension = name.substringAfterLast('.', "jpg")
        return try {
            app.assets.open(name).use { input ->
                writeBackground(app, input, extension)
            }?.let { file ->
                enable(app, file)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun setCustom(context: Context, uri: Uri): Boolean {
        val app = context.applicationContext
        val mime = app.contentResolver.getType(uri).orEmpty()
        val extension = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        return try {
            val input = app.contentResolver.openInputStream(uri) ?: return false
            input.use { stream -> writeBackground(app, stream, extension) }?.let { file ->
                enable(app, file)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        customPath?.let { runCatching { File(it).delete() } }
        val directory = File(app.filesDir, "backgrounds")
        for (name in listOf("custom.jpg", "custom.png", "custom.webp", "custom.tmp")) {
            runCatching { File(directory, name).delete() }
        }
        customPath = null
        enabled = false
        skipLocalOverrides = true
        prefs?.edit()
            ?.putBoolean(KEY_ENABLED, false)
            ?.putBoolean(KEY_SKIP_LOCAL, true)
            ?.apply()
        revision++
        AppBackground.invalidate()
        LiveDataStore.notifyDisplayChanged()
    }

    private fun enable(context: Context, file: File) {
        customPath = file.absolutePath
        enabled = true
        skipLocalOverrides = false
        prefs?.edit()
            ?.putString(KEY_PATH, file.absolutePath)
            ?.putBoolean(KEY_ENABLED, true)
            ?.putBoolean(KEY_SKIP_LOCAL, false)
            ?.apply()
        revision++
        AppBackground.invalidate()
        LiveDataStore.notifyDisplayChanged()
    }

    private fun writeBackground(context: Context, input: InputStream, extension: String): File? {
        val directory = File(context.filesDir, "backgrounds").apply { mkdirs() }
        val temp = File(directory, "custom.tmp")
        return try {
            FileOutputStream(temp).use { output -> input.copyTo(output) }
            if (temp.length() == 0L) {
                temp.delete()
                return null
            }
            val target = File(directory, "custom.$extension")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            for (other in listOf("jpg", "png", "webp")) {
                if (other != extension) {
                    runCatching { File(directory, "custom.$other").delete() }
                }
            }
            target
        } catch (_: Exception) {
            runCatching { temp.delete() }
            null
        }
    }
}
