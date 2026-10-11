#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY C1. Removes/reinstalls only our probe on our two owned emulators."""
from run import *
def flags(s):
    dump=adb(s,'shell','dumpsys','package',PKG)
    return '\n'.join(l for l in dump.splitlines() if any(p in l for p in ['READ_SMS:','READ_CALL_LOG:','installerPackageName=']))
def restricted_baseline(s):
    adb(s,'shell','dpm','remove-active-admin',PKG+'/.Admin')
    adb(s,'uninstall',PKG)
    adb(s,'install','-t','--restrict-permissions','probe/build/outputs/apk/debug/probe-debug.apk')
    adb(s,'shell','dpm','set-device-owner',PKG+'/.Admin')
    adb(s,'logcat','-c');command(s,'grant');time.sleep(.3)
    save(s,'adb-restricted',flags(s))
for s in ['emulator-5572','emulator-5574']:
    for mode in ['none','default','both']:
        restricted_baseline(s)
        adb(s,'logcat','-c')
        result=adb(s,'shell','am','instrument','-w','-e','mode',mode,PKG+'/.InstallerInstrumentation')
        time.sleep(2);command(s,'grant');time.sleep(.3)
        save(s,'restricted-session-'+mode,result+'\n'+flags(s))
        print(s,mode,flags(s),flush=True)
        print('\n'.join(l for l in logs(s).splitlines() if any(k in l for k in ['install-status','permission=','unlocked='])),flush=True)
