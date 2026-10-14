// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.release

/** Shipped changes only. Grouped entries cover the inclusive version range. */
data class ReleaseNote(val firstCode: Long, val lastCode: Long, val changes: List<String>) {
    val label: String get() = if (firstCode == lastCode) "$lastCode" else "$firstCode–$lastCode"
}
object ReleaseNotes {
    val all = listOf(
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
    return org.plainphone.ui.user.AboutUiState(name, version.current, ReleaseNotes.current(version.current)?.ui(),
        ReleaseNotes.since(version.current, version.previous).map { it.ui() },
        ReleaseNotes.all.filter { it.lastCode < version.current }.map { it.ui() }, secure)
}
