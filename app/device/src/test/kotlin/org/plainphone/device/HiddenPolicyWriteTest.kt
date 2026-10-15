// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLA-624: a hidden policy stored before an uninstall must not stop the hide of the reinstalled package. */
class HiddenPolicyWriteTest {
    /**
     * The API 34+ device policy engine as seen on pla104-a36: it keeps the admin's stored value across an
     * uninstall, enforces only a changed value, and setApplicationHidden returns the live state == value.
     */
    private class PolicyEngine(var stored: Boolean?, var hidden: Boolean, var installed: Boolean = true, val refuseHide: Boolean = false) {
        val writes = mutableListOf<Boolean>()
        fun set(value: Boolean): Boolean {
            writes += value
            if (stored != value) {
                stored = value
                if (!(value && refuseHide)) hidden = value
            }
            return hidden == value
        }
        fun read(): Boolean? = if (installed) hidden else null
    }

    private fun PolicyEngine.write(hidden: Boolean) = HiddenPolicyWrite.write(hidden, ::set, ::read)

    @Test fun fakeReproducesTheStalePolicy() {
        val engine = PolicyEngine(stored = true, hidden = false)
        assertFalse(engine.set(true))
        assertFalse(engine.hidden)
    }

    @Test fun reinstalledPackageWithStaleHiddenPolicyIsHidden() {
        val engine = PolicyEngine(stored = true, hidden = false)
        engine.write(true)
        assertEquals(listOf(true, false, true), engine.writes)
        assertTrue(engine.hidden)
        assertEquals(true, engine.stored)
    }

    @Test fun firstInstallHidesWithOneWrite() {
        val engine = PolicyEngine(stored = null, hidden = false)
        engine.write(true)
        assertEquals(listOf(true), engine.writes)
        assertTrue(engine.hidden)
    }

    @Test fun updateOverHiddenPackageWritesOnce() {
        val engine = PolicyEngine(stored = true, hidden = true)
        engine.write(true)
        assertEquals(listOf(true), engine.writes)
        assertTrue(engine.hidden)
    }

    @Test fun refusedHideIsRetriedOnceAndStaysVisibleForTheReadback() {
        val engine = PolicyEngine(stored = null, hidden = false, refuseHide = true)
        engine.write(true)
        assertEquals(listOf(true, false, true), engine.writes)
        assertFalse(engine.hidden)
    }

    @Test fun absentPackageIsNotRetried() {
        val engine = PolicyEngine(stored = true, hidden = false, installed = false)
        engine.write(true)
        assertEquals(listOf(true), engine.writes)
        assertNull(engine.read())
    }

    @Test fun showIsNeverRetried() {
        val engine = PolicyEngine(stored = false, hidden = true)
        engine.write(false)
        assertEquals(listOf(false), engine.writes)
        assertTrue(engine.hidden)
    }
}
