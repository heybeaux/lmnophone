// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.plainphone.ui.theme.PlainphoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lane D1 wires HomeScreen here through a view-model once lane B's HomeUiState lands.
        setContent {
            PlainphoneTheme {}
        }
    }
}
