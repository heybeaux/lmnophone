// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

/**
 * The practice screen's lifetime, for the host. The host's call and screen-off listeners are armed
 * while the screen shows and the activity is started. Stopping the activity stops all audio and
 * disarms them, but keeps the screen and its draft: starting again re-arms them without reopening,
 * so a person who comes back to the same screen is still guarded (PLA-465 finding 1).
 * Main thread only.
 */
class VoicePracticeSession(
    val controller: VoicePracticeController,
    private val arm: () -> Unit,
    private val disarm: () -> Unit,
) {
    /** The practice route is on screen. */
    var showing = false
        private set
    var armed = false
        private set
    private var activityStopped = false

    /** The route appeared; [place] when it is coming back after a configuration change (PLA-479). */
    fun show(place: VoicePlace? = null) {
        if (showing) return
        showing = true
        setArmed(!activityStopped)
        controller.open(place)
    }

    /** Where the screen is, for a configuration change; null when the route isn't showing. */
    fun place(): VoicePlace? = if (showing) controller.place() else null

    /** The route went away (Back, Home key). Recording stops and is kept. */
    fun hide() {
        controller.close(Interruption.AppStopped)
        showing = false
        setArmed(false)
    }

    /** Not visible any more: stop audio (a recording is kept) and the listeners; the screen stays. */
    fun activityStopped() {
        activityStopped = true
        controller.close(Interruption.AppStopped)
        setArmed(false)
    }

    /** Visible again: guard the screen that is still showing. */
    fun activityStarted() {
        activityStopped = false
        setArmed(showing)
    }

    private fun setArmed(on: Boolean) {
        if (on == armed) return
        armed = on
        if (on) arm() else disarm()
    }
}
