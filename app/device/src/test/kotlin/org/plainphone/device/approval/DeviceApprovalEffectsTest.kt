// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.hiddenApp
import org.plainphone.device.visible
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionWrite
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.RuntimePermission
import org.plainphone.policy.SweepMode

/**
 * The device [org.plainphone.policy.AppApprovalEffects] adapter (PLA-186) over [FakeDevicePolicyPort]
 * and a grant port that, like API 35, refuses every grant write while the app is hidden.
 */
class DeviceApprovalEffectsTest {
    private val app = PackageName("test.app")
    private val dialer = PackageName("test.dialer")
    private val pp = PackageName("org.plainphone")
    private val other = PackageName("test.other")
    private val signer = CertDigest("ab".repeat(32))
    private val identity = ApprovalBinding(app, setOf(signer))
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")

    private val port = FakeDevicePolicyPort(mapOf(app.value to hiddenApp(), dialer.value to visible(), pp.value to visible(), other.value to visible()))
        .apply { lockTask = setOf(pp, dialer, app, other) }
    private val grants = HiddenRefusingGrants(port)
    private val home = FakeHome(port.ops).apply { listed += setOf(app, other) }
    private var neverHide: Set<PackageName>? = setOf(pp, dialer)
    private val manifest = PermissionManifest(identity, 1, "build", listOf(
        RuntimePermission(camera, "Camera", emptyList(), PermissionGrantCapability.SUPPORTED),
        RuntimePermission(mic, "Microphone", emptyList(), PermissionGrantCapability.SUPPORTED)))
    private val dispatcher = SerialDispatcher("test-policy")
    private fun effects(devicePort: DevicePolicyPort = port) =
        DeviceApprovalEffects(dispatcher, devicePort, grants, home, { neverHide }) { name -> manifest.takeIf { name == app } }
    private val effects = effects()

    @After fun stop() = dispatcher.close()

    private fun <T> onDispatcher(block: () -> T): T = dispatcher.run(block)
    private fun at(op: String) = port.ops.indexOf(op).also { assertTrue("missing $op in ${port.ops}", it >= 0) }

    @Test fun openExcludesLockTaskAndHomeAndReadsThemBackBeforeTheUnhide() {
        assertTrue(onDispatcher { effects.openPermissionWindow(identity) })
        assertEquals("only this package left lock task; the floor and others stay", setOf(pp, dialer, other), port.lockTask)
        assertEquals(setOf(other), home.listed)
        assertTrue(at("lockTask org.plainphone,test.dialer,test.other") < at("home-remove test.app"))
        assertTrue(at("home-remove test.app") < at("unhide test.app"))
        assertTrue("the unhide is flushed", at("unhide test.app") < port.ops.indexOfFirst { it.startsWith("flip") })
        assertFalse(port.hidden(app.value))
        assertTrue("open never suspends", port.ops.none { it.startsWith("suspend") })
    }

    @Test fun aFailedExclusionNeverUnhides() {
        val failures = listOf<(FakeDevicePolicyPort, FakeHome) -> Unit>(
            { p, _ -> p.lockTaskSticks = false },
            { p, _ -> p.throwOnWrite = "lockTask" },
            { _, h -> h.removeSticks = false },
            { _, h -> h.readable = false },
            { _, _ -> grants.signers = setOf(CertDigest("cd".repeat(32))) },
            { p, _ -> p.state[app] = hiddenApp().copy(suspended = true) },
        )
        for ((i, fail) in failures.withIndex()) {
            val p = FakeDevicePolicyPort(mapOf(app.value to hiddenApp(), dialer.value to visible())).apply { lockTask = setOf(dialer, app) }
            val h = FakeHome(p.ops).apply { listed += app }
            grants.signers = setOf(signer)
            fail(p, h)
            val e = DeviceApprovalEffects(dispatcher, p, HiddenRefusingGrants(p).also { it.signers = grants.signers }, h, { neverHide }) { manifest }
            assertFalse("case $i", onDispatcher { e.openPermissionWindow(identity) })
            assertTrue("case $i never unhid: ${p.ops}", p.ops.none { it == "unhide test.app" })
            assertTrue("case $i", p.hidden(app.value))
        }
    }

