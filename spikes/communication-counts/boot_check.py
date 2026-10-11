#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY C6. Owned test AVD only; synthetic PIN. Run phases explicitly."""
from run import *
phase=sys.argv[1]
for s in (sys.argv[2:] or ['emulator-5572','emulator-5574']):
    if phase=='reboot':
        command(s,'grant');command(s,'query');save(s,'before-reboot')
        print(s,adb(s,'shell','locksettings','set-pin','1234'))
        adb(s,'reboot')
    elif phase=='locked':
        # Never treat sys.boot_completed alone as first-unlock evidence.
        print(s,adb(s,'shell','getprop','sys.boot_completed'))
        command(s,'query');save(s,'before-unlock');print(logs(s))
    elif phase=='unlock':
        adb(s,'shell','input','keyevent','KEYCODE_WAKEUP')
        adb(s,'shell','input','swipe','540','2000','540','500','300')
        adb(s,'shell','input','text','1234');adb(s,'shell','input','keyevent','KEYCODE_ENTER')
        time.sleep(2);command(s,'query');save(s,'after-unlock');print(logs(s))
