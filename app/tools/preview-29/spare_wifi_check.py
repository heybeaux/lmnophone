# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Lead spare Wi-Fi check for build 29. Names never printed. Run from repo root."""
import sys, importlib.util, time, re
from pathlib import Path
root = Path.cwd()
spec = importlib.util.spec_from_file_location('gate', root/'app/tools/preview-29/upgrade_check.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
g = m.Gate('ZY22KW6P9J', root/'docs/releases/preview-29/evidence')
require = m.previous.require; nodes = m.previous.nodes
loc0 = g.sh('read Location mode before','settings','get','secure','location_mode')
net0 = g.sh('read saved networks before','cmd','wifi','list-networks')
font0 = g.sh('read font scale before','settings','get','system','font_scale')
print('Location before:', loc0, '| saved network lines:', len(net0.splitlines()), flush=True)
conn = g.sh('read connectivity','dumpsys','connectivity')
wifi_caps = [l for l in conn.splitlines() if 'Transports: WIFI' in l and 'NetworkAgentInfo' in l]
validated = any('VALIDATED' in l for l in wifi_caps)
portal = any('CAPTIVE_PORTAL' in l for l in wifi_caps)
print('platform: wifi agents=%d validated=%s captive_portal=%s' % (len(wifi_caps), validated, portal), flush=True)
def texts(): return [n.get('text') for n in nodes(g.dump()) if n.get('text')]
g.home(); g.tap('^More$'); g.tap('^Phone settings$'); g.tap('^Wi-Fi$'); time.sleep(2)
t = texts()
safe = [x for x in t if not x.startswith('Connected to ') or x == 'Connected to Wi-Fi']
print('Wi-Fi page:', ' | '.join(safe), flush=True)
require("Wi-Fi status can't be read right now." not in t, 'overall read succeeded')
require(any(x.startswith('Connected to ') for x in t), 'page says connected (platform connected)')
warn = lambda tt: [x for x in tt if 'needs a sign-in page' in x or "isn't reaching" in x]
if validated and not portal:
    require(not warn(t), 'validated Wi-Fi shows no internet warning')
g.shot('spare-wifi-page.png')
time.sleep(12)
t2 = texts()
if validated and not portal:
    require(not warn(t2), 'still no warning after 12 s (past 10 s grace)')
print('After 12 s warnings:', warn(t2), flush=True)
g.tap('^‹ Phone settings$'); g.home(); g.readback(); g.calls_empty()
require(g.sh('read Location after','settings','get','secure','location_mode') == loc0, 'Location unchanged')
require(g.sh('read saved networks after','cmd','wifi','list-networks') == net0, 'saved networks exactly unchanged (names withheld)')
require(g.sh('read font after','settings','get','system','font_scale') == font0, 'font scale unchanged')
print('exit=0', flush=True)
