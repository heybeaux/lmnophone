# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Deferred c3a64c60 candidate only, NOT final F1/F2 subset. Requires owned synthetic AP.
Create it through the isolated netsim AP API; remove it after the gate. Names stay in memory.
"""
import importlib.util, os, re, json, hashlib, time
from pathlib import Path
root=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('gate',Path(__file__).with_name('upgrade_check.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
g=m.Gate('emulator-5880',root/'app/tools/preview-34/evidence');require=m.previous.require
baseline=json.loads((Path(os.environ['PAPERCLIP_SCRATCH_DIR'])/'baseline33.json').read_text())
if os.environ.get('LOCATION_OFF_FIXTURE') == '1': baseline['location']='0'
require('pla363-policy-a35' in g.adb('assigned emulator','emu','avd','name'),'emulator identity')
def saved():
    raw=g.sh('read saved ids without names','cmd','wifi','list-networks')
    ids={int(l.split()[0]) for l in raw.splitlines() if l.split() and l.split()[0].isdigit()}
    return ids,hashlib.sha256(raw.encode()).hexdigest()
def page():
    xml=g.dump();g.sh('remove transient UI dump','rm','-f','/data/local/tmp/preview26-ui.xml');return xml
def labels(xml):return [n.get('text','') for n in m.previous.nodes(xml)]
def tap_synthetic(xml):
    # In-memory only, including the regex and source XML.
    selected=[n for n in m.previous.nodes(xml) if n.get('text')=='Sam-preview34']
    require(len(selected)==1,'one synthetic AP row available')
    bounds=list(map(int,re.findall(r'\d+',selected[0].get('bounds',''))))
    require(len(bounds)==4,'synthetic AP row bounds')
    g.sh('select synthetic AP','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
before,hashed=saved();require(hashed==baseline['saved'],'original saved list intact before joining')
g.home();g.tap('^More$');g.tap('^Phone settings$');g.tap('^Wi-Fi$')
require('Forget this network' in labels(page()),'Forget shown for identifiable original connection; not tapped')
if 'Show nearby networks' in labels(page()):g.tap('^Show nearby networks$')
g.sh('refresh emulator scan fixture','cmd','wifi','start-scan');time.sleep(5)
tap_synthetic(page());g.tap('^Connect$')
for _ in range(30):
    time.sleep(1)
    if 'Connected to Sam-preview34' in labels(page()):break
else:raise m.GateFailure('synthetic AP association not confirmed')
joined,joinedhash=saved();require(len(joined)==len(before)+1 and before<joined,'one synthetic saved id added')
require(g.sh('read Location','settings','get','secure','location_mode')==baseline['location'],'join restored Location')
require('Forget this network' in labels(page()),'Forget shown after synthetic join')
g.tap('^Forget this network$')
for _ in range(12):
    time.sleep(1)
    current,currenthash=saved()
    if current==before:break
else:raise m.GateFailure('saved count did not reduce by exactly one')
require(currenthash==baseline['saved'],'original saved configuration hash unchanged after Forget')
xml=page();require('Connected to Sam-preview34' not in labels(xml),'no longer connected to forgotten configuration')
require("Wi-Fi status can't be read right now." not in labels(xml),'product readback accepted removal')
for key,scope,setting in [('location','secure','location_mode'),('font','system','font_scale'),('ringtone','system','ringtone'),('bluetooth','global','bluetooth_on')]:
    require(g.sh('read '+key,'settings','get',scope,setting)==baseline[key],key+' unchanged')
g.home();g.readback();g.calls_empty()
print('PASS: connected synthetic network forgotten; saved count -1; original hash retained; zero calls',flush=True)
