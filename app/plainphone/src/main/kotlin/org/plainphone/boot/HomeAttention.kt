// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.device.boot.BootOutcome
import org.plainphone.ui.home.HomeNotice

/**
 * The Home notice for a re-apply outcome: "Plainphone needs attention" for corrupt policy (baseline),
 * failed flips, unrepairable or disabled floor packages, or any other attention flag. It never decides
 * whether Home shows; calls stay reachable.
 */
fun homeNotice(outcome: BootOutcome?): HomeNotice? = if (outcome?.needsAttention != false) HomeNotice.NeedsAttention else null
