// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458).
package org.plainphone.spike458;

import android.app.admin.DeviceAdminReceiver;

/** Lets the probe become Device Owner on the emulator so the lock task handoff can be tested. */
public class Admin extends DeviceAdminReceiver {}
