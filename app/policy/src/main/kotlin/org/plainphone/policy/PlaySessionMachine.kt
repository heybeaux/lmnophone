// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.BootTime
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.core.WindowKind
import java.util.Collections

/** Identity data, never authority. Compare the complete current signer set, not a requested app name. */
class ApprovalBinding(val packageName: PackageName, signingCerts: Set<CertDigest>) {
    val signingCerts: Set<CertDigest> = immutableSet(signingCerts)
    init { require(this.signingCerts.isNotEmpty()) }
    fun matches(packageName: PackageName, signingCerts: Set<CertDigest>): Boolean =
        this.packageName == packageName && this.signingCerts == signingCerts
    override fun equals(other: Any?): Boolean = other is ApprovalBinding &&
        packageName == other.packageName && signingCerts == other.signingCerts
    override fun hashCode(): Int = 31 * packageName.hashCode() + signingCerts.hashCode()
}

sealed interface PlayEndReason {
    data class SessionEnded(val reason: SessionEnd) : PlayEndReason
    data object RESTART : PlayEndReason
    data object INVALID_CLOCK : PlayEndReason
    data object ACCESS_REVOKED : PlayEndReason
}

/** An immutable, process-local record of unapproved candidates observed during one Play session. */
class PlaySessionState internal constructor(
    internal val access: SessionAccess,
    internal val lastReading: BootTime,
    candidates: Map<PackageName, ApprovalBinding> = emptyMap(),
    val endedBecause: PlayEndReason? = null,
) {
    val candidates: Map<PackageName, ApprovalBinding> = Collections.unmodifiableMap(LinkedHashMap(candidates))
}

data class PlayInstallResult(val state: PlaySessionState, val accepted: Boolean)

/** Pure event intake. SessionController owns access; a separate fresh-authority transaction owns approval. */
object PlaySessionMachine {
    fun begin(access: SessionAccess?, now: BootTime): PlaySessionState? {
        if (access == null || access.id.isBlank() || access.kind != WindowKind.PLAY ||
            access.packages != setOf(PLAY_PACKAGE) || !access.isLive(now) ||
            access.deadline.elapsedMillis - now.elapsedMillis > 600_000) return null
        return PlaySessionState(access, now)
    }

    fun installed(
        state: PlaySessionState,
        eventSessionId: String,
        identity: ApprovalBinding,
        now: BootTime,
        currentAccess: SessionAccess?,
    ): PlayInstallResult {
        if (eventSessionId != state.access.id || state.endedBecause != null) return PlayInstallResult(state, false)
        if (now.bootId != state.lastReading.bootId || now.elapsedMillis < state.lastReading.elapsedMillis) {
            return PlayInstallResult(close(state, PlayEndReason.INVALID_CLOCK), false)
        }
        if (!state.access.isLive(now)) {
            return PlayInstallResult(close(state, PlayEndReason.SessionEnded(SessionEnd.DEADLINE)), false)
        }
        // Exact original access prevents a replacement session or changed deadline from extending intake.
        if (currentAccess != state.access) {
            return PlayInstallResult(close(state, PlayEndReason.ACCESS_REVOKED), false)
        }
        if (state.candidates[identity.packageName] == identity && now == state.lastReading) {
            return PlayInstallResult(state, true)
        }
        return PlayInstallResult(PlaySessionState(state.access, now,
            state.candidates + (identity.packageName to identity)), true)
    }

    /** Retained candidates are unapproved data even after the session ends. */
    fun candidate(state: PlaySessionState, packageName: PackageName): ApprovalBinding? = state.candidates[packageName]

    fun end(state: PlaySessionState, eventSessionId: String, reason: SessionEnd): PlaySessionState =
        if (eventSessionId != state.access.id) state else close(state, PlayEndReason.SessionEnded(reason))

    fun onRestart(state: PlaySessionState): PlaySessionState = close(state, PlayEndReason.RESTART)

    private fun close(state: PlaySessionState, reason: PlayEndReason): PlaySessionState =
        if (state.endedBecause != null) state
        else PlaySessionState(state.access, state.lastReading, state.candidates, reason)
}
