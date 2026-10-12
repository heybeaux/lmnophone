# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Throwaway API 35 probe; only the assigned emulator; never records names."""
import subprocess, time
from pathlib import Path

ADB = '/Users/beauxwalton/Library/Android/sdk/platform-tools/adb'
def run(*args):
    return subprocess.check_output([ADB, '-s', 'emulator-5880', *args], text=True, timeout=30).strip()
assert 'pla363-policy-a35' in run('emu', 'avd', 'name')
before = run('shell', 'settings', 'get', 'secure', 'location_mode')
networks = run('shell', 'cmd', 'wifi', 'list-networks')
assert before == '3'
print(run('install', '-r', '-t', 'app/plainphone/build/outputs/apk/androidTest/debug/plainphone-debug-androidTest.apk'), flush=True)
lines = ['Foreground probe: API 35, Device Owner, fine location granted; no names recorded.']
try:
    for mode in ('3', '0', '3'):
        run('shell', 'settings', 'put', 'secure', 'location_mode', mode)
        run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        run('shell', 'wm', 'dismiss-keyguard')
        p = subprocess.Popen([ADB, '-s', 'emulator-5880', 'shell', 'am', 'instrument', '-w',
            'org.plainphone.test/org.plainphone.settings.WifiLocationProbe'], stdout=subprocess.PIPE, text=True)
        try:
            time.sleep(0.8)
            run('shell', 'am', 'start', '-n', 'org.plainphone/.MainActivity')
            out, _ = p.communicate(timeout=20)
        finally:
            if p.poll() is None:
                p.kill(); p.communicate()
                run('shell', 'am', 'force-stop', 'org.plainphone')
        lines.append(out.strip()); print(out, flush=True)
        assert p.returncode == 0 and 'outcome=PASS' in out
finally:
    run('shell', 'settings', 'put', 'secure', 'location_mode', '3')
    assert run('shell', 'settings', 'get', 'secure', 'location_mode') == before
    assert run('shell', 'cmd', 'wifi', 'list-networks') == networks
    lines.append('Location before=3 probe=0 after=3; saved networks unchanged=true (names withheld)')
    Path('app/tools/preview-29/evidence/location-probe.txt').write_text('\n'.join(lines)+'\n')
    print(lines[-1], flush=True)
