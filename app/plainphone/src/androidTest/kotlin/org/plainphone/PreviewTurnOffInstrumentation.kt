// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import java.io.File
import java.security.MessageDigest
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.policy.*

/** Emulator only. Starts are always through UI; the sole mutation under test advances proven time. */
class PreviewTurnOffInstrumentation : Instrumentation() {
    private lateinit var phase: String
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); phase = arguments.getString("phase")!!; start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val dir = targetContext.createDeviceProtectedStorageContext().filesDir
            val backup = File(dir, "preview24-backup")
            val names = listOf("authority", "clock")
            fun hashes(root: File) = names.flatMap { name -> File(root, name).walkTopDown().filter { it.isFile }.map {
                it.relativeTo(root).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
            }.toList() }.toMap()
            when (phase) {
                "backup" -> {
                    check(!backup.exists()); check(backup.mkdir())
                    for (name in names) if (File(dir, name).exists()) check(File(dir, name).copyRecursively(File(backup, name)))
                    check(hashes(dir) == hashes(backup)); File(backup, "ready").writeText("ready")
                }
                "clean-enrollment" -> {
                    // The previous emulator PIN was random and not retained. Keep its exact backup;
                    // remove only the test credential/clock files, then enroll and start through UI.
                    check(File(backup, "ready").exists())
                    for (name in names) check(File(dir, name).deleteRecursively())
                }
                "mature" -> {
                    check(File(backup, "ready").exists())
                    val store = JointAuthorityStore(File(dir, "authority"))
                    val op = store.waitingStore.read()!!.operation!!
                    check(op.kind == WaitingChangeKind.TURN_OFF && op.phase == WaitingPhase.PENDING)
                    val clock = FileClockStateStore(File(dir, "clock"))
                    val old = clock.read()!!
                    check(old.proven.epoch == op.startedAt.epoch)
                    check(clock.compareAndSet(old, old.copy(proven = old.proven.copy(totalMillis = op.startedAt.totalMillis + 86_400_000))))
                }
                "cancelled", "closed" -> {
                    val store = JointAuthorityStore(File(dir, "authority"))
                    val state = store.waitingStore.read()!!
                    val op = state.operation!!
                    check(op.phase == WaitingPhase.CANCELLED && op.restoredSteps == 0)
                    val event = if (phase == "closed") WaitingEvent.CANCELLED_AFTER_ROLLBACK else WaitingEvent.CANCELLED_ON_PHONE
                    check(state.texts.any { it.event == event && it.operation.identity.requestId == op.identity.requestId })
                    check(state.texts.none { it.event == WaitingEvent.COMMITTING })
                }
                "restore" -> {
                    check(File(backup, "ready").exists())
                    for (name in names) {
                        check(File(dir, name).deleteRecursively())
                        if (File(backup, name).exists()) check(File(backup, name).copyRecursively(File(dir, name)))
                    }
                    check(hashes(dir) == hashes(backup)); check(backup.deleteRecursively())
                }
                else -> error("Unknown phase")
            }
            result.putString("outcome", "PASS: $phase")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("outcome", "FAIL: ${e.javaClass.simpleName}: ${e.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
