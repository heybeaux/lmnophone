// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458): role-eligibility stub only.
package org.plainphone.spike458;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class NoopService extends Service {
    @Override public IBinder onBind(Intent i) { return null; }
}
