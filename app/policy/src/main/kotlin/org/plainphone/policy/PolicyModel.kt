// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.PackageName

internal fun <T> immutableSet(values: Iterable<T>): Set<T> = Collections.unmodifiableSet(values.toSet())
internal fun <T> immutableList(values: Iterable<T>): List<T> = Collections.unmodifiableList(values.toList())

enum class PolicyHealth { VALID, LAST_KNOWN_GOOD, BOTH_INVALID }
enum class SweepMode { FULL, USER_APPS_ONLY, NONE }
enum class DestructiveKind { HIDE, SUSPEND, DISABLE, UNINSTALL, DISABLE_COMPONENT }
data class DestructiveRequest(val name: PackageName, val kind: DestructiveKind)

enum class Restriction {
    DISALLOW_SAFE_BOOT, DISALLOW_FACTORY_RESET, DISALLOW_DEBUGGING_FEATURES,
    DISALLOW_ADD_USER, DISALLOW_USER_SWITCH, DISALLOW_ADD_MANAGED_PROFILE,
    DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, DISALLOW_USB_FILE_TRANSFER,
    DISALLOW_CONFIG_VPN, DISALLOW_CONFIG_PRIVATE_DNS, DISALLOW_NETWORK_RESET,
    DISALLOW_CONFIG_DATE_TIME, DISALLOW_SYSTEM_ERROR_DIALOGS, DISALLOW_CREATE_WINDOWS,
    DISALLOW_MODIFY_ACCOUNTS, DISALLOW_CONFIG_CELL_BROADCASTS, DISALLOW_CONFIG_DEFAULT_APPS,
}
enum class LockTaskFeature { HOME, KEYGUARD, GLOBAL_ACTIONS, NOTIFICATIONS, SYSTEM_INFO, BLOCK_ACTIVITY_START_IN_TASK }
enum class ApplyStage { REVOKE_JOURNAL, RESTRICTIONS, EMERGENCY_FLOOR, NEVER_HIDE_GUARD, HIDES, LOCK_TASK, HOME }

/** Each step is applied and read back before the applier advances. */
data class ApplyStep(val stage: ApplyStage, val readbackRequired: Boolean = true)

data class PolicyInput(
    val model: String,
    val fingerprint: String,
    val plainphone: PackageName,
    val messages: PackageName,
    val installed: List<InstalledPackage>,
    val identities: List<FloorIdentity>,
    val inventory: BuildInventory? = null,
    val approved: Set<PackageName> = emptySet(),
    val classifiedBrowsers: Set<PackageName> = emptySet(),
    val health: PolicyHealth = PolicyHealth.VALID,
    val previousHidden: Set<PackageName> = emptySet(),
    /** Protected UI state: session relaxations already excluded by the journal/controller. */
    val previousLockTask: Set<PackageName> = emptySet(),
    val requestedDestruction: List<DestructiveRequest> = emptyList(),
    val lastSupportedPackages: List<InstalledPackage> = emptyList(),
    val lockedBoot: Boolean = false,
    val sessionAccess: SessionAccess? = null,
    val currentBoot: BootTime? = null,
    /** Fresh highest-valid-revision operation read on the serialized dispatcher, never a cached UI flag. */
    val waitingOperation: WaitingOperation? = null,
)

/** Null fields are outside this plan: neither write nor assert their values during readback. */
data class ExpectedPolicyState(
    val hidden: Set<PackageName>?,
    val unhiddenAndEnabled: Set<PackageName>,
    val unsuspended: Set<PackageName>,
    val lockTaskPackages: Set<PackageName>?,
    val features: Set<LockTaskFeature>?,
    val restrictions: Set<Restriction>?,
    val home: PackageName?,
    val systemInputMethodsOnly: Boolean? = true,
    val systemAccessibilityServicesOnly: Boolean? = true,
)

data class PolicyPlan(
    val hidden: Set<PackageName>,
    val unhide: Set<PackageName>,
    val neverHide: Set<PackageName>,
    val emergencyUi: Set<PackageName>,
    val lockTaskPackages: Set<PackageName>,
    val features: Set<LockTaskFeature>,
    val restrictions: Set<Restriction>,
    val steps: List<ApplyStep>,
    val expected: ExpectedPolicyState,
    val sweepMode: SweepMode,
    val refusedDestruction: List<DestructiveRequest>,
    /** Guard result only; the policy applier must not execute caller-supplied destruction. */
    val allowedDestruction: List<DestructiveRequest>,
    val classifierConflicts: Set<PackageName>,
    val changedPackages: Set<PackageName>,
    val needsAttention: Boolean,
    val notifyBuildReview: Boolean,
)

/** Lane D owns schema/checksum validation, atomic replacement and last-known-good storage. */
interface PolicyStore {
    fun read(): StoredPolicy
    /** False means nothing can be reported as durably saved. */
    fun write(policy: StoredPolicy): Boolean
}
data class StoredPolicy(val schemaVersion: Int, val approved: Set<PackageName>, val health: PolicyHealth)
