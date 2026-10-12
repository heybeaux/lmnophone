// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.storage.FileSuspensionRecord

/** device-lockdown, Fallback containment (PLA-344; board card da306a1a Q1). */
class FallbackContainmentTest {
    @get:Rule val tmp = TemporaryFolder()

    private val home = PackageName("org.plainphone")
    private val dialer = PackageName("com.android.dialer")
    private val keyboard = PackageName("com.google.android.inputmethod.latin")
    private val chrome = PackageName("com.android.chrome")
    private val testApp = PackageName("org.example.test")
    private val settings = PackageName("com.android.settings")
    private val wellbeing = PackageName("org.example.suspendedbyothers")
    /** The lock-task UI list plus the floor and never-hide dependencies, as BootOutcome.keep gives it. */
    private val keep = setOf(home, dialer, keyboard)

    private val events = mutableListOf<String>()
    private class ProcessDeath : Error()

    /** Android's suspension state, with a refusal list like API 35's OP_SYSTEM_EXEMPT_FROM_SUSPENSION. */
    private inner class Port(
        installed: Set<PackageName>,
        private val refuses: Set<PackageName> = setOf(settings),
        preSuspended: Set<PackageName> = emptySet(),
    ) : SuspensionPort {
        val installed = installed.toMutableSet()
        val state = installed.associateWith { it in preSuspended }.toMutableMap()
        var throwOnWrite = false
        /** The process dies inside the call (an Error, which no caller catches). */
        var dieOnWrite = false
        override fun installed(): Set<PackageName> = installed.toSet()
        override fun suspended(name: PackageName): Boolean? = if (name in installed) state[name] else null
        override fun setSuspended(names: Set<PackageName>, suspended: Boolean): Set<PackageName> {
            events += "${if (suspended) "suspend" else "unsuspend"} ${names.map { it.value }.sorted()}"
            if (dieOnWrite) throw ProcessDeath()
            if (throwOnWrite) throw IllegalStateException("write refused")
            val refused = if (suspended) names intersect refuses else emptySet()
            for (name in names - refused) if (name in installed) state[name] = suspended
            return refused
        }
        fun suspendedNow(): Set<PackageName> = state.filterValues { it }.keys
    }

    private inner class Record(initial: Set<PackageName> = emptySet()) : SuspensionRecord {
        var saved = initial
        var acknowledge = true
        var unreadable = false
        override fun read(): Set<PackageName> {
            if (unreadable) throw IOException("record unreadable")
            return saved
        }
        override fun save(names: Set<PackageName>): Boolean {
            events += "record ${names.map { it.value }.sorted()}"
            if (!acknowledge) return false
            saved = names
            return true
        }
    }

    private var locked = false
    private var keyguard = false
    private fun containment(port: Port, record: SuspensionRecord) = FallbackContainment(port, record, { locked }, { keyguard })

    @Test fun suspendsEverythingOutsideKeepAndReadsItBack() {
        val port = Port(keep + chrome + testApp + settings)
        val result = containment(port, Record()).contain(keep)
        assertEquals(setOf(chrome, testApp), result.suspended)
        assertEquals(setOf(settings), result.refused)
        assertTrue(result.recorded)
        assertEquals(setOf(chrome, testApp), port.suspendedNow())
    }

    @Test fun recordIsSavedBeforeAnyPackageIsSuspended() {
        val port = Port(keep + chrome + testApp)
        containment(port, Record()).contain(keep)
        val firstRecord = events.indexOfFirst { it.startsWith("record") && "com.android.chrome" in it }
        val firstSuspend = events.indexOfFirst { it.startsWith("suspend") }
        assertTrue("events=$events", firstRecord in 0 until firstSuspend)
    }

    @Test fun nothingNewIsSuspendedWhenTheRecordIsNotAcknowledged() {
        val port = Port(keep + chrome)
        val record = Record().apply { acknowledge = false }
        val result = containment(port, record).contain(keep)
        assertFalse(result.recorded)
        assertTrue(port.suspendedNow().isEmpty())
        assertTrue("events=$events", events.none { it.startsWith("suspend") && "com.android.chrome" in it })
    }

    @Test fun nothingIsSuspendedWhenTheRecordIsUnreadable() {
        val port = Port(keep + chrome)
        val record = Record().apply { unreadable = true }
        val result = containment(port, record).contain(keep)
        assertFalse(result.recorded)
        assertTrue(port.suspendedNow().isEmpty())
    }

    @Test fun floorAndKeepAreNeverSuspended() {
        val port = Port(keep + chrome)
        val c = containment(port, Record())
        repeat(3) { c.contain(keep) }
        assertTrue("events=$events", events.filter { it.startsWith("suspend") }.none { e -> keep.any { it.value in e } })
        assertTrue(keep.none { port.state[it] == true })
    }

    @Test fun aRecordedPackageThatJoinsKeepIsReleasedAtOnce() {
        val port = Port(keep + chrome + testApp)
        val record = Record()
        val c = containment(port, record)
        c.contain(keep)
        assertEquals(setOf(chrome, testApp), record.saved)
        // testApp is approved now (on the lock-task list), while lock task still can't start.
        val result = c.contain(keep + testApp)
        assertEquals(false, port.state[testApp])
        assertEquals(setOf(chrome), record.saved)
        assertEquals(setOf(chrome), result.suspended)
    }

