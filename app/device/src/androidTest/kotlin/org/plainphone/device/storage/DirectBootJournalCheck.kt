// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import java.io.File
import org.plainphone.core.BootTime
import org.plainphone.core.ClockReading
import org.plainphone.core.PackageName
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.WindowKind
import org.plainphone.policy.AuthorityConsumer
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.SessionPhase

/**
 * On-device check of [FileJournalStore] in device-protected storage, in two phases:
 *
 * - `-e phase seed` while unlocked: journal an open Play session.
 * - `-e phase locked` after a reboot, before the first unlock: the seeded entry is still there,
 *   a start revokes and clears it, compare-and-set works, and a flipped byte reads Damaged and is
 *   repaired by the next start. Credential-encrypted storage is tried as a control.
 *
 * `-e phase apply` runs the PLA-117 policy applier check instead ([org.plainphone.device.apply.DpmPortCheck]);
 * it needs this APK as Device Owner.
 *
 * Plain [Instrumentation] so no test library has to load before unlock. Lines go to logcat (tag
 * PLA113) and to the `am instrument` output; the run fails on the first unmet expectation.
 */
class DirectBootJournalCheck : Instrumentation() {
    private val lines = mutableListOf<String>()
    private lateinit var phase: String

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = arguments?.getString("phase") ?: "missing"
        start()
    }

    override fun onStart() {
        val ok = try {
            when (phase) {
                "seed" -> seed()
                "locked" -> locked()
                "apply" -> org.plainphone.device.apply.DpmPortCheck(targetContext, ::note).check()
                else -> fail("unknown phase $phase")
            }
            note("RESULT PASS phase=$phase")
            true
        } catch (e: Throwable) {
            note("RESULT FAIL phase=$phase: $e")
            false
        }
        val results = Bundle().apply { putString(REPORT_KEY_STREAMRESULT, lines.joinToString("\n", postfix = "\n")) }
        finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
    }

    private val deContext by lazy { targetContext.createDeviceProtectedStorageContext() }
    private val dir by lazy { File(deContext.filesDir, "session-journal") }
    private fun store() = FileJournalStore(dir, null, Build.MODEL, Build.FINGERPRINT)
    private fun unlocked() = targetContext.getSystemService(UserManager::class.java).isUserUnlocked
    private fun bootId() = File("/proc/sys/kernel/random/boot_id").readText().trim()
    private fun play(id: String, phase: SessionPhase) = SessionJournal.restore(id, WindowKind.PLAY,
        setOf(PackageName("com.android.vending")), BootTime(bootId(), SystemClock.elapsedRealtime() + 600_000), phase, null)

    private fun seed() {
        note("api=${Build.VERSION.SDK_INT} unlocked=${unlocked()} dir=${dir.absolutePath}")
        dir.deleteRecursively()
        val s = store()
        expect("fresh dir reads Damaged", s.read() == SessionJournal.Damaged)
        s.beginRevocation()
        expect("latch on disk", store().revocationPending())
        expect("CAS Damaged -> Empty", s.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        val entry = play("seeded-session", SessionPhase.OPEN)
        expect("CAS Empty -> Active(PLAY, OPEN)", s.compareAndSet(SessionJournal.Empty, entry))
        expect("new instance reads the entry", store().read() == entry)
        note("seeded ${File(dir, "journal.rec").length()} bytes, bootId=${bootId()}")
    }

    private fun locked() {
        note("api=${Build.VERSION.SDK_INT} unlocked=${unlocked()} dir=${dir.absolutePath} bootId=${bootId()}")
        expect("user is still locked", !unlocked())
        val seeded = store().read()
        expect("seeded entry survived the reboot", seeded is SessionJournal.Active && seeded.access.id == "seeded-session" &&
            seeded.phase == SessionPhase.OPEN && seeded.access.deadline.bootId != bootId())
        expect("latch clear after seed", !store().revocationPending())

        val effects = RecordingEffects()
        expect("start revokes and clears the journal", controller(effects).onStart())
        expect("revoked with latch already on disk", effects.calls.first() == "revoke(latch=true)")
        expect("journal Empty after start", store().read() == SessionJournal.Empty && !store().revocationPending())

        val s = store()
        val entry = play("locked-session", SessionPhase.OPENING)
        expect("CAS Empty -> Active before unlock", s.compareAndSet(SessionJournal.Empty, entry))
        expect("stale CAS fails", !s.compareAndSet(SessionJournal.Empty, SessionJournal.Empty))
        expect("new instance reads it", store().read() == entry)

        val record = File(dir, "journal.rec")
        val bytes = record.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x10).toByte()
        record.writeBytes(bytes)
        expect("flipped byte reads Damaged", store().read() == SessionJournal.Damaged && store().revocationPending())
        val again = RecordingEffects()
        expect("start repairs the damaged journal", controller(again).onStart())
        expect("damaged journal revoked everything", again.calls == listOf("revoke(latch=true)", "check", "readback"))
        expect("journal Empty after repair", store().read() == SessionJournal.Empty)

        // Control: credential-encrypted storage is not usable yet.
        val ce = try {
            File(targetContext.filesDir, "ce-probe").writeText("x"); "writable"
        } catch (e: Exception) {
            "unavailable (${e.javaClass.simpleName})"
        }
        note("control: credential-encrypted filesDir is $ce")
        expect("credential-encrypted storage unavailable before unlock", ce != "writable")
    }

    private inner class RecordingEffects : SessionEffects {
        val calls = mutableListOf<String>()
        override fun revokeAllRelaxations(): Boolean { calls += "revoke(latch=${store().revocationPending()})"; return true }
        override fun open(access: SessionAccess) = false
        override fun protectedStateCheck(): Boolean { calls += "check"; return true }
        override fun readBackPolicy(): Boolean { calls += "readback"; return true }
    }

    private fun controller(effects: SessionEffects) = SessionController(store(), effects,
        RunningClock { ClockReading(RunningTime("check", 0), BootTime(bootId(), SystemClock.elapsedRealtime())) },
        null, AuthorityConsumer { _, _ -> false })

    private fun expect(what: String, ok: Boolean) {
        note("${if (ok) "ok  " else "FAIL"} $what")
        if (!ok) fail(what)
    }

    private fun fail(what: String): Nothing = throw IllegalStateException(what)

    private fun note(line: String) {
        Log.i("PLA113", line)
        lines += line
    }
}
