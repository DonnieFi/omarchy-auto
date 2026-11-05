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
    private var allControllers: List<MediaController> = emptyList()
    private var preferredPackage: String? = null
    private var started = false
    private var wanted = false
    private val labelCache = mutableMapOf<String, String>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            if (!wanted) return@OnActiveSessionsChangedListener
            val list = controllers.orEmpty()
            allControllers = list
            publishSessions(list)
            bind(choose(list))
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (wanted) publish()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (!wanted) return
            publish()
            scheduleTicker()
        }

        override fun onSessionDestroyed() {
            if (wanted) bindActive()
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            if (!wanted) return
            publish()
            if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    fun start() {
        if (wanted || started || manager == null) return
        wanted = true
        started = true
        instance = this
        try {
            manager.addOnActiveSessionsChangedListener(
                sessionsListener,
                listenerComponent,
                handler,
            )
            bindActive()
        } catch (_: SecurityException) {
            started = false
            wanted = false
            LiveDataStore.setMediaSessions(emptyList())
            onPermissionDenied()
        } catch (_: Exception) {
            started = false
            wanted = false
            LiveDataStore.setMediaSessions(emptyList())
            onPermissionDenied()
        }
    }

    fun stop() {
        if (!started && !wanted) return
        if (instance === this) instance = null
        wanted = false
        started = false
        handler.removeCallbacks(progressTicker)
        val previous = controller
        controller?.unregisterCallback(controllerCallback)
        controller = null
        allControllers = emptyList()
        LiveDataStore.setMediaSessions(emptyList())
        MediaTransport.detach(previous)
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
            // The service can be torn down around us.
        }
    }

    private fun bindActive() {
        if (!wanted) return
        try {
            val list = manager?.getActiveSessions(listenerComponent).orEmpty()
            allControllers = list
            publishSessions(list)
            bind(choose(list))
        } catch (_: SecurityException) {
            started = false
            wanted = false
            LiveDataStore.setMediaSessions(emptyList())
            onPermissionDenied()
        } catch (_: Exception) {
            started = false
            wanted = false
            LiveDataStore.setMediaSessions(emptyList())
            onPermissionDenied()
        }
    }

    private fun publishSessions(controllers: List<MediaController>) {
        val items = controllers
            .distinctBy { it.packageName }
            .map { c ->
                val title = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                MediaSessionItem(
                    packageName = c.packageName,
                    label = appLabel(c.packageName).uppercase(),
                    title = title,
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                )
            }
        LiveDataStore.setMediaSessions(items)
    }

    private fun choose(controllers: List<MediaController>): MediaController? {
        val preferred = preferredPackage?.let { pkg ->
            controllers.firstOrNull { it.packageName == pkg }
        }
        if (preferred != null) return preferred
        val withTitle = controllers.filter {
            !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        }
        return withTitle.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: withTitle.firstOrNull() ?: controllers.firstOrNull()
    }

    private fun bind(next: MediaController?) {
        if (!wanted) return
        handler.removeCallbacks(progressTicker)
        MediaTransport.detach(controller)
        controller?.unregisterCallback(controllerCallback)
        controller = next
        MediaTransport.attach(next)
        if (next == null) {
            onUpdate(null)
            return
        }
        next.registerCallback(controllerCallback, handler)
        publish()
        scheduleTicker()
    }

    private fun scheduleTicker() {
        handler.removeCallbacks(progressTicker)
        if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) {
            handler.postDelayed(progressTicker, 1000L)
        }
    }

    private fun publish() {
        if (!wanted) return
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
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            .orEmpty()
            .ifBlank { appLabel(current.packageName) }

        publishSessions(allControllers)
        onUpdate(
            MediaSnapshot(
                title = title,
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                playing = playback?.state == PlaybackState.STATE_PLAYING,
                positionMs = position,
                durationMs = duration,
                source = appLabel(current.packageName),
                packageName = current.packageName,
                art = art,
            ),
        )
    }

    private fun selectPackageInternal(packageName: String) {
        handler.post {
            preferredPackage = packageName
            bindActive()
            val selected = controller
            if (selected != null && selected.packageName == packageName &&
                selected.playbackState?.state != PlaybackState.STATE_PLAYING
            ) {
                try {
                    selected.transportControls.play()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
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

    companion object {
        @Volatile
        private var instance: MediaWatcher? = null

        fun selectSession(packageName: String) {
            instance?.selectPackageInternal(packageName)
        }
    }
}
