/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

/** Display facts only. The host selects mode, recipients, eligibility and navigation. */
/** [voicePractice] shows the practice voice messages row (preview builds only, PLA-457). */
data class MoreUiState(val mode: MoreMode, val voicePractice: Boolean = false)
sealed interface MoreMode {
    data class Trusted(val people: List<String>, val requestRecipient: String) : MoreMode
    data class SelfManaged(val waitMinutes: Int) : MoreMode
}
sealed interface MoreEvent {
    data object Back : MoreEvent
    /** Host opens the system dialer with the emergency number; never places a call. */
    data object OpenEmergencyDialer : MoreEvent
    data object OpenPhoneSettings : MoreEvent
    data object AskForApp : MoreEvent
    data object AddApp : MoreEvent
    data object OpenTrustedPerson : MoreEvent
    data object OpenSelfManagedSettings : MoreEvent
    data object OpenAbout : MoreEvent
    data object OpenVoicePractice : MoreEvent
}

/** Host enforces 280 characters, letter requirement, quota and fixed recipient before sending.
 * Supply null counter through 200 characters; history is newest-first, at most ten records.
 */
data class AskForAppUiState(
    val recipientName: String,
    val message: String = "",
    val canSend: Boolean = false,
    val charactersRemaining: Int? = null,
    val requests: List<AppRequestRow> = emptyList(),
    val sending: Boolean = false,
)
data class AppRequestRow(val id: String, val message: String, val day: String, val status: AppRequestStatus)
sealed interface AppRequestStatus {
    data object Sending : AppRequestStatus
    data object Sent : AppRequestStatus
    data class Delivered(val recipientName: String) : AppRequestStatus
    data object Failed : AppRequestStatus
    data object Added : AppRequestStatus
    data object NotAdded : AppRequestStatus
}
sealed interface AskForAppEvent {
    data object Back : AskForAppEvent
    data class MessageChanged(val value: String) : AskForAppEvent
    data object Send : AskForAppEvent
    data class Retry(val requestId: String) : AskForAppEvent
}
enum class AppRequestResult { Sent, Failed, DailyLimit }
/** Host sets sending before starting work and also rejects duplicate commands. */
data class AppRequestResultUiState(val recipientName: String, val result: AppRequestResult,
    val requests: List<AppRequestRow> = emptyList(), val sending: Boolean = false)
sealed interface AppRequestResultEvent {
    data object Ok : AppRequestResultEvent
    data object Retry : AppRequestResultEvent
    data class RetryRequest(val requestId: String) : AppRequestResultEvent
}

/** Only host-sanitized display data enters this seam. Never pass a raw URL.
 * Host owns registrable-host extraction, ASCII conversion, length checks and SMS sanitization.
 */
sealed interface BlockedLinkUiState {
    data class Blocked(val host: String, val unusualLetters: Boolean = false, val shareWith: String? = null) : BlockedLinkUiState
    data object TooLong : BlockedLinkUiState
    /** Host sets sending before starting work and also rejects duplicate commands. */
    data class Confirm(val recipientName: String, val sanitizedSmsPreview: String, val sending: Boolean = false) : BlockedLinkUiState
    data class Sent(val recipientName: String) : BlockedLinkUiState
    data object Failed : BlockedLinkUiState
}
/** Deliberately no More, settings, trusted-person, raw-URI or launch event. */
sealed interface BlockedLinkEvent {
    data object GoBack : BlockedLinkEvent
    data object ReviewSharing : BlockedLinkEvent
    data object Send : BlockedLinkEvent
    data object DontSend : BlockedLinkEvent
    data object Ok : BlockedLinkEvent
}
enum class WifiProblem { SignInRequired, NoInternet }
data class WifiMessageUiState(val problem: WifiProblem)
sealed interface WifiMessageEvent {
    data object ForgetNetwork : WifiMessageEvent
    data object Ok : WifiMessageEvent
}
