# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Private emulator only. No calls, sends, recovery-code capture, or physical-device commands.
import os,re,subprocess,time,xml.etree.ElementTree as ET
ADB=os.environ['ANDROID_HOME']+'/platform-tools/adb'
SERIAL='emulator-5964'
def adb(*args): return subprocess.check_output([ADB,'-s',SERIAL,*args],text=True,stderr=subprocess.STDOUT,timeout=30).strip()
def nodes():
 adb('shell','uiautomator','dump','/data/local/tmp/pla564.xml')
 return list(ET.fromstring(adb('shell','cat','/data/local/tmp/pla564.xml')).iter('node'))
def labels(): return [n.get('text') or n.get('content-desc') for n in nodes() if n.get('text') or n.get('content-desc')]
def tap_node(n):
 a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.3)
def scroll_forward():
 ns=[n for n in nodes() if n.get('scrollable')=='true']
 if not ns: raise AssertionError('no scrollable body')
 a,b,c,d=map(int,re.findall(r'\d+',ns[0].get('bounds')))
 adb('shell','input','swipe',str((a+c)//2),str(b+3*(d-b)//4),str((a+c)//2),str(b+(d-b)//4),'600')
def tap(label,scroll=False):
 for i in range(14 if scroll else 3):
  n=next((n for n in nodes() if n.get('text')==label or n.get('content-desc')==label),None)
  if n is not None and n.get('bounds')!='[0,0][0,0]': tap_node(n);return
  if scroll: scroll_forward()
  time.sleep(.3)
 raise AssertionError('missing '+label+'')
def pin(value='111111'):
 for _ in range(10):
  n=next((n for n in nodes() if n.get('password')=='true'),None)
  if n is not None: break
  scroll_forward()
 else: raise AssertionError('PIN field not reached')
 tap_node(n);adb('shell','input','text',value)
 if 'mInputShown=true' in adb('shell','dumpsys','input_method'): adb('shell','input','keyevent','BACK')
 tap('Continue')
