// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.time.LocalDate
import java.time.ZonedDateTime
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput
import org.plainphone.ui.user.AppRequestResult
import org.plainphone.ui.user.AppRequestStatus

/** An app the home screen lists. [id] is the package name. */
data class ApprovedApp(val id: String, val label: String)

/** One saved "ask for an app" request. */
data class SavedRequest(val id: String, val message: String, val sentOn: LocalDate, val status: AppRequestStatus)

/**
 * Everything the screens need, read at one moment so dates and countdowns agree.
 * [requests] is newest first.
 */
data class PhoneSnapshot(
    val now: ZonedDateTime,
    val userName: String,
    val mode: Mode,
    val trustedPeople: List<String>,
    val requestRecipient: String,
    val selfManagedWaitMinutes: Int,
    val apps: List<ApprovedApp>,
    val notices: NoticeInput,
    val requests: List<SavedRequest>,
    val requestsReady: Boolean = false,
    val familyEnabled: Boolean = false,
    val familyCount: Int = 0,
)

/** Read side of the phone. The preview reads [PreviewPhone]; later D1 slices read `:device` and `:policy`. */
fun interface PhoneSource {
    fun snapshot(): PhoneSnapshot
}

/** Saves an app request and sends it to the fixed recipient. */
fun interface RequestOutbox {
    fun send(message: String): AppRequestResult
}
