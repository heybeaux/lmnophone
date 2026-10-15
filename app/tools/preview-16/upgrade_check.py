# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Preview upgrade gate. Only typed digits: never a call intent, key or button."""
import datetime
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


class GateFailure(Exception):
    pass


def passed(check):
    print('PASS: '+check, flush=True)


def require(ok, check):
    if not ok:
        raise GateFailure(check)
    passed(check)


def exact(actual, expected, check):
    require(actual == expected, check)


def nodes(xml):
    try:
        return list(ET.fromstring(xml).iter('node'))
    except ET.ParseError as error:
        raise GateFailure('parse UI XML') from error


def dialtext(xml):
    matches = [n for n in nodes(xml) if n.get('resource-id') == 'com.google.android.dialer:id/digits']
    require(len(matches) == 1 and 'text' in matches[0].attrib, 'read exactly one dial field')
    return matches[0].get('text')


def empty_field(value):
    # Google Phone's numeric EditText exposes its empty hint as text in UIAutomator.
    # Keep dialtext() raw; accept only the observed English empty hint here, never
    # during the exact 911 assertions. An unfamiliar/localized hint fails closed.
    require(value in ('', 'Phone Number'), 'dial field empty (raw text='+repr(value)+')')


def no_current_calls(text):
    # Telecom's current CallsManager mCalls section; never the historical call log.
    lines = text.splitlines()
    headers = [(i, re.fullmatch(r'(\s*)mCalls:\s*(.*)', line)) for i, line in enumerate(lines)]
    headers = [(i, m) for i, m in headers if m]
    require(len(headers) == 1, 'read exactly one Telecom current-call section')
    index, match = headers[0]
    require(not match[2], 'Telecom current-call header is empty')
    indent = len(match[1])
    body = []
    terminated = False
    for line in lines[index+1:]:
        if not line.strip():
            continue
        if len(line)-len(line.lstrip()) <= indent:
            terminated = True
            break
        body.append(line)
    require(terminated, 'read complete Telecom current-call section')
    require(not body, 'Telecom has zero current calls')


