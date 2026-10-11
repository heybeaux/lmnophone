# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned-emulator readback. Read-only; compares with saved build-6 baseline."""
import datetime
import json
from pathlib import Path
import re
import subprocess
import sys

OUT = Path(__file__).parent / 'emulator'
SERIAL = sys.argv[2]
assert SERIAL.startswith('emulator-')

def adb(*args):
    return subprocess.check_output(['adb', '-s', SERIAL, *args], timeout=60).decode()

def package_flags(text):
    result = {'hidden': [], 'suspended': [], 'disabled': []}
    count = 0
    for match in re.finditer(r'^  Package \[([^]]+)\].*?\n(.*?)(?=^  Package \[|\Z)', text, re.M | re.S):
        user = re.search(r'^    User 0: (.*)$', match[2], re.M)
        if not user:
            continue
        count += 1
        flags = dict(re.findall(r'(\w+)=(\S+)', user[1]))
        for flag in ('hidden', 'suspended'):
            if flags.get(flag) == 'true':
                result[flag].append(match[1])
        if flags.get('enabled') in ('2', '3', '4'):
            result['disabled'].append(match[1])
    assert count > 200, 'incomplete package read'
    for key in result:
        result[key].sort()
    result['packages_read'] = count
    return result

def restriction_sections(text):
    lines = text.splitlines()
    sections = []
    labels = {'Restrictions:', 'Device policy restrictions:', 'Effective restrictions:',
              'Device policy global restrictions:', 'Guest restrictions:'}
    for index, line in enumerate(lines):
        if line.strip() not in labels:
            continue
        indent = len(line) - len(line.lstrip())
        values = []
        for following in lines[index + 1:]:
            if not following.strip():
                continue
            if len(following) - len(following.lstrip()) <= indent:
                break
            values.extend(re.findall(r'\bno_[a-z_]+\b', following))
        sections.append({'section': line.strip(), 'values': sorted(set(values))})
    assert any(x['section'] == 'Effective restrictions:' for x in sections)
    return sections

def summary(label):
    def read(name):
        return (OUT / f'{label}-{name}.txt').read_text()
    policy, users, activity, package = [read(n) for n in ('device-policy', 'users', 'activities', 'package')]
    # Capture all named restrictions, including user/profile defaults and supported-name tables;
    # compare like for like. Raw device_policy/user dumps retain the actual scope of every entry.
    restrictions = sorted(set(re.findall(r'\bno_[a-z_]+\b', policy + users)))
    lock = re.findall(r'LockTaskPolicy \{mPackages= ([^;]+); mFlags= (\d+)', policy)
    assert lock and len(set(lock)) == 1, 'ambiguous lock-task readback'
    first = re.search(r'firstInstallTime=(.+)', package)
    state = re.search(r'mLockTaskModeState=(\w+)', activity)
    assert first and state
    return dict(owner=read('owner').strip(), restriction_names_in_dump=restrictions,
                active_restriction_sections=restriction_sections(users),
                lock_packages=sorted(x.strip() for x in lock[0][0].split(',')),
                lock_features=int(lock[0][1]), lock_state=state[1], first_install_time=first[1],
                count_preference=read('count-preference').strip(),
                package_flags=json.loads((OUT / f'{label}-package-flags.json').read_text()))

if __name__ == '__main__':
    label = sys.argv[1]
    OUT.mkdir(parents=True, exist_ok=True)
    assert adb('emu', 'avd', 'name').splitlines()[0] == 'pla353-ui-a35'
    for name, command in {
        'device-policy': ['dumpsys', 'device_policy'],
        'users': ['dumpsys', 'user'],
        'activities': ['dumpsys', 'activity', 'activities'],
        'owner': ['dpm', 'list-owners'],
        'package': ['dumpsys', 'package', 'org.plainphone'],
        'count-preference': ['run-as', 'org.plainphone', 'cat', '/data/user_de/0/org.plainphone/shared_prefs/home_preferences.xml'],
    }.items():
        (OUT / f'{label}-{name}.txt').write_text(adb('shell', *command))
    (OUT / f'{label}-package-flags.json').write_text(json.dumps(package_flags(adb('shell', 'dumpsys', 'package')), indent=2) + '\n')
    before, after = summary('before'), summary(label)
    result = {'time_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'serial': '<emulator>', 'before': before, 'after': after}
    (OUT / f'{label}-safety.json').write_text(json.dumps(result, indent=2) + '\n')
    for flag in ('hidden', 'suspended', 'disabled'):
        assert not (set(after['package_flags'][flag]) - set(before['package_flags'][flag])), 'new ' + flag
    assert not (set(after['restriction_names_in_dump']) - set(before['restriction_names_in_dump'])), 'new restriction'
    assert 'no_debugging_features' not in after['restriction_names_in_dump']
    for field in ('owner', 'lock_packages', 'lock_features', 'first_install_time', 'count_preference', 'active_restriction_sections'):
        assert before[field] == after[field], 'changed ' + field
    assert after['lock_state'] == 'LOCKED'
    assert adb('get-state').strip() == 'device'
    print('PASS: safety unchanged; adb reachable; no_debugging_features absent')
