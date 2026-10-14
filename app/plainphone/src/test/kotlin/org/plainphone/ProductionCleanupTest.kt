// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.plainphone.shell.*
import org.plainphone.ui.user.*
import org.plainphone.ui.trusted.AdminContext
import org.plainphone.device.voice.*
import org.plainphone.voice.VoiceUiMapper

class ProductionCleanupTest {
    @Test fun liveDefaultsHaveNoPeopleAppsOrHistoryAndCannotClaimSent() {
        val snapshot = PreviewPhone.empty()
        assertEquals("", snapshot.userName)
        assertTrue(snapshot.trustedPeople.isEmpty())
        assertEquals("", snapshot.requestRecipient)
        assertTrue(snapshot.apps.isEmpty())
        assertTrue(snapshot.requests.isEmpty())
        val phone = PreviewPhone(snapshot)
        assertEquals(AppRequestResult.Failed, phone.send("Maps"))
        assertTrue(phone.snapshot().requests.isEmpty())
        val mapper = ShellUiMapper(Locale.UK, true)
        assertTrue(mapper.more(snapshot).pinHolderOnly)
        assertFalse(mapper.ask(snapshot, "Maps").isSetUp)
        assertFalse(mapper.ask(snapshot, "Maps").canSend)
        assertTrue(mapper.ask(snapshot, "Maps").requests.isEmpty())
        val nav = ShellNavigator(phone, phone, "LMNOPhone", testBuild = true)
        assertEquals(ShellRoute.AskForApp, nav.onMore(ShellState(), MoreEvent.AskForApp).state.current)
        val ask = ShellState(listOf(ShellRoute.Home, ShellRoute.AskForApp), "Maps")
        assertEquals(ask, nav.onAsk(ask, AskForAppEvent.Send).state)
        val blocked = ShellState.forPreviewEntry("blocked_link").current as ShellRoute.BlockedLink
        assertNull((blocked.stage as BlockedLinkUiState.Blocked).shareWith)
    }
    @Test fun pinMapperHasNoInventedHelper() {
        for (page in PinPage.entries) {
            val state = pinUiState(PinPageState(page), AdminContext(""), "", "", "", "", "", null)
            assertEquals("", state.helper)
        }
    }
    @Test fun productionPinWiringUsesSetupHelperForEveryHelperRole() {
        val activity = File("src/main/kotlin/org/plainphone/PinAccessActivity.kt").readText()
        assertFalse(activity.contains("R.string.pin_holder"))
        assertEquals(4, Regex("R\\.string\\.setup_helper").findAll(activity).count())
        val resources = File("../ui/src/main/res/values/production_strings.xml").readText()
        val helper = "the person who set up LMNOPhone on this phone"
        assertTrue(resources.contains("<string name=\"setup_helper\">$helper</string>"))
        val state = pinUiState(PinPageState(PinPage.RECOVERY_LOCKED), AdminContext(""),
            "", "", "", "30 seconds", "", null, helper = helper)
        assertEquals(helper, state.helper)
    }
    @Test fun unusedPracticeSendHookIsNotAdvertisedOrWritable() {
        assertFalse(File("src/debug/AndroidManifest.xml").readText().contains("org.plainphone.test.VOICE_SEND"))
        assertFalse(File("src/debug/kotlin/org/plainphone/boot/TestHook.kt").readText().contains("VOICE_SEND"))
        assertFalse(File("src/debug/kotlin/org/plainphone/voice/VoiceSendBuild.kt").readText().contains("fun marker("))
    }
    @Test fun familyNamesAreActualDataEvenWhenNamedSam() {
        val data = FamilyList(true, listOf(FamilyPerson("realentry", "Sam", "Sam", "5550100")))
        val sam = VoiceUiMapper.familyPeople(data).single()
        assertEquals("Sam", sam.name)
        assertTrue(VoiceUiMapper.familyPeople(data.copy(enabled = false)).isEmpty())
        assertEquals("Sam", data.people.single().name)
        val mapped = VoiceUiMapper.map(VoiceState.ChoosePerson, listOf(sam), family = true) as VoiceUiState.ChoosePerson
        assertEquals(listOf("Sam"), mapped.people.map { it.name })
        assertTrue((VoiceUiMapper.map(VoiceState.ChoosePerson) as VoiceUiState.ChoosePerson).people.isEmpty())
    }
    @Test fun activityAndDebugWiringCannotReintroduceFixtures() {
        val root = File("src")
        val sources = listOf("main", "debug").flatMap { File(root, it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        for (f in sources) {
            val text = f.readText()
            assertFalse(f.path, Regex("\"(?:Sam|Jo|Alex|sam)\"").containsMatchIn(text))
            assertFalse(f.path, text.contains("PreviewPhone.sample("))
            assertFalse(f.path, text.contains("VoicePeople.practice"))
        }
        assertTrue(File("src/main/kotlin/org/plainphone/PlainphoneApp.kt").readText().contains("InstalledRelease.observe"))
        assertTrue(File("src/main/kotlin/org/plainphone/MainActivity.kt").readText().contains("InstalledRelease.about"))
    }
}
