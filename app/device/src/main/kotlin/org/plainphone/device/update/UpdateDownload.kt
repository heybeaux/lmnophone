// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Where the trusted person said the helper's computer serves the update: `http://A.B.C.D:PORT/path`
 * with a private-network IPv4 address (10/8, 172.16/12, 192.168/16) written as digits, so no name
 * lookup can move it, and an explicit port. Everything else is refused, including names, loopback,
 * public addresses, IPv6, HTTPS (a laptop has no certificate a phone could check; the APK signature
 * is what binds the bytes), user info, fragments, any character that could end the request line and
 * a path longer than [MAX_PATH_CHARS].
 */
class UpdateSource internal constructor(val address: InetAddress, val port: Int, val path: String) {
    init { require(port in 1..65535 && path.startsWith("/") && path.all { it in PATH_CHARS }) }

    companion object {
        /** Longest accepted path, so the request line stays small (R3, PLA-207). */
        const val MAX_PATH_CHARS = 2048
        private const val PATH_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~/?=&%+,;:@!$'()*"
        private val URL = Regex("http://([0-9.]+):([1-9][0-9]{0,4})(/[^#]*)")
        private val OCTET = Regex("0|[1-9][0-9]{0,2}")

        fun parse(url: String): UpdateSource? {
            val m = URL.matchEntire(url) ?: return null
            val (host, portText, path) = m.destructured
            val port = portText.toInt()
            if (port > 65535 || path.length > MAX_PATH_CHARS || !path.all { it in PATH_CHARS }) return null
            val parts = host.split(".")
            if (parts.size != 4 || parts.any { !OCTET.matches(it) || it.toInt() > 255 }) return null
            val (a, b) = parts.map { it.toInt() }
            if (!(a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168))) return null
            return UpdateSource(InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray()), port, path)
        }
    }
}

/**
 * Downloads one update into [staging]: a direct connection (no proxy) to exactly the source's
 * address and port, one GET, a 200 response with a single plain `Content-Length` of at most
 * [maxBytes], no redirect (any 3xx is a failure and its `Location` is never contacted), no transfer
 * or content encoding, and the whole exchange within [limitNanos]. A failure leaves nothing staged.
 *
 * The deadline covers connect, request write and every read (R3, PLA-207): a watchdog closes the
 * socket when it passes, whichever call is blocked. A request longer than [maxRequestBytes], or with
 * a path longer than that bound leaves after the fixed request text and the longest address, is
 * refused before connecting; by default the path bound is [UpdateSource.MAX_PATH_CHARS].
 */
class UpdateDownload internal constructor(private val staging: UpdateStaging, private val maxBytes: Long,
    private val limitNanos: Long, private val maxRequestBytes: Int = MAX_REQUEST_BYTES) {
    constructor(staging: UpdateStaging) : this(staging, MAX_BYTES, LIMIT_NANOS)

    /** The sealed staged file, or null on any failure. */
    fun fetch(source: UpdateSource): File? {
        val deadline = System.nanoTime() + limitNanos
        val request = request(source)
        if (source.path.length.toLong() > maxRequestBytes.toLong() - REQUEST_OVERHEAD || request.size > maxRequestBytes) return null
        val part = staging.receive() ?: return null
        val ok = try {
            exchange(source, request, part, deadline)
        } catch (_: Exception) {
            false
        }
        if (ok && staging.seal(part)) return staging.staged()
        staging.clear()
        return null
    }

    private fun exchange(source: UpdateSource, request: ByteArray, part: File, deadline: Long): Boolean = Socket(Proxy.NO_PROXY).use { socket ->
        val watchdog = thread(isDaemon = true, name = "update-download-deadline") {
            try {
                val left = deadline - System.nanoTime()
                if (left > 0) Thread.sleep(left / 1_000_000, (left % 1_000_000).toInt())
                socket.close()
            } catch (_: InterruptedException) {
            } catch (_: IOException) {
            }
        }
        try {
            transfer(socket, source, request, part, deadline)
        } finally {
            watchdog.interrupt()
        }
    }

    private fun transfer(socket: Socket, source: UpdateSource, request: ByteArray, part: File, deadline: Long): Boolean {
        socket.connect(InetSocketAddress(source.address, source.port), remainingMillis(deadline))
        socket.getOutputStream().apply {
            write(request)
            flush()
        }
        val input = socket.getInputStream()
        var left = header(input, socket, deadline) ?: return false
        part.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (left > 0) {
                socket.soTimeout = remainingMillis(deadline)
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) return false
                out.write(buffer, 0, n)
                left -= n
            }
            out.flush()
            out.fd.sync()
        }
        return System.nanoTime() < deadline
    }

    private fun request(source: UpdateSource): ByteArray =
        request(source.path, "${source.address.hostAddress}:${source.port}").toByteArray(Charsets.US_ASCII)

    /** Reads the status line and headers; the body length, or null when the response is not acceptable. */
    private fun header(input: InputStream, socket: Socket, deadline: Long): Long? {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        var total = 0
        while (true) {
            socket.soTimeout = remainingMillis(deadline)
            val c = input.read()
            if (c < 0 || ++total > MAX_HEADER_BYTES) return null
            if (c == '\n'.code) {
                val text = line.toString().removeSuffix("\r")
                line.setLength(0)
                if (text.isEmpty()) break
                lines += text
            } else {
                line.append(c.toChar())
            }
        }
        val status = lines.firstOrNull() ?: return null
        if (!Regex("HTTP/1\\.[01] 200( .*)?").matches(status)) return null
        val fields = lines.drop(1).map { it.substringBefore(':').trim().lowercase() to it.substringAfter(':', "").trim() }
        if (fields.any { it.first == "transfer-encoding" || (it.first == "content-encoding" && it.second != "identity") }) return null
        val lengths = fields.filter { it.first == "content-length" }.map { it.second }
        val length = lengths.singleOrNull()?.takeIf { it.matches(Regex("[0-9]{1,12}")) }?.toLong() ?: return null
        return length.takeIf { it <= maxBytes }
    }

    private fun remainingMillis(deadline: Long): Int {
        val left = (deadline - System.nanoTime()) / 1_000_000
        if (left <= 0) throw IOException("time limit")
        return minOf(left, Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        private fun request(path: String, host: String) =
            "GET $path HTTP/1.1\r\nHost: $host\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
        /** The request text without its path, with the longest IPv4 address and port. */
        private val REQUEST_OVERHEAD = request("", "255.255.255.255:65535").length
        private val MAX_REQUEST_BYTES = REQUEST_OVERHEAD + UpdateSource.MAX_PATH_CHARS
        const val MAX_BYTES = 100L * 1024 * 1024
        const val LIMIT_NANOS = 120_000_000_000L
        private const val MAX_HEADER_BYTES = 16 * 1024
    }
}
