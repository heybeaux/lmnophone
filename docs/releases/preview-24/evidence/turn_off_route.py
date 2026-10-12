# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned API 35 emulator. Supply PREVIEW_TEST_PIN privately. No waiting operation is seeded.
Back up first using PreviewTurnOffInstrumentation; restore after this test.
Use clean-enrollment only when the prior emulator PIN is unavailable, then enroll via UI.
"""
import os,subprocess,time,re,xml.etree.ElementTree as E
from pathlib import Path
ADB='/Users/beauxwalton/Library/Android/sdk/platform-tools/adb'
def adb(*a):return subprocess.check_output([ADB,'-s','emulator-5880',*a],timeout=50)
def sh(*a):return adb('shell',*a).decode().strip()
def dump():
 sh('input','keyevent','KEYCODE_WAKEUP');sh('wm','dismiss-keyguard')
 sh('uiautomator','dump','/data/local/tmp/preview24-ui.xml')
 return E.fromstring(sh('cat','/data/local/tmp/preview24-ui.xml'))
def texts(root):return [n.get('text') for n in root.iter('node') if n.get('text')]
def press(n):
 x,y,xx,yy=map(int,re.findall(r'\d+',n.get('bounds')))
 assert xx>x and yy>y
 sh('input','tap',str((x+xx)//2),str((y+yy)//2));time.sleep(.4)
def tap(label):
 for i in range(8):
  root=dump();nodes=[n for n in root.iter('node') if label in [n.get('text'),n.get('content-desc')]]
  if nodes: assert len(nodes)==1;press(nodes[0]);return
  sh('input','swipe','540','1800','540','600','300')
 raise AssertionError((label,texts(root)))
def home():
 sh('am','start','-n','org.plainphone/.MainActivity');time.sleep(2)
 sh('input','keyevent','KEYCODE_HOME');time.sleep(.5)
def instrument(phase):
 out=sh('am','instrument','-w','-e','phase',phase,'org.plainphone.test/org.plainphone.PreviewTurnOffInstrumentation')
 assert 'outcome=PASS' in out,out
 return out
def pin():
 root=dump();nodes=[n for n in root.iter('node') if n.get('class')=='android.widget.EditText']
 assert nodes,texts(root)
 for n in nodes:
  press(n);sh('input','text',os.environ['PREVIEW_TEST_PIN'])
 sh('input','keyevent','KEYCODE_BACK');time.sleep(.3);tap('Continue')

import json
OUT=Path(__file__).resolve().parent

def start_wait():
    home();tap('More');tap('For Sam and Jo');pin();time.sleep(1)
    tap('Turn LMNOPhone off');tap('Continue');tap('Yes, turn it off');pin();time.sleep(1)
    assert 'Last check' in texts(dump())
    tap('Start the 24-hour wait');assert 'Waiting 24 hours' in texts(dump())
    tap('OK');assert any('will turn off after' in t for t in texts(dump()))
    print('PASS: UI start; separate fresh PIN; two confirmations; receipt OK returns Home',flush=True)

def state():
    current=None;hidden=[];suspended=[]
    for line in sh('dumpsys','package','--all-components').splitlines():
        m=re.match(r'  Package \[([^]]+)\]',line)
        if m:current=m.group(1)
        if current and 'User 0:' in line:
            if 'hidden=true' in line:hidden.append(current)
            if 'suspended=true' in line:suspended.append(current)
    device=sh('dumpsys','device_policy');activity=sh('dumpsys','activity','activities')
    assert 'org.plainphone/.PlainphoneAdminReceiver' in device
    assert 'mLockTaskModeState=LOCKED' in activity
    locks=sorted(set(l.strip() for l in device.splitlines() if 'LockTaskPolicy {' in l))
    assert locks and hidden
    return {'hidden':sorted(hidden),'suspended':sorted(suspended),'lock_task':locks,
        'restrictions':sorted(l.strip() for l in device.splitlines() if 'UserRestrictionPolicyKey' in l),
        'device_owner':True,'lock_task_state':'LOCKED'}

assert adb('emu','avd','name').decode().splitlines()[0]=='pla363-policy-a35'
assert sh('getprop','ro.build.version.sdk')=='35'
assert 'versionCode=24 ' in sh('dumpsys','package','org.plainphone')
start_wait()
(OUT/'home-pending.png').write_bytes(adb('exec-out','screencap','-p'))
tap('More');tap('Phone settings');root=dump()
label=next(n.get('content-desc') for n in root.iter('node') if n.get('content-desc','').startswith('A change is waiting,'))
(OUT/'settings-pending.png').write_bytes(adb('exec-out','screencap','-p'));tap(label)
root=dump();assert 'Cancel this change?' in texts(root)
assert not any(n.get('password')=='true' for n in root.iter('node'))
tap('Yes, cancel it');assert 'Cancelled' in texts(dump());tap('OK');home()
assert not any('will turn off after' in t for t in texts(dump()))
tap('More');tap('Phone settings')
assert not any(n.get('content-desc','').startswith('A change is waiting,') for n in dump().iter('node'))
(OUT/'settings-cancelled.png').write_bytes(adb('exec-out','screencap','-p'))
print(instrument('cancelled'),flush=True)
(OUT/'cancel.json').write_text(json.dumps({'UI_start':True,'fixture_start':False,'fresh_PIN':True,
    'both_confirmations':True,'home_pending':True,'settings_box':True,'cancel_without_PIN':True,
    'both_removed':True,'terminal':'CANCELLED; zero restore steps'},indent=2)+'\n')
start_wait();before=state()
print(instrument('mature'),flush=True);home();visible=texts(dump())
assert "Turning off isn't in this test build. Nothing changed." in visible,visible
(OUT/'home-matured.png').write_bytes(adb('exec-out','screencap','-p'));after=state();assert before==after
assert not any('will turn off after' in t for t in visible)
tap('More');tap('Phone settings')
assert not any(n.get('content-desc','').startswith('A change is waiting,') for n in dump().iter('node'))
print(instrument('closed'),flush=True);home()
assert "Turning off isn't in this test build. Nothing changed." in texts(dump())
(OUT/'maturity.json').write_text(json.dumps({'before':before,'after':after,'unchanged':True,
    'terminal':'CANCELLED; zero restore steps; no COMMITTING receipt','restart_notice':True},indent=2)+'\n')
print('PASS: maturity; unchanged DO, hidden/suspended sets, restrictions and LOCKED; restart notice',flush=True)
