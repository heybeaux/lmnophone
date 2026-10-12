# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Build 21 owned emulator exercise. Never touches a call control or a physical device."""
import datetime, json, re, subprocess, time, xml.etree.ElementTree as E
from pathlib import Path
OUT = Path(__file__).resolve().parent
SERIAL = 'emulator-5880'
def adb(*args): return subprocess.check_output(['adb', '-s', SERIAL, *args], timeout=60)
def sh(*args): return adb('shell', *args).decode().strip()
def dump():
    sh('uiautomator', 'dump', '/sdcard/preview21-settings.xml')
    return E.fromstring(sh('cat', '/sdcard/preview21-settings.xml'))
def texts(root): return [n.get('text') for n in root.iter('node') if n.get('text')]
def bounds(n): return list(map(int, re.findall(r'\d+', n.get('bounds'))))
def tap(label, index=0):
    assert label in ['More','Phone settings','Brightness','Battery','Sound','−','+','Adjust automatically','Done','‹ Phone settings']
    root=dump(); matches=[n for n in root.iter('node') if n.get('text')==label]
    assert len(matches)>index,(label,texts(root))
    x,y,xx,yy=bounds(matches[index]);assert xx>x and yy>y
    sh('input','tap',str((x+xx)//2),str((y+yy)//2));time.sleep(.25)
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def read(stream):
    raw=sh('cmd','media_session','volume','--stream',str(stream),'--get')
    m=re.search(r'volume is (\d+) in range \[(\d+)\.\.(\d+)\]',raw);assert m,raw
    return dict(zip(['level','min','max'],map(int,m.groups())))
def record(action,stream,expected=None):
    value=read(stream)
    if expected is not None: assert value['level']==expected,(action,value,expected)
    assert "Volume couldn't be changed." not in texts(dump())
    records.append({'action':action,'stream':stream,**value});print('PASS',records[-1],flush=True)
def slider(index,position):
    seek=[n for n in dump().iter('node') if n.get('class')=='android.widget.SeekBar'][index]
    x,y,xx,yy=bounds(seek);cy=(y+yy)//2
    if position=='min': sh('input','swipe',str((x+xx)//2),str(cy),'0',str(cy),'350')
    elif position=='max': sh('input','swipe',str((x+xx)//2),str(cy),'359',str(cy),'350')
    else: sh('input','tap',str((x+xx)//2),str(cy))
    time.sleep(.25)
assert adb('emu','avd','name').decode().splitlines()[0]=='pla363-policy-a35'
assert sh('getprop','ro.build.version.sdk')=='35'
original={s:read(s) for s in (2,3)}
brightness={k:sh('settings','get','system',k) for k in ['screen_brightness','screen_brightness_mode','font_scale']}
records=[]
try:
    sh('input','keyevent','KEYCODE_WAKEUP');sh('wm','dismiss-keyguard')
    sh('wm','size','360x640');sh('wm','density','160');sh('settings','put','system','font_scale','1.0')
    sh('input','keyevent','KEYCODE_HOME');time.sleep(1)
    tap('More');assert 'Brightness, sound, battery' in texts(dump())
    tap('Phone settings');visible=texts(dump());assert visible.index('Brightness')<visible.index('Sound')<visible.index('Battery');shot('settings')
    tap('Sound');shot('sound-open')
    for index,stream in enumerate((2,3)):
        before=read(stream)['level'];tap('+',index);record('plus',stream,min(before+1,read(stream)['max']))
        tap('−',index);record('minus',stream,before)
        slider(index,'max');record('slider maximum',stream,read(stream)['max'])
        slider(index,'min');record('slider floor',stream,1 if stream==2 else 0)
        tap('−',index);record('minus at floor',stream,1 if stream==2 else 0)
        slider(index,'middle');record('slider middle',stream)
    shot('sound-after');tap('Done')
    tap('Brightness')
    if sh('settings','get','system','screen_brightness_mode')=='0':tap('Adjust automatically')
    assert not any(re.fullmatch(r'\d+%',t) for t in texts(dump()));shot('brightness-automatic')
    tap('Adjust automatically');assert any(re.fullmatch(r'\d+%',t) for t in texts(dump()));tap('Done')
    tap('Battery');sh('dumpsys','battery','set','status','5');time.sleep(.4)
    assert 'Charged' in texts(dump()) and 'Charging' not in texts(dump());shot('battery-full')
    records.append({'action':'automatic hides percentage; manual restores it; battery full shows Charged'})
    sh('dumpsys','battery','reset');tap('‹ Phone settings')
    sh('settings','put','system','font_scale','2.0');time.sleep(.4);tap('Sound');shot('sound-2.0')
    assert 'Done' in texts(dump());sh('input','swipe','180','480','180','230','350');time.sleep(.3)
    shot('sound-2.0-scrolled');assert 'Media volume' in texts(dump());tap('Done')
finally:
    for stream,v in original.items():sh('cmd','media_session','volume','--stream',str(stream),'--set',str(v['level']))
    sh('dumpsys','battery','reset')
    for k,v in brightness.items():sh('settings','put','system',k,v)
    sh('wm','size','reset');sh('wm','density','reset');sh('input','keyevent','KEYCODE_HOME')
    for stream,v in original.items():assert read(stream)==v
    (OUT/'sound-route.json').write_text(json.dumps({'timeUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'records':records,'originalAndRestored':original},indent=2)+'\n')
print('PASS build 21 routes and volume restoration',flush=True)
