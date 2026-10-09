// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import kotlin.test.*

class PermissionChoicesTest {
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val identity = ApprovalBinding(PackageName("test.app"), setOf(CertDigest("ab".repeat(32))))
    private fun facts(capability: PermissionGrantCapability = PermissionGrantCapability.SUPPORTED,
        version: Long = 1, build: String = "build", binding: ApprovalBinding = identity) =
        PermissionManifest(binding, version, build, listOf(
            RuntimePermission(camera, "Camera", listOf("Taking photos"), capability),
            RuntimePermission(mic, "Microphone", listOf("Recording sound"), PermissionGrantCapability.SUPPORTED)))
    private val choices = mapOf(camera to PermissionChoice.ALLOW, mic to PermissionChoice.DENY)
    private fun ready(facts: PermissionManifest = facts(), selected: Map<PermissionName, PermissionChoice> = choices) =
        assertIs<PermissionPlanning.Ready>(PermissionChoices.plan(PermissionSelection(facts, selected), facts)).plan

    @Test fun `each listed runtime permission requires an explicit choice`() {
        val f = facts()
        val result = assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(
            PermissionSelection(f, mapOf(camera to PermissionChoice.ALLOW)), f))
        assertEquals(PermissionPlanningFailure.MISSING_CHOICES, result.reason)
        assertEquals(setOf(mic), result.permissions)
        assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(PermissionSelection(f, emptyMap()), f))
    }

    @Test fun `choices for permissions outside the installed manifest are refused`() {
        val f = facts()
        val extra = PermissionName("test.permission.EXTRA")
        val result = assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(
            PermissionSelection(f, choices + (extra to PermissionChoice.ALLOW)), f))
        assertEquals(PermissionPlanningFailure.EXTRA_CHOICES, result.reason)
        assertEquals(setOf(extra), result.permissions)
    }

    @Test fun `ready plan writes Allow and Deny individually and reports lost features`() {
        val p = ready()
        assertEquals(identity, p.manifest.identity)
        assertEquals(choices, p.choices)
        assertEquals(listOf(PermissionWrite(camera, PermissionChoice.ALLOW), PermissionWrite(mic, PermissionChoice.DENY)), p.writes)
        val limitation = p.limitations.single()
        assertEquals(mic, limitation.permission)
        assertEquals(listOf("Recording sound"), limitation.features)
        assertEquals(PermissionLimitationReason.DENIED, limitation.reason)
    }

    @Test fun `unsupported Allow is never silently converted to Deny or treated as granted`() {
        val f = facts(PermissionGrantCapability.UNSUPPORTED)
        val result = assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(PermissionSelection(f, choices), f))
        assertEquals(PermissionPlanningFailure.ALLOW_UNSUPPORTED, result.reason)
        assertEquals(setOf(camera), result.permissions)
        assertEquals("Camera", f.permissions[0].label)
        assertEquals(listOf("Taking photos"), f.permissions[0].features)
        val p = ready(f, choices + (camera to PermissionChoice.DENY))
        assertEquals(PermissionChoice.DENY, p.choices[camera])
        assertEquals(PermissionLimitationReason.UNSUPPORTED, p.limitations.first().reason)
        assertEquals(listOf("Taking photos"), p.limitations.first().features)
    }

    @Test fun `unknown grant capability cannot approve an Allow but explicit Deny can be attempted`() {
        val f = facts(PermissionGrantCapability.UNKNOWN)
        val result = assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(PermissionSelection(f, choices), f))
        assertEquals(PermissionPlanningFailure.CAPABILITY_UNKNOWN, result.reason)
        assertEquals(PermissionChoice.DENY, ready(f, choices + (camera to PermissionChoice.DENY)).choices[camera])
    }

    @Test fun `replacement version signer set package build or permission facts require new choices`() {
        val original = facts()
        val changed = listOf(facts(version = 2), facts(build = "next-build"),
            facts(binding = ApprovalBinding(PackageName("test.other"), identity.signingCerts)),
            facts(binding = ApprovalBinding(identity.packageName, setOf(CertDigest("cd".repeat(32))))),
            facts(binding = ApprovalBinding(identity.packageName, identity.signingCerts + CertDigest("cd".repeat(32)))),
            facts(PermissionGrantCapability.UNSUPPORTED),
            PermissionManifest(identity, 1, "build", original.permissions.drop(1)),
            PermissionManifest(identity, 1, "build", listOf(
                RuntimePermission(camera, "Different explanation", listOf("Other feature"), PermissionGrantCapability.SUPPORTED),
                original.permissions[1])))
        for (f in changed) {
            val r = assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(PermissionSelection(original, choices), f))
            assertEquals(PermissionPlanningFailure.FACTS_CHANGED, r.reason)
            assertIs<PermissionVerification.Stale>(PermissionChoices.verify(ready(original), f,
                mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED)))
        }
    }

    @Test fun `missing live package facts never produce a plan or successful readback`() {
        val result = assertIs<PermissionPlanning.Blocked>(PermissionChoices.plan(PermissionSelection(facts(), choices), null))
        assertEquals(PermissionPlanningFailure.FACTS_UNAVAILABLE, result.reason)
        assertIs<PermissionVerification.Stale>(PermissionChoices.verify(ready(), null, emptyMap()))
    }

    @Test fun `verification requires explicit managed grant states for every choice`() {
        val p = ready()
        assertIs<PermissionVerification.Verified>(PermissionChoices.verify(p, facts(),
            mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED)))
        for (bad in listOf(PermissionReadback.DEFAULT, PermissionReadback.UNKNOWN, PermissionReadback.DENIED)) {
            val result = assertIs<PermissionVerification.Mismatch>(PermissionChoices.verify(p, facts(),
                mapOf(camera to bad, mic to PermissionReadback.DENIED)))
            assertEquals(setOf(camera), result.permissions)
        }
        for (bad in listOf(PermissionReadback.DEFAULT, PermissionReadback.UNKNOWN, PermissionReadback.GRANTED)) {
            val result = assertIs<PermissionVerification.Mismatch>(PermissionChoices.verify(p, facts(),
                mapOf(camera to PermissionReadback.GRANTED, mic to bad)))
            assertEquals(setOf(mic), result.permissions)
        }
        assertEquals(setOf(camera, mic), assertIs<PermissionVerification.Mismatch>(
            PermissionChoices.verify(p, facts(), emptyMap())).permissions)
    }

    @Test fun `empty runtime permission list is a valid empty plan but still identity bound`() {
        val f = PermissionManifest(identity, 1, "build", emptyList())
        val p = ready(f, emptyMap())
        assertTrue(p.writes.isEmpty())
        assertTrue(p.limitations.isEmpty())
        assertIs<PermissionVerification.Verified>(PermissionChoices.verify(p, f, emptyMap()))
        assertIs<PermissionVerification.Stale>(PermissionChoices.verify(p, null, emptyMap()))
    }

    @Test fun `input collections are snapshots and exported collections cannot be changed`() {
        val features = mutableListOf("Taking photos")
        val perms = mutableListOf(RuntimePermission(camera, "Camera", features, PermissionGrantCapability.SUPPORTED))
        val f = PermissionManifest(identity, 1, "build", perms)
        val selected = mutableMapOf(camera to PermissionChoice.DENY)
        val s = PermissionSelection(f, selected)
        features.clear(); perms.clear(); selected.clear()
        val p = assertIs<PermissionPlanning.Ready>(PermissionChoices.plan(s, f)).plan
        assertEquals(listOf("Taking photos"), p.limitations.single().features)
        assertEquals(mapOf(camera to PermissionChoice.DENY), p.choices)
        assertFailsWith<UnsupportedOperationException> { (f.permissions as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (f.permissions.single().features as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (s.choices as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (p.writes as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (p.limitations as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (p.choices as MutableMap).clear() }
    }

    @Test fun `invalid or duplicate manifest entries are rejected without guessing defaults`() {
        assertFailsWith<IllegalArgumentException> { PermissionName("") }
        assertFailsWith<IllegalArgumentException> { PermissionName("has space") }
        assertFailsWith<IllegalArgumentException> { RuntimePermission(camera, "", listOf("Photos"), PermissionGrantCapability.SUPPORTED) }
        assertFailsWith<IllegalArgumentException> { RuntimePermission(camera, "Camera", listOf(""), PermissionGrantCapability.SUPPORTED) }
        assertFailsWith<IllegalArgumentException> { PermissionManifest(identity, -1, "build", emptyList()) }
        assertFailsWith<IllegalArgumentException> { PermissionManifest(identity, 1, "", emptyList()) }
        assertFailsWith<IllegalArgumentException> { PermissionManifest(identity, 1, "build", listOf(facts().permissions[0], facts().permissions[0])) }
    }

    @Test fun `independently decoded equivalent manifests are equal regardless of permission enumeration order`() {
        val f = facts()
        val reordered = PermissionManifest(identity, 1, "build", f.permissions.reversed())
        val p = assertIs<PermissionPlanning.Ready>(PermissionChoices.plan(PermissionSelection(f, choices), reordered)).plan
        assertIs<PermissionVerification.Verified>(PermissionChoices.verify(p, facts(),
            mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED)))
    }
}
