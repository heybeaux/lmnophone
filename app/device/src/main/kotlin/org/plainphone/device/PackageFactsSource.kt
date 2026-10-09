// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.links.PackageFacts

/** Reads [PackageFacts] for every installed package, hidden ones included. */
fun interface PackageFactsSource {
    fun installedPackages(): List<PackageFacts>
}
