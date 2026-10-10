// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import org.junit.Test
import org.junit.Assert.*
import org.plainphone.core.*
import org.plainphone.device.*
import org.plainphone.policy.*

/**
 * The PLA-218 reviewer's three probes (review document on PLA-218), kept as regressions. The first two
 * are verbatim. The third called the raw `handle`, which is now private; it is adapted to check that the
 * queued entry is the only public way in and that it writes nothing on the caller's thread.
 */
class VerifierWindowProbeTest {
    private val app = PackageName("test.candidate")
    private val other = PackageName("test.other")
    private val pp = PackageName("org.plainphone")
    private val signer = CertDigest("ab".repeat(32))
    private val identity = ApprovalBinding(app, setOf(signer))
    private val port = FakeDevicePolicyPort(mapOf(app.value to hiddenApp(), other.value to hiddenApp(), pp.value to visible()))
        .apply { lockTask = setOf(pp) }
    private val home = PublishedHomeApps()
    private val grant = object : PermissionGrantPort {
        override fun signers(name: PackageName) = setOf(signer)
        override fun policyState(name: PackageName, permission: PermissionName) = PolicyGrant.DEFAULT
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant) = true
        override fun granted(name: PackageName, permission: PermissionName) = false
        override fun uninstallBlocked(name: PackageName) = false
        override fun setUninstallBlocked(name: PackageName, blocked: Boolean) = true
    }
    private fun effects(d: SerialDispatcher) = DeviceApprovalEffects(d, port, grant, home, { setOf(pp) }) { null }

    @Test fun failedFinalFlushMustNotLeaveCandidateVisibleAndPermitted() {
        SerialDispatcher().use { d ->
            val e = effects(d)
            port.flipsIgnored = 2
            val ui = setOf(pp, app)
            val stages = listOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD, ApplyStage.HIDES, ApplyStage.LOCK_TASK)
            val plan = PolicyPlan(hidden = setOf(other), unhide = ui, neverHide = setOf(pp), emergencyUi = emptySet(),
                lockTaskPackages = ui, features = emptySet(), restrictions = emptySet(),
                steps = stages.map { ApplyStep(it, true) },
                expected = ExpectedPolicyState(setOf(other), ui, ui, ui, emptySet(), null, null, null, null),
                sweepMode = SweepMode.FULL, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
                classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false)
            assertFalse(d.run { e.applyPolicy(plan) })
            println("PROBE flush: hidden=${port.hidden(app.value)} permitted=${app in port.lockTask} home=${home.lists(app)}")
            assertTrue("failed apply left candidate visible AND lock-task permitted", port.hidden(app.value) || app !in port.lockTask)
        }
    }

    @Test fun aSecondWindowMustNotOrphanTheFirstVisiblePackage() {
        SerialDispatcher().use { d ->
            val e = effects(d)
            d.run {
                assertTrue(e.openPermissionWindow(identity))
                val second = ApprovalBinding(other, setOf(signer))
                val opened = e.openPermissionWindow(second)
                e.closePermissionWindow(second)
                println("PROBE nested: secondOpened=$opened firstHidden=${port.hidden(app.value)} secondHidden=${port.hidden(other.value)}")
                assertFalse("second window accepted while the first app remains visible", opened)
            }
        }
    }

    @Test fun publicHandlerMustNotWriteOutsideDispatcher() {
        // PackageName is a value class, so Kotlin mangles these names (onPackageAdded-<hash>).
        val public = PackageAddedHandler::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }.map { it.name.substringBefore('-') }.toSet()
        assertEquals("the queued entry and the receiver are the only public ways in", setOf("onPackageAdded", "register"), public)
        SerialDispatcher().use { d ->
            port.state[app] = visible()
            val handler = PackageAddedHandler(d, port, { false }, { setOf(pp) })
            val release = CountDownLatch(1)
            d.post { release.await() } // hold the dispatcher busy
            handler.onPackageAdded(app)
            println("PROBE handler: hidden=${port.hidden(app.value)} writes=${port.ops}")
            assertTrue("off-dispatcher entry wrote directly", port.ops.isEmpty())
            release.countDown()
            d.run { }
            assertTrue("the queued hide ran on the dispatcher", port.hidden(app.value))
        }
    }
}
