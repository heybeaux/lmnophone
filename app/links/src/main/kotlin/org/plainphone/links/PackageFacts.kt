// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName

// Interface stubs from the D0 skeleton. Lane C owns this module and replaces them.

/** Plain facts about one installed package. Lane D fills them from PackageManager. */
data class PackageFacts(
    val packageName: PackageName,
    val isSystem: Boolean,
    val signingCerts: Set<CertDigest>,
)

/** Plainphone's own exact package name and signing-certificate digests. */
data class PlainphoneIdentity(val packageName: PackageName, val signingCerts: Set<CertDigest>)
