package com.omarchy.auto.core

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Watches the phone's active MediaSession. MediaSessionManager requires the
 * notification-listener grant on modern Android, so the Media tile is honest
 * when access is missing.
 */
class MediaWatcher(
    context: Context,
    private val onUpdate: (MediaSnapshot?) -> Unit,
    private val onPermissionDenied: () -> Unit,
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = OmarchyNotificationListener.component(app)

    private var controller: MediaController? = null
    private var started = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(choose(controllers.orEmpty()))
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            bindActive()
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            publish()
            if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    fun start() {
        if (started || manager == null) return
        started = true
        try {
            manager.addOnActiveSessionsChangedListener(
                sessionsListener,
                listenerComponent,
                handler,
            )
            bindActive()
        } catch (_: SecurityException) {
            started = false
            onPermissionDenied()
        } catch (_: Exception) {
            started = false
            onPermissionDenied()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(progressTicker)
        controller?.unregisterCallback(controllerCallback)
        controller = null
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
            // The service can be torn down around us.
        }
    }

    private fun bindActive() {
        try {
            bind(choose(manager?.getActiveSessions(listenerComponent).orEmpty()))
        } catch (_: SecurityException) {
            onPermissionDenied()
        } catch (_: Exception) {
            onPermissionDenied()
        }
    }

    private fun choose(controllers: List<MediaController>): MediaController? {
        val withTitle = controllers.filter {
            !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        }
        return withTitle.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: withTitle.firstOrNull()
    }

    private fun bind(next: MediaController?) {
        handler.removeCallbacks(progressTicker)
        controller?.unregisterCallback(controllerCallback)
        controller = next
        if (next == null) {
            onUpdate(null)
            return
        }
        next.registerCallback(controllerCallback, handler)
        publish()
        if (next.playbackState?.state == PlaybackState.STATE_PLAYING) {
            handler.postDelayed(progressTicker, 1000L)
        }
    }

    private fun publish() {
        val current = controller ?: run {
            onUpdate(null)
            return
        }
        val metadata = current.metadata
        val playback = current.playbackState
        val position = playback?.let {
            var value = it.position
            if (it.state == PlaybackState.STATE_PLAYING && it.lastPositionUpdateTime > 0L) {
                value += SystemClock.elapsedRealtime() - it.lastPositionUpdateTime
            }
            value.coerceAtLeast(0L)
        } ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) {
            onUpdate(null)
            return
        }

        onUpdate(
            MediaSnapshot(
                title = title,
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                playing = playback?.state == PlaybackState.STATE_PLAYING,
                positionMs = position,
                durationMs = duration,
                source = appLabel(current.packageName),
                art = art,
            ),
        )
    }

    private fun appLabel(pkg: String): String = try {
        val pm = app.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(
                pkg,
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }
}
