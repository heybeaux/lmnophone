/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.trusted

/** Display facts only. The host owns authentication, FLAG_SECURE, policy, clocks and persistence.
 * A null trustedPersonName means self-managed. No event is proof of fresh authority.
 */
data class AdminContext(val userName: String, val trustedPersonName: String? = null)
data class TrustedPersonChoice(val id: String, val name: String, val contact: String = "")
data class WhoAreYouUiState(val people: List<TrustedPersonChoice>)
sealed interface WhoAreYouEvent {
    data class Choose(val id: String) : WhoAreYouEvent
    data object Back : WhoAreYouEvent
}
enum class PinStage { Enter, Locked, Recovery, RecoveryLocked, NewPin, RecoveryIssued }
enum class RecoveryLockoutKind { PinAvailable, PinWaiting, Skip }
data class PinUiState(
    val context: AdminContext, val stage: PinStage = PinStage.Enter,
    val value: String = "", val confirmation: String = "", val canContinue: Boolean = false,
    val sending: Boolean = false, val wrongPin: Boolean = false, val wait: String = "",
    val otherName: String? = null, val pendingChange: String? = null,
    val freshExplanation: Boolean = false, val recoveryCode: String = "",
    val recoveryLockout: RecoveryLockoutKind = RecoveryLockoutKind.PinAvailable,
    val pinWait: String = "", val helper: String = "", val wrongRecoveryCode: Boolean = false,
)
sealed interface PinEvent {
    data class ValueChanged(val value: String) : PinEvent
    data class ConfirmationChanged(val value: String) : PinEvent
    data object Continue : PinEvent
    data object EnterPin : PinEvent
    data object UseRecoveryCode : PinEvent
    data object ChooseSomeoneElse : PinEvent
    data object Ok : PinEvent
}

enum class AdminDestination { AddApp, HiddenApp, Apps, Requests, Updates, Settings, Limits, People, History, Waits, UpdatePlainphone, ChangePin, SwitchMode, TurnOff }
data class AdminMenuItem(val destination: AdminDestination, val detail: String = "")
data class AdminNotice(val id: String, val text: String)
data class AdminMenuUiState(val context: AdminContext, val items: List<AdminMenuItem>,
    val needsYou: List<AdminNotice> = emptyList(), val openings: String? = null)
sealed interface AdminMenuEvent {
    data class Open(val destination: AdminDestination) : AdminMenuEvent
    data class OpenNotice(val id: String) : AdminMenuEvent
    data object Done : AdminMenuEvent
}

data class InstalledApp(val id: String, val name: String, val developer: String)
sealed interface WebReview {
    data class Checked(val note: String, val version: String) : WebReview
    data class Older(val note: String, val reviewed: String, val installed: String) : WebReview
    data object Unknown : WebReview
}
enum class AppReviewStage { Review, Unreviewed, Confirm, Added, Hidden, Remove, Removed }
data class AppReviewUiState(val context: AdminContext, val app: InstalledApp,
    val review: WebReview = WebReview.Unknown, val stage: AppReviewStage = AppReviewStage.Review,
    val helper: String = "", val request: String? = null, val allowed: String = "", val denied: String = "",
    val sending: Boolean = false)
sealed interface AppReviewEvent { data object Continue : AppReviewEvent; data object DontAdd : AppReviewEvent; data object MoreAbout : AppReviewEvent; data object Ok : AppReviewEvent }
enum class PermissionGroup { Camera, Microphone, Location, Contacts, Phone, CallHistory, Sms, Calendar, Nearby, Media, Notifications, Health }
enum class PermissionDecision { Allow, Deny }
data class PermissionChoice(val id: String, val group: PermissionGroup, val why: String? = null,
    val decision: PermissionDecision? = null, val unavailable: Boolean = false)
data class PermissionsUiState(val context: AdminContext, val appName: String,
    val permissions: List<PermissionChoice>, val canContinue: Boolean = false, val sending: Boolean = false,
    val waitMinutes: Int = 10)
sealed interface PermissionsEvent {
    data class Choose(val id: String, val decision: PermissionDecision) : PermissionsEvent
    data object Continue : PermissionsEvent
}

