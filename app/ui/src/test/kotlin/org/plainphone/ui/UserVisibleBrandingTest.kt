// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class UserVisibleBrandingTest {
    @Test fun userVisibleResourcesUseTheCurrentName() {
        val oldNames = listOf(File("src/main/res"), File("../plainphone/src/main/res"))
            .flatMap { root -> root.walkTopDown().filter { it.extension == "xml" && it.parentFile.name.startsWith("values") }.toList() }
            .flatMap { file ->
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
                (0 until root.childNodes.length).map { root.childNodes.item(it) }
                    .filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE && it.textContent.contains("Plainphone") }
                    .map { "${file.name}: ${it.attributes.getNamedItem("name")?.nodeValue}" }
            }
        assertTrue("Old product name in user-visible resources: $oldNames", oldNames.isEmpty())
    }
}
