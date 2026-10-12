# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('upgrade_check', Path(__file__).with_name('upgrade_check.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class GateTest(unittest.TestCase):
    def test_digits_exactly_one_node(self):
        node = '<node resource-id="com.google.android.dialer:id/digits" text="911" />'
        self.assertEqual(gate.dialtext('<hierarchy>'+node+'</hierarchy>'), '911')
        self.assertEqual(gate.dialtext('<hierarchy>'+node.replace('911','')+'</hierarchy>'), '')
        for xml in ['<hierarchy/>', '<hierarchy>'+node+node+'</hierarchy>', 'bad XML']:
            with self.assertRaises(gate.GateFailure): gate.dialtext(xml)

    def test_exact_digits_do_not_normalize_duplicate_prefill(self):
        with self.assertRaises(gate.GateFailure): gate.exact('911-911', '911', 'digits')
        with self.assertRaises(gate.GateFailure): gate.exact('\n911', '911', 'digits')

    def test_empty_dial_field_has_known_google_phone_hint(self):
        gate.empty_field('')
        gate.empty_field('Phone Number')
        for value in ['911', '911-911', 'Phone Number 911', 'unknown hint']:
            with self.assertRaises(gate.GateFailure): gate.empty_field(value)

    def test_current_calls_only(self):
        empty = '  mCalls:\n  mCallAudioManager:\n    All calls:\n  Historical calls:\n    old call\n'
        gate.no_current_calls(empty)
        for text in ['', 'mCalls: active\n', '  mCalls:\n    Call active\n  mCallAudioManager:\n', empty+empty]:
            with self.assertRaises(gate.GateFailure): gate.no_current_calls(text)

    def test_safe_tap_selector(self):
        call = '<node text="Call" content-desc="" resource-id="dialpad_fab" bounds="[0,0][100,100]" />'
        with self.assertRaises(gate.GateFailure): gate.tap_bounds('<hierarchy>'+call+'</hierarchy>', 'dialpad_fab')
        with self.assertRaises(gate.GateFailure): gate.tap_bounds('<hierarchy/>', 'More')

    def test_serial_and_negative_control_guards(self):
        gate.check_target('emulator-5860', {})
        fake = 'ZZ00FAKE0000'
        spare = {'PLAINPHONE_SPARE_SERIAL':fake, 'PLAINPHONE_SPARE_DRIVER':'1'}
        for serial in ['emulator-5590', 'emulator-5604', 'emulator-5742', 'unknown', fake]:
            with self.assertRaises(gate.GateFailure): gate.check_target(serial, {})
        gate.check_target(fake, spare)
        # Driver opt-in alone, or a serial set but not matching, still refuses a physical serial.
        with self.assertRaises(gate.GateFailure): gate.check_target(fake, {'PLAINPHONE_SPARE_DRIVER':'1'})
        with self.assertRaises(gate.GateFailure): gate.check_target(fake, {'PLAINPHONE_SPARE_SERIAL':fake})
        with self.assertRaises(gate.GateFailure): gate.check_target('ZZ00FAKE0001', spare)
        with self.assertRaises(gate.GateFailure): gate.check_target('emulator-5590', dict(spare, PLAINPHONE_SPARE_SERIAL='emulator-5590'))
        with self.assertRaises(gate.GateFailure): gate.check_target(fake, dict(spare, EXPECT_DIAL='912'))
        with self.assertRaises(gate.GateFailure): gate.check_target('emulator-5860', {'EXPECT_DIAL':'912'})
        gate.check_target('emulator-5860', {'EXPECT_DIAL':'912', 'PLAINPHONE_CHECK_SELF_TEST':'1'})

    def test_failed_adb_survives_tee(self):
        with tempfile.TemporaryDirectory(dir=os.environ['PAPERCLIP_SCRATCH_DIR']) as td:
            root=Path(td); adb=root/'adb'; adb.write_text('#!/bin/sh\nexit 7\n'); adb.chmod(0o755)
            env=dict(os.environ, PATH=td+':'+os.environ['PATH'], SKIP_INSTALL='1')
            p=subprocess.run(['bash',str(Path(__file__).with_name('upgrade-check.sh')),'emulator-5860','unused.apk',str(root/'evidence')],env=env,capture_output=True,text=True)
            self.assertNotEqual(p.returncode,0)
            self.assertIn('FAIL:',p.stdout)
            self.assertIn('exit=1',p.stdout)
            self.assertIn('FAIL:',(root/'evidence/upgrade-check.txt').read_text())


if __name__ == '__main__': unittest.main()
