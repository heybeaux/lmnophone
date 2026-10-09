// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.admin.DeviceAdminReceiver

/**
 * Device Owner receiver. On a fresh emulator:
 * `adb shell dpm set-device-owner org.plainphone/.PlainphoneAdminReceiver`
 */
class PlainphoneAdminReceiver : DeviceAdminReceiver()
