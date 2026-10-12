# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Regression for MainActivity's PIN-free cancel route on three-button navigation.

Install the app and its test APK built with
-PpreviewTestRunner=org.plainphone.WaitingSettingsInstrumentation first.
Usage: python3 tools/tests/waiting-insets.py emulator-5880 <evidence-directory>
Uses the existing synthetic pending-change fixture; restores authority/clock and
navigation mode even on failure. Never opens a dialer or sends a text.
"""
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

serial, destination = sys.argv[1:]
assert serial == 'emulator-5880', 'Only the policy engineer retained emulator'
out = Path(destination)
out.mkdir(parents=True, exist_ok=True)
records = []

def adb(*args):
    return subprocess.check_output(['adb', '-s', serial, *args], timeout=60)

def shell(*args):
    return adb('shell', *args).decode().strip()

def dump():
    shell('uiautomator', 'dump', '/data/local/tmp/waiting-insets.xml')
    return ET.fromstring(shell('cat', '/data/local/tmp/waiting-insets.xml'))

def node(label):
    matches = [n for n in dump().iter('node')
               if label in (n.get('text'), n.get('content-desc'))]
    assert len(matches) == 1, f'Expected one {label!r}, found {len(matches)}'
    return matches[0]

def bounds(n):
    return list(map(int, re.findall(r'\d+', n.get('bounds'))))

def tap(label):
    assert label in ('More', 'Phone settings', 'Yes, cancel it', 'OK',
                    'A change is waiting, turn LMNOPhone (preview) off. Opens the change.')
    x, y, right, bottom = bounds(node(label))
    shell('input', 'tap', str((x + right) // 2), str((y + bottom) // 2))
    time.sleep(.5)

def home():
    shell('am', 'start', '-n', 'org.plainphone/.MainActivity')
    time.sleep(2)
    shell('input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(.5)

def fixture(phase):
    result = shell('am', 'instrument', '-w', '-e', 'phase', phase,
                   'org.plainphone.test/org.plainphone.WaitingSettingsInstrumentation')
    (out / f'fixture-{phase}.txt').write_text(result + '\n')
    assert 'outcome=PASS' in result, result

def above_navigation(label):
    button = bounds(node(label))
    # Read the actual WindowManager inset, not a guessed dp-to-pixel conversion.
    frames = set(re.findall(
        r'InsetsSource id=\w+ type=navigationBars frame=(\[\d+,\d+\]\[\d+,\d+\]) visible=true',
        shell('dumpsys', 'window')))
    assert len(frames) == 1, f'Expected one visible navigation inset, got {frames}'
    navigation = list(map(int, re.findall(r'\d+', frames.pop())))
    assert navigation[3] > navigation[1] > 0
    records.append({'button': label, 'bounds': button, 'navigation': navigation})
    (out / f'{len(records)}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    assert button[3] <= navigation[1], f'{label}: bottom {button[3]} overlaps navigation top {navigation[1]}'

assert adb('emu', 'avd', 'name').decode().splitlines()[0] == 'pla363-policy-a35'
assert shell('getprop', 'ro.hardware') in ('ranchu', 'goldfish')
old_mode = shell('settings', 'get', 'secure', 'navigation_mode')
overlays = {'0': 'threebutton', '1': 'twobutton', '2': 'gestural'}
assert old_mode in overlays
seeded = False
try:
    shell('cmd', 'overlay', 'enable-exclusive', '--category',
          'com.android.internal.systemui.navbar.threebutton')
    for _ in range(20):
        if shell('settings', 'get', 'secure', 'navigation_mode') == '0':
            break
        time.sleep(.1)
    assert shell('settings', 'get', 'secure', 'navigation_mode') == '0'
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    fixture('seed')
    seeded = True
    home()
    tap('More')
    tap('Phone settings')
    tap('A change is waiting, turn LMNOPhone (preview) off. Opens the change.')
    node('Cancel this change?')
    assert not any(n.get('password') == 'true' for n in dump().iter('node'))
    above_navigation('Yes, cancel it')
    tap('Yes, cancel it')
    node('Cancelled')
    above_navigation('OK')
    tap('OK')
    node('More')
    fixture('verify')
    records.append({'result': 'PASS: center taps cancel and return Home; durable cancellation'})
finally:
    try:
        if seeded:
            fixture('restore')
    finally:
        shell('cmd', 'overlay', 'enable-exclusive', '--category',
              'com.android.internal.systemui.navbar.' + overlays[old_mode])
        home()
        shell('rm', '-f', '/data/local/tmp/waiting-insets.xml')
        (out / 'result.json').write_text(json.dumps(records, indent=2) + '\n')
print(json.dumps(records, indent=2))
