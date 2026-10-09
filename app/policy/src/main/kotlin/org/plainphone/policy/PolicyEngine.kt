// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

object PolicyEngine {
    fun decide(input: PolicyInput): PolicyPlan {
        val floor = EmergencyFloor.compute(input.installed, input.identities)
        val inventory = input.inventory?.takeIf { it.model == input.model && it.fingerprint == input.fingerprint }
        val baseline = input.health == PolicyHealth.BOTH_INVALID
        val unknown = inventory == null
        val neverHide = immutableSet(floor.neverHide + inventory?.neverHide?.packages.orEmpty() + input.plainphone + input.messages)
        val conflicts = immutableSet(input.classifiedBrowsers.intersect(neverHide))
        val sessionOnly = setOf(PLAY_PACKAGE) + inventory?.settingsSessionPackages.orEmpty()
        val approved = if (baseline) emptySet() else input.approved - input.classifiedBrowsers - sessionOnly
        val session = input.sessionAccess?.takeIf { access ->
            !baseline && !input.lockedBoot && input.currentBoot?.let { access.isLive(it) } == true &&
                access.packages == sessionPackages(access.kind, inventory)
        }?.packages.orEmpty()
        val accessible = approved + session
        val sweep = when {
            input.lockedBoot || (unknown && baseline) -> SweepMode.NONE
            unknown -> SweepMode.USER_APPS_ONLY
            else -> SweepMode.FULL
        }
        val sweepCandidates = when (sweep) {
            SweepMode.NONE -> emptySet()
            SweepMode.FULL -> input.installed.map { it.name }.toSet() - accessible
            SweepMode.USER_APPS_ONLY -> input.installed.filter { !it.isSystem }.map { it.name }.toSet() - accessible
        }
        val hidden = immutableSet(when (sweep) {
            SweepMode.FULL -> sweepCandidates - neverHide
            SweepMode.USER_APPS_ONLY -> (input.previousHidden + sweepCandidates) - neverHide - accessible
            SweepMode.NONE -> input.previousHidden - neverHide
        })
        val ui = immutableSet(when {
            input.lockedBoot -> input.previousLockTask + floor.emergencyUi
            baseline -> setOf(input.plainphone, input.messages) + floor.emergencyUi + inventory?.minimumUi.orEmpty()
            unknown -> input.previousLockTask + floor.emergencyUi
            else -> setOf(input.plainphone) + approved + floor.emergencyUi + inventory.minimumUi
        }.plus(input.plainphone).plus(session) - hidden)
        val unhidden = immutableSet(neverHide + if (input.lockedBoot) emptySet() else accessible - hidden)
        val restrictions = immutableSet(Restriction.entries)
        val features = immutableSet(LockTaskFeature.entries)
        val stages = if (input.lockedBoot) ApplyStage.entries.take(4) else ApplyStage.entries
        val previousVersions = input.lastSupportedPackages.associate { it.name to it.versionCode }
        val changed = if (unknown) input.installed.filter { previousVersions[it.name] != it.versionCode }.map { it.name } else emptyList()
        val refused = input.requestedDestruction.filter { it.name in neverHide }
        val allowed = input.requestedDestruction.filterNot { it.name in neverHide }
        return PolicyPlan(
            hidden = hidden,
            unhide = unhidden, // The applier flushes each unhide before advancing; floor repairs are never battery gated.
            neverHide = neverHide,
            emergencyUi = floor.emergencyUi,
            lockTaskPackages = ui,
            features = features,
            restrictions = restrictions,
            steps = immutableList(stages.map { ApplyStep(it) }),
            expected = ExpectedPolicyState(hidden, unhidden, neverHide, ui, features, restrictions, input.plainphone),
            sweepMode = sweep,
            refusedDestruction = immutableList(refused),
            allowedDestruction = immutableList(allowed),
            classifierConflicts = conflicts,
            changedPackages = immutableSet(changed),
            needsAttention = baseline || !floor.hasSystemDialer || conflicts.isNotEmpty() || refused.isNotEmpty(),
            notifyBuildReview = unknown,
        )
    }
}
