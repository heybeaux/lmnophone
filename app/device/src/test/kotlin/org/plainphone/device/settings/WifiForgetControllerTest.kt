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
        var facts = WifiForgetFacts(true, 7, setOf(2, 7, 9), validated = true, connection = A)
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
        val removalConnections = mutableListOf<Long>()
        override fun removeConnected(networkId: Int, connection: Long): Boolean {
            assertTrue(on); removalConnections += connection
            // Mirrors the adapter's last check: the confirmed connection and its configuration are still current.
            if (facts.connection != connection || facts.forgettableId != networkId) return false
            removed += networkId; events += "remove"
            if (accepted && immediate) facts=facts.copy(connected=false, connectedId=null, savedIds=facts.savedIds-networkId)
            return accepted
        }
        val location = WifiLocationController(this) { time }
        val controller = WifiForgetController(this, location) { time }
        fun forget() = controller.forget(A)
    }
    private companion object { const val A = 100L; const val B = 200L }
    @Test fun locationOffTapOpensBeforeReadingRemovesOnlyCurrentAndCleansAfterReadback() {
        val f=Fixture(); f.forget()
        assertEquals(listOf(7), f.removed); assertEquals(setOf(2,9), f.facts.savedIds)
        assertFalse(f.controller.pending); assertFalse(f.controller.failed); assertFalse(f.on); assertFalse(f.marker)
        assertTrue(f.events.indexOf("location:true") < f.events.indexOf("read:true"))
        assertTrue(f.events.lastIndexOf("read:true") < f.events.indexOf("location:false"))
    }
    @Test fun noIdentityDisconnectedUnvalidatedUnsavedOrUnreadableRemovesNothingAndCleans() {
        for (facts in listOf(WifiForgetFacts(true, null, setOf(7), true, A), WifiForgetFacts(false, 7, setOf(7), true, A),
            WifiForgetFacts(true, 7, setOf(9), true, A), WifiForgetFacts(true, 7, setOf(7), false, A))) {
            val f=Fixture(); f.facts=facts; f.forget(); f.time=2_000; f.controller.refresh()
            assertTrue(f.controller.failed); assertTrue(f.removed.isEmpty()); assertFalse(f.on)
        }
        val f=Fixture(); f.unreadable=true; f.forget()
        assertTrue(f.controller.failed); assertTrue(f.removed.isEmpty()); assertFalse(f.on)
    }
    @Test fun locationReadbackCanPrecedeWifiIdentityPropagation() {
        val f=Fixture(); f.redactedReads=1; f.forget()
        assertTrue(f.controller.pending); assertTrue(f.on); assertTrue(f.removed.isEmpty())
        f.time=1_000; f.controller.refresh()
        assertEquals(listOf(7),f.removed); assertFalse(f.controller.failed); assertFalse(f.on)
    }
    @Test fun failedEnableDoesNotRemove() {
        val f=Fixture(); f.enableWorks=false; f.forget()
        assertTrue(f.controller.failed); assertTrue(f.removed.isEmpty()); assertFalse(f.marker)
    }
    @Test fun pendingReadbackKeepsLocationAndDuplicateTapDoesNotRepeat() {
        val f=Fixture(); f.immediate=false; f.forget(); f.forget()
        assertTrue(f.controller.pending); assertTrue(f.on); assertEquals(listOf(7),f.removed)
        f.facts=f.facts.copy(savedIds=setOf(2,9)); f.controller.refresh(); assertTrue(f.controller.pending)
        f.facts=f.facts.copy(connected=false,connectedId=null); f.controller.refresh()
        assertFalse(f.controller.pending); assertFalse(f.controller.failed); assertFalse(f.on)
    }
    @Test fun wrongReadbackTimesOutWithoutRemovingAnythingElse() {
        for (facts in listOf(WifiForgetFacts(false, null, setOf(2,7), false, A), WifiForgetFacts(false, null, setOf(9), false, A),
            WifiForgetFacts(true, null, setOf(2,9), true, A), WifiForgetFacts(true, 7, setOf(2,9), true, A))) {
            val f=Fixture(); f.immediate=false; f.forget(); f.facts=facts; f.time=10_000; f.controller.refresh()
            assertTrue(f.controller.failed); assertFalse(f.controller.pending); assertFalse(f.on); assertEquals(listOf(7),f.removed)
        }
    }
    @Test fun rejectedOrThrowingReadbackStillCleans() {
        val f=Fixture(); f.accepted=false; f.forget(); assertTrue(f.controller.failed); assertFalse(f.on)
        val g=Fixture(); g.immediate=false; g.forget(); g.unreadable=true; g.controller.refresh()
        assertTrue(g.controller.failed); assertFalse(g.on); assertEquals(listOf(7),g.removed)
    }
    @Test fun cleanupFailureWarnsAndRetriesWithDurableMarker() {
        val f=Fixture(); f.offWorks=false; f.forget()
        assertTrue(f.on); assertTrue(f.marker); assertTrue(f.location.offFailed)
        f.offWorks=true; f.location.tick(); assertFalse(f.on); assertFalse(f.marker); assertFalse(f.location.offFailed)
    }
    @Test fun alreadyOnLocationIsPreserved() {
        val f=Fixture(); f.on=true; f.forget(); assertTrue(f.on); assertFalse(f.marker)
    }
    @Test fun leaveBeforeRemovalEndsAndCleansAndCallDefersCleanup() {
        // Leaving before the removal ends the attempt at once and removes nothing.
        val f=Fixture(); f.redactedReads=5; f.forget(); f.controller.leave()
        assertFalse(f.controller.pending); assertFalse(f.controller.failed); assertFalse(f.on); assertTrue(f.removed.isEmpty())
        val g=Fixture(); g.idle=false; g.forget(); assertTrue(g.on); assertTrue(g.marker)
        g.idle=true; g.location.tick(); assertFalse(g.on)
    }
    // PLA-488 item 2: with Location off Android returns no saved networks to the app (emulator, API 35),
    // so a readback taken after leaving would look like a removal. The removal readback instead finishes
    // inside its existing 10-second window and its result is shown on return.
    @Test fun leavingAfterRemovalKeepsTheBoundedReadbackAndShowsRemoved() {
        val f=Fixture(); f.immediate=false; f.forget(); f.controller.leave()
        assertTrue(f.controller.pending); assertTrue(f.on); assertFalse(f.controller.failed); assertNull(f.controller.result)
        f.facts=f.facts.copy(connected=false, connectedId=null, savedIds=setOf(2,9)); f.time=1_000; f.controller.refresh()
        assertFalse(f.controller.pending); assertFalse(f.on); assertFalse(f.marker)
        assertEquals(WifiForgetResult.Removed, f.controller.result); assertFalse(f.controller.failed); assertEquals(listOf(7), f.removed)
    }
    @Test fun leavingTwiceStillKeepsTheReadback() {
        val f=Fixture(); f.immediate=false; f.forget(); f.controller.leave(); f.controller.leave()
        assertTrue(f.controller.pending); assertTrue(f.on)
    }
    @Test fun leftReadbackThatTimesOutStillSaysStatusUnavailable() {
        val f=Fixture(); f.immediate=false; f.forget(); f.controller.leave(); f.time=10_000; f.controller.refresh()
        assertFalse(f.controller.pending); assertFalse(f.on); assertTrue(f.controller.failed); assertNull(f.controller.result)
    }
    @Test fun leavingBeforeTheRemovalShowsNotRemoved() {
        val f=Fixture(); f.redactedReads=5; f.forget(); assertTrue(f.controller.pending)
        f.controller.leave(); f.controller.refresh()
        assertEquals(WifiForgetResult.NotRemoved, f.controller.result); assertTrue(f.removed.isEmpty()); assertFalse(f.on)
    }
    @Test fun confirmedRemovalSaysRemovedAndOpenClearsItOncePresented() {
        val f=Fixture(); f.forget(); assertEquals(WifiForgetResult.Removed, f.controller.result)
        f.controller.onOpen(); assertEquals(WifiForgetResult.Removed, f.controller.result)
        f.controller.presented(); f.controller.onOpen(); assertNull(f.controller.result)
    }
    // PLA-491 P1: the question is bound to the connection it was asked about, through the removal.
    @Test fun aReplacementConnectionBeforeTheTapRemovesNothing() {
        // Unnamed or same-label networks: only the connection handle tells them apart.
        val f=Fixture(); f.facts=f.facts.copy(connection=B); f.forget()
        assertTrue(f.removed.isEmpty()); assertEquals(WifiForgetResult.NotRemoved, f.controller.result)
        assertFalse(f.controller.failed); assertFalse(f.controller.pending); assertFalse(f.on); assertFalse(f.marker)
    }
    @Test fun aSwitchDuringTheIdentityWaitRemovesNothing() {
        val f=Fixture(); f.redactedReads=1; f.forget(); assertTrue(f.controller.pending)
        f.facts=f.facts.copy(connectedId=9, connection=B); f.time=1_000; f.controller.refresh()
        assertTrue(f.removed.isEmpty()); assertEquals(WifiForgetResult.NotRemoved, f.controller.result)
        assertFalse(f.controller.failed); assertFalse(f.controller.pending); assertFalse(f.on)
    }
    @Test fun aSwitchToTheSameConfigurationOnANewConnectionRemovesNothing() {
        val f=Fixture(); f.redactedReads=1; f.forget()
        f.facts=f.facts.copy(connection=B); f.time=500; f.controller.refresh()
        assertTrue(f.removed.isEmpty()); assertEquals(WifiForgetResult.NotRemoved, f.controller.result)
    }
    @Test fun theRemovalCarriesTheConfirmedConnection() {
        val f=Fixture(); f.forget(); assertEquals(listOf(A), f.removalConnections); assertEquals(listOf(7), f.removed)
    }
    // PLA-491 P2: a settled outcome is kept until the Wi-Fi page has shown it.
    @Test fun anOutcomeSettledOffThePageSurvivesReopening() {
        val f=Fixture(); f.immediate=false; f.forget(); f.controller.leave()
        f.facts=f.facts.copy(connected=false, connectedId=null, savedIds=setOf(2,9)); f.time=1_000; f.controller.refresh()
        f.controller.onOpen(); assertEquals(WifiForgetResult.Removed, f.controller.result)
        f.controller.presented(); f.controller.onOpen(); assertNull(f.controller.result)
        val g=Fixture(); g.immediate=false; g.forget(); g.controller.leave(); g.time=10_000; g.controller.refresh()
        g.controller.onOpen(); assertTrue(g.controller.failed)
        g.controller.presented(); g.controller.onOpen(); assertFalse(g.controller.failed)
        val h=Fixture(); h.redactedReads=5; h.forget(); h.controller.leave()
        h.controller.onOpen(); assertEquals(WifiForgetResult.NotRemoved, h.controller.result)
    }
    @Test fun presentingWhilePendingDoesNotLaterClearTheOutcome() {
        val f=Fixture(); f.immediate=false; f.forget(); f.controller.presented()
        f.controller.leave(); f.facts=f.facts.copy(connected=false, connectedId=null, savedIds=setOf(2,9)); f.time=1_000; f.controller.refresh()
        f.controller.onOpen(); assertEquals(WifiForgetResult.Removed, f.controller.result)
    }
    @Test fun aNewForgetClearsTheEarlierResult() {
        val f=Fixture(); f.redactedReads=5; f.forget(); f.controller.leave()
        assertEquals(WifiForgetResult.NotRemoved, f.controller.result)
        f.redactedReads=0; f.forget(); assertEquals(WifiForgetResult.Removed, f.controller.result)
        val g=Fixture(); g.accepted=false; g.forget(); assertNull(g.controller.result); assertTrue(g.controller.failed)
    }
}
