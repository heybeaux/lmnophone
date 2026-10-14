// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Android announces changed semantics, not every Compose recomposition. */
internal fun Modifier.statusAnnouncement(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
