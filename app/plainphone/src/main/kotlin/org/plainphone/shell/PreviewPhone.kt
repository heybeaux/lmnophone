// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.time.LocalDate
import java.time.ZonedDateTime
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput
import org.plainphone.ui.user.AppRequestResult

/** Live shell snapshot. App-request delivery is not configured in this increment. */
class PreviewPhone(private val initial: PhoneSnapshot,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now) : PhoneSource, RequestOutbox {
    override fun snapshot() = initial.copy(now = clock())
    fun requestsOn(day: LocalDate) = initial.requests.count { it.sentOn == day }
    override fun send(message: String) = AppRequestResult.Failed
    companion object {
        fun empty(now: ZonedDateTime = ZonedDateTime.now()) = PhoneSnapshot(now, "", Mode.SELF_MANAGED,
            emptyList(), "", 10, emptyList(), NoticeInput(Mode.SELF_MANAGED), emptyList())
    }
}
