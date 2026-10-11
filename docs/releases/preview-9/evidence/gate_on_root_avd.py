# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Run the unchanged preview gate; restore AVD adb root after its reboot.

This API 35 AVD returns EFAULT for shell-user /sdcard writes. Root can write
there. Only the host driver changes UID; the app stays unprivileged. No gate
assertion is replaced or skipped. Never use this on a physical device.
"""
import sys
# Supplied module directory avoids a host-specific path in the shared harness.
sys.path.insert(0,sys.argv.pop(1))
import upgrade_check as gate
original_restart=gate.Gate.restart

def restart(self):
    gate.require(self.serial == 'emulator-5880', 'owned profile emulator only')
    original_restart(self)
    self.adb('restore root host driver for AVD sdcard EFAULT', 'root')
    self.adb('wait for root host driver', 'wait-for-device')
    gate.require(self.sh('host driver uid', 'id', '-u') == '0', 'root host driver; app UID unchanged')

gate.Gate.restart=restart
raise SystemExit(gate.main())
