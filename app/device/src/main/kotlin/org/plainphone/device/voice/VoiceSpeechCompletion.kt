// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

/** Main-thread, one-shot handoff. Late speech callbacks cannot navigate after another action. */
internal class VoiceSpeechCompletion {
    private var generation = 0L
    private var action: (() -> Unit)? = null
    fun begin(next: (() -> Unit)?): String {
        cancel()
        action = next
        return generation.toString()
    }
    fun cancel() { generation++; action = null }
    fun finish(id: String?) {
        if (id != generation.toString()) return
        val next = action.also { action = null }
        next?.invoke()
    }
}
