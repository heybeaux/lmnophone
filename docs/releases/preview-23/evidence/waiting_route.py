# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned emulator only. Fixture tests cancellation; this is not a supported UI start route."""
import json, re, subprocess, time, xml.etree.ElementTree as E
from pathlib import Path
OUT = Path(__file__).resolve().parent
SERIAL = 'emulator-5880'
def adb(*args): return subprocess.check_output(['adb', '-s', SERIAL, *args], timeout=60)
def sh(*args): return adb('shell', *args).decode().strip()
def dump():
    sh('uiautomator', 'dump', '/sdcard/preview23-settings.xml')
    return E.fromstring(sh('cat', '/sdcard/preview23-settings.xml'))
def texts(root): return [n.get('text') for n in root.iter('node') if n.get('text')]
def tap(label):
    assert label in ['More', 'Phone settings', 'Yes, cancel it', 'OK',
        'A change is waiting, turn LMNOPhone (preview) off. Opens the change.']
    root=dump()
    nodes=[n for n in root.iter('node') if label in [n.get('text'),n.get('content-desc')]]
    assert len(nodes)==1, (label,texts(root))
    x,y,xx,yy=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
    assert xx>x and yy>y
    sh('input','tap',str((x+xx)//2),str((y+yy)//2)); time.sleep(.5)
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def instrument(phase):
    value=sh('am','instrument','-w','-e','phase',phase,
        'org.plainphone.test/org.plainphone.WaitingSettingsInstrumentation')
    (OUT/('fixture-'+phase+'.txt')).write_text(value+'\n')
    assert 'outcome=PASS' in value, value
    return value
def home():
    sh('am','start','-n','org.plainphone/.MainActivity'); time.sleep(2)
    sh('input','keyevent','KEYCODE_HOME'); time.sleep(1)
assert adb('emu','avd','name').decode().splitlines()[0]=='pla363-policy-a35'
assert sh('getprop','ro.build.version.sdk')=='35'
assert 'versionCode=23 ' in sh('dumpsys','package','org.plainphone')
records=[]
seeded=False
try:
    sh('input','keyevent','KEYCODE_WAKEUP'); sh('wm','dismiss-keyguard')
    home(); tap('More'); tap('Phone settings')
    assert 'A change is waiting' not in texts(dump()); shot('settings-before')
    # Instrumentation stops the target process before touching its test state.
    seeded=True; instrument('seed'); home(); shot('home-pending')
    assert any('will turn off after' in t for t in texts(dump()))
    tap('More'); tap('Phone settings'); root=dump(); shot('settings-pending')
    assert any(n.get('content-desc')=='A change is waiting, turn LMNOPhone (preview) off. Opens the change.' for n in root.iter('node'))
    tap('A change is waiting, turn LMNOPhone (preview) off. Opens the change.')
    root=dump(); assert 'Cancel this change?' in texts(root); shot('cancel-confirm')
    assert not any(n.get('password')=='true' for n in root.iter('node'))
    tap('Yes, cancel it'); root=dump(); assert 'Cancelled' in texts(root); shot('cancelled')
    tap('OK'); sh('input','keyevent','KEYCODE_HOME'); time.sleep(1)
    tap('More'); tap('Phone settings'); root=dump(); shot('settings-after-cancel')
    assert 'A change is waiting' not in texts(root)
    assert not any('Opens the change.' in n.get('content-desc','') for n in root.iter('node'))
    instrument('verify')
    assert any('org.plainphone/.PlainphoneAdminReceiver' in s and 'DeviceOwner' in s for s in sh('dpm','list-owners').splitlines())
    records.append('TURN_OFF fixture: U7 present, spoken label exact, existing confirmation opens, cancel without PIN, box absent, durable CANCELLED and zero restore steps, owner retained')
finally:
    if seeded:
        instrument('restore'); home()
        records.append('Original authority and clock restored byte for byte; returned Home')
    sh('rm','-f','/sdcard/preview23-settings.xml')
    (OUT/'waiting-route.json').write_text(json.dumps({'fixtureOnly':True,'noSupportedUiStartRoute':True,'checks':records},indent=2)+'\n')
print(json.dumps(records,indent=2))