    @Test fun closeRehidesAfterEveryPartialOpenAndNeverRestoresAccess() {
        val partials = listOf<(FakeDevicePolicyPort, FakeHome) -> Unit>(
            { _, _ -> },                                // complete open
            { p, _ -> p.lockTaskSticks = false },       // stopped at lock task
            { _, h -> h.removeSticks = false },         // stopped at Home
            { p, _ -> p.flushComponent = null },        // unhidden, flush unreadable
            { p, _ -> p.flipsIgnored = 2 },             // unhidden, flush did not read back
        )
        for ((i, partial) in partials.withIndex()) {
            val p = FakeDevicePolicyPort(mapOf(app.value to hiddenApp(), dialer.value to visible())).apply { lockTask = setOf(dialer, app) }
            val h = FakeHome(p.ops).apply { listed += app }
            partial(p, h)
            val e = DeviceApprovalEffects(dispatcher, p, HiddenRefusingGrants(p), h, { neverHide }) { manifest }
            onDispatcher { e.openPermissionWindow(identity) }
            val lockTaskAfterOpen = p.lockTask
            val homeAfterOpen = h.listed.toSet()
            val writesBeforeClose = p.ops.size
            assertTrue("case $i: ${p.ops}", onDispatcher { e.closePermissionWindow(identity) })
            assertTrue("case $i", p.hidden(app.value))
            val closeOps = p.ops.drop(writesBeforeClose)
            assertTrue("case $i close only hides: $closeOps", closeOps.all { it == "hide test.app" })
            assertEquals("case $i", lockTaskAfterOpen, p.lockTask)
            assertEquals("case $i Home never re-added", homeAfterOpen, h.listed)
            assertTrue("case $i", h.published.isEmpty())
        }
    }

    @Test fun neverHideGuardRefusesOpenCloseAndHideForFloorPackages() {
        val floor = ApprovalBinding(dialer, setOf(signer))
        assertFalse(onDispatcher { effects.openPermissionWindow(floor) })
        assertFalse(onDispatcher { effects.closePermissionWindow(floor) })
        assertFalse(onDispatcher { effects.hideAndReadBack(floor) })
        neverHide = null // unreadable: no hide can be proven safe
        assertFalse(onDispatcher { effects.hideAndReadBack(identity) })
        assertFalse(onDispatcher { effects.closePermissionWindow(identity) })
        assertFalse(onDispatcher { effects.openPermissionWindow(identity) })
        assertEquals(emptyList<String>(), port.ops)
        assertTrue(dialer in port.lockTask && !port.hidden(dialer.value))
    }

