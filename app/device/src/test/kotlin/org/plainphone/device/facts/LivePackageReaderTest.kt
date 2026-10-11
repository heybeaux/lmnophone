// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.InstalledPackage

/**
 * Task 4.6, filling PackageFacts from the live OS; PLA-46 CertDigest handoff: approvals bind the package
 * plus its exact current signer set, are re-read live every pass, and duplicate or conflicting OS facts
 * fail closed.
 */
class LivePackageReaderTest {
    private val port = FakePackageSystemPort()
    private val reader = LivePackageReader(port)

    private fun digest(text: String) = CertDigest(
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) })
    private fun name(n: String) = PackageName(n)

    @Test fun digestIsSha256OfTheEncodedCertificateInLowerCaseHex() {
        assertEquals(CertDigest("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            CertDigests.sha256("abc".toByteArray()))
    }

    @Test fun everyPackageThePortReportsIsListedWithItsCurrentSigners() {
        port.packages(raw("com.example.notes", false, 7, "keyA"), raw("com.android.phone", true, 35, "platform"))
        val live = reader.read()
        assertEquals(listOf("com.example.notes", "com.android.phone"), live.facts.map { it.packageName.value })
        val notes = live.facts.first()
        assertEquals(setOf(digest("keyA")), notes.signingCerts)
        assertFalse(notes.isSystem)
        assertTrue(live.facts.last().isSystem)
        assertEquals(reader.installedPackages(), live.facts)
    }

    @Test fun installedPackagesCarryNameSystemFlagAndVersionCode() {
        port.packages(raw("com.example.notes", false, 7, "keyA"), raw("com.android.phone", true, 35, "platform"))
        assertEquals(listOf(InstalledPackage(name("com.example.notes"), false, 7), InstalledPackage(name("com.android.phone"), true, 35)),
            reader.read().installed)
    }

    @Test fun multipleCurrentSignersAreAllKept() {
        port.packages(raw("com.example.multi", false, 1, "keyA", "keyB"))
        val live = reader.read()
        assertEquals(setOf(digest("keyA"), digest("keyB")), live.facts.single().signingCerts)
        assertEquals(listOf(ApprovalBinding(name("com.example.multi"), setOf(digest("keyA"), digest("keyB")))), live.identities)
    }

    @Test fun approvalBindingsComeFromTheSameSignerRead() {
        port.packages(raw("com.example.notes", false, 7, "keyA"))
        val live = reader.read()
        assertEquals(listOf(ApprovalBinding(name("com.example.notes"), live.facts.single().signingCerts)), live.identities)
    }

    @Test fun aPackageWithNoSigningInfoGetsNoSignersAndNoBinding() {
        port.packages(unsigned("com.android.apex.shim"))
        val live = reader.read()
        assertEquals(emptySet<CertDigest>(), live.facts.single().signingCerts)
        assertTrue(live.identities.isEmpty())
        assertEquals(1, live.installed.size)
    }

    @Test fun anUpdateSignedWithARotatedKeyChangesTheSignerSetSoTheOldBindingNoLongerMatches() {
        port.packages(raw("com.example.notes", false, 1, "keyA"))
        val before = reader.read().identities.single()
        // apksigner --lineage: the new APK's current signer is keyB; keyA is only in the history.
        port.reads = mutableListOf(listOf(raw("com.example.notes", false, 2, "keyB")))
        port.packageReads = 0
        val after = reader.read()
        assertEquals(setOf(digest("keyB")), after.facts.single().signingCerts)
        assertFalse(before.matches(name("com.example.notes"), after.facts.single().signingCerts))
        assertFalse(after.identities.single() == before)
    }

    @Test fun theListIsReadTwiceWithTheBetweenWorkInTheMiddle() {
        port.packages(raw("com.example.notes", false, 1, "keyA"))
        reader.read { port.calls += "between" }
        assertEquals(listOf("packages", "browsable", "browsable", "between", "packages"), port.calls)
    }

    @Test fun probeFlagsComeFromTheExactProbeUrls() {
        port.packages(raw("com.example.browser", false, 1, "k"), raw("com.example.reader", false, 1, "k"))
        port.browsable["http://plainphone-probe.invalid/"] = setOf("com.example.browser", "com.example.reader")
        port.browsable["https://plainphone-probe.invalid/"] = setOf("com.example.browser")
        val facts = reader.read().facts.associateBy { it.packageName.value }
        assertTrue(facts.getValue("com.example.browser").handlesProbeHttp && facts.getValue("com.example.browser").handlesProbeHttps)
        assertTrue(facts.getValue("com.example.reader").handlesProbeHttp)
        assertFalse(facts.getValue("com.example.reader").handlesProbeHttps)
    }

    @Test fun aSystemFlagThatChangesBetweenReadsIsNotSystemInPackageFactsAndIsReportedBothWays() {
        port.reads = mutableListOf(listOf(raw("com.example.dialer", true, 1, "k")), listOf(raw("com.example.dialer", false, 1, "k")))
        val live = reader.read()
        assertFalse(live.facts.single().isSystem)
        assertEquals(setOf(true, false), live.installed.map { it.isSystem }.toSet())
        assertEquals(2, live.installed.size)
    }

    @Test fun signersThatChangeBetweenReadsGiveNoPackageFactsSignersAndNoSingleBinding() {
        port.reads = mutableListOf(listOf(raw("com.example.notes", false, 1, "keyA")), listOf(raw("com.example.notes", false, 2, "keyB")))
        val live = reader.read()
        assertEquals(emptySet<CertDigest>(), live.facts.single().signingCerts)
        // Duplicate bindings fail closed in :policy (approvedPackages uses singleOrNull).
        assertNull(live.identities.filter { it.packageName == name("com.example.notes") }.singleOrNull())
        assertEquals(2, live.identities.size)
    }

    @Test fun signersMissingInOneReadGiveNoBindingAtAll() {
        port.reads = mutableListOf(listOf(raw("com.example.notes", false, 1, "keyA")), listOf(unsigned("com.example.notes", false, 1)))
        val live = reader.read()
        assertTrue(live.identities.isEmpty())
        assertEquals(emptySet<CertDigest>(), live.facts.single().signingCerts)
    }

    @Test fun aVersionOnlyChangeKeepsSystemIdentityAndOneBinding() {
        port.reads = mutableListOf(listOf(raw("com.android.dialer", true, 1, "k")), listOf(raw("com.android.dialer", true, 2, "k")))
        val live = reader.read()
        assertTrue(live.facts.single().isSystem)
        assertEquals(listOf(1L, 2L), live.installed.map { it.versionCode })
        assertTrue(live.installed.all { it.isSystem })
        assertEquals(1, live.identities.size)
    }

    @Test fun aPackageSeenInOnlyOneReadIsTakenFromThatRead() {
        port.reads = mutableListOf(listOf(raw("com.example.old", false, 1, "k")), listOf(raw("com.example.new", false, 1, "k")))
        val live = reader.read()
        assertEquals(listOf("com.example.old", "com.example.new"), live.facts.map { it.packageName.value })
        assertEquals(2, live.installed.size)
    }

    @Test fun identicalReadsGiveOneEntryPerPackage() {
        port.packages(raw("com.example.notes", false, 1, "keyA"))
        val live = reader.read()
        assertEquals(1, live.installed.size)
        assertEquals(1, live.identities.size)
        assertEquals(1, live.facts.size)
    }
}
