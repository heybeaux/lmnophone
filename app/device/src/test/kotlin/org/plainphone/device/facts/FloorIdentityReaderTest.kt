// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.IdentitySource

/**
 * device-lockdown, "A runtime emergency floor, from OS-managed identities only": each member comes from
 * the OS source the spec table names and carries the IdentitySource its FloorRole expects.
 */
class FloorIdentityReaderTest {
    private val port = FakePackageSystemPort()
    private fun read() = FloorIdentityReader(port).read()
    private fun id(name: String, role: FloorRole) = FloorIdentity(PackageName(name), role, role.source)

    private fun fullPhone() {
        port.systemDialer = "com.google.android.dialer"
        port.defaultDialer = "com.google.android.dialer"
        port.defaultSms = "com.google.android.apps.messaging"
        port.inCallUi = setOf("com.google.android.dialer")
        port.activities["com.android.phone.EmergencyDialer.DIAL"] = setOf("com.android.phone")
        port.activities["android.intent.action.DIAL_EMERGENCY"] = setOf("com.android.phone", "com.google.android.apps.safetyhub")
        port.activities["android.intent.action.CALL_EMERGENCY tel:"] = emptySet()
        port.receivers["android.provider.action.SMS_EMERGENCY_CB_RECEIVED"] = setOf("com.google.android.cellbroadcastreceiver")
        port.receivers["android.provider.Telephony.SMS_CB_RECEIVED"] = setOf("com.google.android.cellbroadcastreceiver")
        port.activities["android.provider.Telephony.SMS_CB_RECEIVED"] = setOf("com.google.android.cellbroadcastreceiver")
        port.webView = "com.google.android.webview"
        port.imes = setOf("com.google.android.inputmethod.latin")
        port.activities["android.content.pm.action.REQUEST_PERMISSIONS"] = setOf("com.google.android.permissioncontroller")
    }

    @Test fun everyRoleIsReadFromItsOsSourceAndTaggedWithTheSourceItsRoleExpects() {
        fullPhone()
        val floor = read()
        assertEquals(setOf(
            id("com.google.android.dialer", FloorRole.SYSTEM_DIALER),
            id("com.google.android.dialer", FloorRole.DEFAULT_DIALER),
            id("com.google.android.dialer", FloorRole.IN_CALL_UI),
            id("com.google.android.apps.messaging", FloorRole.SMS),
            id("com.android.phone", FloorRole.EMERGENCY_DIALER),
            id("com.google.android.apps.safetyhub", FloorRole.EMERGENCY_DIALER),
            id("com.google.android.cellbroadcastreceiver", FloorRole.CELL_BROADCAST_RECEIVER),
            id("com.google.android.cellbroadcastreceiver", FloorRole.CELL_BROADCAST_DISPLAY),
            id("com.google.android.webview", FloorRole.WEBVIEW),
            id("com.google.android.inputmethod.latin", FloorRole.KEYBOARD),
            id("com.google.android.permissioncontroller", FloorRole.PERMISSION_CONTROLLER),
        ), floor.identities.toSet())
        assertTrue(floor.identities.all { it.source == it.role.source })
        assertEquals(floor.identities.size, floor.identities.toSet().size)
        assertTrue(floor.unreadable.isEmpty())
    }

    @Test fun dialerRolesComeFromTelecomAndRoleHoldersOnly() {
        port.systemDialer = "com.android.dialer"
        port.defaultDialer = "com.example.thirdpartydialer"
        port.defaultSms = "com.example.sms"
        val floor = read().identities
        assertTrue(id("com.android.dialer", FloorRole.SYSTEM_DIALER) in floor)
        assertTrue(id("com.example.thirdpartydialer", FloorRole.DEFAULT_DIALER) in floor)
        assertTrue(id("com.example.sms", FloorRole.SMS) in floor)
        assertTrue(port.calls.containsAll(listOf("systemDialer", "defaultDialer", "defaultSms")))
    }

    @Test fun inCallUiIsTheSystemDialerOnlyWhenItDeclaresAnInCallUiService() {
        port.systemDialer = "com.android.dialer"
        port.inCallUi = setOf("com.example.carui")
        assertTrue(read().identities.none { it.role == FloorRole.IN_CALL_UI })
        port.inCallUi = setOf("com.example.carui", "com.android.dialer")
        assertEquals(listOf(id("com.android.dialer", FloorRole.IN_CALL_UI)), read().identities.filter { it.role == FloorRole.IN_CALL_UI })
    }

    @Test fun noSystemDialerMeansNoInCallUi() {
        port.inCallUi = setOf("com.android.dialer")
        assertTrue(read().identities.none { it.role == FloorRole.IN_CALL_UI })
    }

    @Test fun emergencyDialerAndCellBroadcastUseSystemOnlyQueriesNotNames() {
        port.activities["android.intent.action.CALL_EMERGENCY tel:"] = setOf("com.oem.emergency")
        port.receivers["android.provider.Telephony.SMS_CB_RECEIVED"] = setOf("com.oem.cb")
        val floor = read().identities
        assertTrue(FloorIdentity(PackageName("com.oem.emergency"), FloorRole.EMERGENCY_DIALER, IdentitySource.SYSTEM_ONLY_QUERY) in floor)
        assertTrue(FloorIdentity(PackageName("com.oem.cb"), FloorRole.CELL_BROADCAST_RECEIVER, IdentitySource.SYSTEM_ONLY_QUERY) in floor)
        assertTrue(port.calls.contains("systemOnlyActivities") && port.calls.contains("systemOnlyReceivers"))
    }

    @Test fun aRefusedSourceIsReportedAndTheOtherRolesAreStillRead() {
        fullPhone()
        port.failing += "defaultSms"
        port.failing += "systemOnlyReceivers"
        val floor = read()
        assertEquals(setOf(FloorRole.SMS, FloorRole.CELL_BROADCAST_RECEIVER), floor.unreadable)
        assertTrue(floor.identities.none { it.role == FloorRole.SMS || it.role == FloorRole.CELL_BROADCAST_RECEIVER })
        assertTrue(id("com.google.android.dialer", FloorRole.SYSTEM_DIALER) in floor.identities)
        assertTrue(id("com.google.android.cellbroadcastreceiver", FloorRole.CELL_BROADCAST_DISPLAY) in floor.identities)
    }

    @Test fun aRefusedSystemDialerAlsoMakesInCallUiUnreadable() {
        fullPhone()
        port.failing += "systemDialer"
        val floor = read()
        assertEquals(setOf(FloorRole.SYSTEM_DIALER, FloorRole.IN_CALL_UI), floor.unreadable)
    }

    @Test fun anEmptyPhoneHasAnEmptyFloor() {
        val floor = read()
        assertTrue(floor.identities.isEmpty())
        assertTrue(floor.unreadable.isEmpty())
    }
}
