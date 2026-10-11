#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY C3: match structural IDs and fixed action labels, never message content."""
from ui import *
for s in ['emulator-5572','emulator-5574']:
    adb(s,'logcat','-c');command(s,'hist')
    rows=[n for n in nodes(s) if n.attrib.get('resource-id','').endswith('swipeableContainer')]
    tap(s,rows[1]);time.sleep(.7);command(s,'hist');save(s,'sms-open-conversation')
    print(s,'opened second conversation',flush=True)
    command(s,'watch',activity=True);adb(s,'logcat','-c')
    adb(s,'emu','sms','send','5550100','TEST');time.sleep(1)
    command(s,'hist');adb(s,'shell','cmd','statusbar','expand-notifications');time.sleep(.5)
    matched=False
    for n in nodes(s):
        if n.attrib.get('resource-id','').endswith('expandableNotificationRow') and any(x.attrib.get('resource-id','').endswith('conversation_icon_container') for x in n.iter('node')):
            b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));y=(b[1]+b[3])//2
            adb(s,'shell','input','swipe','500',str(y),'1050',str(y),'250');matched=True;break
    time.sleep(.7);command(s,'hist');save(s,'sms-confirmed-swipe',f'conversation-notification-found={matched}');print(s,'confirmed-swipe',matched,flush=True)
    adb(s,'shell','cmd','statusbar','collapse');adb(s,'logcat','-c')
    adb(s,'emu','sms','send','5550100','TEST');time.sleep(1)
    command(s,'hist');adb(s,'shell','cmd','statusbar','expand-notifications');time.sleep(.5)
    matched=False
    for n in nodes(s):
        if n.attrib.get('text','').lower() in ('mark as read','mark read'):
            tap(s,n);matched=True;break
    time.sleep(.5);command(s,'hist');save(s,'sms-confirmed-markread',f'mark-read-found={matched}');print(s,'markread',matched,flush=True)
    if not matched:
        # Only fixed UI action labels; no notification message text.
        print([(n.attrib.get('text'),n.attrib.get('bounds')) for n in nodes(s) if n.attrib.get('resource-id')=='android:id/action0'],flush=True)
    adb(s,'shell','cmd','statusbar','collapse')