    @Test fun permissionsAreWrittenOnlyInsideTheWindowAndReadBackHidden() {
        val deny = PermissionWrite(mic, PermissionChoice.DENY)
        val allow = PermissionWrite(camera, PermissionChoice.ALLOW)
        assertFalse("no window", onDispatcher { effects.setPermission(identity, deny) })
        assertTrue(grants.writes.isEmpty())
        val visibleRead = onDispatcher {
            check(effects.openPermissionWindow(identity))
            check(effects.setPermission(identity, deny) && effects.setPermission(identity, allow))
            effects.readPermissions(identity).also { check(effects.closePermissionWindow(identity)) }
        }
        assertEquals(mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED), visibleRead)
        assertTrue(port.hidden(app.value))
        assertEquals("hidden reads are live and agree", visibleRead, onDispatcher { effects.readPermissions(identity) })
        assertFalse("closed window", onDispatcher { effects.setPermission(identity, deny) })
        assertEquals(listOf("RECORD_AUDIO DENIED", "CAMERA GRANTED"), grants.writes)
    }

    @Test fun offDispatcherCallsFailClosedAndTouchNothing() {
        assertFalse(effects.openPermissionWindow(identity))
        assertFalse(effects.closePermissionWindow(identity))
        assertFalse(effects.hideAndReadBack(identity))
        assertFalse(effects.setPermission(identity, PermissionWrite(camera, PermissionChoice.ALLOW)))
        assertEquals(emptyList<String>(), port.ops)
    }

    @Test fun hideAndReadBackHidesAnApprovedVisibleAppForAnEditFirst() {
        port.state[app] = visible()
        assertTrue(onDispatcher { effects.hideAndReadBack(identity) })
        assertTrue(port.hidden(app.value))
        port.hidesStick = false
        port.state[app] = visible()
        assertFalse("hide that does not read back", onDispatcher { effects.hideAndReadBack(identity) })
    }

    @Test fun editExcludesLockTaskBeforeTheUnhide() {
        // An approved app: in lock task, on Home, visible. Policy hides it first, then opens.
        port.state[app] = visible()
        assertTrue(onDispatcher { effects.hideAndReadBack(identity) && effects.openPermissionWindow(identity) })
        assertTrue(at("hide test.app") < at("lockTask org.plainphone,test.dialer,test.other"))
        assertTrue(at("lockTask org.plainphone,test.dialer,test.other") < at("unhide test.app"))
        assertFalse(app in port.lockTask || app in home.listed)
    }

    @Test fun packageAddedDuringTheWindowWaitsUntilTheWindowCloses() {
        val events = mutableListOf<String>()
        val handler = PackageAddedHandler(dispatcher, port, { name -> (name != app).also { events += "handler ${name.value}" } }, { neverHide })
        // Android sends ACTION_PACKAGE_ADDED for an unhide; the receiver posts it to the dispatcher.
        val broadcasting = object : DevicePolicyPort by port {
            override fun setHidden(name: PackageName, hidden: Boolean) {
                port.setHidden(name, hidden)
                events += (if (hidden) "hide " else "unhide ") + name.value
                if (!hidden) handler.onPackageAdded(name)
            }
        }
        val e = effects(broadcasting)
        val wrote = onDispatcher {
            e.openPermissionWindow(identity) &&
                e.setPermission(identity, PermissionWrite(mic, PermissionChoice.DENY)).also { events += "write mic" } &&
                e.setPermission(identity, PermissionWrite(camera, PermissionChoice.ALLOW)).also { events += "write camera" } &&
                e.closePermissionWindow(identity)
        }
        assertTrue("every write saw the app visible", wrote)
        dispatcher.run { } // drain
        assertEquals(listOf("unhide test.app", "write mic", "write camera", "hide test.app", "handler test.app"), events)
        assertTrue(port.hidden(app.value))
    }

    @Test fun aHandlerOutsideTheDispatcherWouldBreakTheWindow() {
        // The control: hide inline at the unhide, as an unserialized receiver could. The handler itself
        // has no such entry: onPackageAdded only queues (VerifierWindowProbeTest).
        val inline = object : DevicePolicyPort by port {
            override fun setHidden(name: PackageName, hidden: Boolean) {
                port.setHidden(name, hidden)
                if (!hidden) port.setHidden(name, true)
            }
        }
        val e = effects(inline)
        assertFalse(onDispatcher { e.openPermissionWindow(identity) && e.setPermission(identity, PermissionWrite(mic, PermissionChoice.DENY)) })
    }

    @Test fun packageAddedHidesOnlyUnapprovedNonFloorPackages() {
        var approved = setOf(other)
        val handler = PackageAddedHandler(dispatcher, port, { it in approved }, { neverHide })
        port.state[app] = visible()
        listOf(app, other, dialer).forEach(handler::onPackageAdded)
        dispatcher.run { }
        assertTrue(port.hidden(app.value))
        assertFalse(port.hidden(other.value) || port.hidden(dialer.value))
        approved = emptySet(); neverHide = null
        handler.onPackageAdded(other); dispatcher.run { }
        assertFalse("no hide without a readable never-hide list", port.hidden(other.value))
    }

    @Test fun applyPolicyDelegatesAndPublishesHomeAndReadBackOnlyReads() {
        val plan = plan()
        assertTrue(onDispatcher { effects.applyPolicy(plan) })
        assertEquals(listOf(setOf(pp, dialer, app)), home.published)
        assertFalse(port.hidden(app.value))
        val writes = port.ops.size
        assertTrue(onDispatcher { effects.readBackPolicy(plan) })
        assertEquals("readback writes nothing", writes, port.ops.size)
        port.state[app] = hiddenApp()
        assertFalse(onDispatcher { effects.readBackPolicy(plan) })
    }

    private fun plan(): PolicyPlan {
        val lockTask = setOf(pp, dialer, app)
        val stages = listOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD, ApplyStage.HIDES, ApplyStage.LOCK_TASK)
        return PolicyPlan(
            hidden = setOf(other), unhide = setOf(pp, dialer, app), neverHide = setOf(pp, dialer), emergencyUi = setOf(dialer),
            lockTaskPackages = lockTask, features = LockTaskFeature.entries.toSet(), restrictions = emptySet(),
            steps = stages.map { ApplyStep(it, true) },
            expected = ExpectedPolicyState(setOf(other), setOf(pp, dialer, app), setOf(pp, dialer, app), lockTask,
                LockTaskFeature.entries.toSet(), null, null, null, null),
            sweepMode = SweepMode.FULL, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
            classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false,
        )
    }

    /** Home's list; writes go into the port's op log so tests can check order. */
    private class FakeHome(private val ops: MutableList<String>) : HomeAppList {
        val listed = mutableSetOf<PackageName>()
        val published = mutableListOf<Set<PackageName>>()
        var removeSticks = true
        var readable = true
        override fun remove(name: PackageName) {
            ops += "home-remove ${name.value}"
            if (removeSticks) listed -= name
        }
        override fun lists(name: PackageName): Boolean? = if (readable) name in listed else null
        override fun publish(names: Set<PackageName>) {
            ops += "home-publish"
            published += names; listed.clear(); listed += names
        }
    }

    /** API 35: grant writes are refused while hidden; reads work hidden or visible. */
    private class HiddenRefusingGrants(private val port: FakeDevicePolicyPort) : PermissionGrantPort {
        var signers: Set<CertDigest>? = setOf(CertDigest("ab".repeat(32)))
        val policy = mutableMapOf<PermissionName, PolicyGrant>()
        val writes = mutableListOf<String>()
        var blocked = false
        override fun signers(name: PackageName) = signers
        override fun policyState(name: PackageName, permission: PermissionName) = policy[permission] ?: PolicyGrant.DEFAULT
        override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            val now = port.state[name] ?: return false
            if (now.hidden || now.suspended) return false
            writes += "${permission.value.substringAfterLast('.')} $state"
            policy[permission] = state
            return true
        }
        override fun granted(name: PackageName, permission: PermissionName) = policy[permission] == PolicyGrant.GRANTED
        override fun uninstallBlocked(name: PackageName) = blocked
        override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean { this.blocked = blocked; return true }
    }
}
