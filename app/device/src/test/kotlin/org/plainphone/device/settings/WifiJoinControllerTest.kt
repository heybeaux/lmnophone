// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings
import org.junit.Assert.*
import org.junit.Test
class WifiJoinControllerTest {
    private class Location : WifiLocationPort {
        var on=false; var marker=false; var failedReadAfterEnable=false; var idle:Boolean?=true; var canWrite=true; var canMark=true
        val writes=mutableListOf<Boolean>()
        override fun enabled(): Boolean { if(on && failedReadAfterEnable) throw SecurityException(); return on }
        override fun owned()=marker
        override fun saveOwned(value:Boolean):Boolean { if(!canMark)return false;marker=value;return true }
        override fun callIdle()=idle
        override fun setEnabled(value:Boolean) { assertTrue(marker);writes+=value;if(canWrite)on=value }
    }
    @Test fun ownsOnlyWhatItEnablesAndPersistsFirst() {
        val p=Location();val c=WifiLocationController(p){0};assertTrue(c.show());c.leave()
        assertFalse(p.on);assertFalse(p.marker);assertEquals(listOf(true,false),p.writes)
        p.on=true;assertTrue(c.show());c.leave();assertTrue(p.on);assertEquals(2,p.writes.size)
    }
    @Test fun allFlowExitsRestoreOwnedLocation() {
        for(exit in listOf("success","Back","Cancel","Home","invisible")) {
            val p=Location();val c=WifiLocationController(p){0};c.show();c.leave();assertFalse(exit,p.on);assertFalse(p.marker)
        }
    }
    @Test fun fiveMinuteLimitCannotBeExtendedByShowingAgain() {
        var now=0L;val p=Location();val c=WifiLocationController(p){now};c.show()
        now=299999;c.show();c.tick();assertTrue(p.on);now++;c.tick();assertFalse(p.on)
    }
    @Test fun restartRecoversMarker() {
        val p=Location().apply{on=true;marker=true};WifiLocationController(p){0}.recover();assertFalse(p.on);assertFalse(p.marker)
    }
    @Test fun callOrUnknownCallStateDefersUntilIdle() {
        for(idle in listOf(false,null)) {
            val p=Location();val c=WifiLocationController(p){0};c.show();p.idle=idle;c.leave();assertTrue(p.on)
            p.idle=true;c.tick();assertFalse(p.on);assertFalse(p.marker)
        }
    }
    @Test fun offFailureRetainsMarkerAndReportsUntilReadbackOff() {
        val p=Location();val c=WifiLocationController(p){0};c.show();p.canWrite=false;c.leave()
        assertTrue(c.offFailed);assertTrue(p.marker);p.canWrite=true;c.tick();assertFalse(p.on);assertFalse(c.offFailed)
    }
    @Test fun markerFailureNeverEnablesAndEnableFailureDoesNotClaimSuccess() {
        val p=Location().apply{canMark=false};val c=WifiLocationController(p){0};assertFalse(c.show());assertTrue(p.writes.isEmpty())
        p.canMark=true;p.canWrite=false;assertFalse(c.show());assertFalse(p.on)
    }
    @Test fun failedEnableReadbackImmediatelyStartsCleanup() {
        val p=Location().apply{failedReadAfterEnable=true};val c=WifiLocationController(p){0}
        assertFalse(c.show());assertTrue(p.marker)
        p.failedReadAfterEnable=false;c.tick();assertFalse(p.on);assertFalse(p.marker)
    }
    private class Join:WifiJoinPort {
        var result:Int?=7;var status=WifiJoinOutcome.Waiting;var calls=0;var cancels=0
        override fun start(choice:WifiChoice,password:String):Int? {calls++;return result}
        override fun outcome(networkId:Int)=status
        override fun cancel(networkId:Int){cancels++}
        override fun complete(networkId:Int){}
    }
    private val choice=WifiChoice("Sam","Sam",WifiSecurity.Personal)
    @Test fun successRequiresConfirmedConnectionAndRunsOnce() {
        val p=Join();var success=0;val c=WifiJoinController(p,{0}){success++};c.select(choice);c.connect("test-password")
        c.tick();assertTrue(c.state.joining);assertEquals(0,success);p.status=WifiJoinOutcome.Connected;c.tick();c.tick()
        assertEquals(1,success);assertNull(c.state.choice)
    }
    @Test fun immediateAndReportedFailureKeepSelection() {
        val p=Join().apply{result=null};val c=WifiJoinController(p,{0}){};c.select(choice);c.connect("test-password")
        assertTrue(c.state.failed);assertEquals(choice,c.state.choice)
        p.result=7;c.connect("test-password");p.status=WifiJoinOutcome.Failed;c.tick();assertTrue(c.state.failed);assertEquals(1,p.cancels)
    }
    @Test fun thirtySecondTimeoutAndCancelRejectLateSuccess() {
        var now=0L;val p=Join();var success=0;val c=WifiJoinController(p,{now}){success++}
        c.select(choice);c.connect("test-password");now=29999;c.tick();assertTrue(c.state.joining)
        now++;c.tick();assertTrue(c.state.failed);assertEquals(1,p.cancels)
        c.connect("test-password");c.cancel();p.status=WifiJoinOutcome.Connected;c.tick();assertEquals(0,success);assertNull(c.state.choice)
    }
    @Test fun unsupportedAndDuplicateSubmitsNeverStartAgain() {
        val p=Join();val c=WifiJoinController(p,{0}){};c.select(choice.copy(security=WifiSecurity.Unsupported));c.connect("")
        assertEquals(0,p.calls);c.select(choice.copy(security=WifiSecurity.Open));c.connect("");c.connect("");assertEquals(1,p.calls)
    }
    @Test fun classifiesOpenPersonalAndUnsupported() {
        assertEquals(WifiSecurity.Open,wifiSecurity("[ESS]"))
        assertEquals(WifiSecurity.Personal,wifiSecurity("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.Personal,wifiSecurity("[RSN-SAE-CCMP][ESS]"))
        for(s in listOf("[WEP][ESS]","[WPA2-EAP-CCMP][ESS]","[OWE][ESS]","[WAPI-PSK][ESS]","[RSN-SUITE_B_192][ESS]"))
            assertEquals(WifiSecurity.Unsupported,wifiSecurity(s))
    }
}
