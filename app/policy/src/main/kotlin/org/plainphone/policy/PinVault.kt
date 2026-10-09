// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.TrustedPersonId

/** PIN and recovery-code checks: separate counters, the lockout schedule, one or two trusted people. */
interface PinVault {
    fun verifyPin(person: TrustedPersonId, pin: CharArray): Boolean

    fun verifyRecoveryCode(person: TrustedPersonId, code: CharArray): Boolean
}
