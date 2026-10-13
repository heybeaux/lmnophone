// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Every positive fact must come from a fresh, readable source. */
data class PreviewAddFacts(val installed: Boolean, val launchable: Boolean, val hidden: Boolean,
    val readable: Boolean, val browserAllowed: Boolean, val protected: Boolean = false,
    val installer: Boolean = false, val store: Boolean = false, val home: Boolean = false,
    val self: Boolean = false)

/** T5 preview edits never alter the live floor or its fixed rows. Null means refuse without writing. */
object PreviewAppRules {
    fun canAdd(id: String, starter: Set<String>, facts: PreviewAddFacts?): Boolean =
        facts != null && id !in starter && facts.installed && facts.launchable && facts.hidden &&
            facts.readable && facts.browserAllowed && !facts.protected && !facts.installer &&
            !facts.store && !facts.home && !facts.self

    fun remove(starter: Set<String>, target: String, protected: Set<String>): Set<String>? =
        if (target !in starter || target in protected) null else starter - target

    fun move(order: List<String>, target: String, delta: Int, protected: Set<String>): List<String>? {
        val index = order.indexOf(target)
        val next = index + delta
        if (delta !in setOf(-1, 1) || index < 0 || next !in order.indices ||
            target in protected || order[next] in protected) return null
        return order.toMutableList().also { it[index] = order[next]; it[next] = target }
    }
}
