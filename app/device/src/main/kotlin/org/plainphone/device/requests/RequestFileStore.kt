// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.requests

import java.io.*
import org.plainphone.policy.*

/** Private CE store. Atomic replacement + fsync before SMS handover. Corruption never means empty. */
class RequestFileStore(private val file: File) : RequestStore {
    override fun read(): RequestBook {
        if (!file.exists()) return RequestBook()
        require(file.length() <= 8_000_000) { "request record too large" }
        return DataInputStream(FileInputStream(file).buffered()).use { input ->
            require(input.readInt() == 1) { "unknown request record" }
            val recipient = if (input.readBoolean()) RequestRecipient(input.readUTF(), input.readUTF()) else null
            val count = input.readInt(); require(count in 0..10_000)
            val entries = List(count) {
                val id = input.readUTF(); val text = input.readUTF(); val day = input.readLong()
                val start = input.readLong(); val process = input.readUTF(); val size = input.readInt()
                require(id.matches(Regex("[A-Za-z0-9-]{1,64}")) && AppRequests.validText(text) && size in 1..16)
                RequestEntry(id, text, day, start, process, List(size) { when (val code = input.readInt()) {
                    -1 -> null
                    else -> RequestPart.entries.getOrNull(code) ?: throw IOException("invalid part")
                } })
            }
            require(entries.map { it.id }.distinct().size == entries.size && input.read() == -1)
            RequestBook(recipient, entries)
        }
    }
    override fun write(value: RequestBook) {
        require(value.requests.size <= 10_000)
        val parent = requireNotNull(file.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("request folder unavailable")
        val temp = File(parent, file.name + ".tmp")
        try {
            FileOutputStream(temp).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(1); out.writeBoolean(value.recipient != null)
                value.recipient?.let { out.writeUTF(it.name); out.writeUTF(it.number) }
                out.writeInt(value.requests.size)
                value.requests.forEach { r ->
                    out.writeUTF(r.id); out.writeUTF(r.text); out.writeLong(r.day); out.writeLong(r.started); out.writeUTF(r.process)
                    out.writeInt(r.parts.size); r.parts.forEach { out.writeInt(it?.ordinal ?: -1) }
                }
                out.flush(); stream.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("request save failed")
        } finally { temp.delete() }
    }
}
