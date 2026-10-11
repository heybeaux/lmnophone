# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY UI driver. Never prints message text, contact labels or numbers."""
from run import *
import re, xml.etree.ElementTree as ET

def nodes(s):
    adb(s,'shell','uiautomator','dump','/sdcard/pla291.xml')
    return list(ET.fromstring(adb(s,'shell','cat','/sdcard/pla291.xml')).iter('node'))
def tap(s,n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    adb(s,'shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7)
def show(s):
    for n in nodes(s):
        a=n.attrib
        if a.get('clickable')=='true':print(a.get('resource-id'),a.get('bounds'))
def byid(s,suffix):
    for n in nodes(s):
        if n.attrib.get('resource-id','').endswith(suffix):tap(s,n);return True
    return False
def bytext(s,text):
    for n in nodes(s):
        if n.attrib.get('text')==text or n.attrib.get('content-desc')==text:tap(s,n);return True
    return False
