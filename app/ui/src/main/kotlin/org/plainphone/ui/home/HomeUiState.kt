// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.home

// Stubs from the D0 skeleton. Lane B owns these types.

data class HomeUiState(val apps: List<String> = emptyList())

sealed interface HomeEvent
