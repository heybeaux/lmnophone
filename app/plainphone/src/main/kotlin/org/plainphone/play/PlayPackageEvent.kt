// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import org.plainphone.core.PackageName

/** PackageEvents carries the action value suffix, not the Android constant's source name. */
fun addedPlayPackage(reason: String): PackageName? {
    if (!reason.startsWith("package:PACKAGE_ADDED:")) return null
    return try { PackageName(reason.substringAfterLast(':')) } catch (_: IllegalArgumentException) { null }
}
