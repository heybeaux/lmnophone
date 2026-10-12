// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.core.PackageName

/** DevicePolicyManager suspension, for fallback containment only. */
interface SuspensionPort {
    fun installed(): Set<PackageName>
    fun suspended(name: PackageName): Boolean?
    fun setSuspended(names: Set<PackageName>, suspended: Boolean): Set<PackageName>
}

interface SuspensionRecord {
    fun read(): Set<PackageName>
    fun save(names: Set<PackageName>): Boolean
}

data class Containment(val suspended: Set<PackageName>, val refused: Set<PackageName>, val recorded: Boolean)

class FallbackContainment(
    private val port: SuspensionPort,
    private val record: SuspensionRecord,
    private val locked: () -> Boolean,
    private val keyguard: () -> Boolean,
) {
    fun contain(keep: Set<PackageName>): Containment = Containment(emptySet(), emptySet(), false)
    fun liftIfLocked(): Set<PackageName>? = null
}
