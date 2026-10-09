// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.BuildInventory
import org.plainphone.core.Mode
import org.plainphone.core.PackageName

// Interface stubs from the D0 skeleton. Lane A owns this module and replaces them.

data class PolicyInput(
    val mode: Mode,
    val inventory: BuildInventory,
    val approved: Set<PackageName>,
    val installed: Set<PackageName>,
)

/** The hidden set, lock-task list and features, user restrictions and apply order. */
data class PolicyPlan(
    val hidden: Set<PackageName>,
    val lockTaskPackages: List<PackageName>,
    val lockTaskFeatures: Set<String>,
    val userRestrictions: Set<String>,
    val applyOrder: List<String>,
)

/** Stored policy copies. Lane D implements it (atomic writes, last-known-good, Direct Boot copy). */
interface PolicyStore {
    fun read(): ByteArray?

    fun write(bytes: ByteArray)
}
