// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.PackageName
import org.plainphone.core.WindowKind

/** The authorized preview relaxation. The baseline remains the existing Preview2Plan. */
object PreviewPlayPlan {
    private val PLAY = PackageName("com.android.vending")
    fun open(base: PolicyPlan, access: SessionAccess): PolicyPlan {
        require(access.kind == WindowKind.PLAY && access.packages == setOf(PLAY))
        require(PLAY !in base.neverHide && PLAY !in base.emergencyUi && PLAY !in base.lockTaskPackages)
        return base.copy(hidden = base.hidden - PLAY, unhide = base.unhide + PLAY,
            lockTaskPackages = base.lockTaskPackages + PLAY,
            expected = base.expected.copy(hidden = base.expected.hidden?.minus(PLAY),
                unhiddenAndEnabled = base.expected.unhiddenAndEnabled + PLAY,
                unsuspended = base.expected.unsuspended + PLAY,
                lockTaskPackages = base.expected.lockTaskPackages?.plus(PLAY)))
    }
}