data class AppOnPhone(val app: InstalledApp, val alwaysOn: Boolean = false,
    val canMoveUp: Boolean = false, val canMoveDown: Boolean = false)
data class AppsOnPhoneUiState(val context: AdminContext, val apps: List<AppOnPhone>, val sending: Boolean = false)
sealed interface AppsOnPhoneEvent {
    data class Open(val id: String) : AppsOnPhoneEvent
    data class MoveUp(val id: String) : AppsOnPhoneEvent
    data class MoveDown(val id: String) : AppsOnPhoneEvent
    data object Back : AppsOnPhoneEvent
}
data class TrustedRequest(val id: String, val text: String, val timestamp: String, val textSent: Boolean)
data class TrustedRequestsUiState(val context: AdminContext, val requests: List<TrustedRequest>,
    val removeRequestId: String? = null, val sending: Boolean = false)
sealed interface TrustedRequestsEvent {
    data class Find(val id: String) : TrustedRequestsEvent
    data class NotNow(val id: String) : TrustedRequestsEvent
    data object Remove : TrustedRequestsEvent
    data object Keep : TrustedRequestsEvent
    data object Back : TrustedRequestsEvent
}

enum class PeopleAction { Add, HandOver, Remove, ToSelfManaged, ToTrusted, Ready }
data class TrustedPeopleUiState(val context: AdminContext, val people: List<TrustedPersonChoice>,
    val actions: List<PeopleAction>, val otherName: String? = null)
sealed interface TrustedPeopleEvent { data class Open(val action: PeopleAction) : TrustedPeopleEvent; data object Back : TrustedPeopleEvent }
data class PeopleChangeUiState(val context: AdminContext, val action: PeopleAction,
    val everyone: List<String>, val newName: String = "", val otherName: String = "",
    val change: String = "", val sending: Boolean = false)
sealed interface PeopleChangeEvent { data object Continue : PeopleChangeEvent; data object NotNow : PeopleChangeEvent }

/** Host provides the precise phase after saving. Never infer completion or elapsed time here.
 * null context is the PIN-free user route. Empty everyone means no SMS-recipient sentence.
 */
enum class WaitingStage { Details, ConfirmCancel, StillCancelling, Cancelled, Committing, Started, Another,
    SaveFailed, SkipIntro, SkipConfirm, SkipStarted, Stuck, StopConfirm, Stopping, Stopped, StillStopping, Recovered, Repair }
data class WaitingChangeUiState(val context: AdminContext? = null, val stage: WaitingStage,
    val change: String, val everyone: List<String> = emptyList(), val starterName: String = "",
    val day: String = "", val startedAt: String = "", val happensAt: String = "",
    val newName: String = "", val helper: String = "", val userName: String = "",
    val happensAtDay: org.plainphone.ui.home.HomeNoticeDay? = null,
    val canSkip: Boolean = false, val canStop: Boolean = false, val turningOff: Boolean = false,
    val sending: Boolean = false, val otherCommitting: Boolean = false, val changeToSelfManaged: Boolean = false)
sealed interface WaitingChangeEvent {
    data object Cancel : WaitingChangeEvent
    data object ConfirmCancel : WaitingChangeEvent
    data object KeepWaiting : WaitingChangeEvent
    data object Skip : WaitingChangeEvent
    data object EnterRecoveryCode : WaitingChangeEvent
    data object ConfirmSkip : WaitingChangeEvent
    data object Stop : WaitingChangeEvent
    data object ConfirmStop : WaitingChangeEvent
    data object Repair : WaitingChangeEvent
    data object Details : WaitingChangeEvent
    data object SeePeople : WaitingChangeEvent
    data object SeeWaiting : WaitingChangeEvent
    data object Ok : WaitingChangeEvent
}
enum class SessionStage { Play, Settings, CheckIntro, CheckResult, FailedApps, CheckConfirm, Checking, Restored, NotReady }
data class CheckApp(val id: String, val name: String, val selected: Boolean = false)
data class SessionUiState(val context: AdminContext, val stage: SessionStage, val helper: String = "",
    val helperIsCurrentPerson: Boolean = false, val dueDate: String = "", val apps: List<CheckApp> = emptyList(),
    val restoredLines: List<String> = emptyList(), val details: List<String> = emptyList(),
    val showDetails: Boolean = false, val sending: Boolean = false, val closesAt: String = "",
    val checkUnfinished: Boolean = false, val playServicesUnchanged: Boolean = false)
