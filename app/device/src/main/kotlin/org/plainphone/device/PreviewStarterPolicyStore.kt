// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import java.io.File
import java.io.IOException
import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyStore
import org.plainphone.policy.StoredPolicy

/** Read-only health of the preview's actual persisted policy. Only Play eligibility uses this.
 * Home keeps its existing lenient reader. There is no backup, migration or full-engine policy.
 * [unlocked] must read CE availability on the device; injectable I/O proves refusal paths in JVM tests.
 */
class PreviewStarterPolicyStore(
    private val dir: File,
    private val unlocked: () -> Boolean = { true },
    private val readText: (File) -> String = { it.readText(Charsets.UTF_8) },
) : PolicyStore {
    override fun read(): StoredPolicy = synchronized(PreviewAppListStore.lock) {
        try {
            val file = File(dir, "starter-list.txt")
            if (!unlocked() || !file.isFile) return@synchronized invalid()
            val text = readText(file)
            if (text.isEmpty() || !text.endsWith('\n')) return@synchronized invalid()
            val names = linkedSetOf<PackageName>()
            for (line in text.split('\n').filter(String::isNotEmpty)) {
                if (line.any(Char::isWhitespace) || !names.add(PackageName(line))) return@synchronized invalid()
            }
            StoredPolicy(1, names, PolicyHealth.VALID)
        } catch (_: IOException) { invalid() }
        catch (_: SecurityException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
    }
    override fun write(policy: StoredPolicy): Boolean = false
    private fun invalid() = StoredPolicy(1, emptySet(), PolicyHealth.BOTH_INVALID)
}
