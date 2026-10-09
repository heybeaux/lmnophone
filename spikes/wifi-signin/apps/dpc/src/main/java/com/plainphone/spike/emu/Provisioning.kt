// THROWAWAY SPIKE (PLA-11, reused by PLA-18). Minimal QR-provisioning activities required on Android 12+.
package com.plainphone.spike.emu

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Emu.log("GET_PROVISIONING_MODE extras=${intent.extras?.keySet()}")
        setResult(RESULT_OK, Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE))
        finish()
    }
}

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Emu.log("ADMIN_POLICY_COMPLIANCE deviceOwner=${Emu.isDeviceOwner(this)}")
        setResult(RESULT_OK)
        finish()
    }
}
