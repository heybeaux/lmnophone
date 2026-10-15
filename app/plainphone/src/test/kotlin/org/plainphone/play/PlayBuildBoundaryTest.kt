// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PlayBuildBoundaryTest {
    @Test fun shippedDebugHooksHaveNoOpeningOrClockAccelerationPath() {
        val forbidden = listOf("addPlayApp(", "commitPlayApproval(", "commitApproval(", "requestPlay(", "startPlay(", "PlaySessionDispatcher(", "ElevationController(", "PlaySessionProofInstrumentation", "set(play, \"clock\"")
        for (dir in listOf("src/debug", "src/noControl", "../device/src/debug", "../ui/src/debug")) {
            File(dir).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.forEach { file ->
                val text = file.readText()
                for (token in forbidden) assertFalse("${file.name}: $token", text.contains(token))
            }
        }
    }
}
