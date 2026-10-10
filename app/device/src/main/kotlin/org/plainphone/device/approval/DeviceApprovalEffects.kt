// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.SerialDispatcher
import org.plainphone.policy.AppApprovalEffects
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionWrite
import org.plainphone.policy.PolicyPlan

/**
 * The apps Plainphone Home lists. Removal is the only change outside a committed policy: only
 * [publish], from a complete policy apply, can list an app again.
 */
interface HomeAppList {
    fun remove(name: PackageName)
    /** Whether Home lists [name] now; null when it cannot be read. */
    fun lists(name: PackageName): Boolean?
    /** Lists exactly [names] (the committed policy's lock-task packages). */
    fun publish(names: Set<PackageName>)
}

/** The in-process [HomeAppList] that Home reads. */
class PublishedHomeApps : HomeAppList {
    @Volatile var listed: Set<PackageName> = emptySet()
        private set
    override fun remove(name: PackageName) { listed = listed - name }
    override fun lists(name: PackageName): Boolean = name in listed
    override fun publish(names: Set<PackageName>) { listed = names.toSet() }
}

/**
 * [AppApprovalEffects] on the device (PLA-186). API 35 refuses grant writes to a hidden app
 * (`app/device/evidence/pla86/hidden-permissions-api35.txt`), so permissions are written in a short
 * permission window: the app is taken out of applied lock task and Home's list, both read back, and
 * only then unhidden. It is visible for that moment but cannot be opened from Plainphone; its own
 * background code may run (for example on `ACTION_PACKAGE_ADDED`). Closing re-hides it and never gives
 * lock task or Home back; only the committed [applyPolicy] does that.
 *
 * Every write runs only on [dispatcher] (otherwise it returns false and touches nothing), so a package
 * event handler on the same dispatcher cannot re-hide the app inside the window. Every hide, window
 * open and window close checks the live never-hide list first; unreadable means refuse. Nothing is
 * suspended: a suspended app misreports sensor grants, so open fails for one.
 */
class DeviceApprovalEffects(
    private val dispatcher: SerialDispatcher,
    private val port: DevicePolicyPort,
    grantPort: PermissionGrantPort,
    private val home: HomeAppList,
    private val neverHide: () -> Set<PackageName>?,
    private val manifest: (PackageName) -> PermissionManifest?,
) : AppApprovalEffects {
    private val grants = PermissionGrants(grantPort)
    private val signers: (PackageName) -> Set<org.plainphone.core.CertDigest>? = { attempt { grantPort.signers(it) } }
    private val applier = ReadbackPolicyApplier(port)
    /** The identity whose window is open now; written only on the dispatcher. */
    private var open: ApprovalBinding? = null

    override fun hideAndReadBack(identity: ApprovalBinding): Boolean = serialized { hide(identity.packageName) }

    override fun openPermissionWindow(identity: ApprovalBinding): Boolean = serialized {
        open = null
        val name = identity.packageName
        if (!mayHide(name)) return@serialized false
        val opened = attempt {
            val before = port.lockTaskPackages()
            if (name in before) port.setLockTaskPackages(before - name)
            if (port.lockTaskPackages() != before - name) return@attempt false
            home.remove(name)
            if (home.lists(name) != false) return@attempt false
            // Only the exact identity becomes visible.
            if (signers(name)?.let { identity.matches(name, it) } != true) return@attempt false
            val now = port.packageState(name) ?: return@attempt false
            if (now.suspended) return@attempt false
            if (now.hidden) {
                port.setHidden(name, false)
                if (!flush()) return@attempt false
            }
            port.packageState(name)?.let { !it.hidden && !it.suspended } == true
        } == true
        if (opened) open = identity
        opened
    }

    override fun closePermissionWindow(identity: ApprovalBinding): Boolean = serialized {
        open = null
        hide(identity.packageName)
    }

    override fun setPermission(identity: ApprovalBinding, write: PermissionWrite): Boolean = serialized {
        open == identity && grants.set(identity, write)
    }

    /** Works hidden or visible; the permissions are the live manifest's. */
    override fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback> {
        val live = attempt { manifest(identity.packageName) } ?: return emptyMap()
        return grants.read(identity, live.permissions.map { it.name })
    }

    override fun applyPolicy(plan: PolicyPlan): Boolean = serialized {
        val result = attempt { applier.apply(plan) } ?: return@serialized false
        if (result.needsAttention) return@serialized false
        plan.expected.lockTaskPackages?.let { attempt { home.publish(it) } ?: return@serialized false }
        true
    }

    override fun readBackPolicy(plan: PolicyPlan): Boolean = attempt { applier.readBack(plan) } == true

    private fun hide(name: PackageName): Boolean {
        if (!mayHide(name)) return false
        return attempt {
            if (port.packageState(name)?.hidden == false) port.setHidden(name, true)
            port.packageState(name)?.hidden != false
        } == true
    }

    private fun mayHide(name: PackageName): Boolean = attempt { neverHide() }?.let { name !in it } == true

    /** One real flip of the flush component, read back, retried once (mitigation B, as in the applier). */
    private fun flush(): Boolean {
        repeat(2) {
            val before = attempt { port.flushComponentEnabled() } ?: return false
            if (attempt { port.setFlushComponentEnabled(!before); port.flushComponentEnabled() == !before } == true) return true
        }
        return false
    }

    private inline fun serialized(block: () -> Boolean): Boolean = dispatcher.onDispatcher && block()

    private inline fun <T> attempt(call: () -> T): T? = try {
        call()
    } catch (_: RuntimeException) {
        null
    }
}
