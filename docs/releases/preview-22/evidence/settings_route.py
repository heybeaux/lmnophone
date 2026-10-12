# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned API 35 emulator only; no call or SOS actions."""
import datetime, json, re, subprocess, time, xml.etree.ElementTree as E
from pathlib import Path
OUT = Path(__file__).resolve().parent
SERIAL = 'emulator-5880'
def adb(*args): return subprocess.check_output(['adb', '-s', SERIAL, *args], timeout=60)
def sh(*args): return adb('shell', *args).decode().strip()
def dump():
    sh('uiautomator', 'dump', '/sdcard/preview22-settings.xml')
    return E.fromstring(sh('cat', '/sdcard/preview22-settings.xml'))
def texts(root): return [n.get('text') for n in root.iter('node') if n.get('text')]
def bounds(n): return list(map(int, re.findall(r'\d+', n.get('bounds'))))
def tap(label):
    assert label in ['More', 'Phone settings', 'Brightness', 'Sound', 'Done', 'Raise media volume', 'Lower ring volume', 'Raise ring volume']
    root = dump(); matches = [n for n in root.iter('node') if n.get('text') == label or n.get('content-desc') == label]
    assert len(matches) == 1, (label, texts(root))
    x,y,xx,yy = bounds(matches[0]); assert xx>x and yy>y
    sh('input','tap',str((x+xx)//2),str((y+yy)//2)); time.sleep(.25)
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def read(stream):
    raw=sh('cmd','media_session','volume','--stream',str(stream),'--get')
    m=re.search(r'volume is (\d+) in range \[(\d+)\.\.(\d+)\]',raw); assert m, raw
    return int(m[1])
def mode():
    raw=sh('dumpsys','audio')
    return re.search(r'mode \(external\) = (\w+)',raw)[1]
def zen(): return sh('settings','get','global','zen_mode')
def change_volume(stream, level): sh('cmd','media_session','volume','--stream',str(stream),'--set',str(level))
def check_values(ring, media):
    ts=texts(dump())
    assert ts[ts.index('Ring volume')+1] == str(ring), ts
    assert ts[ts.index('Media volume')+1] == str(media), ts
    assert "Volume couldn't be changed." not in ts
    records.append({'action':'outside stream change reflected on open page','ring':ring,'media':media})
def check_enabled(label, enabled, root=None):
    ns=[n for n in (root if root is not None else dump()).iter('node') if n.get('content-desc')==label]
    assert len(ns)==1,(label,len(ns))
    assert ns[0].get('enabled')==str(enabled).lower(), (label,ns[0].attrib)
assert adb('emu','avd','name').decode().splitlines()[0]=='pla363-policy-a35'
assert sh('getprop','ro.build.version.sdk')=='35'
original={str(s):read(s) for s in (2,3)}
original_mode=mode(); original_zen=zen()
brightness={k:sh('settings','get','system',k) for k in ['screen_brightness','screen_brightness_mode','font_scale']}
records=[]
try:
    sh('input','keyevent','KEYCODE_WAKEUP'); sh('wm','dismiss-keyguard')
    sh('wm','size','360x640'); sh('wm','density','160'); sh('settings','put','system','font_scale','1.0')
    sh('cmd','audio','set-ringer-mode','NORMAL'); assert mode()=='NORMAL'
    sh('input','keyevent','KEYCODE_HOME'); time.sleep(1)
    tap('More'); tap('Phone settings'); tap('Sound')
    change_volume(2,3); change_volume(3,6); time.sleep(1.1); shot('sound-external-first'); check_values(3,6)
    change_volume(2,4); change_volume(3,8); time.sleep(1.1); shot('sound-external-second'); check_values(4,8)
    sh('input','keyevent','VOLUME_DOWN'); time.sleep(1.1)
    check_values(read(2),read(3)); records[-1]['action']='side-key snapshot matched Android; stream changes proven separately by cmd media_session'; shot('sound-side-button')
    root=dump()
    for label in ['Lower ring volume','Raise ring volume','Lower media volume','Raise media volume','Ring volume','Media volume']:
        check_enabled(label,True,root)
    sh('cmd','audio','set-ringer-mode','VIBRATE'); assert mode()=='VIBRATE'; time.sleep(1.1)
    root=dump(); ts=texts(root); assert "Ring volume can't be changed while the phone is on silent or vibrate." in ts
    for label in ['Ring volume','Lower ring volume','Raise ring volume']: check_enabled(label,False,root)
    for label in ['Media volume','Lower media volume','Raise media volume']: check_enabled(label,True,root)
    shot('sound-vibrate')
    before=read(2); tap('Raise ring volume'); assert read(2)==before and mode()=='VIBRATE'
    before_media=read(3)
    sh('input','swipe','180','480','180','270','350');time.sleep(.3)
    tap('Raise media volume'); assert read(3)==before_media+1 and mode()=='VIBRATE'
    shot('sound-vibrate-media')
    assert zen()==original_zen
    records.append({'action':'vibrate disables ring; attempted ring tap unchanged; media increment works; DND unchanged'})
    tap('Done'); sh('cmd','audio','set-ringer-mode','NORMAL'); assert mode()=='NORMAL'
    tap('Sound');time.sleep(1.1);assert "Ring volume can't be changed while the phone is on silent or vibrate." not in texts(dump())
    check_enabled('Raise ring volume',True); tap('Done')
    sh('settings','put','system','screen_brightness_mode','0'); tap('Brightness')
    for raw,percent in [(102,'40%'),(204,'80%')]:
        sh('settings','put','system','screen_brightness',str(raw));time.sleep(1.1)
        shot('brightness-external-'+str(raw)); assert percent in texts(dump())
    for label in ['Lower brightness','Raise brightness','Brightness']:check_enabled(label,True)
    records.append({'action':'outside manual brightness changes reflected as 40% and 80%; all nine labels checked'})
    tap('Done')
finally:
    sh('cmd','audio','set-ringer-mode','NORMAL')
    for stream,level in original.items():change_volume(int(stream),level)
    sh('cmd','audio','set-ringer-mode',original_mode)
    for k,v in brightness.items():sh('settings','put','system',k,v)
    sh('wm','size','reset'); sh('wm','density','reset'); sh('input','keyevent','KEYCODE_HOME')
    for stream,level in original.items():assert read(int(stream))==level
    assert mode()==original_mode and zen()==original_zen
    for k,v in brightness.items(): assert sh('settings','get','system',k)==v
    result={'timeUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'records':records,'originalAndRestoredVolumes':original,'originalAndRestoredRingerMode':original_mode,'originalAndRestoredZenMode':original_zen,'originalAndRestoredBrightness':brightness,'sampleWaitSeconds':1.1,'note':'Screenshots taken after the 1.1 s wait; XML assertions add UI-dump latency.'}
    (OUT/'settings-route.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS build 22 live settings, spoken labels, vibrate and restoration',flush=True)
