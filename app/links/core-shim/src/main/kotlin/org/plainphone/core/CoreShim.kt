// SPDX-License-Identifier: Apache-2.0
// Temporary copy of the :core types that :links consumes, matching lane A's mvp/policy shape.
// Deleted when :links is rebased onto the skeleton with the real :core.
package org.plainphone.core

import java.util.Collections

@JvmInline
value class PackageName(val value: String) {
    init { require(value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) }
    override fun toString(): String = value
}

class NeverHideList(packages: Set<PackageName>) {
    val packages: Set<PackageName> = Collections.unmodifiableSet(packages.toSet())
    operator fun contains(packageName: PackageName): Boolean = packageName in packages
}
