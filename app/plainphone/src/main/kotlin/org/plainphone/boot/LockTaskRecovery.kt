// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.ActivityManager
import java.io.File
import java.io.IOException

/**
 * The live lock-task state, read at the Android boundary (PLA-322 finding 3). Only [LOCKED] is managed
 * containment; screen pinning ([PINNED]) and a state that can't be read are not.
 */
enum class LockTaskState {
    NONE, LOCKED, PINNED, UNREADABLE;

    val contained: Boolean get() = this == LOCKED

    companion object {
        /** Maps `ActivityManager.getLockTaskModeState()`. A throw or an unknown value is [UNREADABLE]. */
        fun read(mode: () -> Int): LockTaskState = try {
            when (mode()) {
                ActivityManager.LOCK_TASK_MODE_NONE -> NONE
                ActivityManager.LOCK_TASK_MODE_LOCKED -> LOCKED
                ActivityManager.LOCK_TASK_MODE_PINNED -> PINNED
                else -> UNREADABLE
            }
        } catch (e: RuntimeException) {
            UNREADABLE
        }
    }
}

/** A system calling screen (the emergency UI subset) that recovery may displace and brings back. */
enum class CallingScreen { IN_CALL, PHONE, EMERGENCY_KEYPAD, EMERGENCY_911 }

/**
 * Where [LockTaskRecovery] keeps the calling screen LMNOPhone opened, so it outlives the process that
 * opened it: Home's process can die while that screen is still opening (PLA-324). Best effort; a
 * failed read counts as nothing opened; a failed save keeps the in-process record.
 */
interface OpenedScreenRecord {
    fun load(): CallingScreen?
    fun save(screen: CallingScreen?)

    object None : OpenedScreenRecord {
        override fun load(): CallingScreen? = null
        override fun save(screen: CallingScreen?) = Unit
    }
}

/**
 * [OpenedScreenRecord] in a small file (device-protected storage on the phone): the screen and the
 * uptime when it was saved. A record from an earlier boot (uptime is lower now) or one that can't be
 * parsed loads as nothing opened.
 */
class FileOpenedScreenRecord(private val file: File, private val uptime: () -> Long) : OpenedScreenRecord {
    override fun load(): CallingScreen? {
        if (!file.exists()) return null
        val parts = file.readText().trim().split(' ')
        val screen = CallingScreen.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        val savedAt = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return screen.takeIf { savedAt <= uptime() }
    }

    override fun save(screen: CallingScreen?) {
        if (screen == null) {
            file.delete()
        } else {
            file.writeText("${screen.name} ${uptime()}")
        }
    }
}

enum class RecoveryAction { NOTHING, HOME_FORWARD, PINNED_ATTENTION }

/** What Home does after reading its own lock-task state: reopen [restore], and ask for a pass. */
data class HomeFollowUp(val restore: CallingScreen?, val reapply: Boolean)

/**
 * device-lockdown, "Lock task must actually be running" (PLA-307 finding 2, PLA-322 findings 1 and 3).
 * A re-apply that reads back does not by itself put the phone in lock task: if Plainphone's process
 * died and another app reached the front before it restarted, Home is not resumed and nothing calls
 * `startLockTask()`. So after every unlocked pass this reads the live state:
 * - `LOCKED`: nothing to do.
 * - `PINNED`: not ready, needs attention. Home is not brought forward, because starting it does not
 *   end someone else's pin. When Home later reads a state other than PINNED, it asks for a fresh pass.
 * - `NONE` or unreadable, after a ready pass: bring Home forward. Home then enters lock task itself,
 *   through the KEYGUARD gate ([PolicyRuntime.mayEnterLockTask]). A Device Owner may start an activity
 *   from the background.
 *
 * Bringing Home forward displaces whatever is on top. Android does not tell a Device Owner what that
 * is (other apps' tasks need `REAL_GET_TASKS`, usage events need the usage-stats app-op), so the
 * calling screen to bring back is identified from what can be known: an ongoing call (Telecom), or a
 * Phone or emergency keypad that LMNOPhone itself opened and the user has not left for Home since,
 * kept in [record] so the next process still knows it.
 * Home reopens it inside lock task as soon as LOCKED reads back ([onHomeLockTaskState]). A foreign
 * screen nobody can identify is not guessed: it stays behind Home in its own task, and Phone is one tap
 * from Home. Recovery never ends or touches a call.
 *
 * A locked pass never opens Home (the full plan has not run), and a pass that is not ready leaves Home
 * on its attention screen. A failed read counts as not contained; a failed start never throws.
 */
class LockTaskRecovery(
    private val lockTaskState: () -> LockTaskState,
    private val inCall: () -> Boolean,
    private val bringHomeForward: () -> Unit,
    private val now: () -> Long,
    private val record: OpenedScreenRecord = OpenedScreenRecord.None,
) {
    private var opened: CallingScreen? = try {
        record.load()
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }
    private var restore: CallingScreen? = null
    private var restoreAt = 0L
    private var pinned = false

    /** LMNOPhone opened [screen] (Home's or the checking screen's Phone or Emergency). */
    @Synchronized fun opened(screen: CallingScreen) {
        opened = screen
        save(screen)
    }

    /** Home is in front again, so nothing it opened is still in front. */
    @Synchronized fun homeResumed() {
        if (opened != null) save(null)
        opened = null
    }

    private fun save(screen: CallingScreen?) {
        try {
            record.save(screen)
        } catch (e: IOException) {
            // Storage failure must not abort the calling-screen action; keep the in-process record.
        } catch (e: RuntimeException) {
            // The in-process record still works; only a later process loses it.
        }
    }

    fun afterPass(locked: Boolean, ready: Boolean): RecoveryAction {
        if (locked) return RecoveryAction.NOTHING
        val state = try {
            lockTaskState()
        } catch (e: RuntimeException) {
            LockTaskState.UNREADABLE
        }
        synchronized(this) { pinned = state == LockTaskState.PINNED }
        if (state == LockTaskState.PINNED) return RecoveryAction.PINNED_ATTENTION
        if (!ready || state.contained) return RecoveryAction.NOTHING
        val call = try {
            inCall()
        } catch (e: RuntimeException) {
            false
        }
        synchronized(this) {
            // Set before Home starts, so its first LOCKED read finds it.
            restore = if (call) CallingScreen.IN_CALL else opened
            restoreAt = now()
        }
        return try {
            bringHomeForward()
            RecoveryAction.HOME_FORWARD
        } catch (e: RuntimeException) {
            synchronized(this) { restore = null }
            RecoveryAction.NOTHING
        }
    }

    /** Home read [state]: the displaced calling screen to reopen (only once, only when LOCKED). */
    @Synchronized fun onHomeLockTaskState(state: LockTaskState): HomeFollowUp {
        val reapply = pinned && state != LockTaskState.PINNED
        if (reapply) pinned = false
        val screen = restore?.takeIf { state.contained }
        if (screen != null) restore = null
        return HomeFollowUp(screen?.takeIf { now() - restoreAt <= RESTORE_WINDOW_MS }, reapply)
    }

    companion object {
        /** A screen not reopened this long after recovery started is left alone. */
        const val RESTORE_WINDOW_MS = 15_000L
    }
}
