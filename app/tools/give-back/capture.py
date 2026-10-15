#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Capture only give-back-relevant emulator facts. Raw dumps stay in run-owned scratch."""
import os, re, subprocess, sys
from pathlib import Path
serial, label = sys.argv[1:]
assert re.fullmatch(r'emulator-59[0-9][02468]', serial), 'private 59xx emulator only'
assert re.fullmatch(r'[a-z0-9-]+', label)
adb = str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb')
def shell(*args):
    return subprocess.check_output([adb, '-s', serial, 'shell', *args], text=True)
scratch = Path(os.environ['PAPERCLIP_SCRATCH_DIR'])
policy = shell('dumpsys', 'device_policy')
(scratch / (label + '-device-policy-raw.txt')).write_text(policy)
lines = ['# ' + label, 'Device policy (relevant fields only):']
need = re.compile(r'Device Owner|admin=|ComponentInfo|User ID:|User restrictions:|no_[a-z_]+|Lock task|lockTask|mLockTask|mPackages|mFlags|permissionPolicy|Permission policy|mFactoryReset|keyguard|Keyguard|mStatusBar|mPermitted|permitted|System Update Policy|systemUpdatePolicy', re.I)
for line in policy.splitlines():
    if need.search(line) and not re.search(r'account|userId|serial|password|ownerName', line, re.I):
        lines.append(line)
lines += ['Lock task state:'] + [line.strip() for line in shell('dumpsys', 'activity', 'activities').splitlines() if 'mLockTaskModeState=' in line]
for name in ['org.plainphone', 'com.google.android.contacts', 'com.google.android.calendar']:
    lines.append('Package: ' + name)
    for line in shell('dumpsys', 'package', name).splitlines():
        if line.strip().startswith('User 0:') or ('android.permission.READ_CONTACTS:' in line):
            lines.append(line.strip())
all_packages = set(shell('pm', 'list', 'packages', '-u').splitlines())
visible = set(shell('pm', 'list', 'packages').splitlines())
lines += ['Hidden or uninstalled package query difference:'] + sorted(all_packages - visible)
lines += ['Home resolver:', shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').strip()]
lines += ['Dialer resolver:', shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.DIAL').strip()]
Path(__file__).parent.joinpath('evidence/emulator', label + '.txt').write_text('\n'.join(line.rstrip() for line in lines).replace(serial, 'private-emulator') + '\n')
print('captured', label)
