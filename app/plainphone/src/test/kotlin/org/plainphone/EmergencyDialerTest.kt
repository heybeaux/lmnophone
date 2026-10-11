// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyDialerTest {
    @Test fun missingEmergencyActivityFallsBackTo911DialIntent() {
        val attempts = mutableListOf<Pair<String, String?>>()

        openEmergencyDialer { action, uri ->
            attempts += action to uri
            action == "android.intent.action.DIAL"
        }

        assertEquals(listOf(
            "com.android.phone.EmergencyDialer.DIAL" to null,
            "android.intent.action.DIAL" to "tel:911",
        ), attempts)
    }

    @Test fun availableEmergencyActivityDoesNotOpenFallback() {
        val attempts = mutableListOf<Pair<String, String?>>()

        openEmergencyDialer { action, uri ->
            attempts += action to uri
            true
        }

        assertEquals(listOf("com.android.phone.EmergencyDialer.DIAL" to null), attempts)
    }

    @Test fun bothActivitiesMissingStopsAfterFallbackAttempt() {
        val attempts = mutableListOf<Pair<String, String?>>()

        openEmergencyDialer { action, uri ->
            attempts += action to uri
            false
        }

        assertEquals(listOf(
            "com.android.phone.EmergencyDialer.DIAL" to null,
            "android.intent.action.DIAL" to "tel:911",
        ), attempts)
    }
}
