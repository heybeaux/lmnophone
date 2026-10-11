// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * The one serialized dispatcher for policy writes: controllers, their effects and package-event
 * handlers all run as tasks on its single thread, in order. A task never interleaves with another,
 * so a package broadcast that arrives during an approval runs only after the approval returns.
 */
class SerialDispatcher(name: String = "plainphone-policy") : AutoCloseable {
    @Volatile private var thread: Thread? = null
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, name).also { thread = it } }

    /** True only on the dispatcher's own thread. */
    val onDispatcher: Boolean get() = Thread.currentThread() === thread

    /** Queues [task] after everything already queued. */
    fun post(task: () -> Unit) = executor.execute(task)

    /** Runs [task] as one dispatcher task and waits for it; inline when already on the dispatcher. */
    fun <T> run(task: () -> T): T {
        if (onDispatcher) return task()
        try {
            return executor.submit(Callable(task)).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    override fun close() = executor.shutdown()
}
