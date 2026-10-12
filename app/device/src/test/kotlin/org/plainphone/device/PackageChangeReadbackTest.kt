// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device
import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName

class PackageChangeReadbackTest {
    private val app = PackageName("example.system")
    private inner class Fixture(val hidden: Boolean = true) {
        val port = FakeDevicePolicyPort(mapOf(app.value to PackageState(hidden, false, true)))
        var protected = emptySet<PackageName>()
        var launcher = hidden
        var clear = true
        var throws = false
        val readback = PackageChangeReadback(port, { if (throws) error("floor") else protected },
            { launcher }, { clear })
        fun capture(excluded: Set<PackageName> = emptySet()) = readback.capture(
            setOf(app), if (hidden) setOf(app) else emptySet(), excluded, emptySet())
    }
    @Test fun hiddenAndNonLauncherSystemPackagesMatchWithoutWrites() {
        for (hidden in listOf(false, true)) {
            val f = Fixture(hidden); f.capture()
            assertTrue(f.readback.unchanged(app)); assertTrue(f.port.ops.isEmpty())
        }
    }
    @Test fun everyStateMismatchAbsentNewRoleJournalAndReadErrorFailsClosed() {
        val changes: List<(Fixture) -> Unit> = listOf(
            { it.port.state[app] = PackageState(false, false, true) },
            { it.port.state[app] = PackageState(true, true, true) },
            { it.port.state[app] = PackageState(true, false, false) },
            { it.port.state.remove(app) }, { it.port.setLockTaskPackages(setOf(app)) },
            { it.protected = setOf(app) }, { it.clear = false }, { it.throws = true })
        for (change in changes) {
            val f = Fixture(); f.capture(); change(f)
            assertFalse(f.readback.unchanged(app))
        }
    }
    @Test fun protectedUnknownAndInvalidatedPackagesNeverSkip() {
        val f = Fixture(); f.capture(setOf(app)); assertFalse(f.readback.unchanged(app))
        f.capture(); assertFalse(f.readback.unchanged(PackageName("example.unknown")))
        f.readback.invalidate(); assertFalse(f.readback.unchanged(app))
    }
    @Test fun newlyEnabledLauncherNeedsFullPlanEvenWithUnchangedFlags() {
        val f = Fixture(false); f.capture(); f.launcher = true
        assertFalse(f.readback.unchanged(app))
    }
    @Test fun failedCaptureCannotKeepOldEvidenceOrAcceptWrongHiddenState() {
        val f = Fixture(); f.capture(); f.throws = true; f.capture(); f.throws = false
        assertFalse(f.readback.unchanged(app))
        f.port.state[app] = PackageState(false, false, true); f.capture()
        assertFalse(f.readback.unchanged(app))
    }
}
