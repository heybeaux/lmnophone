// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.policy.*

/** Shared, host-tested package restoration; Android supplies the remaining settings and inventory. */
class ReadbackGiveBackPort(private val packages: DevicePolicyPort, private val platform: GiveBackPort) : GiveBackPort by platform {
    override fun restore(action: GiveBackAction) {
        val name by lazy { PackageName(action.target) }
        when (action.kind) {
            GiveBackKind.VISIBLE -> {
                val state = packages.packageState(name) ?: return
                if (state.hidden) {
                    // Flush even when the binder returns an uncertain failure after changing visibility.
                    try { packages.setHidden(name, false) } finally { flush() }
                } else flush() // Repeat the flush after a crash between unhide and journal save.
                if (!state.enabled) packages.enable(name)
            }
            GiveBackKind.UNSUSPENDED -> packages.unsuspend(name)
            GiveBackKind.PERMISSION -> {
                check(packages.packageHidden(name) != true) { "permission target still hidden" }
                platform.restore(action)
            }
            GiveBackKind.PREFERRED_HOME -> {
                check(platform.anotherHomeResolves()) { "no successor Home" }
                platform.restore(action)
            }
            else -> platform.restore(action)
        }
    }
    override fun readBack(action: GiveBackAction): Boolean = when (action.kind) {
        GiveBackKind.VISIBLE -> packages.packageState(PackageName(action.target))?.let { !it.hidden && it.enabled } ?: true
        GiveBackKind.UNSUSPENDED -> packages.packageState(PackageName(action.target))?.let { !it.suspended } ?: true
        else -> platform.readBack(action)
    }
    private fun flush() {
        repeat(2) {
            try {
                val before = checkNotNull(packages.flushComponentEnabled())
                packages.setFlushComponentEnabled(!before)
                check(packages.flushComponentEnabled() == !before)
                packages.setFlushComponentEnabled(before)
                check(packages.flushComponentEnabled() == before)
                return
            } catch (_: Exception) { /* retry once; never undo the unhide */ }
        }
        error("unhide flush did not read back")
    }
}
