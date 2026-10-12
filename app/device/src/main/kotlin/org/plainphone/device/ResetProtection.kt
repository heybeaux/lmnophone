// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.plainphone.device.boot.RestrictionFilter
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

    private fun valid(ids: Set<String>) = validResetIds(ids)

    private fun attempt(block: () -> Unit): Boolean = try { block(); true } catch (_: RuntimeException) { false }
}

/**
 * The reset-protection effects this build may use: [WithheldResetProtection] when [filter] withholds
 * DISALLOW_FACTORY_RESET (test builds), else the real [ResetProtectionEffects] over [port].
 * [withheld] is told the account count of each withheld list, never the IDs.
 */
fun resetProtectionFor(filter: RestrictionFilter, port: ResetProtectionPort, withheld: (Int) -> Unit): PeopleResetProtectionEffects =
    if (filter.withholdsResetProtection) WithheldResetProtection(withheld) else ResetProtectionEffects(port)

/**
 * Test builds: the reset-protection write is withheld, like DISALLOW_FACTORY_RESET (Lead decision on
 * PLA-92). Nothing reaches the platform: no set, no broadcast, no read. A valid list is recorded by its
 * account count only and reported to [withheld]; broadcast and readback then succeed for that recorded
 * list, so a change can proceed on a test phone, but the policy is never set and never reported as
 * applied. Invalid lists are refused exactly as [ResetProtectionEffects] refuses them.
 */
class WithheldResetProtection(private val withheld: (Int) -> Unit) : PeopleResetProtectionEffects {
    /** The account count of the last withheld list; null before any. */
    @Volatile var withheldAccounts: Int? = null
        private set

    override fun setResetProtection(userIds: Set<String>): Boolean {
        if (!validResetIds(userIds)) return false
        withheldAccounts = userIds.size
        withheld(userIds.size)
        return true
    }

    override fun broadcastResetProtectionChanged(): Boolean = withheldAccounts != null

    override fun readBackResetProtection(userIds: Set<String>): Boolean =
        validResetIds(userIds) && withheldAccounts == userIds.size
}

private fun validResetIds(ids: Set<String>) = ids.isNotEmpty() && ids.none { id -> id.isBlank() || id.any(Char::isWhitespace) }

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
