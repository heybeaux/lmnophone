// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.plainphone.device.settings.BluetoothPairingPhase as Phase

class BluetoothPairingControllerTest {
    private class Port : BluetoothPairingPort {
        var bond: BluetoothBond? = BluetoothBond.None
        var pairResult = true
        var pairThrows = false
        var listenResult = true
        val pairs = mutableListOf<String>()
        var listens = 0
        var stops = 0
        var listening: String? = null
        var callback: (BluetoothPairingSignal) -> Unit = {}
        var onPair: () -> Unit = {}
        override fun bond(id: String) = bond
        override fun listen(id: String, onSignal: (BluetoothPairingSignal) -> Unit): Boolean {
            listens++; listening = id; callback = onSignal
            return listenResult
        }
        override fun stopListening() { stops++; listening = null }
        override fun pair(id: String): Boolean {
            pairs += id
            if (pairThrows) throw SecurityException()
            onPair()
            return pairResult
        }
        fun signal(s: BluetoothPairingSignal) = callback(s)
        fun bondChanged(b: BluetoothBond) { bond = b; signal(BluetoothPairingSignal.Bond(b)) }
    }
    private var now = 0L
    private fun controller(p: Port, audio: Boolean = true) =
        BluetoothPairingController(p, { now }, timeoutMs = 45_000).apply { select("A", audio); attach() }

