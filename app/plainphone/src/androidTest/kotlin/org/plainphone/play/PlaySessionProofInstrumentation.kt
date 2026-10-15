// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import org.plainphone.MainActivity
import org.plainphone.boot.HomeGate
import org.plainphone.boot.PolicyRuntime
import org.plainphone.core.*
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.authority.PinEnrollmentResult
import org.plainphone.device.session.*
import org.plainphone.policy.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator-only clock acceleration lives in the androidTest APK, never in the shipped debug app.
 * Uses the existing runtime, controller, vault, journal and device effects; changes only its clock.
 * No account, message content or real recipient is read or supplied.
 */
class PlaySessionProofInstrumentation : Instrumentation() {
    private lateinit var runtime: PolicyRuntime
    private lateinit var serial: SerialDispatcher
    private lateinit var play: PlaySessionDispatcher
    private var offset = 0L
    private val report = StringBuilder()
    private var phase = ""
    private var baseline: Set<PackageName> = emptySet()
    private fun lockPackages() = serial.run { (field(runtime, "port") as org.plainphone.device.DevicePolicyPort).lockTaskPackages() }
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); phase = arguments.getString("phase", "open"); start() }
    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private fun await(action: (() -> Unit) -> Unit) {
        val latch = CountDownLatch(1); action { latch.countDown() }; check(latch.await(30, TimeUnit.SECONDS)) { "dispatcher timeout" }
    }
    private fun waitFor(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 20000
        while (!condition()) { check(SystemClock.elapsedRealtime() < until) { "condition timeout" }; Thread.sleep(100) }
    }
    private fun shell(command: String): String = uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun home() = runOnMainSync {
        targetContext.startActivity(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
    private fun state(label: String) {
        val activities = shell("dumpsys activity activities")
        report.appendLine("$label locked=" + activities.contains("mLockTaskModeState=LOCKED"))
        report.appendLine(activities.lineSequence().filter { "topResumedActivity=" in it }.joinToString("\n"))
        report.appendLine(activities.substringAfter("mLockTaskPackages (userId:packages)=").lineSequence().take(3).joinToString("\n"))
        report.appendLine("$label view=" + runtime.playView.value)
        report.appendLine("$label play=" + shell("dumpsys package com.android.vending").lineSequence().firstOrNull { "User 0:" in it })
    }
    private fun node(test: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean): android.view.accessibility.AccessibilityNodeInfo? {
        fun find(n: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (test(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::find)
    }
    private fun tap(text: String) {
        waitFor { node { it.text?.toString() == text } != null }
        var target = node { it.text?.toString() == text }!!
        while (!target.isClickable) target = target.parent ?: error("not clickable: $text")
        check(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun openUi() {
        runOnMainSync { targetContext.startActivity(Intent(targetContext, PlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        tap("Start the wait")
        waitFor { node { it.isEditable } != null }
        node { it.isEditable }!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, CharArray(6) { '1' }.concatToString()) })
        tap("Continue")
        waitFor { runtime.playView.value.countdownMillis != null }
        report.appendLine("UI fresh PIN -> countdown=true")
        tap("OK")
        waitFor { node { it.text?.toString()?.startsWith("Opening up in") == true } != null }
        report.appendLine("Home countdown notice=true")
        serial.run { offset += 600000 }
        await { runtime.tickPlay(it) }
        tap("Ready – tap to start")
        tap("Start")
        waitFor { runtime.playView.value.active }
        Thread.sleep(2000)
        state("during-ui")
        shell("input keyevent KEYCODE_BACK")
        waitFor { !runtime.playView.value.active }
        report.appendLine("back to LMNOPhone closed=true")
    }
    private fun open() {
        var request: PlayRequestResult? = null
        await { done -> runtime.requestPlay(CharArray(6) { '1' }) { request = it; done() } }
        check(request == PlayRequestResult.Counting) { "request=$request" }
        report.appendLine("countdown=" + runtime.playView.value.countdownMillis)
        serial.run { offset += 600000 }
        await { runtime.tickPlay(it) }
        check(runtime.playView.value.readyMillis != null)
        val id = runtime.playView.value.id!!
        var started = false
        await { done -> runtime.startPlay(id) { started = it; done() } }
        check(started) { "start refused: ${runtime.playView.value}" }
        runOnMainSync {
            val launch = targetContext.packageManager.getLaunchIntentForPackage("com.android.vending") ?: error("Play has no launch intent")
            targetContext.startActivity(launch)
        }
        Thread.sleep(2000)
        check(runtime.playView.value.active) { "closed before proof" }
        check(lockPackages() == baseline + PackageName("com.android.vending")) { "widened lock task" }
        state("during")
    }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            runtime = PolicyRuntime.get(targetContext)
            serial = field(runtime, "dispatcher") as SerialDispatcher
            home(); waitFor { runtime.gate.value == HomeGate.READY }
            if (phase == "restart") {
                await { runtime.tickPlay(it) }; state("after-restart")
                check(!runtime.playView.value.active)
                check(shell("dumpsys package com.android.vending").contains("hidden=true"))
            } else {
                var enrolled: PinEnrollmentResult? = null
                await { done -> runtime.enrollPin(CharArray(6) { '1' }) { enrolled = it; done() } }
                (enrolled as? PinEnrollmentResult.Created)?.code?.clear()
                play = field(runtime, "play") as PlaySessionDispatcher
                serial.run {
                    val original = field(play, "clock") as RunningClock
                    val accelerated = RunningClock {
                        original.checkpoint()?.let { ClockReading(RunningTime(it.proven.epoch, it.proven.totalMillis + offset),
                            BootTime(it.boot.bootId, it.boot.elapsedMillis + offset)) }
                    }
                    set(play, "clock", accelerated)
                    set(field(play, "elevation"), "clock", accelerated)
                    val store = field(runtime, "authorityStore") as org.plainphone.device.authority.JointAuthorityStore
                    val last = store.elevationStore.read()?.state?.ledger?.debits?.maxOfOrNull { it.at.totalMillis } ?: 0L
                    offset = (last - original.checkpoint()!!.proven.totalMillis).coerceAtLeast(0) + 86400001L
                }
                await { runtime.tickPlay(it) }; state("before"); baseline = lockPackages()
                if (phase == "ui") openUi() else open()
                report.appendLine("Play screen text=" + uiAutomation.rootInActiveWindow?.let { root ->
                    fun texts(n: android.view.accessibility.AccessibilityNodeInfo): List<String> =
                        listOfNotNull(n.text?.toString()) + (0 until n.childCount).flatMap { n.getChild(it)?.let(::texts).orEmpty() }
                    texts(root).distinct().joinToString(" | ")
                })
                if (phase == "open") {
                    // Keep the real session journal open, then process death/reboot must revoke it.
                    state("before-process-end")
                } else {
                    when (phase) {
                        "done" -> await { done -> runtime.endPlay(SessionEnd.DONE, done) }
                        "home" -> { home(); waitFor { !runtime.playView.value.active } }
                        "screen" -> { shell("input keyevent KEYCODE_POWER"); waitFor { !runtime.playView.value.active } }
                        "install" -> {
                            report.appendLine("install=" + shell("pm install -r -t /data/local/tmp/play-candidate.apk"))
                            waitFor { shell("dumpsys package org.plainphone.t5calculator").contains("hidden=true") }
                            await { done -> runtime.endPlay(SessionEnd.DONE, done) }
                            var listed = false
                            runtime.previewHiddenApps { apps -> listed = apps?.any { it.id == "org.plainphone.t5calculator" } == true }
                            waitFor { listed }
                            report.appendLine("candidate hidden and listed=true")
                        }
                        "deadline" -> { serial.run { offset += 600000 }; await { runtime.tickPlay(it) } }
                    }
                    state("after-$phase")
                    report.appendLine("market-after=" + shell("am start -a android.intent.action.VIEW -d market://details?id=test.play.candidate -p com.android.vending"))
                    check(targetContext.packageManager.getLaunchIntentForPackage("com.android.vending") == null)
                    check(!runtime.playView.value.active)
                    check(runtime.playView.value.handbackReady)
                    check(lockPackages() == baseline) { "baseline not restored" }
                    check(!shell("dumpsys activity activities").lineSequence().any { "topResumedActivity=" in it && "com.android.vending/" in it })
                    val store = field(runtime, "authorityStore") as org.plainphone.device.authority.JointAuthorityStore
                    check(store.elevationStore.read()?.journal == SessionJournal.Empty)
                    report.appendLine("exact baseline and empty shared journal=true")
                }
            }
            result.putString("proof", report.toString())
            result.putString("result", "PASS:$phase")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("proof", report.toString())
            result.putString("result", "FAIL:$phase:${e.javaClass.simpleName}:${e.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
