// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.*
import org.plainphone.ui.trusted.*

class PreviewAppsFlowTest {
    private class Port : PreviewAppsPort {
        var permissionReads = 0
        var manifest = org.plainphone.device.approval.AppPermissionSnapshot(
            org.plainphone.policy.ApprovalBinding(org.plainphone.core.PackageName("calculator"), setOf(org.plainphone.core.CertDigest("a".repeat(64)))), 1, emptyList())
        override fun permissions(id: String, hidden: Boolean, done: (org.plainphone.device.approval.AppPermissionRead?) -> Unit) {
            permissionReads++
            done(if (unreadable) null else org.plainphone.device.approval.AppPermissionRead(manifest, emptyMap()))
        }
        var unreadable = false
        var adds = 0
        var addCallback: ((AppAdditionResult) -> Unit)? = null
        override fun hidden(done: (List<PreviewHiddenApp>?) -> Unit) = done(if (unreadable) null else listOf(PreviewHiddenApp("calculator", "Calculator", "1", manifest.selectionIdentity)))
        override fun add(app: PreviewHiddenApp, selection: org.plainphone.device.approval.AppPermissionSnapshot, choices: Map<String, org.plainphone.policy.PermissionChoice>, pin: CharArray, active: () -> Boolean, done: (AppAdditionResult) -> Unit) {
            adds++; pin.fill('\u0000'); this.active = active; addCallback = done
        }
        var removes = 0
        var callback: ((AppRemovalResult) -> Unit)? = null
        var active: (() -> Boolean)? = null
        override fun read(done: (List<PreviewAppRow>?) -> Unit) = done(listOf(
            PreviewAppRow("calculator", "Calculator", "1", false), PreviewAppRow("phone", "Phone", "1", true),
            PreviewAppRow("sms", "Messages", "1", true), PreviewAppRow("self", "LMNOPhone", "1", true),
            PreviewAppRow("role-dialer", "Dialer", "1", true), PreviewAppRow("role-sms", "Texts", "1", true)))
        override fun move(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit) = done(true)
        override fun remove(id: String, pin: CharArray, active: () -> Boolean, done: (AppRemovalResult) -> Unit) {
            removes++; pin.fill('\u0000'); this.active = active; callback = done
        }
    }
    @Test fun realChoicesAreUnselectedAndRequiredBeforeFreshPinAndFailureNeverAdds() {
        val port = Port()
        port.manifest = port.manifest.copy(groups = listOf(
            org.plainphone.device.approval.RequestedPermissionGroup("mic", "Microphone", listOf(org.plainphone.policy.PermissionName("android.permission.RECORD_AUDIO")))))
        val flow = PreviewAppsFlow(port) {}
        flow.hidden(); flow.selectHidden("calculator"); repeat(2) { flow.nextAdd() }
        assertEquals(PreviewAppsStage.AddPermissions, flow.state.stage)
        assertTrue(flow.state.choices.isEmpty()); assertFalse(flow.canContinuePermissions())
        flow.nextAdd(); flow.submit(charArrayOf('1')); assertEquals(0, port.adds)
        flow.choosePermission("unknown", PermissionDecision.Allow); assertTrue(flow.state.choices.isEmpty())
        flow.choosePermission("mic", PermissionDecision.Deny); assertTrue(flow.canContinuePermissions())
        flow.nextAdd(); flow.nextAdd(); flow.submit(charArrayOf('1')); assertEquals(1, port.adds)
        port.addCallback!!(AppAdditionResult.PermissionFailed(listOf("mic"), org.plainphone.device.approval.AppPermissionRead(port.manifest, emptyMap())))
        assertEquals(PreviewAppsStage.PermissionFailed, flow.state.stage)
        assertEquals(listOf("mic"), flow.state.failedGroups)
    }
    @Test fun protectedPageDoesNotReadOrOfferChoices() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.open()
        listOf("phone", "sms", "self", "role-dialer", "role-sms").forEach { id ->
            flow.select(id); flow.choosePermission("mic", PermissionDecision.Allow)
            assertEquals(PreviewAppsStage.List, flow.state.stage)
        }
        assertEquals(0, port.permissionReads); assertTrue(flow.state.choices.isEmpty())
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
        flow.nextAdd(); assertEquals(PreviewAppsStage.AddPermissions, flow.state.stage)
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
        flow.hidden(); flow.selectHidden("calculator"); repeat(4) { flow.nextAdd() }
        flow.submit(charArrayOf('1')); flow.back()
        assertFalse(port.active!!()); port.addCallback!!(AppAdditionResult.Added)
        assertEquals(PreviewAppsStage.List, flow.state.stage)
    }
    @Test fun addReadyIsTheOnlySuccessAndWrongPinStaysInPin() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.hidden(); flow.selectHidden("calculator"); repeat(4) { flow.nextAdd() }
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
    @Test fun pinSettingsHiddenAppEntryUsesTheSameAddFlowAndFreshPin() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.openHidden()
        assertEquals(PreviewAppsStage.HiddenList, flow.state.stage)
        assertTrue(flow.leavesOnBack())
        flow.selectHidden("calculator"); assertFalse(flow.leavesOnBack())
        flow.nextAdd(); flow.nextAdd(); flow.nextAdd(); flow.nextAdd()
        assertEquals(PreviewAppsStage.AddPin, flow.state.stage)
        assertEquals(0, port.adds)
        flow.back(); assertEquals(PreviewAppsStage.AddConfirm, flow.state.stage)
        flow.back(); assertEquals(PreviewAppsStage.HiddenList, flow.state.stage)
        assertTrue(flow.leavesOnBack())
        flow.selectHidden("calculator"); repeat(4) { flow.nextAdd() }
        flow.submit(charArrayOf('1')); assertEquals(1, port.adds)
    }
    @Test fun appsListEntryStillReturnsToTheListFromHiddenApps() {
        val port = Port(); val flow = PreviewAppsFlow(port) {}
        flow.open(); assertTrue(flow.leavesOnBack())
        flow.hidden(); assertFalse(flow.leavesOnBack())
        flow.back(); assertEquals(PreviewAppsStage.List, flow.state.stage)
        flow.openHidden(); flow.open(); flow.hidden(); assertFalse(flow.leavesOnBack())
    }
    @Test fun playGrantReusesPermissionChoicesAndConfirmsWithoutPinPage() {
        val port = Port(); val flow = PreviewAppsFlow(port, playApproval = true) {}
        flow.openHidden(); flow.selectHidden("calculator")
        repeat(3) { flow.nextAdd() }
        assertEquals(PreviewAppsStage.AddConfirm, flow.state.stage)
        assertEquals(0, port.adds)
        flow.nextAdd()
        assertEquals(1, port.adds)
        assertNotEquals(PreviewAppsStage.AddPin, flow.state.stage)
        port.addCallback!!(AppAdditionResult.Added)
        assertEquals(PreviewAppsStage.Added, flow.state.stage)
    }

}
