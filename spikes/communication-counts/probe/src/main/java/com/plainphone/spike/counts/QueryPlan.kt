// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package com.plainphone.spike.counts

/** THROWAWAY metadata-only contract. Selections execute in the provider. */
object QueryPlan {
    val projection = listOf("_id")
    const val callsNew = "type = 3 AND new = 1"
    const val callsUnacknowledged = "type = 3 AND new = 1 AND is_read IS NOT 1"
    const val callsUnread = "type = 3 AND is_read = 0"
    const val sms = "type = 1 AND read = 0"
    const val mms = "msg_box = 1 AND read = 0"
}
