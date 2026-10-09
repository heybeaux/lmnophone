// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName

/** Plainphone's own identity: the exact package name and the exact set of current signing certificates. */
data class PlainphoneIdentity(val packageName: PackageName, val signingCerts: Set<CertDigest>) {
    init { require(signingCerts.isNotEmpty()) }

    fun matches(facts: PackageFacts): Boolean = facts.packageName == packageName && facts.signingCerts == signingCerts
}

/**
 * Plain facts about one installed package. Lane D fills these from PackageManager; this module never calls Android.
 *
 * @property handlesProbeHttp `queryIntentActivities(MATCH_ALL)` lists the package for `ACTION_VIEW` + `BROWSABLE`
 *   on `http://plainphone-probe.invalid/`. A local resolver probe only: the URL is never fetched.
 * @property handlesProbeHttps the same probe for `https://plainphone-probe.invalid/`.
 * @property verifiedDomains hosts whose App Links are verified for this package (informational, used for the reason).
 */
data class PackageFacts(
    val packageName: PackageName,
    val signingCerts: Set<CertDigest>,
    val isSystem: Boolean,
    val handlesProbeHttp: Boolean,
    val handlesProbeHttps: Boolean,
    val verifiedDomains: Set<String> = emptySet(),
) {
    companion object {
        /** The probe host. `.invalid` is reserved (RFC 2606), so no real site can answer it. */
        const val PROBE_HOST: String = "plainphone-probe.invalid"
    }
}
