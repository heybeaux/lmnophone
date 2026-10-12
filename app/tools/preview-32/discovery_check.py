# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned-emulator Bluetooth gate. Names stay in memory, evidence records counts only."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time

root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('upgrade_check.py'))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
g = m.Gate('emulator-5880', root / 'app/tools/preview-32/evidence')
require = m.previous.require
baseline = Path(os.environ['PAPERCLIP_SCRATCH_DIR']) / 'preview32-baseline.json'

def snapshot():
    raw = g.sh('read Bluetooth state and bonded count (names withheld)', 'dumpsys', 'bluetooth_manager')
    state = re.findall(r'^\s*state: (\S+)\s*$', raw, re.M)
    require(len(state) == 1, 'one adapter state')
    lines = raw.splitlines()
    starts = [i for i, line in enumerate(lines) if line.strip() == 'Bonded devices:']
    require(len(starts) == 1, 'one bonded devices section')
    start = starts[0]; body = []
    indent = len(lines[start]) - len(lines[start].lstrip())
    for line in lines[start+1:]:
        if not line.strip() or len(line)-len(line.lstrip()) <= indent: break
        body.append(line)
    require(all(re.search(r'(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}', line) for line in body), 'bonded rows parsed')
    return {'bluetooth': state[0], 'bondedCount': len(body),
        'location': g.sh('read Location mode', 'settings', 'get', 'secure', 'location_mode'),
        'ringtoneSha256': hashlib.sha256(g.sh('read ringtone without recording title', 'settings', 'get', 'system', 'ringtone').encode()).hexdigest(),
        'savedNetworksSha256': hashlib.sha256(g.sh('read saved networks without names', 'cmd', 'wifi', 'list-networks').encode()).hexdigest()}

def page():
    try: return [node.get('text') for node in m.previous.nodes(g.dump())]
    finally: g.sh('remove transient UI dump', 'rm', '-f', '/data/local/tmp/preview26-ui.xml')

def discovering():
    raw = g.sh('read discovery state without names', 'dumpsys', 'bluetooth_manager')
    match = re.search(r'^\s*Discovering: (true|false)', raw, re.M)
    require(match is not None, 'platform discovery flag readable')
    return match[1] == 'true'

require('pla363-policy-a35' in g.adb('check owned emulator', 'emu', 'avd', 'name'), 'correct owned emulator')
if sys.argv[1] == 'before':
    g.VERSION = 31; g.readback()
    before = snapshot(); baseline.write_text(json.dumps(before))
    print(json.dumps(before), flush=True)
else:
    before = json.loads(baseline.read_text())
    require(snapshot() == before, 'upgrade preserved captured settings')
    try:
        g.sh('set owned-emulator Location off', 'settings', 'put', 'secure', 'location_mode', '0')
        g.home(); g.tap('^More$'); g.tap('^Phone settings$'); g.tap('^Bluetooth$')
        require('Pair a new device' in page(), 'fully-on Bluetooth offers nearby page')
        g.tap('^Pair a new device$'); time.sleep(1)
        require('Looking for nearby devices…' in page(), 'scan starts on open')
        time.sleep(14)
        text = page()
        require('PLA434 Test Peer' in text, 'one synthetic peer found with Location off')
        require(text.count('PLA434 Test Peer') == 1, 'duplicate discovery events make one row')
        require('Look again' in text, 'completion offers Look again')
        print('nearby synthetic peer count=1', flush=True)
        xy = m.previous.tap_bounds(g.dump(), '^PLA434 Test Peer$')
        g.sh('tap synthetic peer row (name withheld)', 'input', 'tap', *xy)
        text = page()
        require("Pairing from LMNOPhone isn't ready yet." in text and 'Pair' not in text, 'device page honestly has no Pair action')
        require(not discovering(), 'leaving nearby page stops discovery')
        g.tap('^‹ Pair a new device$'); time.sleep(1)
        require(discovering(), 'back to nearby starts a fresh scan')
        g.sh('pause app using screen off (not SOS)', 'input', 'keyevent', 'KEYCODE_SLEEP'); time.sleep(1)
        require(not discovering(), 'pause cancels discovery')
        g.sh('wake screen', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        g.sh('dismiss no-PIN keyguard', 'wm', 'dismiss-keyguard'); time.sleep(2)
        g.sh('turn owned-emulator Bluetooth off during scan', 'cmd', 'bluetooth_manager', 'disable')
        g.sh('wait for Bluetooth off', 'cmd', 'bluetooth_manager', 'wait-for-state:STATE_OFF'); time.sleep(2)
        text = page()
        require("Nearby devices can't be shown right now." in text and 'PLA434 Test Peer' not in text, 'off mid-scan clears list and shows error')
        g.tap('^‹ Bluetooth$'); time.sleep(2)
        text = page()
        require('Bluetooth is off.' in text and 'Pair a new device' not in text, 'off page has no discovery row')
        perms = g.sh('read app scan permission', 'dumpsys', 'package', 'org.plainphone')
        require('android.permission.BLUETOOTH_SCAN: granted=true' in perms, 'SCAN grant independently read back')
    finally:
        g.sh('restore Bluetooth on', 'cmd', 'bluetooth_manager', 'enable')
        g.sh('wait for Bluetooth on', 'cmd', 'bluetooth_manager', 'wait-for-state:STATE_ON')
        g.sh('restore original Location', 'settings', 'put', 'secure', 'location_mode', before['location'])
    g.home(); g.readback(); g.calls_empty()
    after = snapshot(); require(after == before, 'final Bluetooth, bonds, Location, ringtone, saved networks unchanged')
    print(json.dumps({'before': before, 'after': after}), flush=True)
    print('exit=0', flush=True)
