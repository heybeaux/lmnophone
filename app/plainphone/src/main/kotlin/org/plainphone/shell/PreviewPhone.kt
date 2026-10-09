// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.time.LocalDate
import java.time.ZonedDateTime
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput
import org.plainphone.ui.user.AppRequestResult
import org.plainphone.ui.user.AppRequestStatus

/**
 * In-memory stand-in for the device, used until `:device` is wired. It holds sample data only,
 * sends no text messages and calls nothing on Android. Requests live until the process ends.
 */
class PreviewPhone(
    initial: PhoneSnapshot,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now,
) : PhoneSource, RequestOutbox {
    private var data = initial
    private var nextId = initial.requests.size

    override fun snapshot(): PhoneSnapshot = data.copy(now = clock())

    fun requestsOn(day: LocalDate): Int = data.requests.count { it.sentOn == day }

    override fun send(message: String): AppRequestResult {
        val today = clock().toLocalDate()
        if (requestsOn(today) >= RequestRules.DAILY_LIMIT) return AppRequestResult.DailyLimit
        val saved = SavedRequest("preview-${nextId++}", message, today, AppRequestStatus.Sent)
        data = data.copy(requests = listOf(saved) + data.requests)
        return AppRequestResult.Sent
    }

    companion object {
        /** Synthetic names and apps. Nothing here belongs to a real person. */
        fun sample(now: ZonedDateTime = ZonedDateTime.now()) = PhoneSnapshot(
            now = now,
            userName = "Alex",
            mode = Mode.TRUSTED,
            trustedPeople = listOf("Sam", "Jo"),
            requestRecipient = "Sam",
            selfManagedWaitMinutes = 10,
            apps = listOf(
                ApprovedApp("com.google.android.calendar", "Calendar"),
                ApprovedApp("com.android.camera2", "Camera"),
                ApprovedApp("com.google.android.deskclock", "Clock"),
                ApprovedApp("com.google.android.apps.maps", "Maps"),
                ApprovedApp("com.google.android.apps.photos", "Photos"),
            ),
            notices = NoticeInput(Mode.TRUSTED),
            requests = listOf(SavedRequest("preview-0", "The bus times app, to check the ferry bus",
                now.toLocalDate(), AppRequestStatus.Delivered("Sam"))),
        )
    }
}
