// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings
import org.junit.Assert.*
import org.junit.Test
class WifiForgetControllerTest {
    private class Fixture : WifiLocationPort, WifiForgetPort {
        var time = 0L
        var on = false; var marker = false; var offWorks = true; var enableWorks = true
        var idle: Boolean? = true
        var facts = WifiForgetFacts(true, 7, setOf(2, 7, 9), validated = true)
        var redactedReads = 0
        var immediate = true; var accepted = true; var unreadable = false
        val events = mutableListOf<String>(); val removed = mutableListOf<Int>()
        override fun enabled() = on
        override fun owned() = marker
        override fun saveOwned(value: Boolean): Boolean { marker=value; events += "own:$value"; return true }
        override fun callIdle() = idle
        override fun setEnabled(value: Boolean) { events += "location:$value"; if (value && enableWorks || !value && offWorks) on=value }
        override fun read(): WifiForgetFacts {
            events += "read:$on"
            if (unreadable) error("unavailable")
            return if (on && redactedReads-- <= 0) facts else facts.copy(connectedId=null)
        }
        override fun removeConnected(networkId: Int): Boolean {
            assertTrue(on); removed += networkId; events += "remove"
            if (accepted && immediate) facts=facts.copy(connected=false, connectedId=null, savedIds=facts.savedIds-networkId)
            return accepted
        }
        val location = WifiLocationController(this) { time }
        val controller = WifiForgetController(this, location) { time }
    }
    @Test fun locationOffTapOpensBeforeReadingRemovesOnlyCurrentAndCleansAfterReadback() {
        val f=Fixture(); f.controller.forget()
        assertEquals(listOf(7), f.removed); assertEquals(setOf(2,9), f.facts.savedIds)
        assertFalse(f.controller.pending); assertFalse(f.controller.failed); assertFalse(f.on); assertFalse(f.marker)
        assertTrue(f.events.indexOf("location:true") < f.events.indexOf("read:true"))
        assertTrue(f.events.lastIndexOf("read:true") < f.events.indexOf("location:false"))
    }
    @Test fun noIdentityDisconnectedUnvalidatedUnsavedOrUnreadableRemovesNothingAndCleans() {
        for (facts in listOf(WifiForgetFacts(true,null,setOf(7),true), WifiForgetFacts(false,7,setOf(7),true),
            WifiForgetFacts(true,7,setOf(9),true), WifiForgetFacts(true,7,setOf(7),false))) {
            val f=Fixture(); f.facts=facts; f.controller.forget(); f.time=2_000; f.controller.refresh()
            assertTrue(f.controller.failed); assertTrue(f.removed.isEmpty()); assertFalse(f.on)
        }
        val f=Fixture(); f.unreadable=true; f.controller.forget()
        assertTrue(f.controller.failed); assertTrue(f.removed.isEmpty()); assertFalse(f.on)
    }
    @Test fun locationReadbackCanPrecedeWifiIdentityPropagation() {
        val f=Fixture(); f.redactedReads=1; f.controller.forget()
        assertTrue(f.controller.pending); assertTrue(f.on); assertTrue(f.removed.isEmpty())
        f.time=1_000; f.controller.refresh()
        assertEquals(listOf(7),f.removed); assertFalse(f.controller.failed); assertFalse(f.on)
    }
    @Test fun failedEnableDoesNotRemove() {
        val f=Fixture(); f.enableWorks=false; f.controller.forget()
        assertTrue(f.controller.failed); assertTrue(f.removed.isEmpty()); assertFalse(f.marker)
    }
    @Test fun pendingReadbackKeepsLocationAndDuplicateTapDoesNotRepeat() {
        val f=Fixture(); f.immediate=false; f.controller.forget(); f.controller.forget()
        assertTrue(f.controller.pending); assertTrue(f.on); assertEquals(listOf(7),f.removed)
        f.facts=f.facts.copy(savedIds=setOf(2,9)); f.controller.refresh(); assertTrue(f.controller.pending)
        f.facts=f.facts.copy(connected=false,connectedId=null); f.controller.refresh()
        assertFalse(f.controller.pending); assertFalse(f.controller.failed); assertFalse(f.on)
    }
    @Test fun wrongReadbackTimesOutWithoutRemovingAnythingElse() {
        for (facts in listOf(WifiForgetFacts(false,null,setOf(2,7),false), WifiForgetFacts(false,null,setOf(9),false),
            WifiForgetFacts(true,null,setOf(2,9),true), WifiForgetFacts(true,7,setOf(2,9),true))) {
            val f=Fixture(); f.immediate=false; f.controller.forget(); f.facts=facts; f.time=10_000; f.controller.refresh()
            assertTrue(f.controller.failed); assertFalse(f.controller.pending); assertFalse(f.on); assertEquals(listOf(7),f.removed)
        }
    }
    @Test fun rejectedOrThrowingReadbackStillCleans() {
        val f=Fixture(); f.accepted=false; f.controller.forget(); assertTrue(f.controller.failed); assertFalse(f.on)
        val g=Fixture(); g.immediate=false; g.controller.forget(); g.unreadable=true; g.controller.refresh()
        assertTrue(g.controller.failed); assertFalse(g.on); assertEquals(listOf(7),g.removed)
    }
    @Test fun cleanupFailureWarnsAndRetriesWithDurableMarker() {
        val f=Fixture(); f.offWorks=false; f.controller.forget()
        assertTrue(f.on); assertTrue(f.marker); assertTrue(f.location.offFailed)
        f.offWorks=true; f.location.tick(); assertFalse(f.on); assertFalse(f.marker); assertFalse(f.location.offFailed)
    }
    @Test fun alreadyOnLocationIsPreserved() {
        val f=Fixture(); f.on=true; f.controller.forget(); assertTrue(f.on); assertFalse(f.marker)
    }
    @Test fun leaveEndsReadbackAndCleansAndCallDefersCleanup() {
        val f=Fixture(); f.immediate=false; f.controller.forget(); f.controller.leave()
        assertFalse(f.controller.pending); assertTrue(f.controller.failed); assertFalse(f.on)
        val g=Fixture(); g.idle=false; g.controller.forget(); assertTrue(g.on); assertTrue(g.marker)
        g.idle=true; g.location.tick(); assertFalse(g.on)
    }
}
