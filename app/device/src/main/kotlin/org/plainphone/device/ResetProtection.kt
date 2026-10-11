// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.plainphone.policy.PeopleResetProtectionEffects

/** The reset-protection policy as Android reports it. */
data class ResetProtectionReading(val accounts: List<String>, val enabled: Boolean)

/** The three platform calls behind reset protection. Writes may throw. */
interface ResetProtectionPort {
    fun setPolicy(accounts: List<String>)
    /** Null when no policy is set. */
    fun readPolicy(): ResetProtectionReading?
    /** Tells Play services the policy changed. Returning normally means it was sent, not that it was handled. */
    fun sendConfigChanged()
}

/**
 * The exact reset-protection list (holders plus backup) as synchronous, idempotent effects.
 * Readback checks that Android holds exactly this list, enabled. It says nothing about which
 * Google account a real wipe would ask for. An empty or blank list is refused: Android would
 * fall back to the accounts on the phone, which could include the user's own.
 */
class ResetProtectionEffects(private val port: ResetProtectionPort) : PeopleResetProtectionEffects {
    override fun setResetProtection(userIds: Set<String>): Boolean {
        if (!valid(userIds)) return false
        return attempt { port.setPolicy(userIds.sorted()) }
    }

    override fun broadcastResetProtectionChanged(): Boolean = attempt { port.sendConfigChanged() }

    override fun readBackResetProtection(userIds: Set<String>): Boolean {
        if (!valid(userIds)) return false
        val reading = try { port.readPolicy() } catch (_: RuntimeException) { null } ?: return false
        return reading.enabled && reading.accounts.size == userIds.size && reading.accounts.toSet() == userIds
    }

    private fun valid(ids: Set<String>) = ids.isNotEmpty() && ids.none { id -> id.isBlank() || id.any(Char::isWhitespace) }

    private fun attempt(block: () -> Unit): Boolean = try { block(); true } catch (_: RuntimeException) { false }
}

/**
 * [ResetProtectionPort] for a Device Owner [admin]: `setFactoryResetProtectionPolicy` (API 30+),
 * then `com.google.android.gms.auth.FRP_CONFIG_CHANGED` sent explicitly to Play services, as the
 * device-lockdown spec requires. Without Play services the broadcast has no receiver and is still "sent".
 */
class DpmResetProtectionPort(private val context: Context, private val admin: ComponentName) : ResetProtectionPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)

    override fun setPolicy(accounts: List<String>) {
        dpm.setFactoryResetProtectionPolicy(admin, FactoryResetProtectionPolicy.Builder()
            .setFactoryResetProtectionAccounts(accounts).setFactoryResetProtectionEnabled(true).build())
    }

    override fun readPolicy(): ResetProtectionReading? = dpm.getFactoryResetProtectionPolicy(admin)?.let {
        ResetProtectionReading(it.factoryResetProtectionAccounts.toList(), it.isFactoryResetProtectionEnabled)
    }

    override fun sendConfigChanged() {
        context.sendBroadcast(Intent(FRP_CONFIG_CHANGED).setPackage(PLAY_SERVICES).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
    }

    companion object {
        const val FRP_CONFIG_CHANGED = "com.google.android.gms.auth.FRP_CONFIG_CHANGED"
        const val PLAY_SERVICES = "com.google.android.gms"
    }
}
