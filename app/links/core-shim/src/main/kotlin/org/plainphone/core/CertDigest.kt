// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

/** SHA-256 of one signing certificate, as 64 lowercase hex digits. */
@JvmInline
value class CertDigest(val value: String) {
    init { require(value.matches(Regex("[0-9a-f]{64}"))) { "Expected 64 lowercase hex digits" } }
    companion object {
        /** Accepts the colon-separated or upper-case forms that tools print. */
        fun parse(text: String): CertDigest = CertDigest(text.replace(":", "").lowercase())
    }
}
