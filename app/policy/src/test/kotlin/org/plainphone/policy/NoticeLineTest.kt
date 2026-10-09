// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.Mode
import kotlin.test.*

class NoticeLineTest {
    private val waiting = WaitingNotice("request", WaitingChangeKind.TURN_OFF, WaitingPhase.PENDING, 86_400_000)
    private val elevation = ElevationNotice("grant", 120_000)
    private val monthly = MonthlyNotice(true, MonthlyNoticeKind.SUPERVISED_OVERDUE)

    @Test fun `first look has no notice or cancellation without applicable state`() {
        val result = NoticeLine.decide(NoticeInput(Mode.TRUSTED))
        assertNull(result.line)
        assertNull(result.attentionChange)
        assertNull(result.settingsChange)
    }

    @Test fun `all combinations show at most one notice in the approved priority order`() {
        for (attention in listOf(false, true)) for (pending in listOf(false, true))
            for (elevating in listOf(false, true)) for (overdue in listOf(false, true)) {
                val result = NoticeLine.decide(NoticeInput(Mode.SELF_MANAGED, attention,
                    waiting.takeIf { pending }, elevation.takeIf { elevating }, monthly.takeIf { overdue }))
                val expected = when {
                    attention -> HomeNoticeKind.NEEDS_ATTENTION
                    pending -> HomeNoticeKind.TURN_OFF_WAITING
                    elevating -> HomeNoticeKind.ELEVATION_COUNTDOWN
                    overdue -> HomeNoticeKind.MONTHLY_SELF_MANAGED
                    else -> null
                }
                assertEquals(expected, result.line?.kind)
            }
    }

    @Test fun `attention masks waiting line but keeps cancellation on attention and settings screens`() {
        val result = NoticeLine.decide(NoticeInput(Mode.TRUSTED, true, waiting, monthly = monthly))
        assertEquals(HomeNoticeKind.NEEDS_ATTENTION, result.line!!.kind)
        assertEquals(NoticeTap.OPEN_ATTENTION, result.line.tap)
        for (details in listOf(result.attentionChange, result.settingsChange)) {
            assertEquals(waiting, details!!.waiting)
            assertEquals(NoticeTap.ConfirmCancel("request"), details.action)
        }
    }

    @Test fun `waiting kinds map to turnoff people or setup without product copy`() {
        for (kind in WaitingChangeKind.entries) {
            val current = waiting.copy(kind = kind)
            val result = NoticeLine.decide(NoticeInput(Mode.TRUSTED, waiting = current, monthly = monthly))
            val expected = when (kind) {
                WaitingChangeKind.TURN_OFF -> HomeNoticeKind.TURN_OFF_WAITING
                WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON -> HomeNoticeKind.PEOPLE_WAITING
                else -> HomeNoticeKind.SETUP_WAITING
            }
            assertEquals(expected, result.line!!.kind)
            assertEquals(86_400_000L, result.line.remainingMillis)
            assertEquals(NoticeTap.ConfirmCancel("request"), result.line.tap)
        }
    }

    @Test fun `cancel confirmation is PIN free id bound and only requests durable cancellation`() {
        assertEquals(NoticeTap.RequestCancellation("request"), NoticeLine.confirmCancel("request", waiting))
        assertEquals(NoticeTap.NONE, NoticeLine.confirmCancel("stale", waiting))
        assertEquals(NoticeTap.NONE, NoticeLine.confirmCancel("request", null))
        for (phase in listOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)) {
            val current = waiting.copy(phase = phase)
            assertEquals(NoticeTap.AlreadyInProgress("request"), NoticeLine.confirmCancel("request", current))
            val selected = NoticeLine.decide(NoticeInput(Mode.TRUSTED, waiting = current))
            assertNull(selected.line)
            assertEquals(NoticeTap.AlreadyInProgress("request"), selected.settingsChange!!.action)
        }
        for (phase in listOf(WaitingPhase.COMPLETED, WaitingPhase.CANCELLED)) {
            val current = waiting.copy(phase = phase)
            assertEquals(NoticeTap.NONE, NoticeLine.confirmCancel("request", current))
            assertNull(NoticeLine.decide(NoticeInput(Mode.TRUSTED, waiting = current)).settingsChange)
        }
    }

    @Test fun `elevation countdown and ready only appear in self managed mode and never start automatically`() {
        assertNull(NoticeLine.decide(NoticeInput(Mode.TRUSTED, elevation = elevation)).line)
        val counting = NoticeLine.decide(NoticeInput(Mode.SELF_MANAGED, elevation = elevation)).line!!
        assertEquals(HomeNoticeKind.ELEVATION_COUNTDOWN, counting.kind)
        assertEquals(120_000L, counting.remainingMillis)
        assertEquals(NoticeTap.OpenElevation("grant"), counting.tap)
        val ready = NoticeLine.decide(NoticeInput(Mode.SELF_MANAGED, elevation = ElevationNotice("grant", 0))).line!!
        assertEquals(HomeNoticeKind.ELEVATION_READY, ready.kind)
        assertEquals(NoticeTap.ConfirmElevationStart("grant"), ready.tap)
    }

    @Test fun `monthly tap explains and mode determines typed kind with no Play entry`() {
        for (mode in Mode.entries) {
            val result = NoticeLine.decide(NoticeInput(mode, monthly = monthly)).line!!
            assertEquals(if (mode == Mode.TRUSTED) HomeNoticeKind.MONTHLY_SUPERVISED else HomeNoticeKind.MONTHLY_SELF_MANAGED, result.kind)
            assertEquals(NoticeTap.EXPLAIN_MONTHLY, result.tap)
            assertNull(result.remainingMillis)
        }
        assertNull(NoticeLine.decide(NoticeInput(Mode.TRUSTED, monthly = MonthlyNotice(false, MonthlyNoticeKind.SUPERVISED_OVERDUE))).line)
    }

    @Test fun `invalid identifiers and negative remaining time are refused`() {
        assertFailsWith<IllegalArgumentException> { waiting.copy(requestId = "") }
        assertFailsWith<IllegalArgumentException> { waiting.copy(remainingMillis = -1) }
        assertFailsWith<IllegalArgumentException> { ElevationNotice("", 0) }
        assertFailsWith<IllegalArgumentException> { ElevationNotice("grant", -1) }
    }
}
