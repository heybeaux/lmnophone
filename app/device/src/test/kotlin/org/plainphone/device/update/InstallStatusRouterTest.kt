// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStatusRouterTest {
    private val routed = mutableListOf<Pair<Int, Boolean>>()
    private val router = InstallStatusRouter { id, success -> routed += id to success }

    @Test fun successIsRoutedAsSuccess() {
        assertTrue(router.route(7, PackageInstaller.STATUS_SUCCESS))
        assertEquals(listOf(7 to true), routed)
    }

    @Test fun everyTerminalFailureIsRoutedAsFailure() {
        val failures = listOf(PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_TIMEOUT)
        failures.forEach { assertTrue(router.route(3, it)) }
        assertEquals(failures.map { 3 to false }, routed)
    }

    @Test fun pendingUserActionIsNeverRouted() {
        assertFalse(router.route(7, PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertTrue(routed.isEmpty())
    }

    @Test fun unknownStatusesAndMissingSessionsAreNotRouted() {
        assertFalse(router.route(7, 99))
        assertFalse(router.route(7, -2))
        assertFalse(router.route(7, InstallStatusRouter.MISSING))
        assertFalse(router.route(-1, PackageInstaller.STATUS_SUCCESS))
        assertTrue(routed.isEmpty())
    }
}
