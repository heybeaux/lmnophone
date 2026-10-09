// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/**
 * Lane D's single transaction over the SAME credential and waiting stores used by PinVault/controller.
 * Compare credential revision, waiting generation/revision, pending request and CLEAR cancel intent.
 * Atomically write both replacements, including the SKIPPED receipt and its fixed delivery snapshot.
 * Crash recovery exposes either all old values or all new values before any reader/controller runs.
 * Never implement this as two independently acknowledged writes. All writers share one dispatcher.
 */
fun interface WaitingSkipCommitter {
    fun commit(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState): Boolean
}

/** No default transaction implementation: the adapter must explicitly wire the shared vault/stores. */
class WaitingSkipSupport(val vault: PinVault, val committer: WaitingSkipCommitter)

sealed interface WaitingSkipResult {
    /** Return this newly generated code to the skipper once, only after the entire transaction succeeds. */
    class Committing(val recoveryCode: String) : WaitingSkipResult {
        override fun toString() = "Committing(redacted)"
    }
    data class Wrong(val remainingMillis: Long, val notifyLockout: Boolean) : WaitingSkipResult
    data class Delayed(val remainingMillis: Long) : WaitingSkipResult
    data class NotSkipped(val outcome: WaitingOutcome) : WaitingSkipResult
}
