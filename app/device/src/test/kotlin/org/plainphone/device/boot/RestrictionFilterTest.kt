// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.visible
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.Restriction

/**
 * device-lockdown: DISALLOW_CONFIG_DEFAULT_APPS "where the API level supports it" (PLA-232 finding 2).
 * api-versions.xml: the key exists from API 34; every other restriction exists by API 30 (minSdk).
 */
class RestrictionFilterTest {
    private val defaultApps = Restriction.DISALLOW_CONFIG_DEFAULT_APPS
    private val pp = PackageName("org.plainphone")
    private val messages = PackageName("com.example.messages")

    private fun plan() = PolicyEngine.decide(PolicyInput(
        model = "model", fingerprint = "build", plainphone = pp, messages = messages,
        installed = listOf(InstalledPackage(pp, false, 1), InstalledPackage(messages, true, 1)), identities = emptyList(),
    ))

    @Test fun onlyDefaultAppsIsLeftOutBelowApi34() {
        for (sdk in listOf(30, 32, 33)) assertEquals("API $sdk", setOf(defaultApps), RestrictionFilter.unsupportedOn(sdk))
        assertEquals(emptySet<Restriction>(), RestrictionFilter.unsupportedOn(34))
    }

    @Test fun belowApi34TheKeyLeavesBothTheWriteAndTheExpectation() {
        for (sdk in listOf(30, 32, 33)) {
            val filtered = RestrictionFilter.NONE.forSdk(sdk).apply(plan())
            assertEquals("API $sdk", Restriction.entries.toSet() - defaultApps, filtered.restrictions)
            assertEquals("API $sdk", Restriction.entries.toSet() - defaultApps, filtered.expected.restrictions)
        }
    }

    @Test fun api34KeepsEveryRestriction() {
        val filtered = RestrictionFilter.NONE.forSdk(34).apply(plan())
        assertEquals(Restriction.entries.toSet(), filtered.restrictions)
        assertEquals(Restriction.entries.toSet(), filtered.expected.restrictions)
    }

    @Test fun theBuildFilterIsKept() {
        val f = RestrictionFilter.TEST_BUILD.forSdk(33)
        assertEquals(RestrictionFilter.TEST_BUILD.withheld + defaultApps, f.withheld)
        assertEquals(RestrictionFilter.TEST_BUILD.withheld, RestrictionFilter.TEST_BUILD.forSdk(34).withheld)
    }

    /** A pre-34 build silently ignores the unknown key, so the unfiltered plan cannot pass readback. */
    @Test fun aPre34BuildCompletesOnlyWithTheSdkFilter() {
        fun port() = FakeDevicePolicyPort(mapOf(pp.value to visible(), messages.value to visible()))
            .apply { unknownRestrictions += defaultApps }
        assertFalse(ReadbackPolicyApplier(port()).apply(plan()).complete)
        val p = port()
        assertTrue(ReadbackPolicyApplier(p).apply(RestrictionFilter.NONE.forSdk(33).apply(plan())).complete)
        assertEquals(Restriction.entries.toSet() - defaultApps, p.restrictionSet)
    }
}
