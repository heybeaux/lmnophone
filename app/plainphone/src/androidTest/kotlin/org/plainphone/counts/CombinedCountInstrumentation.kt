// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import android.app.Instrumentation
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony

/** Owned-emulator fixture oracle. Reads count only, under the target app's own UID/grant. */
class CombinedCountInstrumentation : Instrumentation() {
    private var expected = 0
    private var expectedSms = 0
    private var expectedMms = 0
    private var fixtureIds = ""
    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        expected = arguments.getString("expected")!!.toInt()
        expectedSms = arguments.getString("expectedSms")!!.toInt()
        expectedMms = arguments.getString("expectedMms")!!.toInt()
        fixtureIds = arguments.getString("fixtureIds")!!
        require(fixtureIds.matches(Regex("[0-9]+(,[0-9]+)*")))
        start()
    }
    override fun onStart() {
        val result = Bundle()
        try {
            check(targetContext.checkSelfPermission("android.permission.READ_SMS") == PackageManager.PERMISSION_GRANTED)
            check(Telephony.Sms.getDefaultSmsPackage(targetContext) != targetContext.packageName)
            fun count(uri: String, projection: Array<String>, selection: String): Int =
                targetContext.contentResolver.query(Uri.parse(uri), projection, selection, null, null)!!.use { it.count }
            val combined = count("content://mms-sms/complete-conversations", arrayOf("_id", "transport_type"), "read = 0")
            val sms = count("content://sms", arrayOf("_id"), "type = 1 AND read = 0")
            val mms = count("content://mms", arrayOf("_id"), "msg_box = 1 AND read = 0")
            val focus = " AND _id IN ($fixtureIds)"
            val focusSms = count("content://sms", arrayOf("_id"), "type = 1 AND read = 0$focus")
            val focusMms = count("content://mms", arrayOf("_id"), "msg_box = 1 AND read = 0$focus")
            result.putString("fixture", "sms=$focusSms mms=$focusMms expectedSms=$expectedSms expectedMms=$expectedMms")
            check(focusSms == expectedSms && focusMms == expectedMms)
            result.putString("counts", "combined=$combined oracleSms=$sms oracleMms=$mms expected=$expected nonDefault=true")
            check(combined == sms + mms && combined == expected)
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("outcome", "FAIL ${e.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
