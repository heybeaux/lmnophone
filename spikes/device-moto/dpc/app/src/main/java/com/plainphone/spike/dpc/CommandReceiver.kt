// THROWAWAY SPIKE (PLA-5). adb test hook:
//   adb -s SPARE_DEVICE_SERIAL shell am broadcast -n com.plainphone.spike.dpc/.CommandReceiver \
//       -a com.plainphone.spike.CMD --es cmd "status"
// Protected by android.permission.DUMP, which the adb shell holds and normal apps cannot get.
package com.plainphone.spike.dpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = intent.getStringExtra("cmd") ?: "status"
        val result = try {
            Commands.run(context, line)
        } catch (t: Throwable) {
            "ERROR ${t.javaClass.simpleName}: ${t.message}"
        }
        Spike.log("cmd [$line] -> $result")
        setResultCode(1)
        setResultData(result)
    }
}
