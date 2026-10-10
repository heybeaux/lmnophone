// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateSourceTest {
    @Test fun acceptsAPrivateNetworkAddressWithAnExplicitPort() {
        val s = UpdateSource.parse("http://192.168.1.20:8080/plainphone.apk")!!
        assertEquals("192.168.1.20", s.address.hostAddress)
        assertEquals(8080, s.port)
        assertEquals("/plainphone.apk", s.path)
        for (ok in listOf("http://10.0.2.2:8000/a.apk", "http://172.16.0.1:1/x", "http://172.31.255.255:65535/",
                "http://192.168.0.1:80/dir/plainphone-2.apk?v=2")) {
            assertEquals(ok, true, UpdateSource.parse(ok) != null)
        }
    }

    @Test fun refusesEverythingElse() {
        for (bad in listOf(
            "http://192.168.1.20/plainphone.apk", // no port
            "http://192.168.1.20:0/a", "http://192.168.1.20:65536/a", "http://192.168.1.20:080/a",
            "https://192.168.1.20:8443/a", "ftp://192.168.1.20:21/a", "HTTP://192.168.1.20:80/a",
            "http://laptop.local:8080/a", "http://localhost:8080/a", "http://127.0.0.1:8080/a",
            "http://8.8.8.8:80/a", "http://172.32.0.1:80/a", "http://172.15.255.255:80/a", "http://169.254.1.1:80/a",
            "http://0.0.0.0:80/a", "http://255.255.255.255:80/a", "http://192.168.001.20:80/a", "http://192.168.1:80/a",
            "http://[fd00::1]:80/a", "http://user@192.168.1.20:80/a", "http://192.168.1.20:80/a#frag",
            "http://192.168.1.20:80", "http://192.168.1.20:80/a b", "http://192.168.1.20:80/a\r\nHost: x",
            "http://192.168.1.20:80/a\\b", " http://192.168.1.20:80/a", "",
        )) {
            assertNull(bad, UpdateSource.parse(bad))
        }
    }
}
