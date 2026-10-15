// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.release

/** Shipped changes only. Grouped entries cover the inclusive version range. */
data class ReleaseNote(val firstCode: Long, val lastCode: Long, val changes: List<String>) {
    val label: String get() = if (firstCode == lastCode) "$lastCode" else "$firstCode–$lastCode"
}
object ReleaseNotes {
    val all = listOf(
        ReleaseNote(63, 63, listOf("Behind the scenes: the steps that give the phone back in full after a turn-off are now built in. On this test phone a turn-off is still cancelled, so nothing changes yet.")),
        ReleaseNote(62, 62, listOf("With a keyboard, Enter confirms your PIN and Tab moves between fields and buttons.")),
        ReleaseNote(61, 61, listOf("PIN settings has clearer TalkBack labels for app choices and app order, and announces errors.",
            "The new recovery code is read one character at a time when you focus it.")),
        ReleaseNote(60, 60, listOf("When no name is set, screens say \"this phone\", for example \"Add Calculator to this phone?\".",
            "Back from New recovery code returns to PIN settings.")),
        ReleaseNote(59, 59, listOf("TalkBack reads settings rows without arrows, and reads sent results without tick marks.",
            "Phone settings has a heading.", "TalkBack announces progress as it appears.")),
        ReleaseNote(58, 58, listOf("PIN settings shows only choices that work in this version.",
            "Add a hidden app opens the same steps as Apps on this phone, and asks for your PIN again before adding.")),
        ReleaseNote(57, 57, listOf("Adding an app that uses Location now works. You choose whether it can use Location, and its page shows that choice.",
            "If adding an app fails partway, what it can use goes back to how it was, and the app stays hidden.")),
        ReleaseNote(56, 56, listOf("In PIN settings, you can now choose Not now on a request, then Remove it or Keep it.",
            "A removed request disappears from Requests and from Ask for an app, and stays removed after a restart. Removing one doesn't change today's request limit.",
            "Needs you shows how many requests are waiting. Tap it to open Requests. The Requests row shows the same count.",
            "Known problem: Add a hidden app can't add an app that asks for Location yet. The app stays hidden.")),
        ReleaseNote(55, 55, listOf("On an app's page, the button now says Remove and the app's name.",
            "Ask for an app now says plainly when no one is set to get requests, and where the PIN holder can set it up.",
            "Changing text size during a PIN wait keeps the waiting page visible, with the time still left.",
            "Known problem: Add a hidden app can't add an app that asks for Location yet. The app stays hidden.")),
        ReleaseNote(54, 54, listOf("Apps on this phone now shows what each app can use.",
            "When you add an app with Add a hidden app, you choose what it can use. To choose again, remove the app and add it again; your things in the app stay.")),
        ReleaseNote(53, 53, listOf("PIN settings now shows a waiting change, or “LMNOPhone needs attention”, under Needs you. Tap it to open that screen.",
            "On the Apps PIN screen, Use recovery code now opens PIN recovery.")),
        ReleaseNote(52, 52, listOf("Who gets requests now says so when its saved details can't be read.")),
        ReleaseNote(51, 51, listOf("When someone on your Family list sends a voice message back by picture message (MMS), their card shows New message. Tap Listen on their page to hear it.",
            "Clearer wording for adding people to Voice messages and for entering the recovery code.")),
        ReleaseNote(50, 50, listOf("Choose who gets app requests in PIN settings, using a fresh PIN.",
            "Ask for an app sends a text and shows Sent, Not sent or Not confirmed.",
            "Requests and their results stay on this phone after a restart or update.")),
        ReleaseNote(49, 49, listOf("About shows your installed version and release history.",
            "Menus and help no longer show sample people or made-up requests.",
            "Voice messages use your Family list. App requests explain when sending is not set up.")),
        ReleaseNote(48, 48, listOf("Recover a forgotten PIN with a recovery code. Get a new recovery code from the PIN menu.",
            "PIN setup shows your recovery code once, so you can keep it away from the phone.")),
        ReleaseNote(47, 47, listOf("Send voice messages to people in Family in the test build. Failed messages stay on the phone to try again.")),
        ReleaseNote(46, 46, listOf("Change the order of apps on Home. Remove an app from Home or add it back with your PIN, keeping its data.")),
        ReleaseNote(45, 45, listOf("Add and remove people in Family with your PIN. Open a person’s page to record a message or open Messages.")),
        ReleaseNote(44, 44, listOf("Change your PIN from the PIN menu. This cancels a waiting change started with the old PIN.")),
        ReleaseNote(43, 43, listOf("Wi-Fi asks before forgetting a network. Bluetooth searches again when you return to the search page.")),
        ReleaseNote(42, 42, listOf("Voice practice stays silent after you change text size.")),
        ReleaseNote(41, 41, listOf("Voice practice keeps your place after you change text size.")),
        ReleaseNote(40, 40, listOf("Voice practice has a clearer recording review. Tap the picture to hear the person’s name.")),
        ReleaseNote(39, 39, listOf("Open voice practice to record and listen on this phone. Practice messages are not sent.")),
        ReleaseNote(38, 38, listOf("Voice practice was included but could not be opened from More. Version 39 added the missing entry.")),
        ReleaseNote(33, 37, listOf("Join and forget Wi-Fi networks. Location turns on briefly for joining.",
            "Postpone a phone software update. Menus have more even spacing.")),
        ReleaseNote(27, 32, listOf("Change text size and ringtone. View Wi-Fi status and search for Bluetooth devices.")),
        ReleaseNote(23, 26, listOf("Start or cancel the wait to turn LMNOPhone off. View waiting changes in Phone settings.",
            "The app is named LMNOPhone. Checks for which apps can open were improved.")),
        ReleaseNote(17, 22, listOf("Set and enter a PIN. Open About, brightness, battery and sound screens.")),
        ReleaseNote(2, 16, listOf("Home shows the apps added to this phone. Other apps stay hidden.",
            "Optional missed-call and unread-text counts, and checks after restarts and app changes.")),
    )
    fun current(code: Long): ReleaseNote? = all.firstOrNull { code in it.firstCode..it.lastCode }
    fun since(current: Long, previous: Long?): List<ReleaseNote> {
        if (previous == null || previous >= current || current(previous) == null || current(current) == null) return emptyList()
        return all.filter { it.lastCode > previous && it.firstCode <= current }
    }
}

/** Pure production mapping shared by startup state and host tests. */
fun releaseAbout(name: String, version: ObservedVersions, secure: Boolean = false): org.plainphone.ui.user.AboutUiState {
    fun ReleaseNote.ui() = org.plainphone.ui.user.ReleaseNoteUi(label, changes)
    val current = ReleaseNotes.current(version.current)
    return org.plainphone.ui.user.AboutUiState(name, version.current, current?.ui(),
        ReleaseNotes.since(version.current, version.previous).filter { it != current }.map { it.ui() },
        ReleaseNotes.all.filter { it.lastCode < version.current }.map { it.ui() }, secure)
}
