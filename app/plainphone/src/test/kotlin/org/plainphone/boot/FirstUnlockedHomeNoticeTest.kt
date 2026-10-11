// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.PackageState
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.boot.RestrictionFilter
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.ui.home.HomeNotice

/**
 * PLA-241 finding 4, end to end: a flip that fails in the locked-boot pass reaches the first unlocked
 * Home's notice, through a process restart, with the same device-protected stores PolicyRuntime uses.
 */
class FirstUnlockedHomeNoticeTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pp = PackageName("org.plainphone")
    private val dialer = PackageName("com.android.dialer")
    private val sms = PackageName("com.example.messages")
    private val play = PackageName("com.android.vending")
    private val visible = PackageState(hidden = false, suspended = false, enabled = true)
    private val port = FakeDevicePolicyPort(listOf(pp, dialer, sms, play).associate { it.value to visible })

    private fun plan(locked: Boolean) = PolicyEngine.decide(PolicyInput(
        model = "model", fingerprint = "build", plainphone = pp, messages = sms,
        installed = listOf(InstalledPackage(pp, false, 1), InstalledPackage(dialer, true, 1),
            InstalledPackage(sms, true, 1), InstalledPackage(play, true, 1)),
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source),
            FloorIdentity(sms, FloorRole.SMS, FloorRole.SMS.source)),
        lockedBoot = locked,
    ))

    private val dir by lazy { tmp.newFolder("de") }
    /** A fresh process: new runtime objects over the same device-protected directory. */
    private fun process() = BootReapply(
        FileJournalStore(java.io.File(dir, "journal"), null, "model", "build"), port, setOf(play), ::plan,
        RestrictionFilter.TEST_BUILD, flipFailures = FileFlipFailureStore(java.io.File(dir, "flip-failures")),
    )

    @Test fun aLockedBootFlipFailureShowsOnTheFirstUnlockedHome() {
        port.state[dialer] = visible.copy(hidden = true)
        port.flushComponent = null
        val locked = process().run(locked = true)
        assertTrue(dialer in locked.apply!!.flipFailures)

        port.flushComponent = false
        val unlocked = process().run(locked = false)
        assertTrue(unlocked.ready)
        assertEquals(HomeNotice.NeedsAttention, homeNotice(unlocked))
    }

    @Test fun anAcknowledgedFailureNoLongerShows() {
        port.state[dialer] = visible.copy(hidden = true)
        port.flushComponent = null
        process().run(locked = true)
        process().acknowledgeFlipFailures()
        port.flushComponent = false
        assertNull(homeNotice(process().run(locked = false)))
    }
}