    @Test fun liftsExactlyTheRecordedSetAndNotWhatWasSuspendedBefore() {
        val port = Port(keep + chrome + testApp + wellbeing, preSuspended = setOf(wellbeing))
        val record = Record()
        val c = containment(port, record)
        c.contain(keep)
        assertEquals(setOf(chrome, testApp), record.saved)
        locked = true; keyguard = true
        assertEquals(setOf(chrome, testApp), c.liftIfLocked())
        assertEquals(setOf(wellbeing), port.suspendedNow())
        assertTrue(record.saved.isEmpty())
        assertTrue("events=$events", events.none { it.startsWith("unsuspend") && wellbeing.value in it })
    }

    @Test fun refusedPackagesAreNotKeptInTheRecord() {
        val port = Port(keep + chrome + settings)
        val record = Record()
        containment(port, record).contain(keep)
        assertEquals(setOf(chrome), record.saved)
        locked = true; keyguard = true
        containment(port, record).liftIfLocked()
        assertTrue("events=$events", events.none { it.startsWith("unsuspend") && settings.value in it })
    }

    @Test fun liftsOnlyWhenLockedAndKeyguardBothReadBack() {
        val port = Port(keep + chrome)
        val record = Record()
        val c = containment(port, record)
        c.contain(keep)
        for ((l, k) in listOf(false to false, true to false, false to true)) {
            locked = l; keyguard = k
            assertNull("locked=$l keyguard=$k", c.liftIfLocked())
            assertEquals(setOf(chrome), port.suspendedNow())
            assertEquals(setOf(chrome), record.saved)
        }
        locked = true; keyguard = true
        assertEquals(setOf(chrome), c.liftIfLocked())
        assertTrue(port.suspendedNow().isEmpty())
    }

    @Test fun aCrashBetweenRecordAndApplyIsRepairedOnTheNextPass() {
        val port = Port(keep + chrome + testApp)
        val record = Record()
        port.dieOnWrite = true
        runCatching { containment(port, record).contain(keep) }
        assertEquals(setOf(chrome, testApp), record.saved)
        assertTrue(port.suspendedNow().isEmpty())
        // The next process, still in fallback.
        port.dieOnWrite = false
        val result = containment(port, record).contain(keep)
        assertEquals(setOf(chrome, testApp), result.suspended)
        assertEquals(setOf(chrome, testApp), port.suspendedNow())
        // Then lock task starts and everything recorded is lifted.
        locked = true; keyguard = true
        assertEquals(setOf(chrome, testApp), containment(port, record).liftIfLocked())
        assertTrue(port.suspendedNow().isEmpty())
        assertTrue(record.saved.isEmpty())
    }

    @Test fun aCrashBeforeApplyThenLockTaskLiftsWithoutTouchingOthers() {
        val port = Port(keep + chrome + wellbeing, preSuspended = setOf(wellbeing))
        val record = Record()
        port.dieOnWrite = true
        runCatching { containment(port, record).contain(keep) }
        port.dieOnWrite = false
        locked = true; keyguard = true
        containment(port, record).liftIfLocked()
        assertEquals(setOf(wellbeing), port.suspendedNow())
        assertTrue(record.saved.isEmpty())
    }

    @Test fun aRecordedPackageStaysRecordedWhenItsLiftDoesNotReadBack() {
        val port = Port(keep + chrome)
        val record = Record()
        val c = containment(port, record)
        c.contain(keep)
        locked = true; keyguard = true
        port.throwOnWrite = true
        assertNull(c.liftIfLocked())
        assertEquals(setOf(chrome), record.saved)
        port.throwOnWrite = false
        assertEquals(setOf(chrome), c.liftIfLocked())
        assertTrue(record.saved.isEmpty())
    }

    @Test fun anUninstalledRecordedPackageIsDropped() {
        val port = Port(keep + chrome + testApp)
        val record = Record()
        val c = containment(port, record)
        c.contain(keep)
        port.installed -= testApp
        c.contain(keep)
        assertEquals(setOf(chrome), record.saved)
    }

    @Test fun fileRecordRoundTripsAndAMissingRecordIsEmpty() {
        val dir = tmp.newFolder("fallback")
        val store = FileSuspensionRecord(java.io.File(dir, "suspended"))
        assertTrue(store.read().isEmpty())
        assertTrue(store.save(setOf(chrome, testApp)))
        assertEquals(setOf(chrome, testApp), FileSuspensionRecord(java.io.File(dir, "suspended")).read())
        assertTrue(store.save(emptySet()))
        assertTrue(store.read().isEmpty())
    }

    @Test fun aCorruptFileRecordThrowsInsteadOfReadingEmpty() {
        val dir = java.io.File(tmp.newFolder("fallback"), "suspended")
        val store = FileSuspensionRecord(dir)
        assertTrue(store.save(setOf(chrome)))
        dir.listFiles()!!.filter { it.name.endsWith(".rec") }.forEach { it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertThrows(IOException::class.java) { store.read() }
    }
}
