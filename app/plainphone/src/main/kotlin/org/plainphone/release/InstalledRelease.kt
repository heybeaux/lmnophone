// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.release

import android.content.Context
import org.plainphone.ui.user.AboutUiState
import org.plainphone.ui.user.ReleaseNoteUi

/** Device-protected preferences alongside Home preferences; never clears unrelated values. */
object InstalledRelease {
    private var observed: ObservedVersions? = null
    private var versionName = ""
    @Synchronized fun observe(context: Context) {
        if (observed != null) return
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        versionName = info.versionName.orEmpty()
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("home_preferences", Context.MODE_PRIVATE)
        observed = VersionTracker(object : VersionStore {
            override fun read(): ObservedVersions? = if (!prefs.contains("release_current")) null else
                ObservedVersions(prefs.getLong("release_current", 0),
                    if (prefs.contains("release_previous")) prefs.getLong("release_previous", 0) else null)
            override fun commit(value: ObservedVersions): Boolean {
                val editor = prefs.edit().putLong("release_current", value.current)
                if (value.previous == null) editor.remove("release_previous")
                else editor.putLong("release_previous", value.previous)
                return editor.commit()
            }
        }).observe(info.longVersionCode)
    }
    fun about(isDeviceSecure: Boolean): AboutUiState {
        val version = checkNotNull(observed)
        return releaseAbout(versionName, version, isDeviceSecure)
    }
}
