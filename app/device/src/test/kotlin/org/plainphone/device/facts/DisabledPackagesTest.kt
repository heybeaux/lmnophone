// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.BuildInventory
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.StoredPolicy

/**
 * Slice 5 correction (PLA-144, PLA-190). On the spare a system-disabled carrier app with a launcher
 * activity never read back as hidden after setApplicationHidden, so the apply stopped at HIDES. A
 * disabled package cannot open, so it stays out of the hide set. A disabled floor package stays in:
 * the floor must still enable and unhide it.
 */
class DisabledPackagesTest {
    private val port = FakePackageSystemPort()
    private val plainphone = PackageName("org.plainphone")
    private val messages = PackageName("com.google.android.apps.messaging")

    private object Valid : PolicyStore {
        override fun read() = StoredPolicy(1, emptySet(), PolicyHealth.VALID)
        override fun write(policy: StoredPolicy) = false
    }

    private fun assemble() = LivePolicyInputAssembler(
        port, Valid, listOf(BuildInventory(port.model, port.fingerprint, 1, NeverHideList(emptySet()), emptySet(), emptySet())),
        plainphone, messages,
    ).assemble()

    private fun phone(dialerEnabled: Boolean = true) {
        port.packages(
            RawPackage("com.google.android.dialer", true, 10, listOf("google".toByteArray()), enabled = dialerEnabled),
            raw("com.google.android.apps.messaging", true, 20, "google"),
            RawPackage("com.tracfone.preload.accountservices", true, 5, listOf("carrier".toByteArray()), enabled = false),
            RawPackage("com.example.disabled", false, 1, listOf("keyD".toByteArray()), enabled = false),
            raw("com.example.notes", false, 3, "keyA"),
            raw("org.plainphone", false, 1, "pp"),
        )
        port.systemDialer = "com.google.android.dialer"
        port.inCallUi = setOf("com.google.android.dialer")
        port.defaultSms = "com.google.android.apps.messaging"
    }

    @Test fun disabledAppsAreLeftOutOfTheHideSet() {
        phone()
        val live = assemble()
        val installed = live.input.installed.map { it.name.value }
        assertFalse("com.tracfone.preload.accountservices" in installed)
        assertFalse("com.example.disabled" in installed)
        val plan = PolicyEngine.decide(live.input)
        assertEquals(setOf(PackageName("com.example.notes")), plan.hidden)
        assertEquals(plan.hidden, plan.expected.hidden)
    }

    @Test fun disabledAppsStayInTheLinkFacts() {
        phone()
        val facts = assemble().packages.map { it.packageName.value }
        assertTrue("com.tracfone.preload.accountservices" in facts)
        assertTrue("com.example.disabled" in facts)
    }

    @Test fun aDisabledFloorPackageIsStillRepaired() {
        phone(dialerEnabled = false)
        val live = assemble()
        val dialer = PackageName("com.google.android.dialer")
        assertTrue(live.input.installed.any { it.name == dialer })
        val plan = PolicyEngine.decide(live.input)
        assertTrue(dialer in plan.emergencyUi)
        assertTrue(dialer in plan.expected.unhiddenAndEnabled)
        assertFalse(dialer in plan.hidden)
    }

    @Test fun aPackageEnabledInEitherReadStaysInTheHideSet() {
        phone()
        val first = port.reads.single()
        port.reads = mutableListOf(
            first,
            first.map { if (it.name == "com.example.disabled") RawPackage(it.name, false, 1, it.signers, enabled = true) else it },
        )
        val plan = PolicyEngine.decide(assemble().input)
        assertTrue(PackageName("com.example.disabled") in plan.hidden)
    }
}
