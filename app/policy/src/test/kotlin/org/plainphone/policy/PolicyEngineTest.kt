// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class PolicyEngineTest {
    private val home = PackageName("test.home")
    private val sms = PackageName("test.sms")
    private val dialer = PackageName("test.dialer")
    private val emergency = PackageName("test.emergency")
    private val calls = PackageName("test.calls")
    private val webview = PackageName("test.webview")
    private val play = PackageName("com.android.vending")
    private val approved = PackageName("test.approved")
    private val browser = PackageName("test.browser")
    private val pushed = PackageName("test.pushed")
    private val newSystem = PackageName("test.newsystem")
    private val keyboard = PackageName("test.keyboard")
    private val oem = PackageName("test.oem")
    private fun input() = PolicyInput(
        model = "model", fingerprint = "build", plainphone = home, messages = sms,
        inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, play, oem)), setOf(sms), setOf(PackageName("test.settings"))),
        installed = listOf(home, sms, dialer, emergency, calls, webview, play, keyboard, oem, newSystem).map { InstalledPackage(it, true, 1) } +
            listOf(approved, browser, pushed).map { InstalledPackage(it, false, 1) },
        identities = listOf(
            FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED),
            FloorIdentity(emergency, FloorRole.EMERGENCY_DIALER, IdentitySource.SYSTEM_ONLY_QUERY),
            FloorIdentity(calls, FloorRole.IN_CALL_UI, IdentitySource.OS_MANAGED),
            FloorIdentity(webview, FloorRole.WEBVIEW, IdentitySource.OS_MANAGED),
            FloorIdentity(keyboard, FloorRole.KEYBOARD, IdentitySource.OS_MANAGED),
        ),
        approved = setOf(approved), classifiedBrowsers = setOf(browser),
    )

    @Test fun `supported build sweeps unapproved installs but preserves inventory and floor`() {
        val plan = PolicyEngine.decide(input())
        assertEquals(setOf(browser, pushed, newSystem), plan.hidden)
        assertEquals(setOf(home, sms, approved, dialer, emergency, calls), plan.lockTaskPackages)
        assertFalse(play in plan.hidden)
        assertFalse(play in plan.lockTaskPackages)
        assertTrue(setOf(webview, keyboard, oem).all { it in plan.neverHide })
        assertTrue(setOf(webview, keyboard, oem).none { it in plan.lockTaskPackages })
    }

    @Test fun `all floor identities reject non system and untrusted discovery`() {
        for (role in FloorRole.entries) {
            for (source in IdentitySource.entries) {
                val result = EmergencyFloor.compute(listOf(InstalledPackage(pushed, false, 1)), listOf(FloorIdentity(pushed, role, source)))
                assertFalse(pushed in result.neverHide, "$role $source accepted a user app")
            }
            val result = EmergencyFloor.compute(listOf(InstalledPackage(newSystem, true, 1)), listOf(FloorIdentity(newSystem, role, IdentitySource.INTENT_DECLARATION)))
            assertFalse(newSystem in result.neverHide)
        }
    }

    @Test fun `identity source must match its OS role`() {
        val result = EmergencyFloor.compute(input().installed, listOf(FloorIdentity(emergency, FloorRole.EMERGENCY_DIALER, IdentitySource.OS_MANAGED)))
        assertFalse(emergency in result.neverHide)
    }

    @Test fun `spoof emergency stays hidden in supported unknown and corrupt builds`() {
        for (inventory in listOf(input().inventory, null)) for (health in PolicyHealth.entries) {
            val plan = PolicyEngine.decide(input().copy(inventory = inventory, health = health, previousHidden = setOf(pushed),
                identities = input().identities + FloorIdentity(pushed, FloorRole.EMERGENCY_DIALER, IdentitySource.SYSTEM_ONLY_QUERY)))
            assertTrue(pushed in plan.hidden)
            assertFalse(pushed in plan.lockTaskPackages)
        }
    }

    @Test fun `every destructive operation and component disable is refused for never hide`() {
        val protected = listOf(dialer, emergency, calls, keyboard, oem, sms, play)
        val actions = protected.flatMap { pkg -> DestructiveKind.entries.map { DestructiveRequest(pkg, it) } }
        val plan = PolicyEngine.decide(input().copy(requestedDestruction = actions))
        assertEquals(actions.toSet(), plan.refusedDestruction.toSet())
        assertTrue(plan.allowedDestruction.isEmpty())
        assertTrue(plan.hidden.intersect(protected.toSet()).isEmpty())
    }

    @Test fun `classifier cannot hide never hide or Plainphone even when approved`() {
        val plan = PolicyEngine.decide(input().copy(classifiedBrowsers = setOf(home, sms, dialer, approved)))
        assertFalse(home in plan.hidden)
        assertFalse(sms in plan.hidden)
        assertFalse(dialer in plan.hidden)
        assertTrue(approved in plan.hidden)
        assertFalse(approved in plan.lockTaskPackages)
        assertEquals(setOf(home, sms, dialer), plan.classifierConflicts)
    }

    @Test fun `unknown build preserves previous UI and adds emergency UI only`() {
        val plan = PolicyEngine.decide(input().copy(inventory = null, previousHidden = setOf(emergency, webview, browser),
            previousLockTask = setOf(home, sms, approved), classifiedBrowsers = setOf(browser, newSystem)))
        assertEquals(setOf(browser, pushed), plan.hidden)
        assertEquals(setOf(home, sms, approved, dialer, emergency, calls), plan.lockTaskPackages)
        assertEquals(setOf(emergency, webview), plan.unhide.intersect(setOf(emergency, webview)))
        assertFalse(newSystem in plan.hidden)
        assertEquals(SweepMode.USER_APPS_ONLY, plan.sweepMode)
        assertTrue(plan.notifyBuildReview)
    }

    @Test fun `inventory for different model or fingerprint is unknown`() {
        assertEquals(SweepMode.USER_APPS_ONLY, PolicyEngine.decide(input().copy(model = "other")).sweepMode)
        assertEquals(SweepMode.USER_APPS_ONLY, PolicyEngine.decide(input().copy(fingerprint = "other")).sweepMode)
    }

    @Test fun `supported baseline drops approvals but protects SMS inventory and floor`() {
        val plan = PolicyEngine.decide(input().copy(health = PolicyHealth.BOTH_INVALID))
        assertTrue(plan.needsAttention)
        assertTrue(approved in plan.hidden)
        assertEquals(setOf(home, sms, dialer, emergency, calls), plan.lockTaskPackages)
        assertFalse(oem in plan.hidden)
        assertEquals(Restriction.entries.toSet(), plan.restrictions)
    }

    @Test fun `unknown corrupt baseline never sweeps and keeps previous hides except floor`() {
        val plan = PolicyEngine.decide(input().copy(inventory = null, health = PolicyHealth.BOTH_INVALID,
            previousHidden = setOf(emergency, webview, browser, newSystem), previousLockTask = setOf(home, sms, approved)))
        assertEquals(setOf(browser, newSystem), plan.hidden)
        assertFalse(pushed in plan.hidden) // no new sweep even of user-installed packages
        assertEquals(setOf(home, sms, dialer, emergency, calls), plan.lockTaskPackages)
        assertEquals(SweepMode.NONE, plan.sweepMode)
        assertTrue(plan.notifyBuildReview)
    }

    @Test fun `last known good policy remains usable`() {
        assertEquals(PolicyEngine.decide(input()).hidden, PolicyEngine.decide(input().copy(health = PolicyHealth.LAST_KNOWN_GOOD)).hidden)
    }

    @Test fun `revocation precedes all other writes and each step has readback`() {
        val plan = PolicyEngine.decide(input())
        assertEquals(listOf(ApplyStage.REVOKE_JOURNAL, ApplyStage.RESTRICTIONS, ApplyStage.EMERGENCY_FLOOR,
            ApplyStage.NEVER_HIDE_GUARD, ApplyStage.HIDES, ApplyStage.LOCK_TASK, ApplyStage.HOME), plan.steps.map { it.stage })
        assertTrue(plan.steps.all { it.readbackRequired })
        assertEquals(plan.hidden, plan.expected.hidden)
        assertEquals(plan.lockTaskPackages, plan.expected.lockTaskPackages)
        assertEquals(home, plan.expected.home)
    }

    @Test fun `locked boot does only revocation restrictions floor and guard`() {
        val plan = PolicyEngine.decide(input().copy(lockedBoot = true, previousHidden = setOf(emergency, browser), previousLockTask = setOf(home, sms)))
        assertEquals(listOf(ApplyStage.REVOKE_JOURNAL, ApplyStage.RESTRICTIONS, ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD), plan.steps.map { it.stage })
        assertEquals(setOf(browser), plan.hidden)
        assertEquals(setOf(home, sms, dialer, emergency, calls), plan.lockTaskPackages)
        assertEquals(SweepMode.NONE, plan.sweepMode)
    }

    @Test fun `mandatory features include home keyguard block and never overview`() {
        assertEquals(setOf(LockTaskFeature.HOME, LockTaskFeature.KEYGUARD, LockTaskFeature.GLOBAL_ACTIONS,
            LockTaskFeature.NOTIFICATIONS, LockTaskFeature.SYSTEM_INFO, LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK), PolicyEngine.decide(input()).features)
    }

    @Test fun `missing system dialer yields attention while preserving available floor`() {
        val plan = PolicyEngine.decide(input().copy(identities = input().identities.filter { it.role != FloorRole.SYSTEM_DIALER }))
        assertTrue(plan.needsAttention)
        assertTrue(emergency in plan.lockTaskPackages)
    }

    @Test fun `unknown build records added and changed package versions`() {
        val before = input().installed.filter { it.name != newSystem }.map { if (it.name == sms) it.copy(versionCode = 0) else it }
        val plan = PolicyEngine.decide(input().copy(inventory = null, lastSupportedPackages = before))
        assertEquals(setOf(sms, newSystem), plan.changedPackages)
    }

    @Test fun `plans are deterministic regardless of installed fact ordering`() {
        val a = PolicyEngine.decide(input())
        val b = PolicyEngine.decide(input().copy(installed = input().installed.reversed(), identities = input().identities.reversed()))
        assertEquals(a, b)
    }
    @Test fun `reapproved app is explicitly unhidden and read back`() {
        val plan = PolicyEngine.decide(input().copy(previousHidden = setOf(approved)))
        assertFalse(approved in plan.hidden)
        assertTrue(approved in plan.unhide)
        assertTrue(approved in plan.expected.unhiddenAndEnabled)
    }

    @Test fun `live Play access opens only Play without changing protections`() {
        val access = SessionAccess("session", WindowKind.PLAY, setOf(play), BootTime("boot", 600_000))
        val plan = PolicyEngine.decide(input().copy(sessionAccess = access, currentBoot = BootTime("boot", 0)))
        assertTrue(play in plan.lockTaskPackages)
        assertFalse(play in plan.hidden)
        assertEquals(PolicyEngine.decide(input()).restrictions, plan.restrictions)
    }

    @Test fun `expired rebooted corrupt or locked boot session access grants nothing`() {
        val access = SessionAccess("session", WindowKind.PLAY, setOf(play), BootTime("boot", 600_000))
        val base = input().copy(sessionAccess = access, currentBoot = BootTime("boot", 0))
        listOf(base.copy(currentBoot = BootTime("boot", 600_000)), base.copy(currentBoot = BootTime("other", 0)),
            base.copy(currentBoot = null), base.copy(health = PolicyHealth.BOTH_INVALID), base.copy(lockedBoot = true)).forEach {
            assertFalse(play in PolicyEngine.decide(it).lockTaskPackages)
        }
    }

    @Test fun `session cannot smuggle unlisted packages into lock task`() {
        val access = SessionAccess("session", WindowKind.PLAY, setOf(play, browser), BootTime("boot", 600_000))
        val plan = PolicyEngine.decide(input().copy(sessionAccess = access, currentBoot = BootTime("boot", 0)))
        assertFalse(play in plan.lockTaskPackages)
        assertFalse(browser in plan.lockTaskPackages)
    }

    @Test fun `Plainphone stays in unknown and locked boot UI with no previous list`() {
        for (unknown in listOf(false, true)) for (locked in listOf(false, true)) {
            val plan = PolicyEngine.decide(input().copy(inventory = if (unknown) null else input().inventory,
                lockedBoot = locked, previousLockTask = emptySet(), previousHidden = setOf(home)))
            assertTrue(home in plan.lockTaskPackages, "unknown=$unknown locked=$locked")
            assertFalse(home in plan.hidden)
        }
    }

    @Test fun `ordinary approval cannot open Play without a live session`() {
        val plan = PolicyEngine.decide(input().copy(approved = setOf(play)))
        assertFalse(play in plan.lockTaskPackages)
        assertFalse(play in plan.hidden) // Keep Play's inventory dependency unhidden.
    }

    @Test fun `ordinary approval cannot open Settings or its same UID siblings`() {
        val settings = PackageName("test.settings")
        val sibling = PackageName("test.settings.sibling")
        val inventory = BuildInventory("model", "build", 1, input().inventory!!.neverHide, setOf(sms), setOf(settings, sibling))
        for (target in setOf(settings, sibling)) {
            val plan = PolicyEngine.decide(input().copy(inventory = inventory, approved = setOf(target),
                installed = input().installed + InstalledPackage(target, true, 1)))
            assertFalse(target in plan.lockTaskPackages)
            assertTrue(target in plan.hidden)
            assertFalse(target in plan.unhide)
        }
    }

    @Test fun `session-only approval exclusion still preserves emergency precedence`() {
        val inventory = BuildInventory("model", "build", 1, input().inventory!!.neverHide, setOf(sms), setOf(dialer))
        val plan = PolicyEngine.decide(input().copy(inventory = inventory, approved = setOf(dialer)))
        assertTrue(dialer in plan.lockTaskPackages)
        assertFalse(dialer in plan.hidden)
    }

}
