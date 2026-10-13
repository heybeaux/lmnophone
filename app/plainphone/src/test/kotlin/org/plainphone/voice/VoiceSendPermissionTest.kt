// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * PLA-499 scope 4: SEND_SMS is declared next to the hard-restricted count permissions, so the install
 * allowlists it the same way (adb install, and the updater keeps the default restricted-permission allowlist),
 * and the send file and result are never exported.
 */
class VoiceSendPermissionTest {
    private fun doc(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(path).also { assertTrue("run from the module directory", it.exists()) })

    private fun Element.android(name: String) = getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun elements(path: String, tag: String) = doc(path).getElementsByTagName(tag).let { list ->
        (0 until list.length).map { list.item(it) as Element }
    }

    @Test fun sendSmsIsDeclaredWithTheOtherRestrictedPermissions() {
        val permissions = elements("src/main/AndroidManifest.xml", "uses-permission").map { it.android("name") }
        for (p in listOf("android.permission.SEND_SMS", "android.permission.READ_SMS", "android.permission.READ_CALL_LOG")) {
            assertTrue(p, p in permissions)
        }
    }

    @Test fun theUpdaterNeverEmptiesTheRestrictedPermissionAllowlist() {
        val source = File("../device/src/main/kotlin/org/plainphone/device/update/AndroidInstallerPort.kt").readText()
        assertFalse("an empty allowlist would drop SEND_SMS after an update", source.contains("setWhitelistedRestrictedPermissions"))
        assertFalse(source.contains("setAllowlistedRestrictedPermissions"))
        assertTrue(source.contains("SEND_SMS"))
    }

    @Test fun theSendFileAndTheResultAreNotExported() {
        val path = "../device/src/main/AndroidManifest.xml"
        val provider = elements(path, "provider").single { it.android("name").endsWith(".VoiceSendFiles") }
        assertEquals("false", provider.android("exported"))
        assertEquals("true", provider.android("grantUriPermissions"))
        val receiver = elements(path, "receiver").single { it.android("name").endsWith(".VoiceSendResultReceiver") }
        assertEquals("false", receiver.android("exported"))
    }
}
