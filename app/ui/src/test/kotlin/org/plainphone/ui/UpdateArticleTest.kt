// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateArticleTest {
    @Test fun updateUsesAnAndTurningOffUsesCurrentName() {
        val strings = File("src/main/res/values").listFiles()!!.filter { it.extension == "xml" }.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) }.map { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }.toMap()
        for ((name, value) in mapOf(
            "admin_update" to "Install an LMNOPhone update",
            "opening_update" to "an LMNOPhone update",
            "opening_update_title" to "Install an LMNOPhone update?",
            "update_too_big" to "That file is too big to be an LMNOPhone update. Nothing has changed.",
            "off_turning" to "Turning LMNOPhone off…",
        )) assertEquals(name, value, strings[name])
    }
}
