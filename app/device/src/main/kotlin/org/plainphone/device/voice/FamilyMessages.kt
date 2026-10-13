// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

import android.content.Intent
import android.net.Uri

/** User-tapped conversation handoff. No text body, attachment, chooser, new task or automatic send. */
object FamilyMessages {
    fun intent(person: FamilyPerson) = Intent(Intent.ACTION_SENDTO,
        Uri.fromParts("smsto", person.number, null)).setPackage("com.google.android.apps.messaging")
}
