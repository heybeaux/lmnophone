// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionWrite

/**
 * PLA-218 finding 3. One permission window at a time, owned by the identity that opened it. A second
 * open is refused and touches nothing; only the owner's close (or a hide of the owner) frees the window.
 */
class WindowOwnershipTest {
    private val h = ApprovalHarness()
    private val port = h.port
    private val e = h.effects
    private val allow = PermissionWrite(h.camera, PermissionChoice.ALLOW)

    @After fun stop() = h.close()

    private fun <T> d(block: () -> T): T = h.dispatcher.run(block)

    @Test fun aWrongIdentityCloseLeavesTheOpenWindowOwnedAndUsable() {
        d {
            assertTrue(e.openPermissionWindow(h.identity))
            assertFalse("not the owner", e.closePermissionWindow(h.siblingIdentity))
            assertFalse("the owner's app is still visible", port.hidden(h.target.value))
            assertTrue("the owner can still write", e.setPermission(h.identity, allow))
            assertTrue(e.closePermissionWindow(h.identity))
        }
        assertTrue(port.hidden(h.target.value))
    }

    @Test fun aSecondOpenIsRefusedAndTheFirstWindowStaysIntactAndClosable() {
        d {
            assertTrue(e.openPermissionWindow(h.identity))
            val writes = port.ops.size
            assertFalse("second identity", e.openPermissionWindow(h.siblingIdentity))
            assertFalse("same identity again", e.openPermissionWindow(h.identity))
            assertTrue("the refused opens wrote nothing: ${port.ops.drop(writes)}", port.ops.size == writes)
            e.closePermissionWindow(h.siblingIdentity) // the controller always closes after an open
            assertTrue(port.hidden(h.sibling.value))
            assertFalse(port.hidden(h.target.value))
            assertTrue("A's window is intact", e.setPermission(h.identity, allow))
            assertFalse("B never got a window", e.setPermission(h.siblingIdentity, allow))
            assertTrue(e.closePermissionWindow(h.identity))
            assertTrue(port.hidden(h.target.value))
            assertTrue("free again after the owner closed", e.openPermissionWindow(h.siblingIdentity))
            assertTrue(e.closePermissionWindow(h.siblingIdentity))
        }
    }

    @Test fun aPartlyOpenedWindowStaysOwnedUntilItsOwnerCloses() {
        port.flipsIgnored = 2 // unhidden, but the flush does not read back
        d {
            assertFalse(e.openPermissionWindow(h.identity))
            assertFalse("the target is visible", port.hidden(h.target.value))
            assertFalse("no write in a window that did not open", e.setPermission(h.identity, allow))
            assertFalse("no second window while the first app is visible", e.openPermissionWindow(h.siblingIdentity))
            assertTrue(e.closePermissionWindow(h.identity))
            assertTrue(e.openPermissionWindow(h.siblingIdentity))
            assertTrue(e.closePermissionWindow(h.siblingIdentity))
        }
    }
}
