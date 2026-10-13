// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.plainphone.device.PreviewAppListStore

/** Production Home mapper, also shared by T5. Unknown new starter entries append after saved entries. */
object PreviewHomeApps {
    fun read(store: PreviewAppListStore, own: Set<String>, label: (String) -> String?): List<Pair<String, String>> {
        val apps = store.starter().filter { it !in own }.mapNotNull { id -> label(id)?.let { id to it } }
        val saved = store.order().withIndex().associate { it.value to it.index }
        return apps.sortedWith(compareBy<Pair<String, String>> { saved[it.first] ?: Int.MAX_VALUE }
            .thenBy { it.second.lowercase() }.thenBy { it.first })
    }
}
