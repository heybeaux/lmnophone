// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.boot.*
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.policy.*

/** Reviewer probes. Assertions describe required behavior; production code is unchanged. */
class Pla241ReviewTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pp = PackageName("org.plainphone")
    private val dialer = PackageName("com.android.dialer")
    private val sms = PackageName("com.example.messages")
    private val play = PackageName("com.android.vending")
    private fun plan(locked: Boolean) = PolicyEngine.decide(PolicyInput(
        model = "model", fingerprint = "build", plainphone = pp, messages = sms,
        installed = listOf(InstalledPackage(pp,false,1), InstalledPackage(dialer,true,1),
            InstalledPackage(sms,true,1), InstalledPackage(play,true,1)),
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source),
            FloorIdentity(sms, FloorRole.SMS, FloorRole.SMS.source)), lockedBoot = locked))
    private fun port() = FakeDevicePolicyPort(mapOf(pp.value to visible(), dialer.value to visible(),
        sms.value to visible(), play.value to visible()))
    private fun journal() = FileJournalStore(tmp.newFolder(), null, "model", "build")

    @Test fun failedPlanMustStillRestoreFixedRestrictions() {
        val p = port()
        val out = BootReapply(journal(), p, setOf(play), { error("package facts unavailable") }, RestrictionFilter.TEST_BUILD).run(true)
        assertFalse(out.ready)
        assertTrue("a failed plan must not skip the fixed emergency baseline", Restriction.DISALLOW_SAFE_BOOT in p.restrictionSet)
    }

    @Test fun throwingJournalMustStillRepairEmergencyFloor() {
        val p = port().apply { state[dialer] = hiddenApp() }
        val delegate = journal()
        val j = object : JournalStore by delegate { override fun beginRevocation() { throw SecurityException("storage unavailable") } }
        val out = runCatching { BootReapply(j,p,setOf(play),::plan,RestrictionFilter.TEST_BUILD).run(true) }
        assertFalse("emergency repair must survive a journal exception", p.hidden(dialer.value))
        assertTrue(out.isSuccess)
    }

    @Test fun lockedBootFlipFailureMustReachFirstUnlockedHome() {
        val p = port().apply { state[dialer] = hiddenApp(); flushComponent = null }
        val boot = BootReapply(journal(),p,setOf(play),::plan,RestrictionFilter.TEST_BUILD)
        val locked = boot.run(true)
        assertTrue(locked.ready)
        assertTrue(locked.needsAttention)
        val unlocked = boot.run(false)
        assertTrue(unlocked.ready)
        assertTrue("failed flip was never repaired or shown to Home", unlocked.needsAttention)
    }

    @Test fun enabledStateChangingBeforeFinalReadbackMustBlockSetup() {
        val p = port()
        val wrapper = object : DevicePolicyPort by p {
            override fun setHome(name: PackageName) {
                p.setHome(name)
                p.state[dialer] = visible().copy(enabled=false)
            }
        }
        val out = BootReapply(journal(),wrapper,setOf(play),::plan,RestrictionFilter.TEST_BUILD).run(false)
        assertFalse("final readback cannot certify a disabled emergency dialer", out.setupMayFinish)
        assertTrue(out.needsAttention)
    }

    @Test fun repairMustPreserveFlipFailureFromFirstAttempt() {
        val p = port().apply { state[dialer] = hiddenApp(); flushComponent = null }
        var writes = 0
        val wrapper = object : DevicePolicyPort by p {
            override fun setLockTaskFeatures(features: Set<LockTaskFeature>) {
                if (++writes == 1) error("transient failure after unhide")
                p.setLockTaskFeatures(features)
            }
        }
        val out = BootReapply(journal(),wrapper,setOf(play),::plan,RestrictionFilter.TEST_BUILD).run(true)
        assertFalse(out.ready)
        assertTrue("repair must not erase the first apply's failed flip", dialer in out.apply!!.flipFailures)
        assertTrue(out.needsAttention)
    }
}