    @Test fun pairIsOfferedOnlyForAudioDevices() {
        assertEquals(Phase.Ready, controller(Port()).state.phase)
        val p = Port(); val c = controller(p, audio = false)
        assertEquals(Phase.NotSupported, c.state.phase)
        c.pair()
        assertTrue(p.pairs.isEmpty())
        assertEquals(Phase.NotSupported, c.state.phase)
    }
    @Test fun bondingThenBondedIsPaired() {
        val p = Port(); val c = controller(p)
        c.pair()
        assertEquals(listOf("A"), p.pairs)
        assertEquals(Phase.Pairing, c.state.phase)
        p.bondChanged(BluetoothBond.Bonding); assertEquals(Phase.Pairing, c.state.phase)
        p.bondChanged(BluetoothBond.Bonded); assertEquals(Phase.Paired, c.state.phase)
    }
    @Test fun bondingThenNoneIsFailedAndCanTryAgain() {
        val p = Port(); val c = controller(p)
        c.pair(); p.bondChanged(BluetoothBond.Bonding); p.bondChanged(BluetoothBond.None)
        assertEquals(Phase.Failed, c.state.phase)
        c.pair()
        assertEquals(2, p.pairs.size); assertEquals(Phase.Pairing, c.state.phase)
    }
    @Test fun refusedStartIsFailed() {
        val p = Port().apply { pairResult = false }; val c = controller(p)
        c.pair(); assertEquals(Phase.Failed, c.state.phase)
    }
    @Test fun adapterExceptionIsUnavailable() {
        val p = Port().apply { pairThrows = true }; val c = controller(p)
        c.pair(); assertEquals(Phase.Unavailable, c.state.phase)
    }
    @Test fun noResultInBoundedTimeIsFailedButALaterBondIsStillShown() {
        val p = Port(); val c = controller(p)
        c.pair(); p.bondChanged(BluetoothBond.Bonding)
        now = 44_999; c.tick(); assertEquals(Phase.Pairing, c.state.phase)
        now = 45_000; c.tick(); assertEquals(Phase.Failed, c.state.phase)
        // Still bonding is not a new result: the failure stays until Android reports one.
        p.bondChanged(BluetoothBond.Bonding); assertEquals(Phase.Failed, c.state.phase)
        p.bondChanged(BluetoothBond.Bonded); assertEquals(Phase.Paired, c.state.phase)
    }
    @Test fun tickDoesNothingUnlessPairing() {
        val p = Port(); val c = controller(p)
        now = 100_000; c.tick(); assertEquals(Phase.Ready, c.state.phase)
    }
    @Test fun aRequestForACodeOrCheckIsNeedsCodeWithNoTryAgain() {
        val p = Port(); val c = controller(p)
        c.pair(); p.bondChanged(BluetoothBond.Bonding)
        p.signal(BluetoothPairingSignal.NeedsCode)
        assertEquals(Phase.NeedsCode, c.state.phase)
        p.bondChanged(BluetoothBond.None)
        assertEquals(Phase.NeedsCode, c.state.phase)
        now = 100_000; c.tick(); assertEquals(Phase.NeedsCode, c.state.phase)
        c.pair(); assertEquals(1, p.pairs.size)
    }
    @Test fun needsCodeIsRememberedForTheSameDeviceOnly() {
        val p = Port(); val c = controller(p)
        c.pair(); p.signal(BluetoothPairingSignal.NeedsCode); p.bondChanged(BluetoothBond.None)
        c.detach(); c.select("A", true); c.attach()
        assertEquals(Phase.NeedsCode, c.state.phase)
        c.detach(); c.select("B", true); c.attach()
        assertEquals(Phase.Ready, c.state.phase)
    }
    @Test fun leavingDuringPairingStopsListeningWithoutCancelling() {
        val p = Port(); val c = controller(p)
        c.pair(); p.bondChanged(BluetoothBond.Bonding)
        val retired = p.callback
        c.detach()
        assertNull(p.listening); assertEquals(1, p.pairs.size)
        // A retired callback can't change the page.
        retired(BluetoothPairingSignal.Bond(BluetoothBond.None))
        assertEquals(Phase.Pairing, c.state.phase)
    }
    @Test fun returningShowsTheTrueBondState() {
        val p = Port(); val c = controller(p)
        c.pair(); p.bondChanged(BluetoothBond.Bonding); c.detach()
        p.bond = BluetoothBond.Bonded; c.attach(); assertEquals(Phase.Paired, c.state.phase)

        val q = Port(); val d = controller(q)
        d.pair(); q.bondChanged(BluetoothBond.Bonding); d.detach()
        q.bond = BluetoothBond.Bonding; d.attach(); assertEquals(Phase.Pairing, d.state.phase)
        assertEquals(2, q.listens)
        q.bondChanged(BluetoothBond.Bonded); assertEquals(Phase.Paired, d.state.phase)

        val r = Port(); val e = controller(r)
        e.pair(); r.bondChanged(BluetoothBond.Bonding); e.detach()
        r.bond = BluetoothBond.None; e.attach(); assertEquals(Phase.Failed, e.state.phase)
    }
    @Test fun timeoutKeepsCountingAcrossLeaveAndReturn() {
        val p = Port(); val c = controller(p)
        c.pair(); p.bondChanged(BluetoothBond.Bonding); c.detach()
        now = 50_000; c.attach(); c.tick()
        assertEquals(Phase.Failed, c.state.phase)
    }
    @Test fun alreadyPairedOnOpenOrBeforePairIsPaired() {
        val p = Port().apply { bond = BluetoothBond.Bonded }
        assertEquals(Phase.Paired, controller(p).state.phase)
        val q = Port(); val c = controller(q)
        q.bond = BluetoothBond.Bonded; c.pair()
        assertTrue(q.pairs.isEmpty()); assertEquals(Phase.Paired, c.state.phase)
    }
    @Test fun unreadableOrLostBluetoothIsUnavailableWithNoPair() {
        val p = Port().apply { bond = null }; val c = controller(p)
        assertEquals(Phase.Unavailable, c.state.phase)
        c.pair(); assertTrue(p.pairs.isEmpty())
        val q = Port(); val d = controller(q)
        q.signal(BluetoothPairingSignal.Unavailable); assertEquals(Phase.Unavailable, d.state.phase)
        val r = Port().apply { listenResult = false }
        assertEquals(Phase.Unavailable, controller(r).state.phase)
    }
    @Test fun notAttachedDoesNotPair() {
        val p = Port(); val c = BluetoothPairingController(p, { now }); c.select("A", true)
        c.pair(); assertTrue(p.pairs.isEmpty())
    }
    @Test fun pairingSourceUsesNoPrivilegedOrSettingsCalls() {
        val src = File("src/main/kotlin/org/plainphone/device/settings/AndroidBluetoothPairing.kt").readText()
        assertTrue(src.contains("createBond()"))
        listOf("setPin", "setPairingConfirmation", "setPasskey", "cancelBondProcess", "removeBond",
            "cancelPairing", "getMethod", "getDeclaredMethod", "android.provider.Settings", "startActivity",
            "abortBroadcast", "setResult", "BLUETOOTH_PRIVILEGED", "setLockTaskPackages").forEach {
            assertFalse("pairing source must not use $it", src.contains(it))
        }
    }
}
