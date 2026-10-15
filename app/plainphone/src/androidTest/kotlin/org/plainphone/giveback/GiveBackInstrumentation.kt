// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.giveback

import android.app.Activity
import android.app.Instrumentation
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.boot.FlushMarker
import org.plainphone.boot.PolicyRuntime
import org.plainphone.core.*
import org.plainphone.device.DpmPort
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.giveback.*
import org.plainphone.policy.*

/** Test APK only. No exported debug receiver, intent or product switch can create COMMITTING. */
class GiveBackInstrumentation : Instrumentation() {
    private lateinit var phase: String
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); phase = arguments.getString("phase")!!; start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            val context = targetContext
            val dir = context.createDeviceProtectedStorageContext().filesDir
            val admin = ComponentName(context, PlainphoneAdminReceiver::class.java)
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val store = JointAuthorityStore(File(dir, "authority"))
            when (phase) {
                "prepare" -> {
                    val latch = CountDownLatch(1)
                    var error: Throwable? = null
                    PolicyRuntime.get(context).reapply("give-back-fixture") {
                        try {
                            File(dir, "give-back-crash-fired").delete()
                            val person = TrustedPersonId("test-holder")
                            val verifier = SecretVerifier(ByteArray(16) { 1 }, ByteArray(32) { 2 }, 600_000)
                            if (store.waitingStore.read() == null) check(store.initialize(
                                CredentialState(Mode.SELF_MANAGED, mapOf(person to PersonCredential(verifier, verifier))),
                                WaitingState(), ElevationRecord()))
                            val old = store.waitingStore.read()!!
                            val op = WaitingOperation(WaitingIdentity("give-back-proof", "test-code"), person, 0, 0,
                                Mode.SELF_MANAGED, RunningTime("proof", 0), WaitingPhase.PENDING, mapOf(person to 0L))
                            // Add a fixed permission to a currently visible app before hiding it.
                            val name = "com.google.android.contacts"
                            dpm.setApplicationHidden(admin, name, false)
                            val port = DpmPort(context, admin, ComponentName(context, FlushMarker::class.java))
                            val before = port.flushComponentEnabled()!!
                            port.setFlushComponentEnabled(!before); check(port.flushComponentEnabled() == !before)
                            port.setFlushComponentEnabled(before)
                            check(dpm.setPermissionGrantState(admin, name, "android.permission.READ_CONTACTS", DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED))
                            for (pkg in listOf(name, "com.google.android.calendar")) {
                                check(dpm.setApplicationHidden(admin, pkg, true)); check(dpm.isApplicationHidden(admin, pkg))
                            }
                            dpm.setUninstallBlocked(admin, name, true)
                            dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_VPN)
                            check(store.waitingStore.compareAndSet(old, WaitingState(old.revision + 1, op,
                                storageGeneration = old.storageGeneration)))
                            File(dir, "give-back-fixture").writeText("ready")
                            android.os.Process.killProcess(android.os.Process.myPid())
                        } catch (t: Throwable) { error = t } finally { latch.countDown() }
                    }
                    check(latch.await(30, TimeUnit.SECONDS)); error?.let { throw it }
                    // Kill immediately to preserve fixture. Next phase starts without a live writer.
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                "run", "crash" -> {
                    check(File(dir, "give-back-fixture").exists())
                    val platform = DpmGiveBackPort(context, admin)
                    val effects = ReadbackGiveBackPort(DpmPort(context, admin, ComponentName(context, FlushMarker::class.java)), platform)
                    fun commitFixture() {
                    val initial = store.waitingStore.read()!!
                    val initialOp = initial.operation!!
                    if (initialOp.phase != WaitingPhase.COMMITTING) {
                        val committing = WaitingOperation(initialOp.identity, initialOp.starter, initialOp.generation,
                            initialOp.structureGeneration, initialOp.mode, initialOp.startedAt, WaitingPhase.COMMITTING,
                            initialOp.recipientGenerations, recipientContacts = initialOp.recipientContacts)
                        check(store.waitingStore.compareAndSet(initial, WaitingState(initial.revision + 1, committing,
                            storageGeneration = initial.storageGeneration)))
                    }
                    }
                    val diagnostics = object : GiveBackPort by effects {
                        override fun restore(action: GiveBackAction) {
                            try { effects.restore(action) } catch (e: Exception) {
                                result.putString("write-${action.kind}", "${e.javaClass.simpleName}: ${e.message}")
                                throw e
                            }
                        }
                    }
                    val wrapped = object : WaitingChangeStore by store.waitingStore {
                        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
                            val ok = store.waitingStore.compareAndSet(expected, replacement)
                            if (ok && phase == "crash" && replacement.giveBack?.entries?.count { it.status == GiveBackStatus.DONE } == 3) {
                                File(dir, "give-back-crash-fired").writeText("after 3 durable items")
                                // Schedule the reboot before killing this process so Android cannot
                                // restart Home and finish restoration before the host reboots it.
                                uiAutomation.executeShellCommand("reboot").close()
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }
                            return ok
                        }
                    }
                    // Access only from the test APK: share the real runtime dispatcher without adding
                    // a product/debug entry point that can create or execute synthetic authority.
                    val runtime = PolicyRuntime.get(context)
                    val field = PolicyRuntime::class.java.getDeclaredField("dispatcher").apply { isAccessible = true }
                    val dispatcher = field.get(runtime) as org.plainphone.device.SerialDispatcher
                    val outcome = dispatcher.run {
                        commitFixture()
                        GiveBackController(wrapped, diagnostics).run("give-back-proof", BatteryState(80, true), retry = true)
                    }
                    val journal = store.waitingStore.read()!!.giveBack!!
                    result.putString("steps", journal.entries.joinToString("\n") { "${it.action.kind}:${it.action.target}:${it.status}" })
                    result.putString("giveBack", outcome.toString())
                    check(outcome.outcome == GiveBackOutcome.OFF)
                    platform.openSuccessor()
                }
                "journal" -> {
                    result.putString("journal", store.waitingStore.read()?.giveBack?.entries?.joinToString("\n") {
                        "${it.action.kind}:${it.action.target}:${it.status}"
                    })
                }
                "inspect" -> {
                    result.putString("lockPackages", dpm.getLockTaskPackages(admin).joinToString(","))
                    result.putInt("lockFeatures", dpm.getLockTaskFeatures(admin))
                    result.putString("home", context.packageManager.resolveActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName)
                    result.putBoolean("owner", dpm.isDeviceOwnerApp(context.packageName))
                    result.putInt("grant", dpm.getPermissionGrantState(admin, "com.google.android.contacts", "android.permission.READ_CONTACTS"))
                    result.putBoolean("contactsHidden", dpm.isApplicationHidden(admin, "com.google.android.contacts"))
                    result.putBoolean("calendarHidden", dpm.isApplicationHidden(admin, "com.google.android.calendar"))
                }
                "verify" -> {
                    check(!dpm.isDeviceOwnerApp(context.packageName))
                    check(store.waitingStore.read()!!.operation!!.phase == WaitingPhase.COMPLETED)
                    result.putString("crash", File(dir, "give-back-crash-fired").exists().toString())
                }
                else -> error("unknown phase")
            }
            result.putString("outcome", "PASS: $phase")
            finish(Activity.RESULT_OK, result)
        } catch (t: Throwable) {
            result.putString("outcome", "FAIL: ${t.javaClass.simpleName}: ${t.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