sealed interface SessionEvent {
    data object Continue : SessionEvent
    data object NotNow : SessionEvent
    data object SomeDidNotUpdate : SessionEvent
    data class ToggleApp(val id: String) : SessionEvent
    data object Details : SessionEvent
}

enum class AppDetailStage { Details, DidNotUpdate, Unexpected, CouldNotHide, TextsNotSending }
data class AppDetailUiState(val context: AdminContext, val app: InstalledApp, val stage: AppDetailStage = AppDetailStage.Details,
    val version: String = "", val added: String = "", val updated: String = "", val helper: String = "",
    val review: WebReview = WebReview.Unknown, val permissions: List<String> = emptyList(), val sending: Boolean = false)
sealed interface AppDetailEvent { data object Primary : AppDetailEvent; data object Secondary : AppDetailEvent; data object Permissions : AppDetailEvent }

enum class UpdateStage { Intro, Address, Getting, Unreachable, TooBig, Invalid, NotNewer, Confirm, Installing, Done }
data class UpdateUiState(val context: AdminContext, val stage: UpdateStage, val helper: String,
    val address: String = "", val version: String = "", val canContinue: Boolean = false, val sending: Boolean = false)
sealed interface UpdateEvent { data object Continue : UpdateEvent; data class AddressChanged(val value: String) : UpdateEvent }

enum class OpeningKind { Play, Settings, App, Update }
enum class OpeningStage { Before, Countdown, Ready, NoOpenings, AlreadyOpening, SaveFailed, AddInstalled, TooLate, UsedUp, UpdateExpired }
data class OpeningUiState(val context: AdminContext, val stage: OpeningStage, val kind: OpeningKind,
    val appName: String = "", val waitMinutes: Int = 10, val durationMinutes: Int = 5,
    val minutes: Int = 1, val left: Int = 2, val limit: Int = 2, val time: String = "",
    val alreadyOpen: Boolean = false, val sending: Boolean = false)
sealed interface OpeningEvent { data object Continue : OpeningEvent; data object NotNow : OpeningEvent; data object Stop : OpeningEvent; data object See : OpeningEvent }
data class WaitsAndLimitsUiState(val context: AdminContext, val waitLabel: String, val limit: Int,
    val canDecreaseWait: Boolean, val canIncreaseWait: Boolean, val canDecreaseLimit: Boolean, val canIncreaseLimit: Boolean,
    val canSave: Boolean, val sending: Boolean = false, val saved: Boolean = false)
sealed interface WaitsAndLimitsEvent { data object DecreaseWait : WaitsAndLimitsEvent; data object IncreaseWait : WaitsAndLimitsEvent
    data object DecreaseLimit : WaitsAndLimitsEvent; data object IncreaseLimit : WaitsAndLimitsEvent; data object Save : WaitsAndLimitsEvent; data object Ok : WaitsAndLimitsEvent }

enum class HistoryAuthority { Pin, Recovery, NoPin, Text, Plainphone, Number }
enum class ReceiptStatus { Sent, Waiting, Failed }
data class HistoryReceipt(val name: String, val status: ReceiptStatus)
data class HistoryEntry(val timestamp: String, val action: String, val authority: HistoryAuthority,
    val name: String = "", val receipts: List<HistoryReceipt> = emptyList())
data class HistoryUiState(val context: AdminContext, val entries: List<HistoryEntry>)
sealed interface HistoryEvent { data object Done : HistoryEvent }

enum class TurnOffStage { Intro, FirstConfirmation, LastConfirmation, TurningOff, Off, Failed }
data class TurnOffUiState(val context: AdminContext, val stage: TurnOffStage, val everyone: List<String> = emptyList(),
    val helper: String = "", val step: Int = 0, val total: Int = 0, val stepDescription: String = "",
    val resumed: Boolean = false, val sending: Boolean = false, val testBuild: Boolean = false)
sealed interface TurnOffEvent { data object Continue : TurnOffEvent; data object KeepOn : TurnOffEvent; data object Details : TurnOffEvent }
