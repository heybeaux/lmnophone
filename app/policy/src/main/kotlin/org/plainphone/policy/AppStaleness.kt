// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.time.Duration
import java.time.Instant
import org.plainphone.core.PackageName

/** Verified package facts, not a prediction that a newer version exists. Persist one per approved app. */
data class AppVersionRecord(
    val packageName: PackageName,
    val versionCode: Long,
    val lastUpdated: Instant,
    val unchangedSince: Instant,
) {
    init { require(versionCode >= 0) }
}

/** Caller supplies calendar instants; these advisory dates never grant policy/session authority. */
object AppStaleness {
    fun observe(previous: AppVersionRecord?, packageName: PackageName, versionCode: Long,
        lastUpdated: Instant, observedAt: Instant): AppVersionRecord {
        val unchanged = previous?.packageName == packageName && previous.versionCode == versionCode && previous.lastUpdated == lastUpdated
        return AppVersionRecord(packageName, versionCode, lastUpdated,
            if (unchanged) previous!!.unchangedSince else observedAt)
    }

    fun playServicesNote(record: AppVersionRecord?, now: Instant): String? {
        if (record == null || record.packageName.value != "com.google.android.gms" ||
            Duration.between(record.unchangedSince, now) < Duration.ofDays(30)) return null
        return "Google Play services has not changed in 30 days."
    }
}
