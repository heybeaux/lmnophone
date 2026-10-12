// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppApprovalOperation
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.AppApprovalReceipt
import org.plainphone.policy.AppApprovalRecord
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.RuntimePermission

/**
 * [RecordedPermissionManifests] (PLA-281): the manifest the controller verifies against is the recorded
 * one, returned only while every live fact still matches it. Anything else, or any unreadable fact, is null.
 */
class RecordedPermissionManifestsTest {
    private val app = PackageName("test.app")
    private val other = PackageName("test.other")
    private val signer = CertDigest("ab".repeat(32))
    private val camera = PermissionName("android.permission.CAMERA")
    private val identity = ApprovalBinding(app, setOf(signer))
    private val manifest = PermissionManifest(identity, 7, "build", listOf(
        RuntimePermission(camera, "Camera", emptyList(), PermissionGrantCapability.SUPPORTED)))
    private val record = AppApprovalRecord("approval-1", PermissionSelection(manifest, mapOf(camera to PermissionChoice.ALLOW)))
    private val receipt = AppApprovalReceipt("approval-1", TrustedPersonId("holder"), mapOf(TrustedPersonId("holder") to 1L))
    private var state: AppApprovalState? = AppApprovalState(1, mapOf(app to record), receipts = listOf(receipt))

    private val facts = object : ManifestFactsPort {
        var signers: Set<CertDigest>? = setOf(signer)
        var version: Long? = 7
        var permissions: Set<PermissionName>? = setOf(camera)
        var fingerprint: String? = "build"
        var throws = false
        override fun signers(name: PackageName) = if (throws) error("read failed") else signers
        override fun versionCode(name: PackageName) = version
        override fun runtimePermissions(name: PackageName) = permissions
        override fun fingerprint() = fingerprint
    }
    private val manifests = RecordedPermissionManifests({ state }, facts)

    @Test fun anApprovedAppWithMatchingLiveFactsGetsItsRecordedManifest() =
        assertSame(manifest, manifests(app))

    @Test fun aPendingRecordIsUsedForItsPackage() {
        state = AppApprovalState(1, emptyMap(), AppApprovalOperation(record, receipt, AppApprovalPhase.PREPARING))
        assertSame(manifest, manifests(app))
    }

    @Test fun anAppWithNoRecordGetsNone() = assertNull(manifests(other))

    @Test fun anUnreadableRecordGivesNone() {
        state = null
        assertNull(manifests(app))
    }

    @Test fun aChangedSignerGivesNone() {
        facts.signers = setOf(CertDigest("cd".repeat(32)))
        assertNull(manifests(app))
    }

    @Test fun aChangedVersionGivesNone() {
        facts.version = 8
        assertNull(manifests(app))
    }

    @Test fun aChangedBuildGivesNone() {
        facts.fingerprint = "other-build"
        assertNull(manifests(app))
    }

    @Test fun aNewRuntimePermissionGivesNone() {
        facts.permissions = setOf(camera, PermissionName("android.permission.RECORD_AUDIO"))
        assertNull(manifests(app))
    }

    @Test fun unreadableFactsGiveNone() {
        facts.permissions = null
        assertNull(manifests(app))
        facts.permissions = setOf(camera)
        facts.signers = null
        assertNull(manifests(app))
        facts.signers = setOf(signer)
        facts.throws = true
        assertNull(manifests(app))
    }
}
