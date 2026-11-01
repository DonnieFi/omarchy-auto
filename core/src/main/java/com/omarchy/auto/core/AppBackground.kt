package com.omarchy.auto.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Optional local wallpaper. The Tokyo Roadster artwork is not committed because
 * its upstream media rights are unresolved. A locally supplied, licensed copy
 * can be placed in the app's files directory or in the debug-only
 * `local-assets/` source directory used by the Gradle build.
 */
object AppBackground {
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
                    bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) return bitmap
                }
            }
            for (asset in listOf("wallpaper.jpg", "wallpaper.png", "roadster.jpg", "roadster.png")) {
                try {
                    context.assets.open(asset).use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream)
                    }
                    if (bitmap != null) return bitmap
                } catch (_: Exception) {
                    // Optional asset.
                }
            }
            return null
        }
    }
}
