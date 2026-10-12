# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned API 35 AVD: unchanged component event during capture must take a full pass."""
import re
import subprocess
import time
from pathlib import Path

ADB = ['adb', '-s', 'emulator-5880']
APP = 'com.google.android.contacts'
COMPONENT = APP + '/com.android.contacts.activities.PeopleActivity'
OUT = Path(__file__).parent

def adb(*args):
    return subprocess.check_output(ADB + list(args), text=True, timeout=30).strip()

def validate(log):
    full = re.search(r'^(.{18}).*reapply reason=updated .*locked=false ready=true .*pass=(\d+) ', log, re.M)
    marker = re.search(r'^(.{18}).*PlainphoneProbe: unchanged-component-request', log, re.M)
    assert full and marker
    capture = re.search(r'^(.{18}).*packageSnapshot pass='+full[2]+r' .*captured=true', log, re.M)
    event = re.search(r'^(.{18}).*reapply reason=package:PACKAGE_CHANGED:'+re.escape(APP)+r' .*locked=false ready=true .*failed=null', log, re.M)
    assert capture and event
    assert full[1] < marker[1] < capture[1] < event[1], 'event request must land during capture'
    assert 'skipped=unchanged-since=' not in log

if __name__ == '__main__':
    assert adb('emu', 'avd', 'name').splitlines()[0] == 'pla363-policy-a35'
    assert adb('shell', 'id', '-u') == '0'
    assert 'versionCode=14 ' in adb('shell', 'dumpsys', 'package', 'org.plainphone')
    before = adb('shell', 'dumpsys', 'package', APP).split('    User 0:')[1]
    assert 'hidden=true' in before and 'enabled=0' in before
    assert 'com.android.contacts.activities.PeopleActivity' not in before
    adb('logcat', '-c')
    try:
        # The existing receiver runs a normal full pass. No test hook changes policy or timing.
        adb('shell', 'am', 'broadcast', '-a', 'android.intent.action.MY_PACKAGE_REPLACED',
            '-n', 'org.plainphone/.boot.BootReceiver')
        adb('shell', 'log', '-p', 'i', '-t', 'PlainphoneProbe', 'unchanged-component-request')
        adb('shell', 'pm', 'disable', '--user', '0', COMPONENT)
        time.sleep(2)
        log = adb('logcat', '-d', '-s', 'PlainphoneBoot', 'PlainphoneProbe')
        (OUT/'before-capture-component.txt').write_text(log+'\n')
        validate(log)
    finally:
        adb('shell', 'pm', 'default-state', '--user', '0', COMPONENT)
    print('PASS: unchanged component event before capture completion took a full verified pass')
