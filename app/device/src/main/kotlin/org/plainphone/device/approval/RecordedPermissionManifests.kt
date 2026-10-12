// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName

/** The live facts a recorded permission manifest must still match. Null where a read failed. */
interface ManifestFactsPort {
    fun signers(name: PackageName): Set<CertDigest>?
    fun versionCode(name: PackageName): Long?
    /** The runtime (dangerous) permissions [name] requests. */
    fun runtimePermissions(name: PackageName): Set<PermissionName>?
    fun fingerprint(): String?
}

/**
 * The controller's `manifest` source for apps it already has a record of (PLA-281). It returns the
 * recorded manifest (the pending record's first, else the approved one) only while the live signer set,
 * version, build fingerprint and runtime-permission names all match it; otherwise, or when anything does
 * not read, null, so activation and verification fail closed. Labels, features and grant capability come
 * from the record: they are fixed for one app version on one build.
 *
 * Not a review-screen reader: an app with no record gets null, so a new approval cannot be prepared
 * through it. That reader belongs to the approval screens.
 */
class RecordedPermissionManifests(
    private val approvals: () -> AppApprovalState?,
    private val facts: ManifestFactsPort,
) : (PackageName) -> PermissionManifest? {
    override fun invoke(name: PackageName): PermissionManifest? = try {
        val state = approvals()
        val record = state?.pending?.record?.takeIf { it.identity.packageName == name } ?: state?.apps?.get(name)
        record?.permissions?.manifest?.takeIf { recorded ->
            facts.signers(name) == recorded.identity.signingCerts &&
                facts.versionCode(name) == recorded.versionCode &&
                facts.fingerprint() == recorded.buildFingerprint &&
                facts.runtimePermissions(name) == recorded.permissions.map { it.name }.toSet()
        }
    } catch (_: RuntimeException) {
        null
    }
}

/** [ManifestFactsPort] over PackageManager; hidden and disabled apps included. */
class AndroidManifestFacts(
    context: Context,
    private val grants: PermissionGrantPort,
    private val build: () -> String,
) : ManifestFactsPort {
    private val pm = context.packageManager

    override fun signers(name: PackageName): Set<CertDigest>? = grants.signers(name)

    override fun versionCode(name: PackageName): Long? = info(name, 0)?.longVersionCode

    override fun runtimePermissions(name: PackageName): Set<PermissionName>? {
        val requested = info(name, PackageManager.GET_PERMISSIONS)?.requestedPermissions.orEmpty()
        return requested.filter { dangerous(it) ?: return null }.map(::PermissionName).toSet()
    }

    override fun fingerprint(): String? = try {
        build()
    } catch (_: RuntimeException) {
        null
    }

    /** A permission no package defines is not a runtime permission; a failed read is unknown. */
    private fun dangerous(permission: String): Boolean? = try {
        pm.getPermissionInfo(permission, 0).protection == PermissionInfo.PROTECTION_DANGEROUS
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: RuntimeException) {
        null
    }

    private fun info(name: PackageName, flags: Int) = try {
        // The int-flags overload: PackageInfoFlags needs API 33 and minSdk is 30.
        @Suppress("DEPRECATION")
        pm.getPackageInfo(name.value, flags or PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
