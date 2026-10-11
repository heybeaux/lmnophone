// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetProtectionEffectsTest {
    private class FakePort : ResetProtectionPort {
        var policy: ResetProtectionReading? = null
        val ops = mutableListOf<String>()
        var throwOn: String? = null
        var sticks = true
        override fun setPolicy(accounts: List<String>) {
            ops += "set $accounts"
            if (throwOn == "set") throw SecurityException("not owner")
            if (sticks) policy = ResetProtectionReading(accounts, true)
        }
        override fun readPolicy(): ResetProtectionReading? {
            ops += "read"
            if (throwOn == "read") throw IllegalStateException("unreadable")
            return policy
        }
        override fun sendConfigChanged() {
            ops += "broadcast"
            if (throwOn == "broadcast") throw SecurityException("denied")
        }
    }

    private val ids = setOf("holder-2", "holder-1", "backup")

    @Test fun setsTheExactSortedListEnabled() {
        val port = FakePort(); val effects = ResetProtectionEffects(port)
        assertTrue(effects.setResetProtection(ids))
        assertEquals(listOf("set [backup, holder-1, holder-2]"), port.ops)
        assertEquals(ResetProtectionReading(listOf("backup", "holder-1", "holder-2"), true), port.policy)
    }

    @Test fun broadcastIsSentAndFailuresReportFalse() {
        val port = FakePort(); val effects = ResetProtectionEffects(port)
        assertTrue(effects.broadcastResetProtectionChanged())
        port.throwOn = "broadcast"
        assertFalse(effects.broadcastResetProtectionChanged())
    }

    @Test fun readbackNeedsExactlyTheSuppliedListIncludingBackupAndEnabled() {
        val port = FakePort(); val effects = ResetProtectionEffects(port)
        assertFalse(effects.readBackResetProtection(ids))
        effects.setResetProtection(ids)
        assertTrue(effects.readBackResetProtection(ids))
        assertFalse(effects.readBackResetProtection(ids - "backup"))
        assertFalse(effects.readBackResetProtection(ids + "extra"))
        port.policy = ResetProtectionReading(listOf("backup", "holder-1", "holder-2"), false)
        assertFalse(effects.readBackResetProtection(ids))
        port.policy = ResetProtectionReading(listOf("backup", "holder-1", "holder-2", "holder-1"), true)
        assertFalse(effects.readBackResetProtection(ids))
        port.throwOn = "read"
        assertFalse(effects.readBackResetProtection(ids))
    }

    @Test fun aWriteThatDoesNotStickFailsReadback() {
        val port = FakePort().apply { sticks = false }; val effects = ResetProtectionEffects(port)
        assertTrue(effects.setResetProtection(ids))
        assertFalse(effects.readBackResetProtection(ids))
    }

    @Test fun refusedWritesReportFalse() {
        val port = FakePort().apply { throwOn = "set" }
        assertFalse(ResetProtectionEffects(port).setResetProtection(ids))
    }

    /** An empty list would fall back to whichever accounts are on the phone, including the user's own. */
    @Test fun emptyOrBlankListsAreRefusedWithoutTouchingThePort() {
        val port = FakePort(); val effects = ResetProtectionEffects(port)
        assertFalse(effects.setResetProtection(emptySet()))
        assertFalse(effects.setResetProtection(setOf("holder-1", " ")))
        assertFalse(effects.setResetProtection(setOf("holder 1")))
        assertFalse(effects.readBackResetProtection(emptySet()))
        assertTrue(port.ops.isEmpty())
    }
}
