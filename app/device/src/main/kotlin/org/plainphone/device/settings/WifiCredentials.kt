// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

/** Android removes the outer quotes; embedded characters are literal bytes. */
internal fun quoteWifiValue(value: String) = "\"" + value + "\""
