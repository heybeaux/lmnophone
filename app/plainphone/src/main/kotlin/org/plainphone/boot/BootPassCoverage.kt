// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.device.boot.BootOutcome
import org.plainphone.policy.ApplyStage

/** Process-local coverage, used only on the serialized policy dispatcher. */
internal class BootPassCoverage {
    private data class Pass(val id: Long, val outcome: BootOutcome?)
    private var last: Pass? = null
    private var nextId = 0L

    fun invalidate() { last = null }

    /**
     * [locked] is read live before starting work. Only a full pass started after that read says
     * unlocked can cover a late boot trigger. Never reuse a locked, incomplete or attention pass.
     * BootOutcome.ready includes independent final verification and journal clearing, not just apply.
     * [afterPass] still runs on a skip (recovery, Home/count refresh, negative-control hooks).
     * Clear coverage first so any exception, failed publication or pending save forces a new pass.
     */
    fun run(
        reason: String,
        locked: Boolean,
        retryPending: () -> Boolean,
        apply: (Long) -> BootOutcome?,
        afterPass: (BootOutcome?) -> Boolean,
        skipped: (Long) -> Unit,
    ) {
        val covered = last.takeIf {
            !locked && (reason == "boot" || reason == "locked-boot") && !retryPending()
        }
        invalidate()
        val pass = if (covered != null) {
            skipped(covered.id)
            covered
        } else {
            val id = ++nextId
            Pass(id, apply(id))
        }
        val published = afterPass(pass.outcome)
        val outcome = pass.outcome
        val applied = outcome?.apply
        if (!locked && published && outcome != null && !outcome.locked && outcome.ready &&
            applied?.complete == true && applied.applied.containsAll(FULL_STAGES) &&
            !outcome.needsAttention && !retryPending()) last = pass
    }

    private companion object {
        val FULL_STAGES = setOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD,
            ApplyStage.HIDES, ApplyStage.LOCK_TASK, ApplyStage.HOME)
    }
}
