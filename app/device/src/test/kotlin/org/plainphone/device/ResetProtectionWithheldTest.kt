// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.device.boot.RestrictionFilter

/**
 * Lead decision on PLA-92 (2026-10-12): test builds withhold the reset-protection write, under the same
 * filter that withholds DISALLOW_FACTORY_RESET, so a test build never writes a new reset-protection
 * policy. It does not read or clear a policy that is already set. Only the account count is recorded,
 * never the IDs.
 */
class ResetProtectionWithheldTest {
    private class Port : ResetProtectionPort {
        val ops = mutableListOf<String>()
        override fun setPolicy(accounts: List<String>) { ops += "set" }
        override fun readPolicy(): ResetProtectionReading? { ops += "read"; return null }
        override fun sendConfigChanged() { ops += "broadcast" }
    }
    private val ids = setOf("account-anna", "account-ben", "account-backup")

    @Test fun theTestBuildFilterWithholdsResetProtection() {
        assertTrue(RestrictionFilter.TEST_BUILD.withholdsResetProtection)
        assertTrue(RestrictionFilter.TEST_BUILD.forSdk(30).withholdsResetProtection)
        assertFalse(RestrictionFilter.NONE.withholdsResetProtection)
        assertFalse(RestrictionFilter.NONE.forSdk(35).withholdsResetProtection)
    }

    /** A test build never calls the platform: no set, no broadcast, no read. */
    @Test fun aTestBuildNeverWritesResetProtection() {
        val port = Port()
        val logged = mutableListOf<Int>()
        val effects = resetProtectionFor(RestrictionFilter.TEST_BUILD.forSdk(35), port) { logged += it }
        assertTrue(effects.setResetProtection(ids))
        assertTrue(effects.broadcastResetProtectionChanged())
        assertTrue(effects.readBackResetProtection(ids))
        assertTrue("platform untouched: ${port.ops}", port.ops.isEmpty())
        assertEquals("the count only, never the IDs", listOf(3), logged)
        assertEquals(3, (effects as WithheldResetProtection).withheldAccounts)
    }

    /** Withholding is not a free pass: invalid lists are still refused, and a readback must match the recorded count. */
    @Test fun withheldStillRefusesWhatTheRealEffectsRefuse() {
        val effects = resetProtectionFor(RestrictionFilter.TEST_BUILD, Port()) {}
        assertFalse("no list recorded yet", effects.readBackResetProtection(ids))
        assertFalse("nothing recorded, nothing to broadcast", effects.broadcastResetProtectionChanged())
        assertFalse(effects.setResetProtection(emptySet()))
        assertFalse(effects.setResetProtection(setOf("account anna")))
        assertNull((effects as WithheldResetProtection).withheldAccounts)
        assertTrue(effects.setResetProtection(ids))
        assertFalse("a different list does not read back", effects.readBackResetProtection(ids - "account-ben"))
    }

    /** A release build writes the real policy through the port. */
    @Test fun aReleaseBuildWritesThroughThePort() {
        val port = Port()
        val effects = resetProtectionFor(RestrictionFilter.NONE.forSdk(35), port) { error("release never logs a withheld list") }
        assertTrue(effects.setResetProtection(ids))
        assertTrue(effects.broadcastResetProtectionChanged())
        assertEquals(listOf("set", "broadcast"), port.ops)
        assertTrue(effects is ResetProtectionEffects)
    }
}
