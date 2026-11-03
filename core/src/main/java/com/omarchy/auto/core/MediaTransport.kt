package com.omarchy.auto.core

import android.media.session.MediaController
import android.media.session.PlaybackState
import com.omarchy.auto.core.diag.OaLog

/** Small command bridge for the stage play/pause control. */
object MediaTransport {
    private const val TAG = "MediaTransport"

    @Volatile
    private var controller: MediaController? = null

    internal fun attach(value: MediaController?) {
        controller = value
    }

    internal fun detach(value: MediaController?) {
        if (controller === value) controller = null
    }

    fun toggle() = withControls { controls ->
        val state = controller?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    }

    fun next() = withControls { it.skipToNext() }

    fun previous() = withControls { it.skipToPrevious() }

    private inline fun withControls(block: (MediaController.TransportControls) -> Unit) {
        val current = controller ?: return
        try {
            block(current.transportControls)
        } catch (error: Exception) {
            OaLog.w(TAG, "Media command failed", error)
        }
    }
}
