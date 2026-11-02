package com.omarchy.auto.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/**
 * Theme-matched wallpaper. Omarchy backgrounds live in
 * `core/src/main/assets/themes/<theme-id>.webp`; a local file can override a
 * theme without committing it. The exact Tokyo Studio image remains a local-only
 * override for Tokyo Night and is never committed.
 */
object AppBackground {
    private const val MAX_DIMENSION = 2560

    @Volatile
    private var currentId: String? = null

    @Volatile
    var bitmap: Bitmap? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omarchy-theme-bg").apply { isDaemon = true }
    }
    private var generation = 0

    fun load(context: Context, theme: OmarchyTheme): Bitmap? {
        val id = theme.id
        if (currentId == id) return bitmap
        synchronized(this) {
            if (currentId == id) return bitmap
            val next = loadTheme(context, id)
            bitmap = next
            currentId = id
            return next
        }
    }

    fun loadAsync(context: Context, theme: OmarchyTheme, callback: (Bitmap?) -> Unit) {
        val app = context.applicationContext
        val request = synchronized(this) { ++generation }
        decoder.execute {
            val loaded = load(app, theme)
            main.post {
                if (request == synchronized(this) { generation }) callback(loaded)
            }
        }
    }

    private fun loadTheme(context: Context, id: String): Bitmap? {
        // Per-theme local override, useful for a licensed image the repo cannot ship.
        for (extension in listOf("webp", "jpg", "png")) {
            val file = File(context.filesDir, "themes/$id.$extension")
            if (file.isFile) {
                decodeFile(file.absolutePath)?.let { return it }
            }
        }

        if (id == "tokyo-night") {
            // Preserve the owner's existing local Tokyo Studio workflow. This
            // must override the committed Omarchy background in private builds.
            for (name in listOf("wallpaper.jpg", "wallpaper.png", "roadster.jpg", "roadster.png")) {
                val file = context.filesDir.resolve(name)
                if (file.isFile) {
                    decodeFile(file.absolutePath)?.let { return it }
                }
            }
            for (name in listOf("wallpaper.jpg", "wallpaper.png", "roadster.jpg", "roadster.png")) {
                try {
                    context.assets.open(name).use { stream ->
                        decodeStream(stream)?.let { return it }
                    }
                } catch (_: Exception) {
                    // Optional private asset.
                }
            }
        }

        try {
            context.assets.open("themes/$id.webp").use { stream ->
                decodeStream(stream)?.let { return it }
            }
        } catch (_: Exception) {
            // No bundled background; the renderer uses its palette fallback.
        }
        return null
    }

    private fun decodeFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun decodeStream(stream: InputStream): Bitmap? {
        // Bundled theme backgrounds are already 1600x900 WebP; decode once.
        return BitmapFactory.decodeStream(stream)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }
}
