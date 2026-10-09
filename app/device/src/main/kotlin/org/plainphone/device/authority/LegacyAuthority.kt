// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import org.plainphone.core.BuildInventory
import org.plainphone.policy.AppApprovalState

/** A committed record as a schema 1-3 store read it, and which copy files held it. */
internal class LegacyRecord(val schema: Int, val sequence: Long, val sections: List<ByteArray>, val holders: Set<String>)

/**
 * Read-only access to records written before schema 4. Those stores wrote copy A, copy B, then
 * both fences, and served the newest valid copy at or above the fence. Schema 1 had four
 * sections (no approvals) and a ledger-only elevation; schemas 1-2 had no people patch in
 * waiting operations. Schema 3 also kept cancel intents. Nothing here writes;
 * [JointAuthorityStore.migrate] does that.
 */
internal object LegacyAuthority {
    private val copies = listOf("authority-a", "authority-b")
    /** Cancel intents first appeared in schema 3; schema 4 changed their payload. */
    private const val INTENT_SCHEMA = 3

    fun load(dir: File): LegacyRecord? {
        for (schema in 3 downTo 1) {
            val fences = listOf("fence-a", "fence-b").mapNotNull { AtomicRecord(dir, it, schema).read() }
            if (fences.isEmpty()) continue
            val fence = fences.mapNotNull { if (it.size == 8) ByteBuffer.wrap(it).long else null }.maxOrNull() ?: return null
            val count = if (schema == 1) 4 else 5
            val valid = copies.mapNotNull { name ->
                AtomicRecord(dir, name, schema).read()?.let { bytes -> decode(bytes, count)?.let { Triple(name, it, bytes) } }
            }.filter { it.second.first >= fence }
            val newest = valid.maxByOrNull { it.second.first } ?: return null
            val same = valid.filter { it.second.first == newest.second.first }
            // Two copies claiming the same sequence must agree exactly.
            if (same.any { !it.third.contentEquals(newest.third) }) return null
            return LegacyRecord(schema, newest.second.first, newest.second.second, same.map { it.first }.toSet())
        }
        return null
    }

    /**
     * Request ids with a cancel intent in the schema 3 format: an entry holding the bare request
     * id (CLEAR) and a separate marker holding it (REQUESTED). A file counts only if it decodes as
     * schema 3 and its content hashes to its own name. Null when the directory can't be listed.
     */
    fun intentRequests(dir: File): Set<String>? {
        val files = dir.listFiles() ?: return null
        return files.mapNotNull { file ->
            val prefix = listOf("intent-clear-", "intent-requested-").firstOrNull { file.name.startsWith(it) && file.name.endsWith(".rec") }
                ?: return@mapNotNull null
            val name = file.name.removeSuffix(".rec")
            val id = AtomicRecord(dir, name, INTENT_SCHEMA).read()?.let { String(it) } ?: return@mapNotNull null
            // The name is the hash of the id's bytes, so a match also proves the bytes round-trip.
            id.takeIf { intentHash(it) == name.removePrefix(prefix) }
        }.toSet()
    }

    /**
     * Current-format sections holding the same values, in their canonical encoding (unordered maps
     * and sets sorted), or null if any old section does not decode.
     */
    fun convert(r: LegacyRecord, matchingInventory: BuildInventory?): List<ByteArray>? {
        val s = r.sections
        val credentials = AuthorityCodec.decodeCredentials(s[Section.CREDENTIALS.ordinal]) ?: return null
        val waiting = AuthorityCodec.decodeWaiting(s[Section.WAITING.ordinal], peoplePatches = r.schema >= 3) ?: return null
        val elevation = (if (r.schema == 1) AuthorityCodec.decodeLedgerOnlyElevation(s[Section.ELEVATION.ordinal])
            else AuthorityCodec.decodeElevation(s[Section.ELEVATION.ordinal], matchingInventory)) ?: return null
        val receipts = AuthorityCodec.decodeReceipts(s[Section.RECEIPTS.ordinal]) ?: return null
        // Schema 1 predates app approvals, so none can have been recorded.
        val approvals = if (r.schema == 1) AppApprovalState() else AuthorityCodec.decodeApprovals(s[Section.APPROVALS.ordinal]) ?: return null
        return listOf(AuthorityCodec.encodeCredentials(credentials) ?: return null, AuthorityCodec.encodeWaiting(waiting) ?: return null,
            AuthorityCodec.encodeElevation(elevation) ?: return null, AuthorityCodec.encodeReceipts(receipts) ?: return null,
            AuthorityCodec.encodeApprovals(approvals) ?: return null)
    }

    fun decode(bytes: ByteArray, count: Int): Pair<Long, List<ByteArray>>? = try {
        val buf = ByteBuffer.wrap(bytes)
        val sequence = buf.long
        val sections = List(count) {
            val size = buf.int
            require(size in 0..buf.remaining())
            ByteArray(size).also { b -> buf.get(b) }
        }
        if (buf.hasRemaining() || sequence < 0) null else sequence to sections
    } catch (_: RuntimeException) {
        null
    }

    fun encode(sequence: Long, sections: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeLong(sequence)
            sections.forEach { data.writeInt(it.size); data.write(it) }
        }
        return out.toByteArray()
    }
}
