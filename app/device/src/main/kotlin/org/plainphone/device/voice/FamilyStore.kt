// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.plainphone.policy.FamilyRules

enum class FamilyPicture { Letter } // Photo import can add a private asset reference in a later slice.

/** Deliberately redacts personal data from logging and exception diagnostics. */
class FamilyPerson(val id: String, val name: String, val spokenName: String, val number: String,
    val picture: FamilyPicture = FamilyPicture.Letter) {
    init { FamilyRules.validatePerson(id, name, spokenName, number) }
    override fun toString() = "FamilyPerson(redacted)"
}

data class FamilyList(val enabled: Boolean = false, val people: List<FamilyPerson> = emptyList()) {
    init { require(people.size <= FamilyRules.MAX_PEOPLE && people.map { it.id }.distinct().size == people.size) }
    val visible get() = enabled && people.isNotEmpty()
}

/** The send controller resolves the live recipient at the point of use, never from a UI label. */
fun interface VoiceRecipientSource { fun recipient(personId: String): FamilyPerson? }

/**
 * Private CE/no-backup file supplied by AndroidFamilyStore. No contact provider, backup or logging.
 * Removal and number changes erase the old draft before publishing the changed list. Erasure failure
 * aborts the change; retries are safe. A crash before atomic replacement retains the previous list.
 */
class FamilyStore(private val file: File, private val eraseDraft: (String) -> Unit) : VoiceRecipientSource {
    fun read(): FamilyList = synchronized(lock) {
        if (!file.exists()) return@synchronized FamilyList()
        try {
            if (file.length() > 32_768) throw IOException("family file too large")
            DataInputStream(file.inputStream()).use { input ->
                if (input.readInt() != 1) throw IOException("unknown family format")
                val enabled = input.readBoolean()
                val count = input.readInt()
                if (count !in 0..FamilyRules.MAX_PEOPLE) throw IOException("invalid family count")
                val people = List(count) { FamilyPerson(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(),
                    FamilyPicture.valueOf(input.readUTF())) }
                if (input.read() != -1) throw IOException("invalid family file")
                FamilyList(enabled, people)
            }
        } catch (_: IllegalArgumentException) { throw IOException("invalid family data") }
    }

    fun save(person: FamilyPerson) = synchronized(lock) {
        val before = read()
        val existing = before.people.firstOrNull { it.id == person.id }
        require(existing != null || before.people.size < FamilyRules.MAX_PEOPLE) { "family list full" }
        if (existing != null && existing.number != person.number) eraseDraft(existing.id)
        write(before.copy(people = if (existing == null) before.people + person else
            before.people.map { if (it.id == person.id) person else it }))
    }

    fun remove(id: String) = synchronized(lock) {
        val before = read()
        if (before.people.none { it.id == id }) return@synchronized
        eraseDraft(id)
        write(before.copy(people = before.people.filterNot { it.id == id }))
    }

    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        val before = read()
        if (!enabled) before.people.forEach { eraseDraft(it.id) }
        write(before.copy(enabled = enabled))
    }

    override fun recipient(personId: String): FamilyPerson? = read().let { list ->
        if (list.enabled) list.people.firstOrNull { it.id == personId } else null
    }

    private fun write(list: FamilyList) {
        val parent = file.parentFile ?: throw IOException("family folder unavailable")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("family folder unavailable")
        val temp = File(parent, file.name + ".new")
        try {
            FileOutputStream(temp).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(1); out.writeBoolean(list.enabled); out.writeInt(list.people.size)
                list.people.forEach { p ->
                    out.writeUTF(p.id); out.writeUTF(p.name); out.writeUTF(p.spokenName)
                    out.writeUTF(p.number); out.writeUTF(p.picture.name)
                }
                out.flush(); stream.fd.sync()
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    companion object { private val lock = Any() }
}
