// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import org.plainphone.core.PackageName
import org.plainphone.device.boot.SuspensionRecord

class FileSuspensionRecord(private val dir: File) : SuspensionRecord {
    override fun read(): Set<PackageName> = emptySet()
    override fun save(names: Set<PackageName>): Boolean = false
}
