// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.*
import org.plainphone.ui.trusted.*

class PreviewAppsFlowTest {
    private class Port : PreviewAppsPort {
        var unreadable = false
        var adds = 0
        var addCallback: ((AppAdditionResult) -> Unit)? = null
        override fun hidden(done: (List<PreviewHiddenApp>?) -> Unit) = done(if (unreadable) null else listOf(PreviewHiddenApp("calculator", "Calculator", "1", "identity")))
        override fun add(app: PreviewHiddenApp, pin: CharArray, active: () -> Boolean, done: (AppAdditionResult) -> Unit) {
            adds++; pin.fill('\u0000'); this.active = active; addCallback = done
        }
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
    @Test fun addNeedsEveryConfirmationAndReadyResult() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.open(); flow.hidden(); flow.selectHidden("calculator")
        flow.submit(charArrayOf('1')); assertEquals(0, port.adds)
        flow.nextAdd(); assertEquals(PreviewAppsStage.AddUnreviewed, flow.state.stage)
        flow.nextAdd(); assertEquals(PreviewAppsStage.AddConfirm, flow.state.stage)
        flow.nextAdd(); flow.submit(charArrayOf('1')); assertEquals(1, port.adds)
        assertEquals(PreviewAppsStage.AddPin, flow.state.stage)
        port.addCallback!!(AppAdditionResult.CheckFailed)
        assertEquals(PreviewAppsStage.CheckFailed, flow.state.stage)
    }
    @Test fun decliningAndRecoveryDoNotWrite() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.hidden(); flow.selectHidden("calculator"); flow.dontAdd()
        assertEquals(PreviewAppsStage.Hidden, flow.state.stage); assertEquals(0, port.adds)
        flow.open(); flow.select("calculator"); flow.askRemove(); flow.confirm(); flow.unsupported()
        assertEquals(PreviewAppsStage.Unsupported, flow.state.stage); assertEquals(0, port.removes)
    }
    @Test fun unreadableListAndStaleSelectionCannotStartAdd() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        port.unreadable = true; flow.hidden(); flow.selectHidden("calculator"); flow.nextAdd(); flow.submit(charArrayOf('1'))
        assertTrue(flow.state.loadFailed); assertTrue(flow.state.hidden.isEmpty()); assertEquals(0, port.adds)
    }
    @Test fun addBackInvalidatesQueuedSaveAndLateReadyDoesNotShowDone() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.hidden(); flow.selectHidden("calculator"); repeat(3) { flow.nextAdd() }
        flow.submit(charArrayOf('1')); flow.back()
        assertFalse(port.active!!()); port.addCallback!!(AppAdditionResult.Added)
        assertEquals(PreviewAppsStage.List, flow.state.stage)
    }
    @Test fun addReadyIsTheOnlySuccessAndWrongPinStaysInPin() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.hidden(); flow.selectHidden("calculator"); repeat(3) { flow.nextAdd() }
        flow.submit(charArrayOf('1')); port.addCallback!!(AppAdditionResult.PinRejected(org.plainphone.device.authority.PinAccessResult.Wrong(0)))
        assertEquals(PreviewAppsStage.AddPin, flow.state.stage); assertTrue(flow.state.wrong)
        flow.submit(charArrayOf('1')); port.addCallback!!(AppAdditionResult.Added)
        assertEquals(PreviewAppsStage.Added, flow.state.stage)
    }
    @Test fun duplicateSubmissionIgnoredAndSaveErrorHonest() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.open(); flow.select("calculator"); flow.askRemove(); flow.confirm()
        flow.submit(charArrayOf('1')); flow.submit(charArrayOf('1')); assertEquals(1, port.removes)
        port.callback!!(AppRemovalResult.SaveFailed)
        assertEquals(PreviewAppsStage.SaveFailed, flow.state.stage)
    }
}
