// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import org.junit.Assert.*
import org.junit.Test

class CountControllerTest {
    private class Store : CountPreferenceStore {
        val values = mutableMapOf<String, Boolean>()
        var interrupt = false
        override fun read(key: String) = values[key]
        override fun write(key: String, value: Boolean) {
            if (interrupt) throw IllegalStateException("interrupted atomic write")
            values[key] = value
        }
    }
    private class Scheduler : CountWork {
        data class Job(val at: Long, val block: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        val jobs = mutableListOf<Job>()
        override fun schedule(delayMillis: Long, block: () -> Unit): CountCancellation {
            val job = Job(now + delayMillis, block); jobs += job
            return CountCancellation { job.cancelled = true }
        }
        fun advance(to: Long) {
            while (true) {
                val job = jobs.filter { !it.cancelled && it.at <= to }.minByOrNull { it.at } ?: break
                jobs.remove(job); now = job.at; job.block()
            }
            now = to
        }
    }
    private class Fixture(val store: Store = Store()) {
        val time = Scheduler()
        data class Read(val kind: CountKind, val done: (Int?) -> Unit, var cancelled: Boolean = false)
        val reads = mutableListOf<Read>()
        val callbacks = mutableListOf<() -> Unit>()
        val observed = mutableListOf<Pair<String, Boolean>>()
        var unregistered = 0
        var grants = 0
        val access = CountKind.entries.associateWith {
            CountAccess(true, true, it.supportedPackage, true, it.supportedPackage)
        }.toMutableMap()
        val c = CountController(CountPreferences(store), time, CountReader { kind, done ->
            val r = Read(kind, done); reads += r; CountCancellation { r.cancelled = true }
        }, { access.getValue(it) }, { time.now }, { uri, descendants, cb ->
            observed += uri to descendants; callbacks += cb; CountCancellation { unregistered++ }
        }, { grants++ })
        fun start() { c.setVisible(true); c.setEnabled(true); time.advance(time.now) }
        fun finish(calls: Int? = 1, messages: Int? = 2) {
            reads.takeLast(2).forEach { it.done(if (it.kind == CountKind.CALLS) calls else messages) }
        }
        fun notifyChange() = callbacks.last().invoke()
    }
    @Test fun registersBeforeReadAndStartsEveryVisibleIntervalEmpty() {
        val f = Fixture(); f.start()
        assertEquals(listOf("content://call_log/calls" to true, "content://mms-sms/" to true), f.observed)
        assertEquals(CommunicationCounts(), f.c.counts)
        f.finish(); f.c.setVisible(false)
        assertEquals(2, f.unregistered); assertEquals(CommunicationCounts(), f.c.counts)
        f.c.setVisible(true); assertEquals(CommunicationCounts(), f.c.counts)
        f.time.advance(0); f.finish(3, 4); assertEquals(CommunicationCounts(3, 4), f.c.counts)
    }
    @Test fun callbackDuringReadAndCompletionAfterNewerGenerationCannotPublish() {
        val f = Fixture(); f.start(); val old = f.reads.toList()
        f.notifyChange(); old.forEach { it.done(12) }
        assertEquals(CommunicationCounts(), f.c.counts)
        f.time.advance(150); f.finish(5, 7)
        old.forEach { it.done(12) }; assertEquals(CommunicationCounts(5, 7), f.c.counts)
    }
    @Test fun firstCallbackDeadlineHidesStalledRefreshAtOneSecond() {
        val f = Fixture(); f.start(); f.finish(); f.time.advance(20); f.notifyChange()
        f.time.advance(1019); assertEquals(CommunicationCounts(1, 2), f.c.counts)
        f.time.advance(1020); assertEquals(CommunicationCounts(), f.c.counts)
        f.finish(3, 4); assertEquals(CommunicationCounts(3, 4), f.c.counts)
    }
    @Test fun steadyCallbacksCannotMoveFirstDeadline() {
        val f = Fixture(); f.start(); f.finish(); f.notifyChange()
        for (t in 100L..1000L step 100) { f.time.advance(t); f.notifyChange() }
        assertEquals(CommunicationCounts(), f.c.counts)
        assertEquals(2, f.reads.size)
        f.time.advance(1150); f.finish(3, 4); assertEquals(CommunicationCounts(3, 4), f.c.counts)
    }
    @Test fun lateOrMissingCallbackCorrectsOrHidesByThirteenSeconds() {
        for (lateCallback in listOf(false, true)) for (stalled in listOf(false, true)) {
            val f = Fixture(); f.start(); f.finish()
            if (lateCallback) { f.time.advance(1500); f.notifyChange() }
            f.time.advance(10000)
            if (!stalled) f.finish(5, 7)
            f.time.advance(13000)
            assertEquals(if (stalled) CommunicationCounts() else CommunicationCounts(5, 7), f.c.counts)
        }
    }
    @Test fun ageStartsAtQueryStartAndIndependentTimerExpiresOldNumber() {
        val f = Fixture(); f.start(); f.time.advance(2900); f.finish()
        f.time.advance(12999); assertEquals(CommunicationCounts(1, 2), f.c.counts)
        f.time.advance(13000); assertEquals(CommunicationCounts(), f.c.counts)
    }
    @Test fun hungQueryTimesOutIndependentlyAndLateCompletionIsDiscarded() {
        val f = Fixture(); f.start(); val old = f.reads.toList()
        old.first().done(5); f.time.advance(3000)
        assertTrue(old.last().cancelled); assertEquals(CommunicationCounts(5, null), f.c.counts)
        old.last().done(12); assertEquals(CommunicationCounts(5, null), f.c.counts)
        f.notifyChange(); f.time.advance(3150); f.finish(5, 7)
        assertEquals(CommunicationCounts(5, 7), f.c.counts)
    }
    @Test fun offOnClearsCancelsAndCannotResurrectOldReads() {
        val f = Fixture(); f.start(); val old = f.reads.toList()
        f.c.setEnabled(false); assertTrue(old.all { it.cancelled })
        f.c.setEnabled(true); f.time.advance(0); f.finish(5, 7)
        old.forEach { it.done(12) }; assertEquals(CommunicationCounts(5, 7), f.c.counts)
        assertEquals(2, f.grants)
    }
    @Test fun everyPermissionUnlockAndSupportedAppGateIsRecheckedAtPublication() {
        val changes: List<(CountAccess) -> CountAccess> = listOf(
            { it.copy(permissionGranted = false) }, { it.copy(unlocked = false) },
            { it.copy(rolePackage = "other") }, { it.copy(rolePackage = null) },
            { it.copy(installedAndEnabled = false) }, { it.copy(homePackage = "other") })
        for (change in changes) for (kind in CountKind.entries) {
            val f = Fixture(); f.start()
            f.access[kind] = change(f.access.getValue(kind)); f.finish()
            assertNull(if (kind == CountKind.CALLS) f.c.counts.missedCalls else f.c.counts.unreadTexts)
        }
    }
    @Test fun unsupportedAppDoesNotReadAndSettingsExplainsIt() {
        val f = Fixture(); f.access[CountKind.MESSAGES] = f.access.getValue(CountKind.MESSAGES).copy(rolePackage = null)
        f.start(); assertEquals(listOf(CountKind.CALLS), f.reads.map { it.kind })
        assertTrue(f.c.unsupportedApps)
    }
    @Test fun packageReplacementInvalidatesInflightReadEvenIfSameHolder() {
        val f = Fixture(); f.start(); val old = f.reads.toList()
        f.c.externalChange(); old.forEach { it.done(12) }
        assertEquals(CommunicationCounts(), f.c.counts)
        f.time.advance(150); f.finish(5, 7); assertEquals(CommunicationCounts(5, 7), f.c.counts)
    }
    @Test fun stopResumeRejectsPriorInterval() {
        val f = Fixture(); f.start(); val old = f.reads.toList()
        f.c.setVisible(false); f.c.setVisible(true); f.time.advance(0)
        old.forEach { it.done(12) }; assertEquals(CommunicationCounts(), f.c.counts)
        f.finish(); assertEquals(CommunicationCounts(1, 2), f.c.counts)
    }
    @Test fun restartAndUpdatePersistOnlySwitchAndDoNotGrantOnResume() {
        for (enabled in listOf(false, true)) {
            val store = Store(); CountPreferences(store).enabled = enabled
            val f = Fixture(store); f.c.setVisible(true); f.time.advance(0)
            if (enabled) f.finish()
            f.c.close()
            val updated = Fixture(store); assertEquals(enabled, updated.c.enabled)
            assertEquals(CommunicationCounts(), updated.c.counts)
            updated.c.setVisible(true); updated.time.advance(0); assertEquals(0, updated.grants)
            assertEquals(mapOf("show_communication_counts" to enabled), store.values)
        }
    }
    @Test fun interruptedPreferenceWriteKeepsLastDurableChoiceAndStoresNoCounts() {
        val store = Store(); CountPreferences(store).enabled = true; val f = Fixture(store)
        store.interrupt = true
        runCatching { f.c.setEnabled(false) }
        assertTrue(Fixture(store).c.enabled)
        assertEquals(mapOf("show_communication_counts" to true), store.values)
    }
    @Test fun singleStatementAlternatingFiveSevenScheduleNeverPublishesTwelve() {
        val f = Fixture(); f.start()
        for (value in listOf(5, 7, 5, 7)) {
            f.reads.last { it.kind == CountKind.MESSAGES }.done(value)
            assertEquals(value, f.c.counts.unreadTexts); assertNotEquals(12, f.c.counts.unreadTexts)
            f.notifyChange(); f.time.advance(f.time.now + 150)
        }
    }
}
