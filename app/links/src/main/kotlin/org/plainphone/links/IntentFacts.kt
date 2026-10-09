// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName

/** One activity that `queryIntentActivities` returned for the intent as dispatched. */
data class Resolver(val packageName: PackageName, val verifiedForHost: Boolean = false)

/** Plain facts about one link or intent being opened. Lane D fills them from the Intent. */
data class IntentFacts(
    val action: String,
    val scheme: String?,
    val host: String?,
    val resolvers: List<Resolver>,
)
