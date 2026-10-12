// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import org.plainphone.core.PackageName
import org.plainphone.device.facts.CertDigests
import java.io.File

/**
 * [InstallerPort] over this app's `PackageInstaller`. [statusSender] gives the IntentSender for a
 * session's status broadcast (to an [InstallStatusRouter]); the app wires it when Update is enabled.
 * As Device Owner the install needs no user action (X2).
 */
class AndroidInstallerPort(context: Context, private val statusSender: (sessionId: Int) -> IntentSender) : InstallerPort {
    private val installer = context.packageManager.packageInstaller
    private val pm = context.packageManager
    private val ownPackage = context.packageName

    override fun ownSessions(): List<OwnSession>? = try {
        installer.mySessions.map { OwnSession(it.sessionId, it.isCommitted, it.appPackageName) }
    } catch (_: RuntimeException) {
        null
    }

    override fun create(appPackage: PackageName, sizeBytes: Long): Int? = try {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appPackage.value)
            setSize(sizeBytes)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        installer.createSession(params)
    } catch (_: Exception) {
        null
    }

    override fun write(sessionId: Int, name: String, source: File): Boolean = try {
        installer.openSession(sessionId).use { session ->
            session.openWrite(name, 0, source.length()).use { out ->
                source.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    override fun entries(sessionId: Int): List<String>? = try {
        installer.openSession(sessionId).use { it.names.toList() }
    } catch (_: Exception) {
        null
    }

    override fun readSha256(sessionId: Int, name: String): String? = try {
        installer.openSession(sessionId).use { session -> session.openRead(name).use(Sha256::of) }
    } catch (_: Exception) {
        null
    }

    override fun commit(sessionId: Int) {
        installer.openSession(sessionId).use { it.commit(statusSender(sessionId)) }
    }

    override fun sessionInfo(sessionId: Int): OwnSession? = installer.getSessionInfo(sessionId)?.let {
        OwnSession(it.sessionId, it.isCommitted, it.appPackageName.takeIf { _ -> it.installerPackageName == ownPackage })
    }

    override fun abandon(sessionId: Int): Boolean = try {
        installer.abandonSession(sessionId)
        true
    } catch (_: RuntimeException) {
        false
    }

    override fun installedVersionCode(name: PackageName): Long? = try {
        pm.getPackageInfo(name.value, 0).longVersionCode
    } catch (_: Exception) {
        null
    }
}

/**
 * [ApkInspector] through `getPackageArchiveInfo`, which makes Android verify the APK signature while
 * collecting the certificates. The signer set is the current one (`signingInfo.apkContentsSigners`),
 * as for installed apps, never the rotation history. A file without verified signing info is null.
 *
 * Android 13 (API 33) collects and verifies certificates for an archive only when GET_SIGNATURES is
 * set, yet fills `signingInfo` only for GET_SIGNING_CERTIFICATES (R4, PLA-207; sources in
 * `spikes/installer-boundary/evidence/`), so [flagsFor] asks for both there. Other levels collect
 * with GET_SIGNING_CERTIFICATES alone. The legacy `signatures` array is never read.
 */
class ArchiveApkInspector(context: Context) : ApkInspector {
    private val pm = context.packageManager

    override fun inspect(apk: File): ApkFacts? = try {
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.path, flagsFor(Build.VERSION.SDK_INT))
        val signers = info?.signingInfo?.apkContentsSigners?.map { CertDigests.sha256(it.toByteArray()) }?.toSet()
        if (info == null || signers.isNullOrEmpty()) null
        else ApkFacts(PackageName(info.packageName), signers, info.longVersionCode)
    } catch (_: Exception) {
        null
    }

    companion object {
        @Suppress("DEPRECATION")
        fun flagsFor(sdk: Int): Int =
            if (sdk == 33) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            else PackageManager.GET_SIGNING_CERTIFICATES
    }
}
