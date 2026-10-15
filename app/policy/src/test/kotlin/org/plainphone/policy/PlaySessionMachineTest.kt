// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class PlaySessionMachineTest {
    private val play = PackageName("com.android.vending")
    private val app = PackageName("test.installed")
    private val other = PackageName("test.other")
    private val signer = CertDigest("ab".repeat(32))
    private val foreign = CertDigest("cd".repeat(32))
    private val identity = ApprovalBinding(app, setOf(signer))
    private val start = BootTime("boot", 10_000)
    private val access = SessionAccess("session", WindowKind.PLAY, setOf(play), BootTime("boot", 610_000))
    private fun begun() = assertNotNull(PlaySessionMachine.begin(access, start))

    @Test fun `approval identity requires exact package and complete nonempty current signer set`() {
        assertFailsWith<IllegalArgumentException> { ApprovalBinding(app, emptySet()) }
        assertTrue(identity.matches(app, setOf(signer)))
        assertFalse(identity.matches(other, setOf(signer)))
        assertFalse(identity.matches(app, setOf(foreign)))
        assertFalse(identity.matches(app, setOf(signer, foreign)))
        assertFalse(identity.matches(app, emptySet()))
    }

    @Test fun `approval bindings snapshot signer sets and compare by value`() {
        val input = mutableSetOf(signer)
        val bound = ApprovalBinding(app, input)
        input.clear()
        assertEquals(identity, bound)
        assertEquals(identity.hashCode(), bound.hashCode())
        assertFailsWith<UnsupportedOperationException> { (bound.signingCerts as MutableSet).clear() }
    }

    @Test fun `begin requires a live exact Play session with no excessive remaining duration`() {
        assertNull(PlaySessionMachine.begin(null, start))
        assertNull(PlaySessionMachine.begin(SessionAccess("x", WindowKind.SETTINGS, setOf(play), access.deadline), start))
        assertNull(PlaySessionMachine.begin(SessionAccess("x", WindowKind.PLAY, setOf(play, app), access.deadline), start))
        assertNull(PlaySessionMachine.begin(access, access.deadline))
        assertNull(PlaySessionMachine.begin(access, BootTime("reboot", 0)))
        assertNull(PlaySessionMachine.begin(SessionAccess("x", WindowKind.PLAY, setOf(play), BootTime("boot", 610_001)), start))
    }

    @Test fun `package event records actual installed identity and never grants approval`() {
        val before = begun()
        val result = PlaySessionMachine.installed(before, "session", identity, start, access)
        assertTrue(result.accepted)
        assertTrue(before.candidates.isEmpty())
        assertEquals(mapOf(app to identity), result.state.candidates)
        assertEquals(identity, PlaySessionMachine.candidate(result.state, app))
        // A candidate is data only: it has no FreshAuthority or approved-policy mutation.
    }

    @Test fun `misleading request names cannot choose another installed package`() {
        val state = PlaySessionMachine.installed(begun(), "session", identity, start, access).state
        assertNull(PlaySessionMachine.candidate(state, other))
        assertFalse(PlaySessionMachine.candidate(state, app)!!.matches(other, setOf(signer)))
    }

    @Test fun `all session end triggers ignore later installs while retaining unapproved candidates`() {
        for (reason in SessionEnd.entries) {
            val state = PlaySessionMachine.installed(begun(), "session", identity, start, access).state
            val ended = PlaySessionMachine.end(state, "session", reason)
            assertEquals(PlayEndReason.SessionEnded(reason), ended.endedBecause)
            val late = PlaySessionMachine.installed(ended, "session", ApprovalBinding(other, setOf(signer)), start, access)
            assertFalse(late.accepted)
            assertSame(ended, late.state)
            assertEquals(mapOf(app to identity), late.state.candidates)
        }
    }

    @Test fun `deadline reboot and backwards elapsed readings close event intake`() {
        for (now in listOf(access.deadline, BootTime("other-boot", 0), BootTime("boot", start.elapsedMillis - 1))) {
            val result = PlaySessionMachine.installed(begun(), "session", identity, now, access)
            assertFalse(result.accepted)
            assertNotNull(result.state.endedBecause)
            assertTrue(result.state.candidates.isEmpty())
        }
    }

    @Test fun `revoked or replaced session access closes intake immediately`() {
        for (current in listOf(null, SessionAccess("new-session", WindowKind.PLAY, setOf(play), access.deadline),
            SessionAccess("session", WindowKind.PLAY, setOf(play), BootTime("boot", 620_000)))) {
            val result = PlaySessionMachine.installed(begun(), "session", identity, start, current)
            assertFalse(result.accepted)
            assertNotNull(result.state.endedBecause)
        }
    }

    @Test fun `stale session callbacks neither add candidates nor close the new session`() {
        val state = begun()
        val result = PlaySessionMachine.installed(state, "old-session", identity, start, access)
        assertFalse(result.accepted)
        assertSame(state, result.state)
        assertSame(state, PlaySessionMachine.end(state, "old-session", SessionEnd.DONE))
    }

    @Test fun `duplicate package event is idempotent but replacement invalidates earlier binding`() {
        val state = PlaySessionMachine.installed(begun(), "session", identity, start, access).state
        val duplicate = PlaySessionMachine.installed(state, "session", ApprovalBinding(app, setOf(signer)), start, access)
        assertSame(state, duplicate.state)
        val changed = PlaySessionMachine.installed(state, "session", ApprovalBinding(app, setOf(foreign)), start, access).state
        assertNull(PlaySessionMachine.candidate(changed, app))
        assertTrue(app in changed.invalidated)
    }

    @Test fun `clock cannot move backwards after an accepted event`() {
        val later = BootTime("boot", 20_000)
        val state = PlaySessionMachine.installed(begun(), "session", identity, later, access).state
        val result = PlaySessionMachine.installed(state, "session", identity, start, access)
        assertFalse(result.accepted)
        assertEquals(PlayEndReason.INVALID_CLOCK, result.state.endedBecause)
    }

    @Test fun `candidate collection cannot be mutated by callers`() {
        val state = PlaySessionMachine.installed(begun(), "session", identity, start, access).state
        assertFailsWith<UnsupportedOperationException> { (state.candidates as MutableMap).clear() }
    }

    @Test fun `process restart never restores Play candidate authority`() {
        val state = PlaySessionMachine.installed(begun(), "session", identity, start, access).state
        val ended = PlaySessionMachine.onRestart(state)
        assertEquals(PlayEndReason.RESTART, ended.endedBecause)
        assertFalse(PlaySessionMachine.installed(ended, "session", identity, start, access).accepted)
    }
    @Test fun `signer replacement invalidates the candidate for the entire grant`() {
        val first = PlaySessionMachine.installed(begun(), "session", identity, start, access).state
        val changed = PlaySessionMachine.installed(first, "session", ApprovalBinding(app, setOf(foreign)), start, access)
        assertFalse(changed.accepted)
        assertNull(PlaySessionMachine.candidate(changed.state, app))
        val restored = PlaySessionMachine.installed(changed.state, "session", identity, start, access)
        assertFalse(restored.accepted)
        assertNull(PlaySessionMachine.candidate(restored.state, app))
    }

}
