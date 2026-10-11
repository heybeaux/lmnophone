// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.StoredPolicy

/**
 * [PolicyStore] on a [RecordFile]: the approved packages, one per line, sorted.
 *
 * [read] reports [PolicyHealth.VALID] for the primary copy, [PolicyHealth.LAST_KNOWN_GOOD] when only
 * the backup decodes, and [PolicyHealth.BOTH_INVALID] (with nothing approved) when neither does, so
 * the engine falls back to the compiled-in baseline.
 */
class FilePolicyStore internal constructor(dir: File, beforeRename: (File) -> Unit) : PolicyStore {
    constructor(dir: File) : this(dir, {})

    private val file = RecordFile(dir, "policy", SCHEMA_VERSION, beforeRename = beforeRename)

    override fun read(): StoredPolicy {
        file.readPrimary()?.let(::decode)?.let { return StoredPolicy(SCHEMA_VERSION, it, PolicyHealth.VALID) }
        file.readLastKnownGood()?.let(::decode)?.let { return StoredPolicy(SCHEMA_VERSION, it, PolicyHealth.LAST_KNOWN_GOOD) }
        return StoredPolicy(SCHEMA_VERSION, emptySet(), PolicyHealth.BOTH_INVALID)
    }

    override fun write(policy: StoredPolicy): Boolean {
        if (policy.schemaVersion != SCHEMA_VERSION) return false
        val text = policy.approved.map { it.value }.sorted().joinToString("") { "$it\n" }
        return file.save(text.toByteArray(Charsets.UTF_8))
    }

    private fun decode(payload: ByteArray): Set<PackageName>? = try {
        val text = payload.toString(Charsets.UTF_8)
        if (text.isNotEmpty() && !text.endsWith("\n")) null
        else text.lineSequence().filter { it.isNotEmpty() }.map(::PackageName).toSet()
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
