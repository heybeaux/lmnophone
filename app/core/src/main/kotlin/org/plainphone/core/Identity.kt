// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

// Interface stubs from the D0 skeleton. Lane A owns this module and replaces them.

@JvmInline
value class PackageName(val value: String) {
    // Same check as lane A's :core; lane C's parsers rely on it.
    init { require(value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) }

    override fun toString(): String = value
}

@JvmInline
value class TrustedPersonId(val value: String)

/** SHA-256 of a signing certificate, 64 lower-case hex characters. Approvals bind package + signer. */
@JvmInline
value class CertDigest(val value: String)

enum class Mode { TRUSTED, SELF_MANAGED }

enum class WindowKind { PLAY, SETTINGS, APPROVE, UPDATE, WEB, WIFI_SIGNIN }
