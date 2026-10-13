// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Bounds for the authorized family-list slice. Never accepts URI syntax or control characters. */
object FamilyRules {
    const val MAX_PEOPLE = 6
    fun validName(value: String) = value.isNotBlank() && value.length <= 80 && value.none {
        Character.getType(it) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt(),
            Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt(), Character.SURROGATE.toInt())
    }
    fun validNumber(value: String): Boolean = value.matches(Regex("\\+?[0-9 ()-]{7,25}")) &&
        value.count { it in '0'..'9' } in 7..15
    fun validatePerson(id: String, name: String, spokenName: String, number: String) {
        require(id.matches(Regex("[a-z0-9]{1,32}"))) { "invalid family id" }
        require(validName(name) && validName(spokenName)) { "invalid family name" }
        require(validNumber(number)) { "invalid family number" }
    }
}
