package com.omarchy.auto.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Optional local wallpaper. The Tokyo Roadster artwork is not committed because
 * its upstream media rights are unresolved. A locally supplied, licensed copy
 * can be placed in the app's files directory or in the debug-only
 * `local-assets/` source directory used by the Gradle build.
 */
object AppBackground {
    private const val MAX_DIMENSION = 2560

    private var loaded = false

    @Volatile
    var bitmap: Bitmap? = null
        private set

    fun load(context: Context): Bitmap? {
        if (loaded) return bitmap
        synchronized(this) {
            if (loaded) return bitmap
            loaded = true
            val candidates = listOf(
                context.filesDir.resolve("wallpaper.jpg"),
                context.filesDir.resolve("wallpaper.png"),
                context.filesDir.resolve("roadster.jpg"),
                context.filesDir.resolve("roadster.png"),
            )
            for (file in candidates) {
                if (file.exists()) {
                    bitmap = decodeFile(file.absolutePath)
                    if (bitmap != null) return bitmap
                }
            }
            for (asset in listOf("wallpaper.jpg", "wallpaper.png", "roadster.jpg", "roadster.png")) {
                try {
                    context.assets.open(asset).use { stream ->
                        bitmap = decodeStream(stream)
                    }
                    if (bitmap != null) return bitmap
                } catch (_: Exception) {
                    // Optional asset.
                }
            }
            return null
        }
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
        // Assets cannot be re-opened from a consumed stream, so decode them in
        // one pass; the app's bundled copy is already sized for the device.
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
