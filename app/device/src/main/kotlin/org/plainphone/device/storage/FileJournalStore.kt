// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.PackageName
import org.plainphone.core.WindowKind
import org.plainphone.policy.JournalStore
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.SessionPhase

/**
 * [JournalStore] on a [RecordFile] in device-protected storage, so it works before the first unlock.
 *
 * One record holds the revocation latch and the journal entry, so a write of [SessionJournal.Empty]
 * clears the latch in the same atomic replacement. A missing, truncated, altered, wrong-schema or
 * non-canonical record reads as [SessionJournal.Damaged], and so does an entry that
 * [SessionJournal.restore] rejects. There is no last-known-good copy: an older entry could hide a
 * live relaxation.
 *
 * [inventory] is used for decoding only when its model and fingerprint match the live build.
 * Writes are serialized per directory and acknowledged only after the synced record reads back.
 */
class FileJournalStore internal constructor(
    dir: File,
    inventory: BuildInventory?,
    liveModel: String,
    liveFingerprint: String,
    beforeRename: (File) -> Unit,
) : JournalStore {
    constructor(dir: File, inventory: BuildInventory?, liveModel: String, liveFingerprint: String) :
        this(dir, inventory, liveModel, liveFingerprint, {})

    private val file = RecordFile(dir, "journal", SCHEMA_VERSION, keepLastKnownGood = false, beforeRename = beforeRename)
    private val matchingInventory = inventory?.takeIf { it.model == liveModel && it.fingerprint == liveFingerprint }
    private val lock = locks.computeIfAbsent(dir.absoluteFile.normalize().path) { Any() }

    // Kept when the latch cannot be written, so this instance still refuses new sessions.
    @Volatile private var latchedInMemory = false

    private class Entry(val latched: Boolean, val journal: SessionJournal)

    override fun read(): SessionJournal = synchronized(lock) { load()?.journal ?: SessionJournal.Damaged }

    /** An unreadable record counts as pending. */
    override fun revocationPending(): Boolean = synchronized(lock) { latchedInMemory || load()?.latched ?: true }

    /** Writes the latch before returning, keeping the entry; an unreadable entry stays Damaged. */
    override fun beginRevocation() {
        synchronized(lock) {
            latchedInMemory = true
            val current = load()
            if (current?.latched != true) file.save(encode(true, current?.journal ?: SessionJournal.Damaged))
        }
    }

    /** Replacing with Empty also clears the latch. Damaged is never written by a caller. */
    override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean = synchronized(lock) {
        if (replacement == SessionJournal.Damaged) return false
        val current = load()
        if ((current?.journal ?: SessionJournal.Damaged) != expected) return false
        val clearing = replacement == SessionJournal.Empty
        if (!file.save(encode(!clearing && current?.latched != false, replacement))) return false
        if (clearing) latchedInMemory = false
        true
    }

    private fun load(): Entry? {
        val payload = file.readPrimary() ?: return null
        val entry = decode(payload) ?: return null
        // One encoding per entry: unsorted, duplicate or extra bytes are not accepted.
        return entry.takeIf { encode(it.latched, it.journal).contentEquals(payload) }
    }

    private fun decode(payload: ByteArray): Entry? = try {
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val latched = when (input.readByte().toInt()) { 0 -> false; 1 -> true; else -> return null }
            val journal = when (input.readByte().toInt()) {
                EMPTY -> SessionJournal.Empty
                DAMAGED -> SessionJournal.Damaged
                ACTIVE -> {
                    val id = input.readUTF()
                    val kind = WindowKind.valueOf(input.readUTF())
                    val count = input.readInt()
                    if (count < 0) return null
                    val packages = (0 until count).map { PackageName(input.readUTF()) }.toSet()
                    val deadline = BootTime(input.readUTF(), input.readLong())
                    val phase = SessionPhase.valueOf(input.readUTF())
                    SessionJournal.restore(id, kind, packages, deadline, phase, matchingInventory)
                }
                else -> return null
            }
            if (input.available() != 0) null else Entry(latched, journal)
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun encode(latched: Boolean, journal: SessionJournal): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeByte(if (latched) 1 else 0)
            when (journal) {
                SessionJournal.Empty -> data.writeByte(EMPTY)
                SessionJournal.Damaged -> data.writeByte(DAMAGED)
                is SessionJournal.Active -> {
                    val access = journal.access
                    data.writeByte(ACTIVE)
                    data.writeUTF(access.id)
                    data.writeUTF(access.kind.name)
                    val packages = access.packages.map { it.value }.sorted()
                    data.writeInt(packages.size)
                    packages.forEach(data::writeUTF)
                    data.writeUTF(access.deadline.bootId)
                    data.writeLong(access.deadline.elapsedMillis)
                    data.writeUTF(journal.phase.name)
                }
            }
        }
        return out.toByteArray()
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private const val EMPTY = 0
        private const val ACTIVE = 1
        private const val DAMAGED = 2
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
