// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.time.ZonedDateTime
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput

/** Live shell snapshot. RequestsActivity owns app requests. */
class PreviewPhone(private val initial: PhoneSnapshot,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now) : PhoneSource {
    override fun snapshot() = initial.copy(now = clock())
    companion object {
        fun empty(now: ZonedDateTime = ZonedDateTime.now()) = PhoneSnapshot(now, "", Mode.SELF_MANAGED,
            emptyList(), 10, emptyList(), NoticeInput(Mode.SELF_MANAGED))
    }
}
