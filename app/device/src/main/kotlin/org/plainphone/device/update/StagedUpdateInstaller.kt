// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.StagedApkFacts
import org.plainphone.policy.StagedUpdateBinding
import org.plainphone.policy.UpdateFinalFacts
import org.plainphone.policy.UpdateInstaller

/**
 * [UpdateInstaller] over one session that this object writes from [staging] and nothing else.
 * Update stays disabled: nothing in production constructs this yet.
 *
 * - [prepare] copies the sealed staged file into ONE new session bound to the pinned self package.
 *   It is the only session write. It refuses while this object holds a session, so a session is never
 *   rewritten or swapped; [abandonOwnSessions] is the only way to drop it.
 * - [finalFacts] stops all writes to the session first (G3), then hashes the session's single entry
 *   through `openRead`. The staged file must still be private and read-only and hash the same before
 *   and after its facts are read; package, exact signer set and version come from that file. Any
 *   failure is null, and a null clears the earlier pass, so [commit] needs a passing final read.
 * - [commit] runs once per session, never again even if it threw or [commits] refused the marker.
 *   The session ID is written durably to [commits] first; without that marker there is no commit.
 * - [abandonOwnSessions] abandons only UNCOMMITTED own sessions: those that read uncommitted and
 *   that no instance or process ever passed to [commit] (per [commits]). Abandoning a committed
 *   session cancels its install (X6, API 35), and `isCommitted()` still reads false for about 16 ms
 *   after `commit()` returns, so the durable marker, not `isCommitted()`, is what keeps it. When the
 *   markers cannot be read nothing is abandoned. Markers of sessions that are gone are dropped.
 * - [committedSessionPending] reads `getSessionInfo(id)`: present and committed (or present and
 *   remembered, during the lag) is pending, absent is gone, anything else or any failure is unknown.
 *   A present session never reads gone. Residual: a committed session parked in
 *   `STATUS_PENDING_USER_ACTION` would read pending with no attention signal. As Device Owner the
 *   self-update finished with `STATUS_SUCCESS` and no user action on API 33 and API 35
 *   (`spikes/installer-boundary/evidence/update-check-api33.txt`, `-api35.txt`).
 */
class StagedUpdateInstaller(private val port: InstallerPort, private val inspector: ApkInspector,
    private val staging: UpdateStaging, private val commits: CommitMemory, private val pinnedSelf: ApprovalBinding,
    private val selfCheck: () -> Boolean) : UpdateInstaller {
    private val self = pinnedSelf.packageName
    private var session: Int? = null
    private var finalRead = false
    private var verified: Int? = null
    /** Sessions this object tried to commit, remembered or not: never committed again. */
    private val attempted = mutableSetOf<Int>()

    /** Writes the staged update into a new session; its ID, or null (then no session is left behind). */
    @Synchronized
    fun prepare(): Int? {
        if (session != null) return null
        val file = staging.staged() ?: return null
        if (!fits(safely { inspector.inspect(file) })) return null
        val id = safely { port.create(self, file.length()) } ?: return null
        if (safely { port.write(id, ENTRY, file) } != true) {
            safely { port.abandon(id) }
            return null
        }
        session = id
        finalRead = false
        verified = null
        return id
    }

    @Synchronized
    override fun finalFacts(): UpdateFinalFacts? {
        verified = null
        val id = session ?: return null
        finalRead = true
        val facts = safely { read(id) } ?: return null
        verified = id
        return facts
    }

    private fun read(id: Int): UpdateFinalFacts? {
        if (id in attempted) return null
        val own = port.ownSessions()?.singleOrNull { it.id == id } ?: return null
        if (own.committed || own.appPackageName != self.value || port.entries(id) != listOf(ENTRY)) return null
        val hash = port.readSha256(id, ENTRY) ?: return null
        val file = staging.staged() ?: return null
        if (Sha256.of(file) != hash) return null
        val apk = inspector.inspect(file) ?: return null
        if (Sha256.of(file) != hash || staging.staged() != file || !fits(apk)) return null
        val installed = port.installedVersionCode(self) ?: return null
        return UpdateFinalFacts(id, StagedApkFacts(ApprovalBinding(apk.packageName, apk.signers),
            StagedUpdateBinding(hash, apk.versionCode), signatureValid = true), installed)
    }

    private fun fits(apk: ApkFacts?) = apk != null && pinnedSelf.matches(apk.packageName, apk.signers)

    @Synchronized
    override fun commit(sessionId: Int) {
        check(sessionId == session && finalRead && verified == sessionId && sessionId !in attempted) {
            "session $sessionId is not this installer's verified, uncommitted session"
        }
        attempted += sessionId
        verified = null
        check(commits.remember(sessionId)) { "session $sessionId could not be remembered before commit" }
        port.commit(sessionId)
    }

    @Synchronized
    override fun committedSessionPending(sessionId: Int): Boolean? {
        val s = try { port.sessionInfo(sessionId) } catch (_: Exception) { return null } ?: return false
        if (s.id != sessionId || s.appPackageName != self.value) return null
        if (s.committed) return true
        return if (commits.ids()?.contains(sessionId) == true) true else null
    }

    @Synchronized
    override fun abandonOwnSessions(): Boolean {
        // Drop this object's session either way: an abandoned one is gone, a committed one is never used again.
        session = null
        finalRead = false
        verified = null
        // Markers first: a session another instance creates after this read has no marker yet, and its
        // marker is written before it is committed, so it can be neither abandoned committed nor pruned.
        val remembered = commits.ids() ?: return false
        val before = safely { port.ownSessions() } ?: return false
        fun abandonable(s: OwnSession) = !s.committed && s.id !in remembered
        var ok = true
        for (s in before) if (abandonable(s) && safely { port.abandon(s.id) } != true) ok = false
        val after = safely { port.ownSessions() } ?: return false
        val pruned = commits.forget(remembered - before.map { it.id }.toSet())
        return ok && after.none(::abandonable) && pruned && staging.clear()
    }

    override fun installedVersionCode(): Long? = safely { port.installedVersionCode(self) }

    /** The handover self-check; a throw is a failed check. Running it again changes nothing. */
    override fun postUpdateSelfCheck(): Boolean = safely(selfCheck) == true

    private inline fun <T> safely(block: () -> T): T? = try { block() } catch (_: Exception) { null }

    private companion object {
        const val ENTRY = "base.apk"
    }
}
