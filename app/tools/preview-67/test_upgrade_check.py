# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Host-only checks: every device operation is mocked; no spare is contacted."""
import importlib.util
import os
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('preview67_gate', Path(__file__).with_name('upgrade_check.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class GateTest(unittest.TestCase):
    def test_current_gate_with_configured_fake_spare(self):
        env = {'PLAINPHONE_SPARE_SERIAL': 'test-spare-serial',
               'PLAINPHONE_SPARE_DRIVER': '1', 'SKIP_INSTALL': '1'}
        self.assertEqual(gate.Gate.VERSION, 67)
        with tempfile.TemporaryDirectory(dir=os.environ['PAPERCLIP_SCRATCH_DIR']) as out:
            with patch.dict(os.environ, env, clear=True), \
                    patch('sys.argv', ['upgrade_check.py', 'test-spare-serial', 'unused.apk', out]), \
                    patch.object(gate.Gate, 'readback') as readback, \
                    patch.object(gate.Gate, 'restart') as restart, \
                    patch.object(gate.Gate, 'routes') as routes, \
                    patch('subprocess.run', side_effect=AssertionError('device access forbidden')) as adb:
                self.assertEqual(gate.previous.main(), 0)
                readback.assert_called_once_with()
                restart.assert_called_once_with()
                routes.assert_called_once_with('911')
                adb.assert_not_called()

    def test_spare_helpers_fail_before_any_device_command(self):
        tools = Path(__file__).resolve().parents[1]
        for helper in ['preview-29/spare_wifi_check.py', 'preview-30/spare_bluetooth_check.py']:
            for env, message in [({}, 'PLAINPHONE_SPARE_SERIAL'),
                                 ({'PLAINPHONE_SPARE_SERIAL': 'test-spare-serial'}, 'sole-driver')]:
                with self.subTest(helper=helper, env=env), \
                        patch.dict(os.environ, env, clear=True), \
                        patch('subprocess.run', side_effect=AssertionError('device access forbidden')) as adb:
                    with self.assertRaisesRegex(Exception, message):
                        runpy.run_path(str(tools / helper), run_name='__main__')
                    adb.assert_not_called()


if __name__ == '__main__':
    unittest.main()
