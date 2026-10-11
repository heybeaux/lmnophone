#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY C5. Set probe as Home, revoke one permission at a time, capture PID/readback."""
from run import *
for s in ['emulator-5572','emulator-5574']:
    adb(s,'shell','cmd','package','set-home-activity',PKG+'/.ProbeActivity')
    for perm in ['READ_CALL_LOG','READ_SMS']:
        for kind in ['default','shell-revoke','deny']:
            command(s,'grant');command(s,'query',activity=True)
            adb(s,'logcat','-c')
            before=adb(s,'shell','pidof',PKG,check=False)
            if kind=='shell-revoke':
                command(s,'default','--es','perm',perm)
                action=adb(s,'shell','pm','revoke',PKG,'android.permission.'+perm,check=False)
            else:action=command(s,kind,'--es','perm',perm)
            time.sleep(.8)
            after=adb(s,'shell','pidof',PKG,check=False)
            command(s,'query',activity=True);time.sleep(.2)
            save(s,perm+'-'+kind,f'pid-before={before} pid-after={after}\n{action}')
            print(s,perm,kind,'pid',before,'->',after,flush=True)
    command(s,'grant')
