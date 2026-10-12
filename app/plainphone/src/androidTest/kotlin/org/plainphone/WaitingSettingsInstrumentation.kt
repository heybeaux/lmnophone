// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import java.io.File
import java.security.MessageDigest
import org.plainphone.core.*
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.runtime.AndroidRuntimeFacts
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.policy.*

/** Emulator-only U7 fixture. Never shipped in the app. Original authority and clock are restored byte for byte. */
class WaitingSettingsInstrumentation : Instrumentation() {
    private lateinit var phase: String
    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        phase = arguments.getString("phase")!!
        start()
    }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            val dir = targetContext.createDeviceProtectedStorageContext().filesDir
            val backup = File(dir, "u7-fixture-backup")
            val names = listOf("authority", "clock")
            fun hashes(root: File): Map<String, String> = names.flatMap { name ->
                File(root, name).walkTopDown().filter { it.isFile }.map { file ->
                    file.relativeTo(root).path to MessageDigest.getInstance("SHA-256")
                        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                }.toList()
            }.toMap()
            when (phase) {
                "seed" -> {
                    check(!backup.exists()) { "Restore the existing fixture first" }
                    check(backup.mkdir())
                    for (name in names) {
                        val file = File(dir, name)
                        if (file.exists()) check(file.copyRecursively(File(backup, name)))
                    }
                    check(hashes(dir) == hashes(backup))
                    File(backup, "ready").writeText("backed up")
                    for (name in names) check(File(dir, name).deleteRecursively())
                    val store = JointAuthorityStore(File(dir, "authority"))
                    val sam = TrustedPersonId("sam")
                    val verifier = SecretVerifier(ByteArray(16) { 1 }, ByteArray(32) { 2 }, 600_000)
                    check(store.initialize(CredentialState(Mode.TRUSTED,
                        mapOf(sam to PersonCredential(verifier, verifier, generation = 1)), 0),
                        WaitingState(), ElevationRecord()))
                    val facts = AndroidRuntimeFacts(targetContext)
                    val clock = ProvenRunningClock(FileClockStateStore(File(dir, "clock")), facts::bootTime, store::reserveEpoch)
                    val operation = WaitingOperation(WaitingIdentity("u7-fixture", "synthetic-cancel"),
                        sam, 1, 0, Mode.TRUSTED, clock.checkpoint()!!.proven, WaitingPhase.PENDING,
                        mapOf(sam to 1L))
                    check(store.cancelIntents.initialize("u7-fixture"))
                    val old = store.waitingStore.read()!!
                    check(store.waitingStore.compareAndSet(old,
                        WaitingState(old.revision + 1, operation, emptyList(), old.storageGeneration)))
                    check(store.waitingStore.read()!!.operation!!.phase == WaitingPhase.PENDING)
                    result.putString("fixture", "TURN_OFF pending; synthetic credential; no delivery address")
                }
                "verify" -> {
                    check(File(backup, "ready").exists())
                    val store = JointAuthorityStore(File(dir, "authority"))
                    val wait = store.waitingStore.read()!!
                    check(wait.operation!!.identity.requestId == "u7-fixture")
                    check(wait.operation!!.phase == WaitingPhase.CANCELLED)
                    check(wait.operation!!.restoredSteps == 0)
                    check(wait.texts.any { it.event == WaitingEvent.CANCELLED_ON_PHONE })
                    check(store.credentials.read()!!.people.keys == setOf(TrustedPersonId("sam")))
                    result.putString("fixture", "CANCELLED durably; no restore steps; credential unchanged")
                }
                "restore" -> {
                    check(File(backup, "ready").exists())
                    for (name in names) {
                        check(File(dir, name).deleteRecursively())
                        val original = File(backup, name)
                        if (original.exists()) check(original.copyRecursively(File(dir, name)))
                    }
                    check(hashes(dir) == hashes(backup))
                    check(backup.deleteRecursively())
                    result.putString("fixture", "Original authority and clock restored byte for byte")
                }
                else -> error("Unknown phase")
            }
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("outcome", "FAIL: " + failure.javaClass.simpleName)
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
