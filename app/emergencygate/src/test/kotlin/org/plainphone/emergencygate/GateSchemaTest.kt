// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.emergencygate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the shared table `judge-cases.txt`; tools/tests/test_emergency_gate.py runs the same table through the shell judge. */
class GateSchemaTest {
    private data class Case(val name: String, val control: String, val expect: String, val lines: List<String>)

    private fun cases(): List<Case> {
        val text = javaClass.classLoader!!.getResource("judge-cases.txt")!!.readText()
        return text.split(Regex("""(?m)^# case """)).drop(1).map { block ->
            val rows = block.lines()
            fun field(key: String) = rows.first { it.startsWith("# $key ") }.removePrefix("# $key ").trim()
            Case(rows.first().trim(), field("control"), field("expect"), rows.filter { it.startsWith("GATE ") })
        }
    }

    @Test fun `every shared case gets its expected outcome`() {
        val all = cases()
        assertTrue("table too small: ${all.size}", all.size >= 30)
        val wrong = all.mapNotNull { case ->
            val judged = GateSchema.judge(case.control, case.lines.mapNotNull(CheckResult::parse))
            if (judged.outcome.name == case.expect) null else "${case.name}: expected ${case.expect}, got ${judged.outcome} ${judged.reasons}"
        }
        assertEquals(wrong.joinToString("\n"), emptyList<String>(), wrong)
    }

    @Test fun `every gate line parses, including the new verdicts`() {
        val unparsed = cases().flatMap { it.lines }.filter { CheckResult.parse(it) == null }
        assertEquals(emptyList<String>(), unparsed)
    }

    @Test fun `reasons name the offending check`() {
        val judged = GateSchema.judge("none", listOf(
            CheckResult("capabilities", Verdict.PASS, "control=none notBuilt=-"),
            CheckResult("home-phone", Verdict.INCONCLUSIVE, "x"),
        ))
        assertEquals(Outcome.INCOMPLETE, judged.outcome)
        assertTrue(judged.reasons.toString(), judged.reasons.any { "home-phone" in it })
        assertTrue(judged.reasons.toString(), judged.reasons.any { "missing" in it && "locked-boot" in it })
    }

    @Test fun `sos and the unbuilt product states are declared not covered, never run`() {
        assertEquals(setOf("sos", "setup", "pin-lockout", "supervised-session", "updater", "turn-off"), GateSchema.NOT_COVERED.keys)
        assertTrue(GateSchema.NOT_COVERED.getValue("sos").contains("Q14"))
    }

    @Test fun `the number allowlist is exactly the test-mode number`() {
        assertTrue(NumberFence.allowed("7357"))
        for (bad in listOf("911", "112", "999", "07357", "7357 ", "73570", "", "*7357", "tel:7357")) {
            assertTrue(bad, !NumberFence.allowed(bad))
        }
    }

    @Test fun `the fence needs an emulator and the number in the test-mode list`() {
        val list = "[112, 911, 7357]" // the `-p` format; API 35 prints `[112, 911]` before `-a`
        assertEquals(null, NumberFence.refusal("7357", qemu = "1", testModeList = list))
        assertTrue(NumberFence.refusal("911", qemu = "1", testModeList = list)!!.contains("allowlist"))
        assertTrue(NumberFence.refusal("7357", qemu = "", testModeList = list)!!.contains("emulator"))
        assertTrue(NumberFence.refusal("7357", qemu = "0", testModeList = list)!!.contains("emulator"))
        assertTrue(NumberFence.refusal("7357", qemu = "1", testModeList = "[112, 911]")!!.contains("test-mode"))
        assertTrue(NumberFence.refusal("7357", qemu = "1", testModeList = "[112, 73570]")!!.contains("test-mode"))
    }
}
