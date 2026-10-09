// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class WaitingChangeControllerTest {
    private val first = TrustedPersonId("first")
    private val second = TrustedPersonId("second")
    private val credential = PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32), 600_000), SecretVerifier(ByteArray(16), ByteArray(32), 600_000))
    private var credentials: CredentialState? = CredentialState(Mode.TRUSTED, mapOf(first to credential, second to credential))
    private var time: ClockReading? = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val store = Store()
    private var sequence = 0
    private var allowed = true
    private var consumedPerson: TrustedPersonId? = null
    private val authority = PersonAuthorityConsumer { _, _, person -> consumedPerson = person; allowed }
    private fun controller() = WaitingChangeController(store, { credentials }, RunningClock { time }, authority,
        WaitingIdentitySource { sequence++; WaitingIdentity("request-$sequence", "synthetic-code-$sequence") })
    private val controller = controller()
    private val battery = BatteryState(50, false)
    private fun start(): String {
        val request = controller.prepareTurnOff(first)!!
        assertEquals(WaitingOutcome.PENDING, controller.startTurnOff(request, FreshAuthority(), true, true))
        return request.requestId
    }
    private fun at(hours: Long) { time = ClockReading(RunningTime("epoch", hours * 3_600_000), BootTime("boot", hours * 3_600_000)) }

    @Test fun `start requires both confirmations and actual starter authority before one atomic record and receipts`() {
        val request = controller.prepareTurnOff(first)!!
        assertEquals(WaitingOutcome.REFUSED, controller.startTurnOff(request, FreshAuthority(), false, true))
        assertNull(store.state!!.operation)
        allowed = false
        assertEquals(WaitingOutcome.REFUSED, controller.startTurnOff(request, FreshAuthority(), true, true))
        allowed = true
        assertEquals(WaitingOutcome.PENDING, controller.startTurnOff(request, FreshAuthority(), true, true))
        assertEquals(first, consumedPerson)
        assertEquals(WaitingPhase.PENDING, store.state!!.operation!!.phase)
        assertEquals(setOf(first, second), store.state!!.texts.single().recipients)
        assertEquals(WaitingEvent.STARTED, store.state!!.texts.single().event)
        assertEquals(1L, store.state!!.revision)
    }

    @Test fun `failed start write never starts and failed clock never matures`() {
        val request = controller.prepareTurnOff(first)!!
        store.fail = true
        assertEquals(WaitingOutcome.NOT_READY, controller.startTurnOff(request, FreshAuthority(), true, true))
        assertNull(store.state!!.operation)
        store.fail = false
        val id = start()
        time = null
        assertEquals(WaitingOutcome.NOT_READY, controller.tick(id, battery))
        assertEquals(WaitingPhase.PENDING, store.state!!.operation!!.phase)
    }

    @Test fun `twenty four proven hours required and low battery delays commit`() {
        val id = start()
        at(23)
        assertEquals(WaitingOutcome.PENDING, controller.tick(id, battery))
        at(24)
        assertEquals(WaitingOutcome.LOW_BATTERY, controller.tick(id, BatteryState(4, false)))
        assertEquals(WaitingOutcome.COMMITTING, controller.tick(id, BatteryState(4, true)))
        assertEquals(WaitingPhase.COMMITTING, store.state!!.operation!!.phase)
        assertEquals(WaitingEvent.COMMITTING, store.state!!.texts.last().event)
    }

    @Test fun `reboots and process restarts credit only proven time and never wall time`() {
        val id = start()
        at(10)
        repeat(10) { assertEquals(WaitingOutcome.PENDING, controller().tick(id, battery)) }
        time = ClockReading(RunningTime("epoch", 10 * 3_600_000), BootTime("reboot", 0))
        assertEquals(WaitingOutcome.PENDING, controller().tick(id, battery))
        time = ClockReading(RunningTime("epoch", 24 * 3_600_000), BootTime("reboot", 14 * 3_600_000))
        assertEquals(WaitingOutcome.COMMITTING, controller().tick(id, battery))
    }

    @Test fun `new clock epoch requires a fresh twenty four hours even after long prior boot`() {
        at(30)
        val id = start()
        time = ClockReading(RunningTime("repaired", 0), BootTime("boot", 60 * 3_600_000))
        assertEquals(WaitingOutcome.PENDING, controller.tick(id, battery))
        time = time!!.copy(proven = RunningTime("repaired", 24 * 3_600_000 - 1))
        assertEquals(WaitingOutcome.PENDING, controller().tick(id, battery))
        time = time!!.copy(proven = RunningTime("repaired", 24 * 3_600_000))
        assertEquals(WaitingOutcome.COMMITTING, controller().tick(id, battery))
    }

    @Test fun `starter generation mode and structure changes cancel but other PIN does not`() {
        val id = start()
        credentials = credentials!!.replacing(second, credential.copy(generation = 1))
        at(1)
        assertEquals(WaitingOutcome.PENDING, controller.tick(id, battery))
        credentials = credentials!!.replacing(first, credential.copy(generation = 1))
        assertEquals(WaitingOutcome.CANCELLED, controller.tick(id, battery))
        assertEquals(WaitingEvent.DEPENDENCY_CHANGED, store.state!!.texts.last().event)
        val next = start()
        credentials = CredentialState(Mode.SELF_MANAGED, mapOf(first to credentials!!.people.getValue(first)), structureGeneration = 1)
        assertEquals(WaitingOutcome.CANCELLED, controller.tick(next, battery))
    }

    @Test fun `PIN free cancel persists before success and failed write latches against maturity`() {
        val id = start()
        store.fail = true
        assertEquals(WaitingOutcome.NOT_READY, controller.cancel(id))
        at(24)
        assertEquals(WaitingOutcome.NOT_READY, controller.tick(id, battery))
        assertEquals(WaitingPhase.PENDING, store.state!!.operation!!.phase)
        store.fail = false
        assertEquals(WaitingOutcome.CANCELLED, controller.tick(id, battery))
        assertEquals(WaitingEvent.CANCELLED_ON_PHONE, store.state!!.texts.last().event)
        assertEquals(setOf(first, second), store.state!!.texts.last().recipients)
        assertEquals(WaitingOutcome.IGNORED, controller.tick(id, battery))
    }

    @Test fun `single slot stays held through commit and stale cancellation cannot hit new request`() {
        val firstId = start()
        val secondRequest = controller.prepareTurnOff(first)!!
        assertEquals(WaitingOutcome.BUSY, controller.startTurnOff(secondRequest, FreshAuthority(), true, true))
        assertEquals(WaitingOutcome.CANCELLED, controller.cancel(firstId))
        val next = start()
        assertEquals(WaitingOutcome.IGNORED, controller.cancel(firstId))
        at(24)
        assertEquals(WaitingOutcome.COMMITTING, controller.tick(next, battery))
        assertEquals(WaitingOutcome.ALREADY_IN_PROGRESS, controller.cancel(next))
        assertEquals(WaitingOutcome.BUSY, controller.startTurnOff(secondRequest, FreshAuthority(), true, true))
    }

    @Test fun `failed commit CAS leaves pending and restart finds no falsely completed change`() {
        val id = start(); at(24); store.fail = true
        assertEquals(WaitingOutcome.NOT_READY, controller.tick(id, battery))
        assertEquals(WaitingPhase.PENDING, store.state!!.operation!!.phase)
        store.fail = false
        assertEquals(WaitingOutcome.COMMITTING, controller().tick(id, battery))
        assertEquals(WaitingOutcome.ALREADY_IN_PROGRESS, controller().tick(id, battery))
    }

    @Test fun `missing operation storage or credentials holds slot and refuses guesses`() {
        val request = controller.prepareTurnOff(first)!!
        store.state = null
        assertEquals(WaitingOutcome.NOT_READY, controller.startTurnOff(request, FreshAuthority(), true, true))
        assertEquals(WaitingOutcome.NOT_READY, controller.tick(request.requestId, battery))
        store.state = WaitingState()
        val id = start(); credentials = null; at(24)
        assertEquals(WaitingOutcome.NOT_READY, controller.tick(id, battery))
        assertEquals(WaitingPhase.PENDING, store.state!!.operation!!.phase)
    }

    @Test fun `queued receipts survive new requests and acknowledgements are durable idempotent`() {
        val id = start()
        val receipt = store.state!!.texts.single()
        controller.cancel(id); start()
        assertEquals(3, store.state!!.texts.size)
        assertEquals(setOf(first, second), receipt.recipients)
        store.fail = true
        assertFalse(controller.acknowledge(receipt.key))
        store.fail = false
        assertTrue(controller.acknowledge(receipt.key))
        assertTrue(controller.acknowledge(receipt.key))
        assertEquals(2, store.state!!.texts.size)
        assertFailsWith<UnsupportedOperationException> { (store.state!!.texts as MutableList).clear() }
    }

    private class Store : WaitingChangeStore {
        var state: WaitingState? = WaitingState()
        var fail = false
        override fun read() = state
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
            if (fail || state?.revision != expected.revision) return false
            state = replacement
            return true
        }
    }
}
