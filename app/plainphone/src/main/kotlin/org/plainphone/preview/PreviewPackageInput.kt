// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.plainphone.device.ReapplyTimings
import org.plainphone.device.facts.FloorIdentityReader
import org.plainphone.device.facts.FloorRead
import org.plainphone.device.facts.LivePackageReader
import org.plainphone.device.facts.PackageSystemPort
import org.plainphone.policy.InstalledPackage

/** Live inputs shared by locked and unlocked preview plans. Disabled floor candidates still get repaired. */
internal data class PreviewPackageInput(val installed: List<InstalledPackage>, val floor: FloorRead) {
    companion object {
        fun read(port: PackageSystemPort, timings: ReapplyTimings): PreviewPackageInput {
            lateinit var floor: FloorRead
            val read = timings.measure("livePackages") {
                LivePackageReader(port, timings).readInstalled {
                    floor = timings.measure("floorIdentities") { FloorIdentityReader(port).read() }
                }
            }
            val floorNames = floor.identities.map { it.name }.toSet()
            return PreviewPackageInput(read.installed.filter { it.name !in read.disabled || it.name in floorNames }, floor)
        }
    }
}
