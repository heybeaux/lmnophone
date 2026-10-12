// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458).
package org.plainphone.spike458;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** sentIntent target for SmsManager.sendMultimediaMessage: logs the result code and every extra. */
public class Sent extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        StringBuilder sb = new StringBuilder("sentIntent resultCode=" + getResultCode());
        Bundle b = intent.getExtras();
        if (b != null) {
            for (String k : b.keySet()) {
                Object v = b.get(k);
                sb.append(' ').append(k).append('=');
                sb.append(v instanceof byte[] ? ("byte[" + ((byte[]) v).length + "]") : String.valueOf(v));
            }
        }
        Log.i(sb.toString());
        Probe.dumpMms(context, "after-sentIntent");
    }
}
