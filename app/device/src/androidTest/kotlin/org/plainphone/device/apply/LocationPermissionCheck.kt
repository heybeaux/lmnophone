// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import org.plainphone.core.PackageName
import org.plainphone.device.approval.*
import org.plainphone.policy.*

/** Emulator-only five-group check. No calls, sends, or app launches. Uses the fixture in tools/pla564. */
class LocationPermissionCheck(private val context: Context, private val note: (String) -> Unit) {
    fun check() {
        val name = PackageName("org.plainphone.permissionfixture")
        val admin = ComponentName(context, CheckAdmin::class.java)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val port = DpmPermissionGrantPort(context, admin)
        val reader = AndroidAppPermissions(context, admin)
        val selected = checkNotNull(reader.snapshot(name.value))
        check(selected.groups.size == 5) { "expected five groups, got ${selected.groups}" }
        check(name.value !in dpm.getLockTaskPackages(admin))
        check(!dpm.isPackageSuspended(admin, name.value))
        val grants = PermissionGrants(port)
        for (choice in listOf(PermissionChoice.ALLOW, PermissionChoice.DENY)) {
            check(dpm.setApplicationHidden(admin, name.value, false))
            for (p in selected.permissions.sortedBy { permissionDependencyOrder(it, choice == PermissionChoice.ALLOW) }) {
                check(grants.write(selected.identity, PermissionWrite(p, choice)) == GrantWrite.DONE) { "$p $choice failed" }
                note("$p $choice fixed=${port.policyState(name, p)} granted=${port.granted(name, p)}")
            }
            check(dpm.setApplicationHidden(admin, name.value, true))
            val expected = if (choice == PermissionChoice.ALLOW) PermissionReadback.GRANTED else PermissionReadback.DENIED
            check(reader.read(name.value)?.states == selected.permissions.associateWith { expected })
            note("PASS all five groups $choice, read back again hidden")
        }
        check(dpm.setApplicationHidden(admin, name.value, false))
        // A mix of default granted, default denied, and already-fixed grants must survive failure.
        selected.permissions.forEachIndexed { index, p ->
            check(port.setPolicyState(name, p, if (index % 2 == 0 && !p.value.contains("LOCATION")) PolicyGrant.GRANTED else PolicyGrant.DENIED))
            check(port.setPolicyState(name, p, PolicyGrant.DEFAULT))
        }
        val before = selected.permissions.associateWith { port.policyState(name, it) to port.granted(name, it) }
        val fine = PermissionName("android.permission.ACCESS_FINE_LOCATION")
        val refusing = object : PermissionGrantPort by port {
            override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean =
                if (permission == fine) false else port.setPolicyState(name, permission, state)
        }
        val attempt = PermissionGrantAttempts(refusing)
        note("rollback baseline=$before")
        check(attempt.checkpoint(selected)) { "checkpoint failed" }
        val coarse = PermissionName("android.permission.ACCESS_COARSE_LOCATION")
        val choice = if (port.granted(name, coarse) == true) PermissionChoice.DENY else PermissionChoice.ALLOW
        check(attempt.write(selected.identity, PermissionWrite(coarse, choice)).result == GrantWrite.DONE) { "coarse injection write failed" }
        check(attempt.write(selected.identity, PermissionWrite(fine, choice)).result == GrantWrite.NOT_DONE) { "fine injection must fail" }
        check(attempt.rollback(selected)) { "rollback failed: ${selected.permissions.associateWith { port.policyState(name, it) to port.granted(name, it) }}" }
        check(before == selected.permissions.associateWith { port.policyState(name, it) to port.granted(name, it) }) { "restored state differs" }
        check(dpm.setApplicationHidden(admin, name.value, true))
        note("PASS injected FINE refusal restored actual and fixed states; fixture hidden, outside lock task")
    }
}
