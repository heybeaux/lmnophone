// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.Application
import org.plainphone.boot.PackageEvents
import org.plainphone.boot.PolicyRuntime
import org.plainphone.boot.ReapplyAlarm

/** Every Plainphone start re-applies the policy, before or after the first unlock. */
class PlainphoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PackageEvents.register(this)
        ReapplyAlarm.schedule(this)
        PolicyRuntime.get(this).reapply("start")
    }
}
