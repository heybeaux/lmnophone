// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings
import org.junit.Assert.assertArrayEquals
import org.junit.Test
class WifiCredentialsTest {
    @Test fun outerQuotesLeavePassphraseAndNetworkNameBytesUnchanged() {
        for (raw in listOf("Sam\"s network", "Jo\\network", "Alex\\\"example")) {
            val encoded = quoteWifiValue(raw)
            assertArrayEquals(raw.toByteArray(Charsets.UTF_8), encoded.substring(1, encoded.length - 1).toByteArray(Charsets.UTF_8))
        }
    }
}
