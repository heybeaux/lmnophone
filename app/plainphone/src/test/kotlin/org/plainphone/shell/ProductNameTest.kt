// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** Read the host resource: its preview app label overrides the library's app_name. */
class ProductNameTest {
    @Test fun sharedLinkUsesProductNameWithoutPreviewSuffix() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val strings = document.getElementsByTagName("string")
        val resources = (0 until strings.length).map { strings.item(it) as Element }
            .associateBy { it.getAttribute("name") }
        val product = resources.getValue("product_name")
        assertEquals("false", product.getAttribute("translatable"))
        assertEquals("LMNOPhone (preview)", resources.getValue("app_name").textContent)
        assertEquals("LMNOPhone: Alex would like to look at this link with you: example.invalid/bus",
            ShareLinkText.format(product.textContent, "Alex", "example.invalid/bus"))
    }
}
