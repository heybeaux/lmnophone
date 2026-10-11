// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.device.SerialDispatcher

class UserUnlockedReapplyTest {
    @Test fun unlockAfterLockedPassRunsFullPassWithoutBootCompleted() = fixture {
        dispatcher.run { trigger.recordPass(locked = true) }
        unlocked = true
        trigger.request()
        drain()
        assertEquals(1, fullPasses)
    }

    @Test fun homeAfterMissedUnlockSignalRunsWhenNoPassExists() = fixture {
        unlocked = true
        trigger.request()
        drain()
        assertEquals(1, fullPasses)
    }

    @Test fun staleSignalWhileLockedDoesNotRunFullPass() = fixture {
        trigger.request()
        drain()
        assertEquals(0, fullPasses)
    }

    @Test fun queuedBootPassSatisfiesLaterUnlockAndResumeSignals() = fixture {
        unlocked = true
        dispatcher.post { trigger.recordPass(locked = false) }
        repeat(3) { trigger.request() }
        drain()
        assertEquals(0, fullPasses)
    }

    @Test fun repeatedUnlockAndHomeSignalsRunOnlyOneFullPass() = fixture {
        unlocked = true
        repeat(20) { trigger.request() }
        drain()
        assertEquals(1, fullPasses)
    }

    @Test fun failedUnlockedPassDoesNotLoopOnHomeResume() = fixture {
        // The runtime records an attempted full pass even if its result was not ready.
        dispatcher.run { trigger.recordPass(locked = false) }
        unlocked = true
        repeat(3) { trigger.request() }
        drain()
        assertEquals(0, fullPasses)
    }

    @Test fun unlockWaitsForActiveLockedPassAndReadsStateOnDispatcher() = fixture {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        dispatcher.post {
            entered.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            trigger.recordPass(locked = true)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            trigger.request()
            assertEquals(0, fullPasses)
            unlocked = true
        } finally {
            finish.countDown()
        }
        drain()
        assertEquals(1, fullPasses)
    }

    @Test fun completionRunsEvenWhenThereIsNoNewPass() = fixture {
        var completed = false
        trigger.request { completed = true }
        drain()
        assertTrue(completed)
    }

    private class Fixture(val dispatcher: SerialDispatcher) {
        @Volatile var unlocked = false
        var fullPasses = 0
        val trigger = UserUnlockedReapply(dispatcher, {
            check(dispatcher.onDispatcher)
            unlocked
        }, ::fullPass)

        private fun fullPass() {
            check(dispatcher.onDispatcher)
            fullPasses++
            trigger.recordPass(locked = false)
        }

        fun drain() = dispatcher.run { Unit }
    }

    private fun fixture(test: Fixture.() -> Unit) {
        SerialDispatcher("unlock-test").use { Fixture(it).test() }
    }
}
