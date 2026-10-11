// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.ReapplyTimings

class LiveInstalledPackagesTest {
    @Test fun lightReadMatchesFullProjectionAcrossChangingAndMissingSnapshots() {
        val variants = listOf<RawPackage?>(null) + listOf(false, true).flatMap { system ->
            listOf(1L, 2L).flatMap { version -> listOf(false, true).flatMap { enabled ->
                listOf(null, listOf("keyA".toByteArray()), listOf("keyB".toByteArray())).map { signers ->
                    RawPackage("com.example.notes", system, version, signers, enabled)
                }
            } }
        }
        for (first in variants) for (second in variants) {
            val snapshots = listOf(listOfNotNull(first), listOfNotNull(second))
            fun port() = FakePackageSystemPort().apply { reads = snapshots.toMutableList() }
            val full = LivePackageReader(port()).read()
            val light = LivePackageReader(port()).readInstalled()
            assertEquals(full.installed, light.installed)
            assertEquals(full.disabled, light.disabled)
        }
    }

    @Test fun packageOrderAndDuplicateVariantsMatchTheFullProjection() {
        val a = RawPackage("com.example.a", false, 1, null, false)
        val b = raw("com.example.b", true, 1, "keyA")
        val changed = raw("com.example.a", true, 2, "keyB")
        fun port() = FakePackageSystemPort().apply {
            reads = mutableListOf(listOf(a, b, a, changed), listOf(b, changed, raw("com.example.c")))
        }
        val full = LivePackageReader(port()).read()
        val light = LivePackageReader(port()).readInstalled()
        assertEquals(full.installed, light.installed)
        assertEquals(full.disabled, light.disabled)
    }

    @Test fun twoLightSnapshotsBracketFloorWorkWithoutSignersOrProbes() {
        val calls = mutableListOf<String>()
        val port = object : PackageSystemPort by FakePackageSystemPort() {
            override fun packages(): List<RawPackage> = error("must not read signers")
            override fun browsableHandlers(url: String): Set<String> = error("must not probe")
            override fun packageStates(): List<PackageState> {
                calls += "snapshot"
                return listOf(PackageState("com.example.notes", false, 1, false))
            }
        }
        val timing = ReapplyTimings { 0L }
        val light = LivePackageReader(port, timing).readInstalled { calls += "floor" }
        assertEquals(listOf("snapshot", "floor", "snapshot"), calls)
        assertEquals(setOf(org.plainphone.core.PackageName("com.example.notes")), light.disabled)
        assertEquals("livePackages.snapshot1=0,livePackages.snapshot2=0,livePackages.merge=0", timing.summary())
    }

    @Test fun failedSecondSnapshotCannotReturnPartialFacts() {
        var reads = 0
        val port = object : PackageSystemPort by FakePackageSystemPort() {
            override fun packageStates(): List<PackageState> {
                check(++reads == 1) { "second snapshot failed" }
                return emptyList()
            }
        }
        assertThrows(IllegalStateException::class.java) { LivePackageReader(port).readInstalled() }
        assertEquals(2, reads)
    }
}
