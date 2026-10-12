# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned emulator only. UI and platform names never leave memory; evidence is counts only."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time

root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('gate', root / 'app/tools/preview-30/upgrade_check.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
g = m.Gate('emulator-5880', root / 'app/tools/preview-30/evidence')
require = m.previous.require
baseline = Path(os.environ['PAPERCLIP_RUN_SCRATCH_DIR']) / 'preview30-baseline.json'

def snapshot():
    raw = g.sh('read Bluetooth state and bonded count (names withheld)', 'dumpsys', 'bluetooth_manager')
    state = re.findall(r'^\s*state: (\S+)\s*$', raw, re.M)
    require(len(state) == 1, 'one adapter state')
    lines = raw.splitlines()
    starts = [i for i, line in enumerate(lines) if line.strip() == 'Bonded devices:']
    require(len(starts) == 1, 'one bonded devices section')
    start = starts[0]
    body = []
    indent = len(lines[start]) - len(lines[start].lstrip())
    for line in lines[start+1:]:
        if not line.strip() or len(line)-len(line.lstrip()) <= indent:
            break
        body.append(line)
    # Each bonded row is a device address + type/name. Fail closed on unfamiliar rows.
    require(all(re.search(r'(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}', line) for line in body), 'bonded rows parse without recording names')
    return {'bluetooth': state[0], 'bondedCount': len(body),
        'location': g.sh('read Location mode', 'settings', 'get', 'secure', 'location_mode'),
        'savedNetworksSha256': hashlib.sha256(g.sh('read saved networks (names withheld)', 'cmd', 'wifi', 'list-networks').encode()).hexdigest()}

def texts():
    # Remove transient XML from device too. Never save Bluetooth UI screenshots.
    try:
        return [node.get('text') for node in m.previous.nodes(g.dump())]
    finally:
        g.sh('remove transient UI dump', 'rm', '-f', '/data/local/tmp/preview26-ui.xml')

require('pla363-policy-a35' in g.adb('check owned emulator', 'emu', 'avd', 'name'), 'correct emulator')
if sys.argv[1] == 'before':
    before = snapshot()
    require(before['bluetooth'] == 'ON', 'initial Bluetooth ON')
    baseline.write_text(json.dumps(before))
    print(json.dumps(before), flush=True)
else:
    before = json.loads(baseline.read_text())
    require(snapshot() == before, 'upgrade preserved Bluetooth, bonded count, Location and saved networks')
    try:
        g.sh('set owned-emulator Location off for probe', 'settings', 'put', 'secure', 'location_mode', '0')
        g.home(); g.tap('^More$'); g.tap('^Phone settings$')
        settings = texts()
        wanted = ['Wi-Fi', 'Bluetooth', 'Brightness', 'Sound', 'Text size', 'Battery']
        require([s for s in settings if s in wanted] == wanted, 'settings rows in requested order')
        g.tap('^Bluetooth$'); time.sleep(2)
        page = texts()
        require('Paired with:' in page and 'Bluetooth is off.' not in page and "Bluetooth status can't be read right now." not in page, 'Location-off Bluetooth ON page readable')
        require(before['bondedCount'] == 0 and 'No paired devices.' in page, 'paired count=0 matches platform')
        require('Pairing a new device comes in a later test build.' in page, 'preview limitation shown')
        perms = g.sh('read own permission grant', 'dumpsys', 'package', 'org.plainphone')
        require('android.permission.BLUETOOTH_CONNECT: granted=true' in perms, 'own BLUETOOTH_CONNECT grant read back')
        require('android.permission.BLUETOOTH_SCAN' not in perms, 'no BLUETOOTH_SCAN requested')
        g.sh('turn owned-emulator Bluetooth off', 'cmd', 'bluetooth_manager', 'disable')
        g.sh('wait for Bluetooth off', 'cmd', 'bluetooth_manager', 'wait-for-state:STATE_OFF')
        time.sleep(2)
        page = texts()
        require('Bluetooth is off.' in page and 'Paired with:' not in page and 'No paired devices.' not in page, 'visible poll shows off without stale list')
        require('Pairing a new device comes in a later test build.' in page, 'off state retains limitation')
    finally:
        g.sh('restore owned-emulator Bluetooth ON', 'cmd', 'bluetooth_manager', 'enable')
        g.sh('wait for Bluetooth on', 'cmd', 'bluetooth_manager', 'wait-for-state:STATE_ON')
        g.sh('restore original Location mode', 'settings', 'put', 'secure', 'location_mode', before['location'])
    time.sleep(2)
    page = texts()
    require('No paired devices.' in page and 'Bluetooth is off.' not in page, 'visible poll shows restored ON state')
    g.tap('^‹ Phone settings$'); g.home(); g.readback(); g.calls_empty()
    after = snapshot()
    require(after == before, 'final Bluetooth, bonded count, Location and saved networks exactly unchanged')
    print(json.dumps({'before': before, 'after': after}), flush=True)
    print('exit=0', flush=True)
