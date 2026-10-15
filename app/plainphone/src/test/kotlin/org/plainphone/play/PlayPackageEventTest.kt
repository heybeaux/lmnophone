// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName

class PlayPackageEventTest {
    @Test fun acceptsRealAndroidAddedActionOnly() {
        // PackageEvents uses Intent.action.substringAfterLast('.'), not the constant's Kotlin name.
        val action = "android.intent.action.PACKAGE_ADDED"
        assertEquals(PackageName("test.app"), addedPlayPackage("package:${action.substringAfterLast('.')}:test.app"))
        for (event in listOf("package:PACKAGE_REMOVED:test.app", "package:PACKAGE_CHANGED:test.app", "package:PACKAGE_ADDED:bad/name"))
            assertNull(addedPlayPackage(event))
    }
}
