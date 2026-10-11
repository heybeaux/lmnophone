#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY: synthetic group-MMS provider fixture; NOT evidence of carrier delivery."""
from ui import *
for s in ['emulator-5572','emulator-5574']:
    command(s,'watch',activity=True);adb(s,'logcat','-c')
    tid=adb(s,'shell',"su 0 content query --uri 'content://mms-sms/threadID?recipient=5550100&recipient=5550101' --projection _id")
    tid=re.search(r'_id=(\d+)',tid).group(1)
    # API 35 already has the first exploratory PDU. Own test data only.
    existing=adb(s,'shell','su','0','content','query','--uri','content://mms/inbox','--projection','_id')
    if 'No result' in existing:
        adb(s,'shell','su','0','content','insert','--uri','content://mms/inbox','--bind',f'thread_id:i:{tid}',
            '--bind','m_type:i:132','--bind','v:i:18','--bind','read:i:0','--bind','seen:i:0',
            '--bind','date:l:1791670000','--bind','ct_t:s:application/vnd.wap.multipart.related')
        existing=adb(s,'shell','su','0','content','query','--uri','content://mms/inbox','--projection','_id')
    mid=re.search(r'_id=(\d+)',existing).group(1)
    for number,kind in [('5550100',137),('5550101',151),('5550102',151)]:
        adb(s,'shell','su','0','content','insert','--uri',f'content://mms/{mid}/addr',
            '--bind',f'address:s:{number}','--bind',f'type:i:{kind}','--bind','charset:i:106')
    adb(s,'shell','su','0','content','insert','--uri',f'content://mms/{mid}/part',
        '--bind','ct:s:text/plain','--bind','text:s:TEST','--bind','chset:i:106')
    command(s,'hist');save(s,'mms-group-before')
    adb(s,'shell','am','force-stop','com.google.android.apps.messaging')
    adb(s,'shell','am','start','-W','-n','com.google.android.apps.messaging/.ui.ConversationListActivity')
    time.sleep(1)
    # The fixed-date synthetic group sorts third behind the later SMS fixtures.
    rows=[n for n in nodes(s) if n.attrib.get('resource-id','').endswith('swipeableContainer')]
    print(s,'conversation-rows',len(rows),flush=True)
    if len(rows) >= 3:tap(s,rows[2])
    time.sleep(.8);command(s,'hist');save(s,'mms-group-open')
    print('\n'.join(l for l in logs(s).splitlines() if 'query=mms-' in l and 'count=0' not in l)[-1000:],flush=True)
