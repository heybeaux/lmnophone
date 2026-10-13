// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.*
import org.plainphone.ui.trusted.*

class PreviewAppsFlowTest {
    private class Port : PreviewAppsPort {
        var removes = 0
        var callback: ((AppRemovalResult) -> Unit)? = null
        var active: (() -> Boolean)? = null
        override fun read(done: (List<PreviewAppRow>?) -> Unit) = done(listOf(
            PreviewAppRow("calculator", "Calculator", "1", false), PreviewAppRow("phone", "Phone", "1", true)))
        override fun move(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit) = done(true)
        override fun remove(id: String, pin: CharArray, active: () -> Boolean, done: (AppRemovalResult) -> Unit) {
            removes++; pin.fill('\u0000'); this.active = active; callback = done
        }
    }
    @Test fun requiresConfirmationAndFreshPinAndBackRevokesPendingWork() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.open(); flow.select("phone"); assertEquals(PreviewAppsStage.List, flow.state.stage)
        flow.select("calculator"); flow.submit(charArrayOf('1')); assertEquals(0, port.removes)
        flow.askRemove(); flow.confirm(); assertEquals(PreviewAppsStage.Pin, flow.state.stage)
        flow.submit(charArrayOf('1')); assertEquals(1, port.removes)
        flow.back(); assertFalse(port.active!!())
        port.callback!!(AppRemovalResult.Removed)
        assertEquals(PreviewAppsStage.List, flow.state.stage)
    }
    @Test fun duplicateSubmissionIgnoredAndSaveErrorHonest() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.open(); flow.select("calculator"); flow.askRemove(); flow.confirm()
        flow.submit(charArrayOf('1')); flow.submit(charArrayOf('1')); assertEquals(1, port.removes)
        port.callback!!(AppRemovalResult.SaveFailed)
        assertEquals(PreviewAppsStage.SaveFailed, flow.state.stage)
    }
}
