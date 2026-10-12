// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings
import org.junit.Assert.*
import org.junit.Test
class WifiForgetControllerTest {
    private class Port : WifiForgetPort {
        var facts = WifiForgetFacts(true, 7, setOf(2, 7, 9))
        var accepted = true; var failRead = false; var removeReadback = true
        val removed = mutableListOf<Int>()
        override fun read(): WifiForgetFacts { if (failRead) error("unavailable"); return facts }
        override fun removeConnected(networkId: Int): Boolean {
            removed += networkId
            if (accepted && removeReadback) facts = WifiForgetFacts(false, null, facts.savedIds - networkId)
            return accepted
        }
    }
    @Test fun removesOnlyConnectedIdAndVerifiesExactSavedSet() {
        val p=Port(); val c=WifiForgetController(p){0}
        c.refresh(); assertEquals(7,c.networkId); assertTrue(p.removed.isEmpty())
        c.forget(7); assertEquals(listOf(7),p.removed); assertFalse(c.failed); assertFalse(c.pending)
        assertEquals(setOf(2,9),p.facts.savedIds); c.forget(7); assertEquals(listOf(7),p.removed)
    }
    @Test fun staleUnknownUnsavedOrUnreadableConnectionNeverRemoves() {
        for (f in listOf(WifiForgetFacts(true,9,setOf(7,9)), WifiForgetFacts(true,null,setOf(7)),
            WifiForgetFacts(false,7,setOf(7)), WifiForgetFacts(true,7,setOf(9)))) {
            val p=Port(); val c=WifiForgetController(p){0}; c.refresh(); p.facts=f
            c.forget(7); assertTrue(c.failed); assertTrue(p.removed.isEmpty())
        }
        val p=Port(); val c=WifiForgetController(p){0}; c.refresh();p.failRead=true;c.forget(7)
        assertTrue(c.failed);assertTrue(p.removed.isEmpty())
    }
    @Test fun rejectedWriteReportsFailureWithoutRetry() {
        val p=Port().apply{accepted=false};val c=WifiForgetController(p){0}
        c.forget(7);c.refresh();assertTrue(c.failed);assertEquals(listOf(7),p.removed)
    }
    @Test fun pendingReadbackNeverRepeatsRemovalAndCompletesAfterDisconnect() {
        var now=0L;val p=Port().apply{removeReadback=false};val c=WifiForgetController(p){now}
        c.forget(7);assertTrue(c.pending);c.forget(7);assertEquals(listOf(7),p.removed)
        p.facts=p.facts.copy(savedIds=setOf(2,9));c.refresh();assertTrue(c.pending)
        p.facts=p.facts.copy(connected=false,connectedId=null);c.refresh();assertFalse(c.pending);assertFalse(c.failed)
    }
    @Test fun wrongCountWrongIdAndUnidentifiableConnectionCannotClaimSuccess() {
        for(f in listOf(WifiForgetFacts(false,null,setOf(2,7)), WifiForgetFacts(false,null,setOf(9)),
            WifiForgetFacts(true,null,setOf(2,9)), WifiForgetFacts(true,7,setOf(2,9)))) {
            var now=0L;val p=Port().apply{removeReadback=false};val c=WifiForgetController(p){now}
            c.forget(7);p.facts=f;now=10_000;c.refresh();assertTrue(c.failed);assertFalse(c.pending)
            assertEquals(listOf(7),p.removed)
        }
    }
    @Test fun failedReadbackShowsFailureAndNeverRemovesAnotherNetwork() {
        val p=Port().apply{removeReadback=false};val c=WifiForgetController(p){0}
        c.forget(7);p.failRead=true;c.refresh();assertTrue(c.failed);assertFalse(c.pending)
        assertEquals(listOf(7),p.removed)
    }
}
