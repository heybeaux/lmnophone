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
            assertEquals("this phone’s PIN holder", state.helper)
        }
    }
    @Test fun familyNamesAreActualDataEvenWhenNamedSam() {
        val sam = VoicePerson("real-entry", "Sam")
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
