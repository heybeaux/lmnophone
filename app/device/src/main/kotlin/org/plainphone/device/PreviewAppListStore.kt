// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.plainphone.policy.PreviewAppRules

/** Existing credential-encrypted starter file plus a separate order file. No migration or reset. */
class PreviewAppListStore(private val dir: File,
    private val replace: (File, File) -> Unit = { source, target ->
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Unit
    }) {
    fun configured(): Boolean = synchronized(lock) { File(dir, "starter-list.txt").isFile }
    fun starter(): Set<String> = synchronized(lock) { read("starter-list.txt").toSet() }
    fun order(): List<String> = synchronized(lock) { read("app-order.txt") }
    fun saveStarter(packages: List<String>) = synchronized(lock) { write("starter-list.txt", packages) }
    fun saveOrder(packages: List<String>) = synchronized(lock) { write("app-order.txt", packages) }
    fun remove(target: String, protected: Set<String>): Boolean = synchronized(lock) {
        val next = PreviewAppRules.remove(starter(), target, protected) ?: return@synchronized false
        write("starter-list.txt", next.toList())
        true
    }
    /** Publish order before membership, so a newly visible app can never appear at its old position.
     * If membership fails, restore order. A crash before membership leaves the app hidden.
     */
    fun add(target: String, currentOrder: List<String>, eligible: () -> Boolean): Boolean = synchronized(lock) {
        val oldStarter = starter()
        if (target in oldStarter || !eligible()) return@synchronized false
        val oldOrder = order()
        val hadOrder = File(dir, "app-order.txt").exists()
        write("app-order.txt", currentOrder.filter { it != target } + target)
        try { write("starter-list.txt", oldStarter.toList() + target) }
        catch (failure: Exception) {
            // The injected write port may be the failure; restore through the real atomic writer.
            if (hadOrder) PreviewAppListStore(dir).saveOrder(oldOrder) else File(dir, "app-order.txt").delete()
            throw failure
        }
        true
    }
    private fun read(name: String): List<String> = File(dir, name).let { file ->
        if (file.exists()) file.readLines().map(String::trim).filter(String::isNotEmpty).distinct() else emptyList()
    }
    private fun write(name: String, packages: List<String>) {
        require(packages.all { it.isNotBlank() && it.none(Char::isWhitespace) })
        val file = File(dir, name)
        val temp = File(dir, "$name.new")
        try {
            FileOutputStream(temp).use { out ->
                out.write(packages.distinct().joinToString("\n", postfix = "\n").toByteArray())
                out.fd.sync()
            }
            replace(temp, file)
        } finally { temp.delete() }
    }
    companion object { private val lock = Any() }
}
