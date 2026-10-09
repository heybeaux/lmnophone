// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.plainphone.core.PackageName
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole

/**
 * Floor candidates for one pass.
 * @property unreadable roles whose OS source threw. Their members are missing from [identities].
 */
data class FloorRead(val identities: List<FloorIdentity>, val unreadable: Set<FloorRole>)

/**
 * Reads emergency-floor candidates from OS-managed sources only (device-lockdown, "A runtime emergency
 * floor, from OS-managed identities only"), each tagged with the source its [FloorRole] expects.
 * Inventories and policy files are never consulted. Whether a candidate is a system app is decided by
 * `EmergencyFloor.compute` against the live package list, not here.
 */
class FloorIdentityReader(private val port: PackageSystemPort) {
    fun read(): FloorRead {
        val identities = mutableListOf<FloorIdentity>()
        val unreadable = mutableSetOf<FloorRole>()
        fun add(role: FloorRole, read: () -> Collection<String?>) {
            val names = try {
                read()
            } catch (e: RuntimeException) {
                unreadable += role
                return
            }
            names.filterNotNull().distinct().forEach { identities += FloorIdentity(PackageName(it), role, role.source) }
        }

        var systemDialer: String? = null
        add(FloorRole.SYSTEM_DIALER) { listOf(port.systemDialer().also { systemDialer = it }) }
        add(FloorRole.DEFAULT_DIALER) { listOf(port.defaultDialer()) }
        if (FloorRole.SYSTEM_DIALER in unreadable) unreadable += FloorRole.IN_CALL_UI
        else add(FloorRole.IN_CALL_UI) { listOfNotNull(systemDialer?.takeIf { it in port.systemInCallUiPackages() }) }
        add(FloorRole.SMS) { listOf(port.defaultSms()) }
        add(FloorRole.EMERGENCY_DIALER) {
            port.systemOnlyActivities(EMERGENCY_DIAL) + port.systemOnlyActivities(DIAL_EMERGENCY) +
                port.systemOnlyActivities(CALL_EMERGENCY, "tel:")
        }
        add(FloorRole.CELL_BROADCAST_RECEIVER) { port.systemOnlyReceivers(EMERGENCY_CB_RECEIVED) + port.systemOnlyReceivers(CB_RECEIVED) }
        add(FloorRole.CELL_BROADCAST_DISPLAY) { port.systemOnlyActivities(CB_RECEIVED) }
        add(FloorRole.WEBVIEW) { listOf(port.webViewProvider()) }
        add(FloorRole.KEYBOARD) { port.enabledInputMethods() }
        add(FloorRole.PERMISSION_CONTROLLER) { port.systemOnlyActivities(REQUEST_PERMISSIONS) }
        return FloorRead(identities, unreadable)
    }

    companion object {
        /** The emergency-dialer screen of `com.android.phone` (PLA-5 A3). */
        const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"
        const val DIAL_EMERGENCY = "android.intent.action.DIAL_EMERGENCY"
        const val CALL_EMERGENCY = "android.intent.action.CALL_EMERGENCY"
        const val EMERGENCY_CB_RECEIVED = "android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
        /** Received by the cell broadcast receiver and resolved by its alert activity. */
        const val CB_RECEIVED = "android.provider.Telephony.SMS_CB_RECEIVED"
        const val REQUEST_PERMISSIONS = "android.content.pm.action.REQUEST_PERMISSIONS"
    }
}
