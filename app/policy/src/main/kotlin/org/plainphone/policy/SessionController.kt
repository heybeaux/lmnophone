// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.WindowKind

/** Supervised sessions and elevation: start, end, and revoke-on-start from the journal. */
interface SessionController {
    fun start(kind: WindowKind)

    fun end()

    fun revokeFromJournal()
}

/** Session journal. Lane D implements it. */
interface JournalStore {
    fun append(record: ByteArray)

    fun readAll(): List<ByteArray>
}
