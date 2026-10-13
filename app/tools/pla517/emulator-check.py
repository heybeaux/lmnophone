# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""T4/T5 on the private emulator after setup and synthetic 111111 PIN enrollment. No calls.
Usage: python3 emulator-check.py emulator-NNNN
Install build-fixture.sh's Calculator fixture and seed Phone/Messages/Camera/fixture first.
The fixture is a blank Android launcher activity, not a real calculator product.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

serial = sys.argv[1]
assert serial == 'emulator-5780', 'owned private emulator only'
adb_path = os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
fixture = 'org.plainphone.t5calculator'
app = 'org.plainphone'

def adb(*args):
    return subprocess.check_output([adb_path, '-s', serial, *args], text=True, stderr=subprocess.STDOUT).strip()

def wait(read, description, seconds=45):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            result = read()
            if result:
                return result
        except (subprocess.CalledProcessError, ET.ParseError):
            pass
        time.sleep(.4)
    raise AssertionError('timed out: ' + description)

def nodes():
    adb('shell', 'uiautomator', 'dump', '/data/local/tmp/t5-ui.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/data/local/tmp/t5-ui.xml')).iter('node'))

def find(label):
    return next((n for n in nodes() if n.get('text') == label or n.get('content-desc') == label), None)

def present(label):
    return find(label) is not None

def tap_node(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def tap(label):
    # ElementTree leaf nodes are falsey, so test explicitly for None.
    wait(lambda: present(label), label)
    tap_node(find(label))
    time.sleep(.4)

def pin(value):
    wait(lambda: any(n.get('password') == 'true' for n in nodes()), 'PIN field')
    tap_node(next(n for n in nodes() if n.get('password') == 'true'))
    time.sleep(.4)
    adb('shell', 'input', 'text', value)
    time.sleep(.4)
    adb('shell', 'input', 'keyevent', 'BACK')  # dismiss only the IME
    tap('Continue')

def starter():
    return adb('shell', 'run-as', app, 'cat', 'files/starter-list.txt').splitlines()

def home_order():
    named = [n for n in nodes() if n.get('text') in ('Calculator', 'Camera', 'Messages', 'Phone')]
    named.sort(key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1]))
    return [n.get('text') for n in named]

def controls():
    tap('More'); tap('For Sam and Jo'); pin('111111'); scroll_tap('Apps on this phone')
    wait(lambda: present('Calculator'), 'apps loaded')

def home():
    adb('shell', 'input', 'keyevent', 'HOME')
    wait(lambda: present('More'), 'Home')

def hidden(package):
    text = adb('shell', 'dumpsys', 'package', package)
    return bool(re.search(r'User 0:.*hidden=true', text))


def scroll_tap(label):
    for _ in range(7):
        if present(label):
            tap(label)
            return
        adb('shell', 'input', 'swipe', '180', '490', '180', '200', '300')
    raise AssertionError('not reachable: ' + label)

original = starter()
assert fixture in original and 'com.android.camera2' in original
adb('shell', f"run-as {fixture} sh -c 'mkdir -p files; echo synthetic-data > files/t4-marker'")
home(); controls(); tap('Calculator')
assert present('Go back')
tap("Remove from Alex's phone")
assert present('Go back')
tap('Remove'); scroll_tap('Forgot PIN? Use recovery code')
wait(lambda: present("Not in this test build yet"), 'recovery explanation')
tap('OK'); tap('Calculator'); tap("Remove from Alex's phone"); tap('Remove'); pin('111111')
wait(lambda: present('Removed.'), 'removal readback')
assert fixture not in starter() and hidden(fixture)
tap('OK'); scroll_tap('Add a hidden app')
wait(lambda: present('Calculator'), 'hidden Calculator')
labels = set()
for _ in range(7):
    labels.update(n.get('text') for n in nodes())
    adb('shell', 'input', 'swipe', '180', '490', '180', '200', '300')
assert not labels.intersection({'Chrome', 'Play Store', 'Google Play Store', 'Settings'})
for _ in range(7):
    adb('shell', 'input', 'swipe', '180', '200', '180', '490', '300')
print('PASS removed Calculator is offered; browser/Play Store/Settings absent', flush=True)
tap('Calculator')
wait(lambda: present('Not reviewed yet – this app may open web pages inside it'), 'unreviewed disclosure')
scroll_tap('Next'); scroll_tap('Add anyway'); scroll_tap('Add Calculator')
pin('222222'); wait(lambda: present("That PIN isn't right. Try again."), 'wrong add PIN')
assert fixture not in starter() and hidden(fixture)
adb('shell', 'input', 'keyevent', 'BACK')
wait(lambda: present('Add Calculator'), 'Back to add confirm')
assert fixture not in starter()
print('PASS fresh wrong PIN and Back leave Calculator hidden and starter unchanged', flush=True)
scroll_tap('Add Calculator'); pin('111111')
wait(lambda: present('Done. Calculator is on the home screen.'), 'ready-only Done')
assert fixture in starter() and not hidden(fixture)
assert adb('shell', 'run-as', app, 'cat', 'files/app-order.txt').splitlines()[-1] == fixture
assert adb('shell', 'run-as', fixture, 'cat', 'files/t4-marker') == 'synthetic-data'
home(); assert home_order() == ['Camera', 'Calculator', 'Messages', 'Phone']
print('PASS Add via production screens, readback, appended Home order and app-private marker', flush=True)
adb('reboot'); time.sleep(3)
wait(lambda: adb('shell', 'getprop', 'sys.boot_completed') == '1', 'reboot', 120)
adb('shell', 'input', 'keyevent', 'WAKEUP'); adb('shell', 'wm', 'dismiss-keyguard')
home(); assert home_order() == ['Camera', 'Calculator', 'Messages', 'Phone']
assert adb('shell', 'run-as', fixture, 'cat', 'files/t4-marker') == 'synthetic-data'
assert not hidden('com.google.android.dialer') and not hidden('com.google.android.apps.messaging')
print('PASS guest reboot preserves added app, order, marker and Phone/Messages visibility. No calls or sends.', flush=True)
# Restore the seeded visible order; shutting down the read-only AVD discards all run changes.
controls(); tap('Move Calculator up'); home()
assert home_order() == ['Calculator', 'Camera', 'Messages', 'Phone']
adb('shell', 'run-as', fixture, 'rm', 'files/t4-marker')
print('PASS original visible order restored; AVD run is read-only', flush=True)
