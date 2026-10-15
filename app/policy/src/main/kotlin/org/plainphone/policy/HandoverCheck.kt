// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** This read-only subset cannot certify handover, even when every available check passes. */
enum class HandoverItem { OWNER, APPROVED_APPS, EMERGENCY, USB_DEBUGGING, WIRELESS_DEBUGGING }
enum class HandoverState { OK, NEEDS_HELPER, COULD_NOT_CHECK }
data class HandoverReport internal constructor(val items: Map<HandoverItem, HandoverState>) {
    val needsHelper: Int get() = items.values.count { it != HandoverState.OK }
    val ready: Boolean get() = false
}
object HandoverCheck {
    fun read(reader: (HandoverItem) -> Boolean?): HandoverReport = HandoverReport(
        HandoverItem.entries.associateWith { item ->
            when (try { reader(item) } catch (_: Exception) { null }) {
                true -> HandoverState.OK
                false -> HandoverState.NEEDS_HELPER
                null -> HandoverState.COULD_NOT_CHECK
            }
        })
}
