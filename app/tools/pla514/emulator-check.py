# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""T5 on a private emulator after setup and synthetic 111111 PIN enrollment. No calls.
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
assert re.fullmatch(r'emulator-\d+', serial), 'emulator only'
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
    tap('More'); tap('For Sam and Jo'); pin('111111'); tap('Apps on this phone')
    wait(lambda: present('Calculator'), 'apps loaded')

def home():
    adb('shell', 'input', 'keyevent', 'HOME')
    wait(lambda: present('More'), 'Home')

def hidden(package):
    text = adb('shell', 'dumpsys', 'package', package)
    return bool(re.search(r'User 0:.*hidden=true', text))

original = starter()
assert fixture in original and 'com.android.camera2' in original
adb('shell', f"run-as {fixture} sh -c 'mkdir -p files; echo synthetic-data > files/t5-marker'")
home()
assert home_order() == ['Calculator', 'Camera', 'Messages', 'Phone']
print('PASS initial Home order; synthetic data marker saved', flush=True)
controls()
assert home_order() == ['Calculator', 'Camera', 'Messages', 'Phone']
assert sum(n.get('text') == 'Always on' for n in nodes()) == 2
assert find('Move Messages up') is None and find('Move Phone down') is None
tap('Move Calculator down')
wait(lambda: adb('shell', 'run-as', app, 'cat', 'files/app-order.txt').splitlines() == ['com.android.camera2', fixture], 'stored order')
home()
assert home_order() == ['Camera', 'Calculator', 'Messages', 'Phone']
print('PASS list move updates production Home and atomic order file', flush=True)
adb('reboot')
time.sleep(3)
wait(lambda: adb('shell', 'getprop', 'sys.boot_completed') == '1', 'reboot', 120)
adb('shell', 'input', 'keyevent', 'WAKEUP')
adb('shell', 'wm', 'dismiss-keyguard')
home()
assert home_order() == ['Camera', 'Calculator', 'Messages', 'Phone']
print('PASS actual emulator reboot preserves production Home order', flush=True)
controls()
tap('Calculator'); wait(lambda: present('Version 1.0'), 'installed version')
tap("Remove from Alex's phone"); tap('Remove'); pin('222222')
wait(lambda: present("That PIN isn't right. Try again."), 'wrong PIN')
assert starter() == original and not hidden(fixture)
adb('shell', 'input', 'keyevent', 'BACK')
wait(lambda: present('Version 1.0'), 'Back from PIN')
assert starter() == original
print('PASS fresh wrong PIN and Back write nothing', flush=True)
tap("Remove from Alex's phone"); tap('Remove'); pin('111111')
wait(lambda: present('Removed.'), 'removed readback')
assert fixture not in starter() and hidden(fixture)
assert not hidden('com.google.android.dialer') and not hidden('com.google.android.apps.messaging')
home()
assert home_order() == ['Camera', 'Messages', 'Phone']
print('PASS fresh correct PIN removes only Calculator; hidden=true; Phone/Messages remain; Home updated', flush=True)
response = adb('shell', 'am', 'broadcast', '-a', 'org.plainphone.preview.SET_STARTER', '-n', app+'/.preview.SetupCommandReceiver', '--esa', 'packages', ','.join(original))
assert 'result=0' in response
wait(lambda: not hidden(fixture), 'fixture restored')
assert adb('shell', 'run-as', fixture, 'cat', 'files/t5-marker') == 'synthetic-data'
controls(); tap('Move Calculator up'); home()
assert home_order() == ['Calculator', 'Camera', 'Messages', 'Phone']
assert starter() == original
adb('shell', 'run-as', fixture, 'rm', 'files/t5-marker')
print('PASS setup-command restoration; app data retained; original app list and Home order restored. No call or send.', flush=True)
