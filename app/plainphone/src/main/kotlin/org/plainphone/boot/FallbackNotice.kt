// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.boot.Containment
import org.plainphone.policy.LockTaskFeature

/**
 * What the attention screen's details say while fallback containment holds (PLA-344). Settings and the
 * permission controller are always named by the board-approved disclosure (card `da306a1a`):
 * "Until this is fixed, Settings and the app-permissions screen can still be opened."
 * @property others labels of any other package Android refused to suspend, which need naming.
 * @property incomplete the record could not be read or saved, so other apps were not suspended.
 */
data class FallbackNotice(val others: List<String>, val incomplete: Boolean)

/** [disclosed] are the packages the disclosure already covers; [label] names a package for people. */
fun fallbackNotice(result: Containment, disclosed: Set<PackageName>, label: (PackageName) -> String): FallbackNotice =
    FallbackNotice((result.refused - disclosed).map(label).distinct().sorted(), !result.recorded)

/** The permission controller's names on AOSP and Google builds: covered by the disclosure. */
val PERMISSION_CONTROLLERS = setOf(PackageName("com.android.permissioncontroller"), PackageName("com.google.android.permissioncontroller"))

/**
 * Packages fallback containment never suspends, whatever the plan says: the calling, emergency and
 * keyguard stack read live (PLA-311 spike kept SystemUI, the keyboard and com.android.emergency too).
 */
val PLATFORM_KEEP = setOf(
    PackageName("android"), PackageName("com.android.systemui"), PackageName("com.android.phone"),
    PackageName("com.android.server.telecom"), PackageName("com.android.emergency"),
)

/**
 * Debug builds only: drops lock-task feature writes so they never read back, the PLA-307 finding 1
 * state (TestHook `FEATURE_FAULT`). [fault] is false in release builds.
 */
internal class FeatureFaultPort(private val inner: DevicePolicyPort, private val fault: () -> Boolean) : DevicePolicyPort by inner {
    override fun setLockTaskFeatures(features: Set<LockTaskFeature>) {
        if (!fault()) inner.setLockTaskFeatures(features)
    }
}
