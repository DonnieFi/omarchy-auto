package com.omarchy.auto.core

import android.media.session.MediaController
import android.media.session.PlaybackState
import com.omarchy.auto.core.diag.OaLog

/** Small command bridge for the stage play/pause control. */
object MediaTransport {
    private const val TAG = "MediaTransport"

    /**
     * Test hook. When set, transport commands are delivered here and the
     * platform session is not touched.
     */
    @Volatile
    var commandSinkForTest: ((String) -> Unit)? = null

    @Volatile
    private var controller: MediaController? = null

    internal fun attach(value: MediaController?) {
        controller = value
    }

    internal fun detach(value: MediaController?) {
        if (controller === value) controller = null
    }

    fun toggle() {
        if (deliverForTest("toggle")) return
        withControls { controls ->
            val state = controller?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
        }
    }

    fun next() {
        if (deliverForTest("next")) return
        withControls { it.skipToNext() }
    }

    fun previous() {
        if (deliverForTest("previous")) return
        withControls { it.skipToPrevious() }
    }

    private fun deliverForTest(command: String): Boolean {
        val sink = commandSinkForTest ?: return false
        sink(command)
        return true
    }

    private inline fun withControls(block: (MediaController.TransportControls) -> Unit) {
        val current = controller ?: return
        try {
            block(current.transportControls)
        } catch (error: Exception) {
            OaLog.w(TAG, "Media command failed", error)
        }
    }
}
