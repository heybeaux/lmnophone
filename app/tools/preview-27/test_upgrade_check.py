# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('preview27', Path(__file__).with_name('upgrade_check.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

class VersionTest(unittest.TestCase):
    def test_requires_exact_preview_27_and_retained_recovery_state(self):
        subject = object.__new__(gate.Gate)
        for code, name, flags, locked, valid in [
            (27, '0.1.0-preview-27', 'TEST_ONLY', 'LOCKED', True),
            (26, '0.1.0-preview-26', 'TEST_ONLY', 'LOCKED', False),
            (27, '0.1.0-preview-27', '', 'LOCKED', False),
            (27, '0.1.0-preview-27', 'TEST_ONLY', 'PINNED', False),
        ]:
            def sh(check, *args):
                if args[:2] == ('dumpsys', 'package'): return f'versionCode={code} minSdk=30\nversionName={name}\n{flags}'
                if args[0] == 'dpm': return 'org.plainphone/.PlainphoneAdminReceiver DeviceOwner'
                return f'mLockTaskModeState={locked}'
            subject.sh = sh
            if valid: subject.readback()
            else:
                with self.assertRaises(gate.GateFailure): subject.readback()

if __name__ == '__main__': unittest.main()
