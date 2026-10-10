// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.plainphone.core.PackageName
import java.io.File
import java.security.MessageDigest

/** PackageInstaller as the X1–X6 spikes observed it: own sessions, entry bytes, commit and abandon. */
class FakeInstallerPort : InstallerPort {
    class Session(val id: Int, val appPackageName: String?) {
        val entries = linkedMapOf<String, ByteArray>()
        var committed = false
    }
    val sessions = linkedMapOf<Int, Session>()
    /** Every port call in order, e.g. "write 7", "read 7", "commit 7". */
    val calls = mutableListOf<String>()
    var nextId = 7
    var installed: Long? = 1
    var createWorks = true
    var writeWorks = true
    var abandonWorks = true
    var listWorks = true
    var readWorks = true
    var commitThrows = false
    var abandonLeaves = false
    /** The platform's `isCommitted()` lags `commit()` by about 16 ms (X6). */
    var commitShowsAtOnce = true
    var onRead: () -> Unit = {}

    fun leftover(appPackageName: String?, committed: Boolean = false): Int {
        val s = Session(nextId++, appPackageName).also { it.committed = committed; it.entries["base.apk"] = byteArrayOf(1) }
        sessions[s.id] = s
        return s.id
    }

    override fun ownSessions(): List<OwnSession>? {
        calls += "list"
        if (!listWorks) return null
        return sessions.values.map { OwnSession(it.id, it.committed, it.appPackageName) }
    }
    override fun create(appPackage: PackageName, sizeBytes: Long): Int? {
        calls += "create"
        if (!createWorks) return null
        val s = Session(nextId++, appPackage.value)
        sessions[s.id] = s
        return s.id
    }
    override fun write(sessionId: Int, name: String, source: File): Boolean {
        calls += "write $sessionId"
        val s = sessions[sessionId] ?: return false
        if (!writeWorks || s.committed) return false
        s.entries[name] = source.readBytes()
        return true
    }
    override fun entries(sessionId: Int): List<String>? = sessions[sessionId]?.entries?.keys?.toList()
    override fun readSha256(sessionId: Int, name: String): String? {
        calls += "read $sessionId"
        onRead()
        if (!readWorks) return null
        val bytes = sessions[sessionId]?.entries?.get(name) ?: return null
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    override fun commit(sessionId: Int) {
        calls += "commit $sessionId"
        if (commitThrows) throw SecurityException("simulated")
        val s = sessions[sessionId] ?: throw SecurityException("Caller has no access to session $sessionId")
        check(!s.committed)
        if (commitShowsAtOnce) s.committed = true
    }
    override fun abandon(sessionId: Int): Boolean {
        calls += "abandon $sessionId"
        if (!abandonWorks) return false
        if (!abandonLeaves) sessions.remove(sessionId)
        return true
    }
    override fun installedVersionCode(name: PackageName): Long? = installed
}
