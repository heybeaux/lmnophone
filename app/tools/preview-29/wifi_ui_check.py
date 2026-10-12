# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned emulator only; names are read in memory, never recorded."""
import sys, importlib.util, json, time
from pathlib import Path
root=Path.cwd()
spec=importlib.util.spec_from_file_location('gate',root/'app/tools/preview-29/upgrade_check.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
g=m.Gate('emulator-5880',root/'app/tools/preview-29/evidence')
require=m.previous.require;nodes=m.previous.nodes
base=json.loads(Path(sys.argv[1]).read_text())
def texts():return [n.get('text') for n in nodes(g.dump())]
g.home();g.tap('^More$');g.tap('^Phone settings$');g.tap('^Wi-Fi$');time.sleep(2)
t=texts()
require(any(x and x.startswith('Connected to ') and x!='Connected to Wi-Fi' for x in t),'Location-on connected name shown (withheld)')
require('Nearby networks' in t,'nearby section shown')
require(not any(x and ('needs a sign-in page' in x or "isn't reaching" in x) for x in t),'validated Wi-Fi shows no internet warning')
try:
    g.sh('owned-emulator Location-off probe','settings','put','secure','location_mode','0')
    time.sleep(2)
    t=texts()
    require('Connected to Wi-Fi' in t,'Location-off unnamed connected fallback')
    require("Nearby networks can't be shown right now." in t,'Location-off unreadable-list fallback')
    require(not any(x and ('needs a sign-in page' in x or "isn't reaching" in x) for x in t),'validated status still readable with Location off')
    g.shot('wifi-location-off.png')
finally:
    g.sh('restore authorized emulator mode 3','settings','put','secure','location_mode','3')
time.sleep(2)
require(any(x and x.startswith('Connected to ') and x!='Connected to Wi-Fi' for x in texts()),'Location-on names restored (withheld)')
g.tap('^‹ Phone settings$');g.home();g.readback();g.calls_empty()
require(g.sh('read final Location mode','settings','get','secure','location_mode')==base['location'],'Location unchanged: 3')
require(g.sh('read final saved networks','cmd','wifi','list-networks')==base['networks'],'saved networks exactly unchanged (names withheld)')
require(g.sh('read final font scale','settings','get','system','font_scale')==base['font'],'font scale unchanged')
print('exit=0',flush=True)
