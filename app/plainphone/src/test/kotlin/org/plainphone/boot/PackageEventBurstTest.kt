// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot
import org.junit.Assert.*
import org.junit.Test

class PackageEventBurstTest {
    @Test fun eventBeforeCaptureFinishesRequiresFullPassEvenWhenDispatchedAfterCapture() {
        val tasks = mutableListOf<() -> Unit>()
        var captured = false
        val eligibility = mutableListOf<Boolean>()
        val burst = PackageEventBurst({ tasks += it }, { _, maySkip -> eligibility += maySkip }, { captured })
        burst.add("package:PACKAGE_CHANGED:one")
        captured = true
        burst.add("package:PACKAGE_CHANGED:one")
        tasks.removeAt(0)()
        assertEquals(listOf(false), eligibility)
        burst.add("package:PACKAGE_CHANGED:one")
        tasks.removeAt(0)()
        assertEquals(listOf(false, true), eligibility)
    }

    @Test fun mixedPackagesOrActionsAlwaysRequireFullPass() {
        for (other in listOf("package:PACKAGE_CHANGED:two", "package:PACKAGE_ADDED:one",
            "package:PACKAGE_REMOVED:one", "package:PACKAGE_REPLACED:one")) {
            val tasks = mutableListOf<() -> Unit>()
            val handled = mutableListOf<String>()
            val burst = PackageEventBurst({ tasks += it }, { reason, _ -> handled += reason })
            burst.add("package:PACKAGE_CHANGED:one"); burst.add(other)
            assertEquals(1, tasks.size)
            tasks.removeAt(0)()
            assertEquals(listOf("package-burst"), handled)
        }
    }
    @Test fun identicalEventsCoalesceAndEventsDuringRunGetAnotherPass() {
        val tasks = mutableListOf<() -> Unit>()
        val handled = mutableListOf<String>()
        lateinit var burst: PackageEventBurst
        burst = PackageEventBurst({ tasks += it }, { reason, _ ->
            handled += reason
            if (handled.size == 1) burst.add("package:PACKAGE_CHANGED:two")
        })
        repeat(2) { burst.add("package:PACKAGE_CHANGED:one") }
        tasks.removeAt(0)(); tasks.removeAt(0)()
        assertEquals(listOf("package:PACKAGE_CHANGED:one", "package:PACKAGE_CHANGED:two"), handled)
    }
}
