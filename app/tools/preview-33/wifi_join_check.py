# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Assigned emulator only; UI names stay in memory and never enter evidence."""
import importlib.util, os, re, json, hashlib, time
from pathlib import Path
root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('upgrade_check.py'))
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
g = m.Gate('emulator-5880', root / 'app/tools/preview-33/evidence')
require = m.previous.require
require('pla363-policy-a35' in g.adb('assigned emulator', 'emu', 'avd', 'name'), 'emulator identity')
baseline = json.loads((Path(os.environ['PAPERCLIP_SCRATCH_DIR'])/'baseline32.json').read_text())
def mode(): return g.sh('read Location', 'settings', 'get', 'secure', 'location_mode')
def page():
    xml = g.dump(); g.sh('remove transient UI dump', 'rm', '-f', '/data/local/tmp/preview26-ui.xml')
    return xml
def wifi():
    g.home();g.tap('^More$');g.tap('^Phone settings$');g.tap('^Wi-Fi$')
def show():
    wifi();g.tap('^Show nearby networks$');time.sleep(2)
    require(mode()=='3','Location enabled after explicit button')
    xml=page();nodes=m.previous.nodes(xml)
    allowed=['‹ Phone settings','Wi-Fi','Nearby networks','Not connected','Connected to Wi-Fi']
    labels=[n for n in nodes if n.get('text') and n.get('text') not in allowed and not n.get('text').startswith('Connected to ')]
    require(len(labels)>0,'nearby list contains named results (names withheld)')
    return xml
try:
    g.sh('Location off setup','settings','put','secure','location_mode','0')
    show();g.sh('Back out','input','keyevent','KEYCODE_BACK');time.sleep(2)
    require(mode()=='0','Back restores Location 0')
    show();g.home();require(mode()=='0','Home restores Location 0')
    show()
    marker=g.sh('read ownership marker only','run-as','org.plainphone','cat','/data/user_de/0/org.plainphone/shared_prefs/wifi_location.xml')
    require('name="owned" value="true"' in marker,'ownership persisted before process death')
    g.sh('crash-recovery setup','am','force-stop','org.plainphone')
    g.sh('restart after process death','am','start','-n','org.plainphone/.MainActivity');time.sleep(3)
    require(mode()=='0','restart recovers owned Location marker')
    # Temporary receiver disconnects repeatedly; no saved network is modified.
    g.sh('disconnect setup','am','broadcast','-n','org.plainphone/org.plainphone.spike437.DisconnectReceiver','--ez','disconnect','true')
    xml=show();nodes=m.previous.nodes(xml)
    require(any(n.get('text')=='Not connected' for n in nodes),'disconnected before choosing network')
    named=[n for n in nodes if n.get('text') and n.get('text') not in ['‹ Phone settings','Wi-Fi','Nearby networks','Not connected']]
    require(len(named)==1,'exactly one available emulator AP')
    target=named[0].get('text')
    xy=m.previous.tap_bounds(xml,'^'+re.escape(target)+'$')
    g.sh('tap available network (name withheld)','input','tap',*xy)
    xml=page();require(any(n.get('text')=='This network has no password.' for n in m.previous.nodes(xml)),'open-network join screen')
    g.tap('^Cancel$');time.sleep(1);require(mode()=='0','Cancel restores Location 0')
    g.sh('renew disconnect setup','am','broadcast','-n','org.plainphone/org.plainphone.spike437.DisconnectReceiver','--ez','disconnect','true')
    xml=show();xy=m.previous.tap_bounds(xml,'^'+re.escape(target)+'$')
    g.sh('select network again (name withheld)','input','tap',*xy)
    g.sh('end disconnect setup','am','broadcast','-n','org.plainphone/org.plainphone.spike437.DisconnectReceiver','--ez','disconnect','false')
    g.tap('^Connect$');time.sleep(3)
    xml=page();require(any(n.get('text')=='Connected to '+target for n in m.previous.nodes(xml)),'UI confirms joined network (name withheld)')
    require(mode()=='0','join success restores Location 0')
    g.home();g.readback();g.calls_empty()
finally:
    g.sh('stop disconnect setup','am','broadcast','-n','org.plainphone/org.plainphone.spike437.DisconnectReceiver','--ez','disconnect','false')
    g.home();g.sh('restore original Location','settings','put','secure','location_mode',baseline['location'])
    require(mode()==baseline['location'],'original Location restored')
    saved=hashlib.sha256(g.sh('compare saved networks without recording names','cmd','wifi','list-networks').encode()).hexdigest()
    require(saved==baseline['saved'],'saved network list unchanged')
    for key,scope,setting in [('font','system','font_scale'),('ringtone','system','ringtone'),('bluetooth','global','bluetooth_on')]:
        require(g.sh('read '+key,'settings','get',scope,setting)==baseline[key],key+' unchanged')
    g.sh('remove UI dump','rm','-f','/data/local/tmp/preview26-ui.xml')
print('PASS: build33 live Wi-Fi gate; no calls; names withheld',flush=True)
