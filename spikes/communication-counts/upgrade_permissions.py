#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY C1: update an installed DO APK that did not declare either count permission."""
from run import *
for s in ['emulator-5572','emulator-5574']:
    adb(s,'push','probe/build/outputs/apk/debug/probe-debug.apk','/data/local/tmp/counts-full.apk')
    for mode in ['none','default','both']:
        adb(s,'shell','dpm','remove-active-admin',PKG+'/.Admin');adb(s,'uninstall',PKG)
        adb(s,'install','-t',os.environ['PAPERCLIP_RUN_SCRATCH_DIR']+'/nocounts.apk')
        adb(s,'shell','dpm','set-device-owner',PKG+'/.Admin')
        adb(s,'logcat','-c')
        result=adb(s,'shell','am','instrument','-w','-e','mode',mode,'-e','path','/data/local/tmp/counts-full.apk',PKG+'/.InstallerInstrumentation')
        time.sleep(1);command(s,'grant');time.sleep(.2)
        dump=adb(s,'shell','dumpsys','package',PKG)
        flags='\n'.join(l for l in dump.splitlines() if any(p in l for p in ['READ_SMS:','READ_CALL_LOG:','installerPackageName=']))
        save(s,'new-permissions-'+mode,result+'\n'+flags);print(s,mode,flags,flush=True)
