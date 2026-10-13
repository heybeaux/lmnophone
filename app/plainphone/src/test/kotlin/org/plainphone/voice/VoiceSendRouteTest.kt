// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.voice.FamilyPerson
import org.plainphone.device.voice.FamilyStore
import org.plainphone.device.voice.MicAccess
import org.plainphone.device.voice.VoiceAudioGate
import org.plainphone.device.voice.VoiceDraft
import org.plainphone.device.voice.VoiceDraftStore
import org.plainphone.device.voice.VoiceFormat
import org.plainphone.device.voice.VoiceNumbers
import org.plainphone.device.voice.VoicePeople
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoicePlayer
import org.plainphone.device.voice.VoicePracticeController
import org.plainphone.device.voice.VoicePrompt
import org.plainphone.device.voice.VoiceRecorder
import org.plainphone.device.voice.VoiceSendPort
import org.plainphone.device.voice.VoiceSpeaker
import org.plainphone.device.voice.VoiceState
import org.plainphone.device.voice.send.SendStatus
import org.plainphone.device.voice.send.SendView
import org.plainphone.ui.user.VoiceUiState

/**
 * PLA-513: the host's real route, controller and mapper together. A family person with a saved number gets Send and
 * no practice band; a practice person, and anyone where the build has family sending off, gets neither Send nor a
 * hand-over. Fictional people and numbers only.
 */
class VoiceSendRouteTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Port : VoiceSendPort {
        val sent = mutableListOf<Pair<String, String>>()
        override fun send(person: VoicePerson, number: String): SendView {
            sent += person.id to number
            return SendView(SendStatus.Sending, false, 0)
        }
        override fun view(personId: String): SendView? = null
        override fun seen(personId: String) {}
    }

    private val familyPort = Port()
    private val practicePort = Port()
    private val family by lazy {
        FamilyStore(File(tmp.root, "family.json")) {}.apply {
            setEnabled(true)
            save(FamilyPerson("sam", "Sam", "Sam", "555-0100"))
        }
    }
    private val samFamily = VoicePerson("sam", "Sam", "Sam")
    private val practiceSam = VoicePeople.practice.first()

    private fun controller(route: VoiceSendRoute, people: List<VoicePerson>) = VoicePracticeController(
        object : VoiceRecorder {
            override fun start(file: File, format: VoiceFormat, maxMs: Long, listener: VoiceRecorder.Listener) {}
            override fun silent() = false
            override fun stop() = false
        },
        object : VoicePlayer {
            override fun play(file: File, onDone: () -> Unit, onError: () -> Unit) {}
            override fun stop() {}
        },
        VoiceDraftStore(tmp.newFolder()) { 1L }, MicAccess { null },
        object : VoiceSpeaker {
            override fun speak(prompt: VoicePrompt, person: VoicePerson?) {}
            override fun stop() {}
        },
        object : VoiceAudioGate {
            override fun acquire() = true
            override fun release() {}
        },
        { 1L }, people = { people }, sender = route.port, numbers = route.numbers,
    )

    private fun route(familyOn: Boolean, familySend: Boolean = true, practiceNumbers: VoiceNumbers? = null) =
        VoiceSendRoute({ familyOn }, familyPort.takeIf { familySend }, family,
            practicePort.takeIf { practiceNumbers != null }, practiceNumbers)

    @Test fun aFamilyPersonWithANumberGetsSendAndNoPracticeBand() {
        val c = controller(route(familyOn = true), listOf(samFamily))
        val review = VoiceUiMapper.map(VoiceState.Review(samFamily, VoiceDraft("sam", 1L)), listOf(samFamily), family = true,
            canSend = c::canSend)
        assertTrue(review is VoiceUiState.Review)
        review as VoiceUiState.Review
        assertTrue(review.canSend)
        assertFalse(review.practice)
        assertFalse(VoiceUiMapper.map(VoiceState.ChoosePerson, listOf(samFamily), true, c::canSend).let {
            (it as VoiceUiState.ChoosePerson).practice
        })
    }

    @Test fun aPracticePersonGetsNoSendAndKeepsTheBand() {
        val c = controller(route(familyOn = false), VoicePeople.practice)
        assertFalse(c.canSend(practiceSam))
        val review = VoiceUiMapper.map(VoiceState.Review(practiceSam, VoiceDraft(practiceSam.id, 1L)), canSend = c::canSend)
            as VoiceUiState.Review
        assertFalse(review.canSend)
        assertTrue(review.practice)
    }

    @Test fun releaseHasNoFamilySenderSoNobodyGetsSend() {
        val r = route(familyOn = true, familySend = false)
        assertNull(r.port)
        assertNull(r.numbers.number("sam"))
        assertFalse(controller(r, listOf(samFamily)).canSend(samFamily))
    }

    @Test fun theFamilyNumberIsReadByIdAtSendTimeAndGoesOnlyToTheFamilySender() {
        val r = route(familyOn = true, practiceNumbers = VoiceNumbers { "555-0199" })
        family.save(FamilyPerson("sam", "Sam", "Sam", "555-0123"))
        r.port!!.send(samFamily, r.numbers.number("sam")!!)
        assertEquals(listOf("sam" to "555-0123"), familyPort.sent)
        assertTrue(practicePort.sent.isEmpty())
        family.remove("sam")
        assertNull(r.numbers.number("sam"))
    }

    @Test fun thePracticeSideNeverReadsTheFamilyList() {
        val r = route(familyOn = false)
        assertNull(r.numbers.number("sam"))
        assertNull(r.port!!.view("sam"))
        assertFalse(controller(r, VoicePeople.practice).canSend(practiceSam))
    }

    @Test fun theDebugBuildTypeTurnsFamilySendingOn() {
        // Host tests run against the debug variant: the build type is the whole gate (no marker).
        assertTrue(VoiceSendBuild.familySend())
    }
}
