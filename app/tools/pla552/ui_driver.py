# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Private emulator only. No calls, sends, recovery-code capture, or physical-device commands.
import os,re,subprocess,time,xml.etree.ElementTree as ET
ADB=os.environ['ANDROID_HOME']+'/platform-tools/adb'
SERIAL='emulator-5780'
def adb(*args): return subprocess.check_output([ADB,'-s',SERIAL,*args],text=True,stderr=subprocess.STDOUT,timeout=30).strip()
def nodes():
 adb('shell','uiautomator','dump','/data/local/tmp/pla552.xml')
 return list(ET.fromstring(adb('shell','cat','/data/local/tmp/pla552.xml')).iter('node'))
def labels(): return [n.get('text') or n.get('content-desc') for n in nodes() if n.get('text') or n.get('content-desc')]
def tap_node(n):
 a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.3)
def tap(label,scroll=False):
 for i in range(8 if scroll else 3):
  n=next((n for n in nodes() if n.get('text')==label or n.get('content-desc')==label),None)
  if n is not None and n.get('bounds')!='[0,0][0,0]': tap_node(n);return
  if scroll: adb('shell','input','swipe','180','480','180','190','300')
  time.sleep(.3)
 raise AssertionError('missing '+label+'')
def pin(value='111111'):
 n=next(n for n in nodes() if n.get('password')=='true')
 tap_node(n);adb('shell','input','text',value);adb('shell','input','keyevent','BACK');tap('Continue')
