// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class UpdateDownloadTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
    private val connections = AtomicInteger()
    private val requests = CopyOnWriteArrayList<String>()
    private val staging by lazy { UpdateStaging(File(tmp.root, "update-staging")) }
    // Loopback stands in for the helper's computer; UpdateSource.parse itself refuses it (UpdateSourceTest).
    private val source by lazy { UpdateSource(InetAddress.getLoopbackAddress(), server.localPort, "/plainphone.apk") }

    @After fun close() = server.close()

    private fun serve(respond: (Socket) -> Unit) {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { return@thread }
                connections.incrementAndGet()
                thread(isDaemon = true) {
                    socket.use {
                        val reader = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val head = generateSequence { reader.readLine() }.takeWhile { line -> line.isNotEmpty() }.toList()
                        requests += head.joinToString("\n")
                        try { respond(it) } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    private fun reply(body: ByteArray, status: String = "200 OK", headers: String = "Content-Length: ${body.size}\r\n") = serve {
        it.getOutputStream().apply { write("HTTP/1.1 $status\r\n$headers\r\n".toByteArray()); write(body); flush() }
    }
    private fun download(maxBytes: Long = 1_000_000, limitMillis: Long = 5_000) =
        UpdateDownload(staging, maxBytes, limitMillis * 1_000_000)
    private fun perms(f: File) = PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath(), LinkOption.NOFOLLOW_LINKS))

    @Test fun downloadsExactBytesIntoPrivateReadOnlyStaging() {
        val body = ByteArray(300_000) { (it * 31).toByte() }
        reply(body)
        val file = download().fetch(source)!!
        assertArrayEquals(body, file.readBytes())
        assertEquals(file, staging.staged())
        assertEquals("r--------", perms(file))
        assertEquals("rwx------", perms(file.parentFile))
        assertEquals("GET /plainphone.apk HTTP/1.1", requests.single().lines().first())
        assertTrue(requests.single().contains("Host: 127.0.0.1:${server.localPort}"))
    }

    @Test fun redirectsAreRefusedAndNeverFollowed() {
        for (status in listOf("301 Moved Permanently", "302 Found", "303 See Other", "307 Temporary Redirect", "308 Permanent Redirect")) {
            val before = connections.get()
            val server2 = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
            server2.use {
                serveOn(it, "HTTP/1.1 $status\r\nLocation: http://127.0.0.1:${server.localPort}/other.apk\r\nContent-Length: 0\r\n\r\n")
                val src = UpdateSource(InetAddress.getLoopbackAddress(), it.localPort, "/plainphone.apk")
                reply(byteArrayOf(1, 2, 3))
                assertNull(status, download().fetch(src))
            }
            assertEquals("redirect target never contacted ($status)", before, connections.get())
            assertNull(staging.staged())
        }
    }

    private fun serveOn(socket: ServerSocket, raw: String) = thread(isDaemon = true) {
        try { socket.accept().use { s -> s.getInputStream().read(ByteArray(4096)); s.getOutputStream().write(raw.toByteArray()) } } catch (_: Exception) {}
    }

    @Test fun nonOkStatusesAreRefused() {
        reply("nope".toByteArray(), status = "404 Not Found")
        assertNull(download().fetch(source))
        assertNull(staging.staged())
    }

    @Test fun declaredSizeOverTheLimitIsRefusedBeforeReading() {
        reply(ByteArray(2_001), headers = "Content-Length: 2001\r\n")
        assertNull(download(maxBytes = 2_000).fetch(source))
        assertNull(staging.staged())
    }

    @Test fun exactlyTheLimitIsAllowed() {
        reply(ByteArray(2_000))
        assertNotNull(download(maxBytes = 2_000).fetch(source))
    }

    @Test fun defaultLimitsAre100MbAnd2Minutes() {
        assertEquals(100L * 1024 * 1024, UpdateDownload.MAX_BYTES)
        assertEquals(120_000_000_000L, UpdateDownload.LIMIT_NANOS)
    }

    @Test fun missingLengthChunkedOrEncodedBodiesAreRefused() {
        for (headers in listOf("", "Transfer-Encoding: chunked\r\n", "Content-Length: 3\r\nContent-Encoding: gzip\r\n",
                "Content-Length: 3\r\nContent-Length: 4\r\n", "Content-Length: -3\r\n", "Content-Length: 3x\r\n")) {
            val s = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
            s.use {
                serveOn(it, "HTTP/1.1 200 OK\r\n$headers\r\nabc")
                assertNull(headers, download().fetch(UpdateSource(InetAddress.getLoopbackAddress(), it.localPort, "/a")))
            }
        }
        assertNull(staging.staged())
    }

    @Test fun shortBodyIsRefused() {
        reply("abc".toByteArray(), headers = "Content-Length: 10\r\n")
        assertNull(download().fetch(source))
        assertNull(staging.staged())
    }

    @Test fun slowDripPastTheTimeLimitIsRefused() {
        serve {
            val out = it.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n".toByteArray())
            repeat(1000) { out.write(1); out.flush(); Thread.sleep(50) }
        }
        val start = System.nanoTime()
        assertNull(download(limitMillis = 600).fetch(source))
        val took = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $took ms", took < 2_000)
        assertNull(staging.staged())
    }

    @Test fun silentServerTimesOut() {
        serve { Thread.sleep(10_000) }
        val start = System.nanoTime()
        assertNull(download(limitMillis = 400).fetch(source))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
    }

    @Test fun refusedConnectionIsNull() {
        val port = server.localPort
        server.close()
        assertNull(download().fetch(UpdateSource(InetAddress.getLoopbackAddress(), port, "/a")))
    }

    @Test fun failedDownloadLeavesNoEarlierStagedFile() {
        reply(byteArrayOf(1, 2, 3))
        assertNotNull(download().fetch(source))
        server.close()
        assertNull(download().fetch(source))
        assertNull("a failed download never leaves the previous file staged", staging.staged())
    }
}
