// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

internal fun quoteWifiValue(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
