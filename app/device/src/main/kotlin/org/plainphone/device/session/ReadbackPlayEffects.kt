// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.session

import org.plainphone.core.PackageName
import org.plainphone.core.WindowKind
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.policy.*

/**
 * Applies plans supplied by policy; it never adds discretionary packages to a plan itself.
 * Only Play may differ between the baseline and opening plan. Serialized with the controllers.
 * The ready preview baseline hides Play and has already lifted fallback suspensions. Until a
 * durable suspension-restore protocol is supplied, suspended/disabled Play refuses opening.
 */
class ReadbackPlayEffects(
    private val port: DevicePolicyPort,
    private val decide: (SessionAccess?) -> PolicyPlan,
) : SessionEffects {
    private val applier = ReadbackPolicyApplier(port)
    private var current: PolicyPlan? = null
    private var baseline: PolicyPlan? = null
    private var verified = false

    override fun open(access: SessionAccess): Boolean = attempt {
        verified = false
        current = null
        if (access.kind != WindowKind.PLAY || access.packages != setOf(PLAY)) return@attempt false
        val base = decide(null)
        baseline = base
        val opening = decide(access)
        if (!onlyPlayChanges(base, opening) || !applier.readBack(base)) return@attempt false
        val before = port.packageState(PLAY) ?: return@attempt false
        if (before.suspended || !before.enabled) return@attempt false
        current = opening
        val result = applier.apply(opening)
        verified = result.complete && result.flipFailures.isEmpty() && result.unrepairable.isEmpty() &&
            applier.disabled(opening).isEmpty() && applier.readBack(opening) &&
            port.packageState(PLAY)?.let { !it.hidden && !it.suspended && it.enabled } == true
        verified
    }

    /** A package event re-reads the preview baseline and hides new candidates without reopening access. */
    fun refresh(access: SessionAccess): Boolean = attempt {
        val base = decide(null)
        val opening = decide(access)
        if (!onlyPlayChanges(base, opening)) return@attempt false
        baseline = base
        current = opening
        val result = applier.apply(opening)
        verified = result.complete && result.flipFailures.isEmpty() && result.unrepairable.isEmpty() &&
            applier.disabled(opening).isEmpty() && applier.readBack(opening)
        verified
    }

    override fun revokeAllRelaxations(): Boolean {
        verified = false
        current = null
        val ok = attempt {
            // Restrictive even if facts cannot be read. Never widen from a cached opening plan.
            val now = port.lockTaskPackages()
            if (PLAY in now) port.setLockTaskPackages(now - PLAY)
            val base = decide(null)
            baseline = base
            current = base
            if (PLAY in base.lockTaskPackages) return@attempt false
            if (PLAY in base.hidden && PLAY !in base.neverHide && PLAY !in base.emergencyUi &&
                port.packageHidden(PLAY) == false) port.setHidden(PLAY, true)
            PLAY !in port.lockTaskPackages() &&
                (PLAY !in base.hidden || port.packageHidden(PLAY) != false)
        }
        // Calls and alerts never wait for cleanup of discretionary access.
        if (!ok) baseline?.let { attempt { applier.repairEmergency(it); true } }
        return ok
    }

    override fun protectedStateCheck(): Boolean {
        val plan = current ?: return false
        val ok = attempt {
            val result = applier.apply(plan)
            result.complete && result.flipFailures.isEmpty() && result.unrepairable.isEmpty() &&
                applier.disabled(plan).isEmpty()
        }
        if (!ok) attempt { applier.repairEmergency(plan); true }
        verified = ok
        return ok
    }

    override fun readBackPolicy(): Boolean = attempt {
        val plan = current ?: return@attempt false
        verified && applier.readBack(plan) && applier.disabled(plan).isEmpty()
    }

    private fun onlyPlayChanges(base: PolicyPlan, opening: PolicyPlan): Boolean =
        !base.needsAttention && !opening.needsAttention &&
            PLAY !in base.lockTaskPackages &&
            opening.lockTaskPackages == base.lockTaskPackages + PLAY &&
            opening.hidden == base.hidden - PLAY && opening.unhide == base.unhide + PLAY &&
            opening.neverHide == base.neverHide && opening.emergencyUi == base.emergencyUi &&
            opening.features == base.features && opening.restrictions == base.restrictions &&
            opening.steps == base.steps &&
            opening.expected.unsuspended.containsAll(base.expected.unsuspended) &&
            (opening.expected.unsuspended - base.expected.unsuspended).all { it == PLAY } &&
            opening.expected == base.expected.copy(
                hidden = base.expected.hidden?.minus(PLAY),
                unhiddenAndEnabled = base.expected.unhiddenAndEnabled + PLAY,
                unsuspended = opening.expected.unsuspended,
                lockTaskPackages = base.expected.lockTaskPackages?.plus(PLAY),
            )

    private inline fun attempt(block: () -> Boolean): Boolean = try { block() } catch (_: Exception) { false }
    companion object { private val PLAY = PackageName("com.android.vending") }
}
