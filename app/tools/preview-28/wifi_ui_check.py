# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned emulator UI gate. Pass a pre-install baseline JSON; run from repository root."""
import sys,importlib.util,json,time,os,re
from pathlib import Path
root=Path.cwd()
spec=importlib.util.spec_from_file_location('gate',root/'app/tools/preview-28/upgrade_check.py')
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
g=m.Gate('emulator-5880',root/'app/tools/preview-28/evidence')
require=m.previous.require; nodes=m.previous.nodes
base=json.loads(Path(sys.argv[1]).read_text())
def texts(xml):return [n.get('text') for n in nodes(xml)]
g.sh('start Home after instrumentation','am','start','-n','org.plainphone/.MainActivity')
time.sleep(4)
g.home();g.tap('^More$');g.tap('^Phone settings$');g.tap('^Wi-Fi$');time.sleep(2)
xml=g.dump();t=texts(xml)
require('Connected to AndroidWifi' in t,'UI connected name matches emulator wifi status')
require('AndroidWifi' in t and 'Nearby networks' in t,'UI nearby names contain AndroidWifi')
require('Joining a network comes in a later test build.' in t,'read-only preview note')
require(all(n.get('clickable')=='false' for n in nodes(xml) if n.get('text')=='AndroidWifi'),'nearby network is not clickable')
require('<unknown ssid>' not in t and '\"AndroidWifi\"' not in t,'no raw SSID quoting or unknown marker')
g.shot('wifi-connected.png')
# Wait across polling ticks; no setting writes are used as refresh stimuli.
time.sleep(3)
require('Connected to AndroidWifi' in texts(g.dump()),'visible refresh retains read-back connection')
g.tap('^‹ Phone settings$');g.tap('^Text size$')
require('This is how big your text will be.' in texts(g.dump()),'new Text size sample is installed')
g.shot('text-size-sample.png')
g.sh('back to Phone settings','input','keyevent','KEYCODE_BACK')
g.home();g.readback();g.calls_empty()
location=g.sh('read Location mode','settings','get','secure','location_mode')
networks=g.sh('read saved networks','cmd','wifi','list-networks')
require(location==base['location'],'Location mode unchanged: '+location)
require(networks==base['networks'],'saved networks exactly unchanged')
print('Saved networks before and after: '+networks,flush=True)
require(g.sh('read font scale','settings','get','system','font_scale')==base['font'],'font scale unchanged')
require('WRITE_SETTINGS: default' in g.sh('read settings app-op','appops','get','org.plainphone','WRITE_SETTINGS'),'WRITE_SETTINGS remains default')
package=g.sh('read final permission state','dumpsys','package','org.plainphone')
permissions=[l.strip() for l in package.splitlines() if ('ACCESS_FINE_LOCATION:' in l or 'ACCESS_COARSE_LOCATION:' in l) and 'granted=' in l]
print('Final location permissions: '+ '; '.join(permissions),flush=True)
require(any('ACCESS_FINE_LOCATION: granted=true' in l for l in permissions),'fine location granted readback')
print('exit=0',flush=True)
