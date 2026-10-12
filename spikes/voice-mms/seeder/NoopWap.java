// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458): role-eligibility stubs only.
package org.plainphone.spike458;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NoopWap extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { Log.i("seeder Noop got " + i.getAction()); }
}
