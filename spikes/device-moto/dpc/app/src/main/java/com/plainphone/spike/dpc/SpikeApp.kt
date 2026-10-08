// THROWAWAY SPIKE (PLA-5).
package com.plainphone.spike.dpc

import android.app.Application

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugTimer.check(this)
    }
}
