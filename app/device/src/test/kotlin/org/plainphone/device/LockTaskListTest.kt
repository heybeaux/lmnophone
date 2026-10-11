// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Test
import org.plainphone.core.PackageName

/**
 * PLA-233 AVD finding: after the lock-task list was cleared and the feature mask set, API 35's
 * getLockTaskPackages returned an entry that is not a package name, and building PackageName from it
 * threw. That broke the revoke and the emergency floor at every re-apply until fixed.
 */
class LockTaskListTest {
    @Test fun blankEntriesAreDropped() {
        assertEquals(emptySet<PackageName>(), lockTaskPackageNames(arrayOf("")))
        assertEquals(setOf(PackageName("org.plainphone")), lockTaskPackageNames(arrayOf("", "org.plainphone", " ")))
    }

    @Test fun validEntriesAreKept() {
        assertEquals(setOf(PackageName("com.android.phone"), PackageName("org.plainphone")),
            lockTaskPackageNames(arrayOf("com.android.phone", "org.plainphone")))
    }
}
