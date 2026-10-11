#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY C4. Synthetic flag updates; time bounds use device uptime, not host clock."""
from run import *
import re
for s in (sys.argv[1:] or ['emulator-5572','emulator-5574']):
    command(s,'watch',activity=True);time.sleep(.3)
    for source,uri,column in [('calls','content://call_log/calls','new'),('sms','content://sms','read'),('mms','content://mms','read')]:
        for value in [0,1]:
            adb(s,'logcat','-c')
            before=float(adb(s,'shell','cat','/proc/uptime').split()[0])*1000
            action=adb(s,'shell','su','0','content','update','--uri',uri,'--bind',f'{column}:i:{value}')
            after=float(adb(s,'shell','cat','/proc/uptime').split()[0])*1000
            time.sleep(.4);command(s,'query')
            save(s,f'latency-{source}-{value}',f'event-uptime-before-ms={before} after-ms={after}\n{action}')
            obs=[int(v) for v in re.findall(r't=(\d+).*observer=',logs(s))]
            print(s,source,value,'first-callback-ms-upper-bound',obs[0]-before if obs else 'none',flush=True)
