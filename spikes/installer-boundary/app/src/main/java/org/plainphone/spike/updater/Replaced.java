// THROWAWAY SPIKE (PLA-86). Not MVP code.
package org.plainphone.spike.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Replaced extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { Log.w(c, "MY_PACKAGE_REPLACED"); }
}
