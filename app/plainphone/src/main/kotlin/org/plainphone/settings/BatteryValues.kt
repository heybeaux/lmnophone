// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

fun batteryPercent(level: Int, scale: Int): Int? =
    if (scale <= 0 || level !in 0..scale) null else (level.toLong() * 100 / scale).toInt()
