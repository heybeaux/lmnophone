// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

/**
 * PLA-624. From API 34 the device policy engine stores an admin's `setApplicationHidden` value per
 * package and keeps it after the package is uninstalled (`dumpsys device_policy` still lists
 * `applicationHidden` = true). Installed again, the package is visible, but a second hide is the same
 * stored value: nothing is enforced and the call returns false. Seen on API 36 (pla104-a36).
 *
 * So a hide whose write returned false, on a package that still reads installed and visible, is
 * written false and then true once; the change makes the platform enforce it. A show is never
 * repeated, and a hidden package is never shown here. This proves nothing: the caller reads back.
 */
internal object HiddenPolicyWrite {
    fun write(hidden: Boolean, set: (Boolean) -> Boolean, readHidden: () -> Boolean?) {
        if (set(hidden) || !hidden || readHidden() != false) return
        set(false)
        set(true)
    }
}
