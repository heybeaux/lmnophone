// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Only the activity that started a locked root can stop it. Registration grants no turn-off authority. */
object GiveBackLockTask {
    private val roots = mutableListOf<WeakReference<Activity>>()
    @Synchronized fun attach(activity: Activity) {
        roots.removeAll { it.get() == null }
        if (activity.isTaskRoot) roots += WeakReference(activity)
    }
    fun stopRoots() {
        val done = CountDownLatch(1)
        val stop = Runnable {
            try {
                val current = synchronized(this) { roots.mapNotNull { it.get() } }
                current.filter { !it.isDestroyed && it.isTaskRoot }.forEach {
                    // A task may disappear while queued. The DPM membership clear still runs;
                    // ActivityManager's final NONE readback decides whether release succeeded.
                    try { it.stopLockTask() } catch (_: RuntimeException) { }
                }
            } finally { done.countDown() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) stop.run() else {
            Handler(Looper.getMainLooper()).post(stop)
            check(done.await(5, TimeUnit.SECONDS)) { "root lock-task stop timed out" }
        }
    }
}
