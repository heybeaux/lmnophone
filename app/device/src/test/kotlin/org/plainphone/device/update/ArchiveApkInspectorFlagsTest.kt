// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R4 (PLA-207): Android 13 r1 `getPackageArchiveInfo` collects and verifies certificates only when
 * GET_SIGNATURES is set (PackageManager.java:8068), but fills `signingInfo` only for
 * GET_SIGNING_CERTIFICATES (PackageParser.java:892). API 33 needs both; the other levels verify
 * with GET_SIGNING_CERTIFICATES alone. The signer set is still read from `signingInfo`.
 */
class ArchiveApkInspectorFlagsTest {
    @Test fun api33AsksForBothSoCertificatesAreCollectedAndVerified() {
        assertEquals(PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES, ArchiveApkInspector.flagsFor(33))
    }

    @Test fun otherSupportedLevelsAskForSigningCertificatesOnly() {
        for (sdk in listOf(30, 31, 32, 34, 35, 36))
            assertEquals("API $sdk", PackageManager.GET_SIGNING_CERTIFICATES, ArchiveApkInspector.flagsFor(sdk))
    }
}