def tap_bounds(xml, pattern):
    want = re.compile(pattern)
    for node in nodes(xml):
        values = [node.get(k, '') for k in ('text', 'content-desc', 'resource-id')]
        # Retain the call-control exclusion, including a bare Dial action.
        if any(re.search(r'call|^dial$', v, re.I) for v in values):
            continue
        if any(want.search(v) for v in values):
            match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
            require(match is not None, 'read tap bounds')
            x, y, xx, yy = map(int, match.groups())
            require(xx > x and yy > y, 'tap target has positive bounds')
            return str((x+xx)//2), str((y+yy)//2)
    raise GateFailure('find non-call tap target '+pattern)


def check_target(serial, env):
    # Only the sole driver supplies the approved spare's serial; never store it here.
    spare = env.get('PLAINPHONE_SPARE_SERIAL', '')
    if not serial.startswith('emulator-'):
        require(bool(spare), 'set PLAINPHONE_SPARE_SERIAL for the authorised spare')
        require(serial == spare, 'authorised spare serial')
        require(env.get('PLAINPHONE_SPARE_DRIVER') == '1', 'spare requires sole-driver opt-in')
        require('EXPECT_DIAL' not in env, 'negative control is emulator-only')
    else:
        require(re.fullmatch(r'emulator-\d{4}', serial) is not None and
                serial not in ('emulator-5590', 'emulator-5604', 'emulator-5742'), 'owned emulator serial')
    if 'EXPECT_DIAL' in env:
        require(env.get('PLAINPHONE_CHECK_SELF_TEST') == '1' and serial.startswith('emulator-'),
                'EXPECT_DIAL requires emulator self-test opt-in')
        require(env['EXPECT_DIAL'] == '912', 'negative control expects only 912; typed digits stay 911')


class Gate:
    def __init__(self, serial, out):
        self.serial, self.out = serial, Path(out)
        self.out.mkdir(parents=True, exist_ok=True)

    def adb(self, check, *args, binary=False):
        try:
            p = subprocess.run(['adb', '-s', self.serial, *args], capture_output=True, timeout=120)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise GateFailure(check+' (command unavailable or timed out)') from error
        require(p.returncode == 0, check)
        if binary:
            return p.stdout
        return p.stdout.decode('utf-8', errors='strict').strip()

    def sh(self, check, *args):
        return self.adb(check, 'shell', *args)

    def dump(self):
        # The no-PIN keyguard can return during readback or between either route's taps.
        # Re-wake immediately before every UI lookup; a refusal fails the gate.
        self.sh('wake screen before UI', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        self.sh('dismiss no-PIN keyguard before UI', 'wm', 'dismiss-keyguard')
        result = self.sh('dump UI', 'uiautomator', 'dump', '/sdcard/preview9-ui.xml')
        require('UI hierchary dumped to:' in result or 'UI hierarchy dumped to:' in result, 'UI dump completed')
        return self.sh('read UI XML', 'cat', '/sdcard/preview9-ui.xml')

    def tap(self, pattern):
        xy = tap_bounds(self.dump(), pattern)
        self.sh('tap '+pattern, 'input', 'tap', *xy)

    def digits(self):
        return dialtext(self.dump())

    def clear(self):
        # Bounded to avoid endlessly tapping a broken delete control.
        for _ in range(32):
            value = self.digits()
            if value in ('', 'Phone Number'):
                empty_field(value)
                return
            self.tap(r'com\.google\.android\.dialer:id/deleteButton$')
        raise GateFailure('clear dial field within 32 deletes')

    def calls_empty(self):
        no_current_calls(self.sh('read Telecom current calls', 'dumpsys', 'telecom'))

    def home(self):
        self.sh('press Home', 'input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(2)

    def shot(self, name):
        data = self.adb('capture '+name, 'exec-out', 'screencap', '-p', binary=True)
        require(data.startswith(b'\x89PNG\r\n\x1a\n'), 'valid PNG '+name)
        (self.out/name).write_bytes(data)

    def readback(self):
        package = self.sh('read package version', 'dumpsys', 'package', 'org.plainphone')
        require('versionCode=16 ' in package and 'versionName=0.1.0-preview-16' in package, 'version is preview-16')
        require('TEST_ONLY' in package, 'testOnly retained')
        owner = self.sh('read Device Owner', 'dpm', 'list-owners')
        require(any('org.plainphone/.PlainphoneAdminReceiver' in l and 'DeviceOwner' in l for l in owner.splitlines()), 'Device Owner retained')
        activity = self.sh('read lock task', 'dumpsys', 'activity', 'activities')
        require('mLockTaskModeState=LOCKED' in activity, 'lock task LOCKED')

    def restart(self):
        self.adb('restart', 'reboot')
        time.sleep(5)
        self.adb('wait for device', 'wait-for-device')
        for _ in range(120):
            if self.sh('read boot status', 'getprop', 'sys.boot_completed') == '1':
                break
            time.sleep(2)
        else:
            raise GateFailure('boot completed within deadline')
        self.sh('wake screen', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        self.sh('dismiss no-PIN keyguard', 'wm', 'dismiss-keyguard')
        self.sh('swipe unlock', 'input', 'swipe', '540', '1800', '540', '600', '200')
        for _ in range(60):
            log = self.sh('read boot gate log', 'logcat', '-d', '-s', 'PlainphoneBoot')
            if 'home shown' in log:
                break
            time.sleep(1)
        full = re.search(r'^(.{18}).*reapply .*locked=false ready=true applied=\[[^]]*HOME', log, re.M)
        home = re.search(r'^(.{18}).*home shown', log, re.M)
        require(full is not None and home is not None and full[1] <= home[1], 'Home after full read-back')
        print('full read-back='+full[1]+'; home shown='+home[1], flush=True)
        self.readback()
        self.home()

    def routes(self, expected):
        self.home()
        self.tap('^More$')
        self.tap('^Emergency 9-1-1')
        time.sleep(2)
        # Route 1a tests the actual prefill; do not replace it by typing first.
        exact(self.digits(), expected, 'More > Emergency prefill exactly '+expected)
        self.calls_empty()
        self.shot('more-emergency-911.png')
        self.clear()
        empty_field(self.digits())
        self.home()
        self.tap(r'^Phone(?:,.*)?$')
        time.sleep(2)
        xml = self.dump()
        if not any(n.get('resource-id') == 'com.google.android.dialer:id/nine' for n in nodes(xml)):
            self.tap(r'id/dialpad_fab$|^[Kk]ey ?pad$|^[Dd]ial ?pad$')
        self.clear()
        empty_field(self.digits())
        for key in ('nine', 'one', 'one'):
            self.tap(r'com\.google\.android\.dialer:id/'+key+'$')
        exact(self.digits(), expected, 'Phone typed field exactly '+expected)
        self.calls_empty()
        self.shot('phone-911.png')
        self.clear()
        self.home()
        self.readback()


def main():
    try:
        require(len(sys.argv) in (4, 5), 'arguments: serial APK output-directory [label]')
        serial, apk, out = sys.argv[1:4]
        check_target(serial, os.environ)
        print('preview-16 gate '+datetime.datetime.now(datetime.timezone.utc).isoformat(), flush=True)
        gate = Gate(serial, out)
        if os.environ.get('SKIP_INSTALL') != '1':
            print('APK sha256='+hashlib.sha256(Path(apk).read_bytes()).hexdigest(), flush=True)
            result = gate.adb('install in place', 'install', '-r', '-t', apk)
            require('Success' in result.splitlines(), 'installer reports Success')
            time.sleep(4)
        else:
            print('Install skipped; earlier in-place install evidence carried forward.', flush=True)
        gate.readback()
        gate.restart()
        gate.routes(os.environ.get('EXPECT_DIAL', '911'))
        passed('all upgrade gate checks')
        print('exit=0', flush=True)
        return 0
    except (GateFailure, OSError, UnicodeError) as error:
        # No raw adb/provider output, contacts or communication content in failure logs.
        print('FAIL: '+(str(error) if isinstance(error, GateFailure) else type(error).__name__), flush=True)
        print('exit=1', flush=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
