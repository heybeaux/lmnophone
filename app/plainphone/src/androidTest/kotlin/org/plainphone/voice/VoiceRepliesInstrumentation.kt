// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.voice

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import org.plainphone.MainActivity
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.device.voice.AndroidFamilyStore
import org.plainphone.device.voice.AndroidMicAccess
import org.plainphone.device.voice.FamilyPerson
import org.plainphone.device.voice.replies.AndroidMmsInbox
import org.plainphone.device.voice.replies.ReplyPlayedStore
import org.plainphone.device.voice.send.VoiceSendChannel

/**
 * PLA-527, private synthetic emulator only (`am instrument`, never connectedAndroidTest). Needs two seeded inbox
 * MMS with an audio part: one from fictional 555-0100, one from a stranger. Drives production Home -> Family ->
 * Sam -> Listen -> Play again -> Back, reads back this app's audio player, then removes Sam through the production
 * store. Never taps Record, Send or Messages; never calls or texts anyone. Argument `label` names the screenshots.
 */
class VoiceRepliesInstrumentation : Instrumentation() {
    private var label = "1.0"
    private val steps = mutableListOf<String>()

    override fun onCreate(arguments: Bundle) { label = arguments.getString("label") ?: label; super.onCreate(arguments); start() }

    private fun waitFor(what: String, timeoutMs: Long = 20_000, read: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!read()) { check(System.currentTimeMillis() < end) { "timed out: $what" }; Thread.sleep(200) }
    }
    private fun find(label: String, node: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        node ?: return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (i in 0 until node.childCount) find(label, node.getChild(i))?.let { return it }
        return null
    }
    private fun tap(label: String) {
        waitFor(label) { find(label) != null }
        var node = find(label)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "not clickable: $label" }
        waitForIdleSync()
    }
    private fun shot(name: String) {
        Thread.sleep(400)
        val image = uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
        File(targetContext.filesDir, "replies-$label-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
    }
    /** This app's own started players in `dumpsys audio` (the TTS engine plays under its own uid). */
    private fun playing(): List<String> {
        val players = shell("dumpsys audio").substringAfter("players:", "").substringBefore("ducked players", "")
        return players.lines().filter { "u/pid:${Process.myUid()}/" in it && "state:started" in it }.map { it.trim() }
    }
    private fun note(line: String) { steps += line }
    /** Home is singleTask and may already be running: startActivitySync would wait for a new instance forever. */
    private fun home() {
        sendKeyDownUpSync(KeyEvent.KEYCODE_WAKEUP)
        targetContext.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        waitForIdleSync()
        waitFor("Home", 40_000) { find("More") != null }
    }

    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            val noBackup = targetContext.noBackupFilesDir
            val store = AndroidFamilyStore.open(targetContext)
            check(store.read().people.all { it.id == "testsam" }) { "requires empty synthetic family store" }
            store.read().people.forEach { store.remove(it.id) }
            store.save(FamilyPerson("testsam", "Sam", "Sam", "555-0100"))
            store.setEnabled(true)
            check(AndroidMicAccess(targetContext, ComponentName(targetContext, PlainphoneAdminReceiver::class.java)).selfGrant())
            val sendRecord = File(noBackup, "${VoiceSendChannel.Family.recordDir}/testsam.send")
            sendRecord.parentFile!!.mkdirs(); sendRecord.writeText("synthetic send record")
            val inbox = AndroidMmsInbox(targetContext).read()
            note("inbox audio messages read with READ_SMS: ${inbox.size} (expected 2: Sam and a stranger)")
            check(inbox.size == 2) { "seed two audio MMS first" }

            home()
            tap("Family, voice messages")
            waitFor("people list") { find("Who do you want to talk to?") != null }
            waitFor("Sam's card shows New message") { find("Sam, new message") != null }
            shot("1-people-new")
            note("V1: card 'Sam, new message'; no other card (the stranger is not on the list)")

            tap("Sam, new message")
            waitFor("Listen to Sam") { find("Listen to Sam") != null }
            check(find("Messages with Sam") != null) { "Messages with Sam missing" }
            check(playing().isEmpty()) { "played on open" }
            shot("2-person-listen")
            note("V2: Listen to Sam with Messages with Sam; nothing playing on open")

            tap("Listen to Sam")
            waitFor("Stop listening") { find("Stop listening") != null }
            waitFor("app player started", 15_000) { playing().isNotEmpty() }
            note("V12a playing: ${playing().first()}")
            shot("3-listening")

            waitFor("Play again", 30_000) { find("Play again") != null }
            Thread.sleep(500)
            check(playing().isEmpty()) { "still playing after end" }
            val played = File(noBackup, "${ReplyPlayedStore.DIR}/testsam.played")
            val keys = played.readLines().filter { it.isNotEmpty() }
            check(keys.size == 1 && keys.all { Regex("[0-9a-f]{32}").matches(it) }) { "played-state not one opaque key" }
            shot("4-play-again")
            note("V12a end: Play again; played-state holds 1 opaque key, no number")

            tap("Play again")
            waitFor("replay started", 15_000) { playing().isNotEmpty() }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor("leaving stops playback", 5_000) { playing().isEmpty() }
            waitFor("people list again") { find("Who do you want to talk to?") != null }
            check(find("Sam, new message") == null && find("Sam") != null) { "heard reply still new" }
            shot("5-people-heard")
            note("Back during Play again stops the app player; Sam's card is no longer New message")

            store.remove("testsam")
            check(!played.exists()) { "played-state left after remove" }
            check(!sendRecord.exists()) { "send record left after remove" }
            check(File(noBackup, ReplyPlayedStore.DIR).listFiles().orEmpty().isEmpty())
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            home()
            Thread.sleep(1_500)
            check(find("Sam, new message") == null && find("Sam") == null) { "Sam still shown after remove" }
            shot("6-after-remove")
            note("Remove Sam (production store): played-state and send record erased; nothing shown")
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result.also { it.putString("steps", steps.joinToString(" | ")) })
        } catch (e: Throwable) {
            result.putString("outcome", "FAIL: ${e.javaClass.simpleName}: ${e.message}")
            finish(Activity.RESULT_CANCELED, result.also { it.putString("steps", steps.joinToString(" | ")) })
        }
    }
}
