# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Run the reviewed PLA-324 scenarios on this task's AVD, with a verified root kill."""
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

assert os.environ.get('ANDROID_SERIAL') == 'emulator-5862'
case = sys.argv[1]
assert case in ('home-phone-open-kill', 'checking-emergency-kill')
root = Path(__file__).resolve().parents[4]
out = Path(__file__).parent / 'emulator' / 'recovery'
out.mkdir(parents=True, exist_ok=True)
sys.argv = [str(root / 'app/device/evidence/pla324/probe324.py'), case, str(out)]
spec = importlib.util.spec_from_file_location('reviewed_probe', sys.argv[0])
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
assert probe.a('emu', 'avd', 'name').splitlines()[0] == 'pla306-policy-a35'
assert 'uid=0(root)' in probe.sh('su', '0', 'id')
kill_result = {}
def root_kill():
    pid = probe.sh('pidof', 'org.plainphone')
    assert re.fullmatch(r'\d+', pid)
    result = subprocess.run(['adb', '-s', probe.SERIAL, 'shell', 'su', '0', 'kill', '-9', pid], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    kill_result.update(old_pid=pid, exit_code=result.returncode, method='su 0 kill -9')
    return pid
probe.kill = root_kill
before, stimulus = probe.CASES[case]
probe.trace(case, before, stimulus)
kill_result['new_pid'] = probe.sh('pidof', 'org.plainphone')
kill_result['after'] = probe.snap()
assert kill_result['new_pid'] != kill_result['old_pid'], 'old process survived kill'
kill_result['process_restarted_during_sample'] = bool(kill_result['new_pid'])
assert 'lockTask=LOCKED' in kill_result['after']
expected = 'com.google.android.dialer/' if case == 'home-phone-open-kill' else 'com.android.phone/.EmergencyDialer'
assert expected in kill_result['after'], kill_result['after']
# Reuse the accepted gate's exact current-call parser, avoiding historical call data in evidence.
spec = importlib.util.spec_from_file_location('upgrade_gate', root / 'app/tools/preview-6/upgrade_check.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
gate.no_current_calls(probe.sh('dumpsys', 'telecom'))
kill_result['current_calls'] = 0
(out / (case + '-result.json')).write_text(json.dumps(kill_result, indent=2) + '\n')
print(json.dumps(kill_result))
