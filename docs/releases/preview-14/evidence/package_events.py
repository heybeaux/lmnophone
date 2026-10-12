# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""API 35 retained author AVD only. Changes and restores a non-floor hidden app; no calls."""
import json
from pathlib import Path
import re
import subprocess
import time

ADB = ['adb', '-s', 'emulator-5880']
APP = 'com.google.android.contacts'
COMPONENT = APP + '/com.android.contacts.activities.PeopleActivity'
OUT = Path(__file__).parent

def adb(*args):
    return subprocess.check_output(ADB + list(args), text=True, timeout=30).strip()

def flags():
    dump = adb('shell', 'dumpsys', 'package', APP)
    line = re.search(r'^    User 0: (.+)$', dump, re.M)[1]
    return dict(re.findall(r'(\w+)=(\S+)', line))

def exercise(label, operation, target, skip):
    adb('logcat', '-c')
    result = adb('shell', 'pm', operation, '--user', '0', target)
    for _ in range(60):
        log = adb('logcat', '-d', '-s', 'PlainphoneBoot')
        lines = [x for x in log.splitlines() if 'reapply reason=package:PACKAGE_CHANGED:'+APP+' ' in x]
        if lines:
            break
        time.sleep(0.25)
    else:
        raise AssertionError('no target event for '+label)
    state = flags()
    assert state['hidden'] == 'true', label+' exposed hidden app'
    if skip:
        assert any('skipped=unchanged-since=' in x for x in lines), label+' did not skip'
        time.sleep(1)
        log = adb('logcat', '-d', '-s', 'PlainphoneBoot')
        assert 'home shown' not in log, label+' refreshed Home'
    else:
        assert any('locked=false ready=true' in x and 'failed=null' in x for x in lines), label+' lacked full pass'
    (OUT/(label+'.txt')).write_text(log+'\n')
    return dict(label=label, command=['pm', operation, '--user', '0', target], result=result,
                hidden=state['hidden'], enabled=state['enabled'], skipped=skip)

if __name__ == '__main__':
    assert adb('emu', 'avd', 'name').splitlines()[0] == 'pla363-policy-a35'
    assert adb('shell', 'id', '-u') == '0'
    assert 'versionCode=14 ' in adb('shell', 'dumpsys', 'package', 'org.plainphone')
    before = flags()
    assert before['hidden'] == 'true' and before['enabled'] == '0'
    user = adb('shell', 'dumpsys', 'package', APP).split('    User 0:')[1]
    assert 'com.android.contacts.activities.PeopleActivity' not in user
    results = []
    try:
        results.append(exercise('component-disabled-skip', 'disable', COMPONENT, True))
        results.append(exercise('component-restored-skip', 'default-state', COMPONENT, True))
        results.append(exercise('app-disabled-full', 'disable', APP, False))
        results.append(exercise('app-restored-full', 'default-state', APP, False))
    finally:
        adb('shell', 'pm', 'default-state', '--user', '0', COMPONENT)
        adb('shell', 'pm', 'default-state', '--user', '0', APP)
    assert flags() == before, 'package flags not restored'
    (OUT/'package-events.json').write_text(json.dumps(results, indent=2)+'\n')
    print('PASS: two skips without Home refresh, two full passes, hidden throughout, flags restored')
