# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned API 35 U7 exercise. No call controls, credentials, or physical devices."""
import datetime, json, re, subprocess, time, xml.etree.ElementTree as E
from pathlib import Path
OUT = Path(__file__).resolve().parent
SERIAL = 'emulator-5880'
def adb(*args):
    return subprocess.check_output(['adb', '-s', SERIAL, *args], timeout=60)
def sh(*args): return adb('shell', *args).decode().strip()
def dump():
    sh('uiautomator', 'dump', '/sdcard/preview20-settings.xml')
    return E.fromstring(sh('cat', '/sdcard/preview20-settings.xml'))
def texts(root): return [n.get('text') for n in root.iter('node') if n.get('text')]
def bounds(n): return list(map(int, re.findall(r'\d+', n.get('bounds'))))
def tap(label):
    assert label in ['More','Phone settings','Brightness','Battery','−','+','Adjust automatically','Done','‹ Phone settings']
    for _ in range(8):
        root = dump()
        for n in root.iter('node'):
            if n.get('text') == label:
                x,y,xx,yy = bounds(n)
                if xx>x and yy>y:
                    sh('input','tap',str((x+xx)//2),str((y+yy)//2));time.sleep(.3);return
        time.sleep(.5)
    raise AssertionError('Missing '+label+' in '+str(texts(root)))
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def home(): sh('input','keyevent','KEYCODE_HOME');time.sleep(.7)
def reading(): return {'raw':int(sh('settings','get','system','screen_brightness')), 'mode':int(sh('settings','get','system','screen_brightness_mode'))}
def record(action, raw=None, mode=None):
    value=reading()
    if raw is not None: assert value['raw']==raw,(action,value,raw)
    if mode is not None: assert value['mode']==mode,(action,value,mode)
    visible=texts(dump())
    assert 'Done' in visible, (action, visible)
    assert "Brightness couldn't be changed." not in visible,action
    records.append({'action':action,**value})
    print('PASS',action,value,flush=True)
assert adb('emu','avd','name').decode().splitlines()[0]=='pla363-policy-a35'
assert sh('getprop','ro.build.version.sdk')=='35'
original=reading(); records=[]
try:
    sh('input','keyevent','KEYCODE_WAKEUP');sh('wm','dismiss-keyguard')
    sh('wm','size','360x640');sh('wm','density','160')
    sh('settings','put','system','font_scale','1.0')
    home();tap('More');tap('Phone settings');shot('settings')
    root=texts(dump());assert root.index('Brightness')<root.index('Battery')
    tap('Brightness')
    if reading()['mode']==1:tap('Adjust automatically')
    start=reading()['raw'];start_percent=round(start*100/255)
    tap('+');record('plus',int(min(start_percent+10,100)*255/100+.5),0)
    tap('−');record('minus',int(start_percent*255/100+.5),0)
    while reading()['raw']>26:tap('−');record('minus toward floor',mode=0)
    record('floor',26,0);tap('−');record('minus at floor',26,0);shot('brightness-floor')
    # Actual slider gesture at its right end and back to the left edge.
    slider=next(n for n in dump().iter('node') if n.get('class')=='android.widget.SeekBar')
    x,y,xx,yy=bounds(slider)
    sh('input','swipe',str(x+16),str((y+yy)//2),str(xx-16),str((y+yy)//2),'350');time.sleep(.4)
    record('slider drag up',mode=0)
    assert reading()['raw'] > 26
    sh('input','tap',str(xx-12),str((y+yy)//2));time.sleep(.4)
    record('slider maximum',255,0)
    sh('input','tap',str((x+xx)//2),str((y+yy)//2));time.sleep(.4)
    sh('input','swipe',str((x+xx)//2),str((y+yy)//2),'0',str((y+yy)//2),'350');time.sleep(.4)
    record('slider floor',26,0)
    tap('Adjust automatically');record('automatic on',mode=1);shot('brightness-automatic')
    tap('+');record('manual plus disabled in automatic',mode=1)
    tap('Adjust automatically');record('automatic off',mode=0)
    assert reading()['raw']>=26
    tap('Done');assert 'Battery' in texts(dump())
    tap('Battery');raw_battery=sh('dumpsys','battery')
    level=int(re.search(r'^\s*level: (\d+)',raw_battery,re.M)[1])
    assert f'Battery: {level}%' in texts(dump());shot('battery-live')
    records.append({'action':'battery matches dumpsys','level':level})
    # Synthetic emulator state proves live delivery without leaving the page.
    sh('dumpsys','battery','set','level','64');sh('dumpsys','battery','set','status','3');time.sleep(.5)
    assert 'Battery: 64%' in texts(dump()) and 'Not charging' in texts(dump())
    shot('battery-updated');records.append({'action':'battery live update','level':64,'charging':False})
    sh('dumpsys','battery','reset');time.sleep(.5)
    tap('‹ Phone settings');assert 'Brightness' in texts(dump())
    tap('Brightness');sh('input','keyevent','KEYCODE_BACK');assert 'Battery' in texts(dump())
    # Real Android layout at largest font; body and Done remain accessible.
    sh('settings','put','system','font_scale','2.0');time.sleep(.5)
    tap('Brightness');assert 'Done' in texts(dump());shot('brightness-2.0')
    tap('Done');tap('Battery');assert any(t.startswith('Battery:') for t in texts(dump()));shot('battery-2.0')
finally:
    sh('dumpsys','battery','reset')
    sh('settings','put','system','screen_brightness',str(original['raw']))
    sh('settings','put','system','screen_brightness_mode',str(original['mode']))
    sh('settings','put','system','font_scale','1.0');sh('wm','size','reset');sh('wm','density','reset');home()
    assert reading()==original
(OUT/'basic-settings-route.json').write_text(json.dumps({'timeUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'records':records,'originalAndRestored':original},indent=2)+'\n')
print('PASS U7 routes and restoration',flush=True)
