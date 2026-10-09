// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlainphoneTheme {
                HomeScreen(state = HomeUiState(), onEvent = {})
            }
        }
    }
}
