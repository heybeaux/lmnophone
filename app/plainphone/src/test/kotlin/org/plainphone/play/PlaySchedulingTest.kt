// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.session.PlaySessionView
import org.plainphone.policy.SessionEnd

class PlaySchedulingTest {
    @Test fun screenOffClosesBeforeQueuedPulseWithoutScreenOn() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val work = PlayWorkQueue({ queue.addLast(it) }, { events += "tick" }, { events += "end:$it" })
        work.tick { events += "pulse complete" }
        work.end(SessionEnd.SCREEN_OFF) { events += "receiver complete" }
        assertEquals(1, queue.size)
        queue.removeFirst().invoke()
        assertEquals(listOf("end:SCREEN_OFF", "receiver complete", "pulse complete"), events)
        assertTrue(queue.isEmpty())
    }
    @Test fun closeArrivingDuringTickRunsAfterItWithoutConcurrentPolicyWrites() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        lateinit var work: PlayWorkQueue
        work = PlayWorkQueue({ queue.addLast(it) }, {
            events += "tick start"
            work.end(SessionEnd.SCREEN_OFF) { events += "finished" }
            events += "tick end"
        }, { events += "end" })
        work.tick {}
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("tick start", "tick end", "end", "finished"), events)
    }
    @Test fun duplicatePulsesCoalesceAndAllCallbacksComplete() {
        val queue = ArrayDeque<() -> Unit>()
        var ticks = 0; var complete = 0
        val work = PlayWorkQueue({ queue.addLast(it) }, { ticks++ }, {})
        repeat(5) { work.tick { complete++ } }
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(1, ticks); assertEquals(5, complete)
    }
    @Test fun pulseOnlyRunsForCountdownReadyOrLiveGrantAndStopsAfterCompletion() {
        val callbacks = ArrayDeque<() -> Unit>()
        val delayed = mutableListOf<Runnable>()
        val pulse = PlayPulse({ delayed += it }, { delayed -= it }, { callbacks.addLast(it) })
        pulse.update(PlaySessionView(handbackReady = true))
        assertTrue(delayed.isEmpty())
        for (view in listOf(PlaySessionView(countdownMillis = 1000), PlaySessionView(readyMillis = 1000),
            PlaySessionView(active = true, accessMillis = 1000), PlaySessionView(approvalAvailable = true))) {
            pulse.update(view); pulse.update(view)
            assertEquals(1, delayed.size)
            delayed.removeAt(0).run()
            pulse.update(view)
            assertTrue(delayed.isEmpty())
            pulse.update(PlaySessionView())
            callbacks.removeFirst().invoke()
            assertTrue(delayed.isEmpty())
        }
    }
    @Test fun cancellationRemovesPendingPulseAndAStaleRunnableDoesNothing() {
        val delayed = mutableListOf<Runnable>(); var ticks = 0
        val pulse = PlayPulse({ delayed += it }, { delayed -= it }, { ticks++ })
        pulse.update(PlaySessionView(readyMillis = 1000))
        val stale = delayed.single()
        pulse.update(PlaySessionView())
        assertTrue(delayed.isEmpty())
        stale.run()
        assertEquals(0, ticks)
    }
}
