// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName

/**
 * The lock-task list as DevicePolicyManager returns it, without entries that are not package names.
 * API 35 returned such an entry after the list was cleared and the feature mask set (PLA-233). It
 * names no package, so dropping it loses nothing; the next list write leaves it out.
 */
internal fun lockTaskPackageNames(raw: Array<String>): Set<PackageName> =
    raw.mapNotNullTo(mutableSetOf()) { runCatching { PackageName(it) }.getOrNull() }
