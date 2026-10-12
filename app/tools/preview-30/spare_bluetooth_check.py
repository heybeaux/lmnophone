# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Lead spare Bluetooth check for build 30. Read-only; device names never printed. Run from repo root."""
import sys, importlib.util, time, re
from pathlib import Path
root = Path.cwd()
spec = importlib.util.spec_from_file_location('gate', root/'app/tools/preview-26/upgrade_check.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
m.Gate.VERSION = 30
g = m.Gate('ZY22KW6P9J', root/'docs/releases/preview-30/evidence')
require = m.require; nodes = m.nodes
def bt():
    d = g.sh('read Bluetooth manager', 'dumpsys', 'bluetooth_manager').splitlines()
    state = next((l.split(':', 1)[1].strip() for l in d if l.strip().startswith('state:')), '?')
    i = next((n for n, l in enumerate(d) if l.strip() == 'Bonded devices:'), None)
    bonded = 0
    if i is not None:
        for l in d[i + 1:]:
            if not l.strip() or not l.startswith('    '): break
            bonded += 1
    return state, bonded
def grant():
    p = g.sh('read package permissions', 'dumpsys', 'package', 'org.plainphone')
    return 'BLUETOOTH_CONNECT: granted=true' in p
loc0 = g.sh('read Location mode before', 'settings', 'get', 'secure', 'location_mode')
net0 = g.sh('read saved networks before', 'cmd', 'wifi', 'list-networks')
bt0 = bt()
print('before: Location mode', loc0, '| saved network lines', len(net0.splitlines()),
      '| Bluetooth state', bt0[0], '| bonded count', bt0[1], '| own CONNECT grant', grant(), flush=True)
known = {'‹ Phone settings', 'Bluetooth', 'Paired with:', 'No paired devices.', 'Bluetooth is off.',
         "Bluetooth status can't be read right now.", 'Pairing a new device comes in a later test build.'}
g.home(); g.tap('^More$'); g.tap('^Phone settings$')
rows = ('Wi-Fi', 'Bluetooth', 'Brightness', 'Sound', 'Text size', 'Battery')
order = [n.get('text') for n in nodes(g.dump()) if n.get('text') in rows]
print('Phone settings rows:', ' | '.join(order), flush=True)
require(order == list(rows), 'Bluetooth row directly after Wi-Fi, other rows unchanged')
g.tap('^Bluetooth$'); time.sleep(2)
t = [n.get('text') for n in nodes(g.dump()) if n.get('text')]
print('Bluetooth page (known strings only):', ' | '.join(x for x in t if x in known),
      '| other text rows withheld:', sum(1 for x in t if x not in known), flush=True)
require("Bluetooth status can't be read right now." not in t, 'read succeeded')
require(grant(), 'own BLUETOOTH_CONNECT granted by Device Owner')
if bt0[0] == 'ON' and bt0[1] == 0:
    require('Paired with:' in t and 'No paired devices.' in t, 'ON with zero bonded shows No paired devices')
require('Pairing a new device comes in a later test build.' in t, 'pairing-later note shown')
if bt0[1] == 0: g.shot('spare-bluetooth-page.png')
g.tap('^‹ Phone settings$'); g.home(); g.readback(); g.calls_empty()
require(bt() == bt0, 'Bluetooth state and bonded count unchanged')
require(g.sh('read Location after', 'settings', 'get', 'secure', 'location_mode') == loc0, 'Location unchanged')
require(g.sh('read saved networks after', 'cmd', 'wifi', 'list-networks') == net0, 'saved networks exactly unchanged (names withheld)')
print('exit=0', flush=True)
