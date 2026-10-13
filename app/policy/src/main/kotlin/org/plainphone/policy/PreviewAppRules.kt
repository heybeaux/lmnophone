// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** T5 preview edits never alter the live floor or its fixed rows. Null means refuse without writing. */
object PreviewAppRules {
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
