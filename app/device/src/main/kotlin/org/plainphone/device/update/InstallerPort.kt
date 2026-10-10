// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** One of this app's own PackageInstaller sessions (`getMySessions()`). */
data class OwnSession(val id: Int, val committed: Boolean, val appPackageName: String?)

/** The PackageInstaller calls behind [StagedUpdateInstaller]. Only [commit] may throw. */
interface InstallerPort {
    /** This app's own sessions; null when the read failed. */
    fun ownSessions(): List<OwnSession>?
    /** A new full-install session with `setAppPackageName(appPackage)`; null on failure. */
    fun create(appPackage: PackageName, sizeBytes: Long): Int?
    /** Writes all of [source] as entry [name] and fsyncs it; false on failure. */
    fun write(sessionId: Int, name: String, source: File): Boolean
    /** The session's entry names; null on failure. */
    fun entries(sessionId: Int): List<String>?
    /** SHA-256 (lowercase hex) of entry [name], read through `Session.openRead`; null on failure. */
    fun readSha256(sessionId: Int, name: String): String?
    /** `Session.commit`. May throw; the caller never retries. */
    fun commit(sessionId: Int)
    /** `abandonSession`; false when Android refused it. */
    fun abandon(sessionId: Int): Boolean
    /** Installed `longVersionCode` of [name]; null when unreadable. */
    fun installedVersionCode(name: PackageName): Long?
}

/** Facts read from an APK file whose signature verified. */
data class ApkFacts(val packageName: PackageName, val signers: Set<CertDigest>, val versionCode: Long)

/** Reads [ApkFacts] from a file; null unless the APK parses and its signature verifies. */
fun interface ApkInspector {
    fun inspect(apk: File): ApkFacts?
}

internal object Sha256 {
    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun of(file: File): String? = try { file.inputStream().use(::of) } catch (_: Exception) { null }
}
