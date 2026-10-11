// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.SerialDispatcher

/**
 * Hides a package that appears, unless the committed policy may show it. Android sends
 * `ACTION_PACKAGE_ADDED` for a new install and also for an unhide (API 35 evidence:
 * `app/device/evidence/pla186/`), so the approval permission window triggers it too.
 *
 * [onPackageAdded] only queues the work on [dispatcher]. An approval runs as one dispatcher task, so
 * the hide can never land inside its permission window (it would make every grant write fail); it
 * runs after the approval, against the state the approval left. [mayShow] is read then, not at the
 * broadcast: true for an approved or floor package, null when unreadable (then the package is hidden).
 * The never-hide list is read live; when it cannot be read nothing is hidden.
 */
class PackageAddedHandler(
    private val dispatcher: SerialDispatcher,
    private val port: DevicePolicyPort,
    private val mayShow: (PackageName) -> Boolean?,
    private val neverHide: () -> Set<PackageName>?,
) {
    /** The only entry point: queues the hide as its own dispatcher task. */
    fun onPackageAdded(name: PackageName) = dispatcher.post { handle(name) }

    /** The hide itself; private so nothing can run it outside its own dispatcher task (PLA-218). */
    private fun handle(name: PackageName) {
        if (!dispatcher.onDispatcher) return
        try {
            if (mayShow(name) == true) return
            val floor = neverHide() ?: return
            if (name in floor) return
            if (port.packageState(name)?.hidden == false) port.setHidden(name, true)
        } catch (_: RuntimeException) {
            // Best effort: the next policy apply hides it again.
        }
    }

    /** A context-registered receiver (manifest receivers no longer get ACTION_PACKAGE_ADDED). */
    fun register(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
                onPackageAdded(PackageName(name))
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        return receiver
    }
}
