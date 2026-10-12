// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.update

import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.util.Log
import org.plainphone.PlainphoneAdminReceiver

/** Android's system update policy types, plus NONE (no policy set). */
enum class UpdatePolicyKind { NONE, AUTOMATIC, WINDOWED, POSTPONE, OTHER }

/** What Android holds: the policy type and how many freeze periods it carries. */
data class UpdatePolicyRead(val kind: UpdatePolicyKind, val freezePeriods: Int)

interface SystemUpdatePolicyPort {
    fun isDeviceOwner(): Boolean
    fun read(): UpdatePolicyRead
    fun write(kind: UpdatePolicyKind)
    /** `setSystemUpdatePolicy(admin, null)`. */
    fun clear()
}

enum class PostponeStatus { NOT_OWNER, ALREADY_SET, SET, MISMATCH, FAILED, RELEASED }

/**
 * device-lockdown, "Delaying updates": as Device Owner, hold `createPostponeInstallPolicy()` (no freeze
 * periods), which delays updates up to Android's limit. Writes only when the readback differs, then reads
 * back. A mismatch or failure is logged as a plain status and tried again at the next re-apply; it never
 * throws. Logs carry the status and policy shape only, never exception text or device details.
 *
 * [release] is the test driver's undo: API 35 keeps the policy after `dpm remove-active-admin` and a reboot,
 * so it is cleared while still Device Owner, and re-applies leave it cleared until the process restarts.
 */
class SystemUpdatePostpone(private val port: SystemUpdatePolicyPort, private val log: (String) -> Unit) {
    @Volatile private var released = false

    fun apply(): PostponeStatus {
        if (released) return PostponeStatus.RELEASED
        val status = try {
            when {
                !port.isDeviceOwner() -> return PostponeStatus.NOT_OWNER
                port.read() == TARGET -> return PostponeStatus.ALREADY_SET
                else -> {
                    port.write(TARGET.kind)
                    val after = port.read()
                    if (after == TARGET) PostponeStatus.SET
                    else PostponeStatus.MISMATCH.also {
                        log("system update policy readback mismatch: kind=${after.kind} freezePeriods=${after.freezePeriods}; retry at next re-apply")
                    }
                }
            }
        } catch (e: RuntimeException) {
            log("system update policy failed: ${e.javaClass.simpleName}; retry at next re-apply")
            return PostponeStatus.FAILED
        }
        if (status == PostponeStatus.SET) log("system update policy set: postpone")
        return status
    }

    /**
     * Clears the policy and, once it reads back as none, stops [apply] for the rest of this process. If it
     * still reads back as set, or the clear or readback fails, re-applies stay on and the status says so.
     */
    fun release(): PostponeStatus = try {
        if (!port.isDeviceOwner()) PostponeStatus.NOT_OWNER
        else {
            port.clear()
            val after = port.read()
            if (after.kind == UpdatePolicyKind.NONE) {
                released = true
                log("system update policy released: cleared until restart")
                PostponeStatus.RELEASED
            } else {
                log("system update policy release readback mismatch: kind=${after.kind} freezePeriods=${after.freezePeriods}")
                PostponeStatus.MISMATCH
            }
        }
    } catch (e: RuntimeException) {
        log("system update policy release failed: ${e.javaClass.simpleName}")
        PostponeStatus.FAILED
    }

    companion object {
        val TARGET = UpdatePolicyRead(UpdatePolicyKind.POSTPONE, 0)

        fun forDevice(context: Context) = SystemUpdatePostpone(
            DpmSystemUpdatePolicyPort(context, ComponentName(context, PlainphoneAdminReceiver::class.java)),
        ) { Log.i(TAG, it) }

        const val TAG = "PlainphoneUpdate"
    }
}

class DpmSystemUpdatePolicyPort(private val context: Context, private val admin: ComponentName) : SystemUpdatePolicyPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)

    override fun isDeviceOwner() = dpm.isDeviceOwnerApp(context.packageName)

    override fun read(): UpdatePolicyRead {
        val policy = dpm.systemUpdatePolicy ?: return UpdatePolicyRead(UpdatePolicyKind.NONE, 0)
        val kind = when (policy.policyType) {
            SystemUpdatePolicy.TYPE_INSTALL_AUTOMATIC -> UpdatePolicyKind.AUTOMATIC
            SystemUpdatePolicy.TYPE_INSTALL_WINDOWED -> UpdatePolicyKind.WINDOWED
            SystemUpdatePolicy.TYPE_POSTPONE -> UpdatePolicyKind.POSTPONE
            else -> UpdatePolicyKind.OTHER
        }
        return UpdatePolicyRead(kind, policy.freezePeriods.size)
    }

    override fun write(kind: UpdatePolicyKind) {
        require(kind == UpdatePolicyKind.POSTPONE) { "only postpone is written" }
        dpm.setSystemUpdatePolicy(admin, SystemUpdatePolicy.createPostponeInstallPolicy())
    }

    override fun clear() = dpm.setSystemUpdatePolicy(admin, null)
}
