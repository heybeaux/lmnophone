// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.util.Collections

@JvmInline
value class PermissionName(val value: String) {
    init { require(value.isNotBlank() && value.none { it.isWhitespace() || it.isISOControl() }) }
}

enum class PermissionChoice { ALLOW, DENY }
enum class PermissionGrantCapability { SUPPORTED, UNSUPPORTED, UNKNOWN }

/** Trusted adapter facts for one requested runtime permission, including plain-language UI copy. */
class RuntimePermission(
    val name: PermissionName,
    val label: String,
    features: List<String>,
    val grantCapability: PermissionGrantCapability,
) {
    val features: List<String> = Collections.unmodifiableList(ArrayList(features))
    init { require(label.isNotBlank() && this.features.all { it.isNotBlank() }) }
    override fun equals(other: Any?): Boolean = other is RuntimePermission && name == other.name &&
        label == other.label && features == other.features && grantCapability == other.grantCapability
    override fun hashCode(): Int = listOf(name, label, features, grantCapability).hashCode()
}

/**
 * Complete runtime-permission inventory from the installed package, never from an app request string.
 * Build and version are additional freshness fences; the complete current signer set must also match.
 * Capabilities come from verified adapter facts on this build, not guesses based on permission names.
 */
class PermissionManifest(
    val identity: ApprovalBinding,
    val versionCode: Long,
    val buildFingerprint: String,
    permissions: List<RuntimePermission>,
) {
    val permissions: List<RuntimePermission> = Collections.unmodifiableList(ArrayList(permissions))
    init {
        require(versionCode >= 0 && buildFingerprint.isNotBlank())
        require(this.permissions.map { it.name }.toSet().size == this.permissions.size)
    }
    internal fun sameFacts(other: PermissionManifest): Boolean = identity == other.identity &&
        versionCode == other.versionCode && buildFingerprint == other.buildFingerprint &&
        permissions.associateBy { it.name } == other.permissions.associateBy { it.name }
}

/** A review snapshot and explicit choices only. This is never approval or fresh PIN authority. */
class PermissionSelection(val manifest: PermissionManifest, choices: Map<PermissionName, PermissionChoice>) {
    val choices: Map<PermissionName, PermissionChoice> = Collections.unmodifiableMap(LinkedHashMap(choices))
}

data class PermissionWrite(val permission: PermissionName, val choice: PermissionChoice)
enum class PermissionLimitationReason { DENIED, UNSUPPORTED }
class PermissionLimitation internal constructor(
    val permission: PermissionName,
    val reason: PermissionLimitationReason,
    features: List<String>,
) {
    val features: List<String> = Collections.unmodifiableList(ArrayList(features))
}

/** D must apply each write with setPermissionGrantState, then read back every requested state. */
class PermissionPlan internal constructor(val manifest: PermissionManifest, choices: Map<PermissionName, PermissionChoice>) {
    val choices: Map<PermissionName, PermissionChoice> = Collections.unmodifiableMap(LinkedHashMap(choices))
    val writes: List<PermissionWrite> = Collections.unmodifiableList(manifest.permissions.map {
        PermissionWrite(it.name, this.choices.getValue(it.name))
    })
    val limitations: List<PermissionLimitation> = Collections.unmodifiableList(manifest.permissions.filter {
        this.choices.getValue(it.name) == PermissionChoice.DENY
    }.map {
        PermissionLimitation(it.name, if (it.grantCapability == PermissionGrantCapability.UNSUPPORTED)
            PermissionLimitationReason.UNSUPPORTED else PermissionLimitationReason.DENIED, it.features)
    })
}

enum class PermissionPlanningFailure {
    FACTS_UNAVAILABLE, FACTS_CHANGED, EXTRA_CHOICES, MISSING_CHOICES, ALLOW_UNSUPPORTED, CAPABILITY_UNKNOWN,
}
sealed interface PermissionPlanning {
    class Ready internal constructor(val plan: PermissionPlan) : PermissionPlanning
    class Blocked internal constructor(val reason: PermissionPlanningFailure, permissions: Set<PermissionName> = emptySet()) : PermissionPlanning {
        val permissions: Set<PermissionName> = immutableSet(permissions)
    }
}

/** DEFAULT is not DENIED: it could leave an interactive permission prompt that lock task blocks. */
enum class PermissionReadback { GRANTED, DENIED, DEFAULT, UNKNOWN }
sealed interface PermissionVerification {
    /** Permission postconditions only; never a grant, allowlist entry, or authorization to unhide. */
    data object Verified : PermissionVerification
    data object Stale : PermissionVerification
    class Mismatch internal constructor(permissions: Set<PermissionName>) : PermissionVerification {
        val permissions: Set<PermissionName> = immutableSet(permissions)
    }
}

/** Pure choice and readback model. No policy storage, Android writes, timing or authorization. */
object PermissionChoices {
    fun plan(selection: PermissionSelection, current: PermissionManifest?): PermissionPlanning {
        if (current == null) return PermissionPlanning.Blocked(PermissionPlanningFailure.FACTS_UNAVAILABLE)
        if (!selection.manifest.sameFacts(current)) return PermissionPlanning.Blocked(PermissionPlanningFailure.FACTS_CHANGED)
        val required = current.permissions.map { it.name }.toSet()
        val extra = selection.choices.keys - required
        if (extra.isNotEmpty()) return PermissionPlanning.Blocked(PermissionPlanningFailure.EXTRA_CHOICES, extra)
        val missing = required - selection.choices.keys
        if (missing.isNotEmpty()) return PermissionPlanning.Blocked(PermissionPlanningFailure.MISSING_CHOICES, missing)
        val allows = current.permissions.filter { selection.choices[it.name] == PermissionChoice.ALLOW }
        val unsupported = allows.filter { it.grantCapability == PermissionGrantCapability.UNSUPPORTED }.map { it.name }.toSet()
        if (unsupported.isNotEmpty()) return PermissionPlanning.Blocked(PermissionPlanningFailure.ALLOW_UNSUPPORTED, unsupported)
        val unknown = allows.filter { it.grantCapability == PermissionGrantCapability.UNKNOWN }.map { it.name }.toSet()
        if (unknown.isNotEmpty()) return PermissionPlanning.Blocked(PermissionPlanningFailure.CAPABILITY_UNKNOWN, unknown)
        return PermissionPlanning.Ready(PermissionPlan(current, selection.choices))
    }

    /** Re-read the same complete installed facts after writes. Missing/unknown states never pass. */
    fun verify(plan: PermissionPlan, current: PermissionManifest?, readback: Map<PermissionName, PermissionReadback>): PermissionVerification {
        if (current == null || !plan.manifest.sameFacts(current)) return PermissionVerification.Stale
        val mismatch = plan.choices.filter { (permission, choice) ->
            readback[permission] != when (choice) {
                PermissionChoice.ALLOW -> PermissionReadback.GRANTED
                PermissionChoice.DENY -> PermissionReadback.DENIED
            }
        }.keys
        return if (mismatch.isEmpty()) PermissionVerification.Verified else PermissionVerification.Mismatch(mismatch)
    }
}
