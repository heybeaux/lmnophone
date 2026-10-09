// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

@JvmInline
value class PackageName(val value: String) {
    init { require(value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) }
    override fun toString(): String = value
}

@JvmInline
value class TrustedPersonId(val value: String) {
    init { require(value.isNotBlank()) }
}

enum class Mode { TRUSTED, SELF_MANAGED }
enum class WindowKind { PLAY, SETTINGS, APPROVE, UPDATE, WEB, WIFI_SIGNIN }
