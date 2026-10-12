# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned API 35 About readback. Start with stock display size/density and scale 1.0.
Uses sample identities only. Never types a PIN, places a call, or runs an SOS gesture.
"""
import datetime,json,os,re,subprocess,time,xml.etree.ElementTree as E
from pathlib import Path
OUT=Path(__file__).resolve().parent; ADB='adb'; SERIAL='emulator-5880'
def adb(*args):
 p=subprocess.run([ADB,'-s',SERIAL,*args],capture_output=True,timeout=60)
 if p.returncode:raise RuntimeError('adb command failed')
 return p.stdout
def sh(*args):return adb('shell',*args).decode().strip()
def dump():
 sh('input','keyevent','KEYCODE_WAKEUP');sh('wm','dismiss-keyguard')
 sh('uiautomator','dump','/sdcard/preview18-route.xml')
 return E.fromstring(sh('cat','/sdcard/preview18-route.xml'))
def texts(r):return [n.get('text') for n in r.iter('node') if n.get('text') and n.get('password')!='true']
def tap(label):
 assert label in ['More','About this phone','OK','For Sam and Jo']
 for _ in range(14):
  r=dump(); ns=[n for n in r.iter('node') if n.get('text')==label]
  if ns:
   x,y,xx,yy=map(int,re.findall(r'\d+',ns[0].get('bounds')))
   if xx>x and yy>y:
    sh('input','tap',str((x+xx)//2),str((y+yy)//2));time.sleep(.5);return
  sh('input','swipe','180','475','180','150','220')
 raise RuntimeError('missing '+label)
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def home():sh('input','keyevent','KEYCODE_HOME');time.sleep(.8)
def opened():home();tap('More');tap('About this phone')
records=[]
assert adb('emu','avd','name').decode().splitlines()[0]=='pla363-policy-a35'
assert sh('getprop','ro.build.version.sdk')=='35'
try:
 sh('wm','size','360x640');sh('wm','density','160')
 for scale in [1.0,1.3,1.5,1.8,2.0]:
  sh('settings','put','system','font_scale',str(scale));time.sleep(1)
  opened(); initial=texts(dump())
  assert any('Test build 0.1.0-preview-18.' in t for t in initial)
  shot(f'about-{scale}-top')
  seen=set(initial);pages=[];prev=None
  for i in range(24):
   r=dump(); current=texts(r);seen.update(current)
   assert not any('power button' in t for t in current)
   assert 'Who to call for help' not in current
   if i in [2,5,9]:shot(f'about-{scale}-scroll-{i}')
   if all(t in current for t in ['To call 9-1-1','Tap Phone, type 9-1-1, and press call.','Or tap More, then Emergency 9-1-1.','From the lock screen, tap Emergency call.']):
    shot(f'about-{scale}-emergency');break
   sh('input','swipe','180','470','180','155','240');time.sleep(.15)
  else:raise RuntimeError('emergency section not fully reachable at '+str(scale))
  assert 'Sam and Jo are equals: either can add apps with you.' in seen
  assert any('Sam and Jo helps choose' in t for t in seen)
  assert any("connects to Sam's computer" in t for t in seen)
  assert any('sends Sam and Jo a text' in t for t in seen)
  assert any('last 90 days' in t for t in seen)
  tap('OK');assert 'More' in texts(dump())
  tap('About this phone');sh('input','keyevent','KEYCODE_BACK');assert 'More' in texts(dump())
  records.append({'fontScale':scale,'sizeDp':[360,640],'copySectionsReachable':True,'emergencySectionReachable':True,'okReturnsToMore':True,'backReturnsToMore':True})
  print('PASS About scroll/OK/Back '+str(scale),flush=True)
finally:
 sh('settings','put','system','font_scale','1.0');sh('wm','size','reset');sh('wm','density','reset');home()
(OUT/'about-route.json').write_text(json.dumps({'timeUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'runs':records},indent=2)+'\n')
# Existing record still selects PIN entry. Never guess, overwrite, or export credentials.
home();tap('More');tap('For Sam and Jo');visible=texts(dump())
assert any('Enter your PIN' in t for t in visible)
assert not any('Choose a new PIN' in t for t in visible)
(OUT/'pin-entry.json').write_text(json.dumps({'existingPinEntry':True,'setupOffered':False,'correctPinReentered':False,'reason':'Build-17 random PIN was retained only in the completed author process.'},indent=2)+'\n')
home();shot('home-final')
