#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""THROWAWAY. Exact owned emulator serials; only redacted probe logcat output."""
import os, pathlib, subprocess, sys, time
ADB = str(pathlib.Path(os.environ.get('ANDROID_HOME',os.path.expanduser('~/Library/Android/sdk')))/'platform-tools/adb')
PKG = 'com.plainphone.spike.counts'
def adb(serial,*args,check=True):
    assert serial in ('emulator-5572','emulator-5574'), 'Owned emulators only'
    r=subprocess.run([ADB,'-s',serial,*args],capture_output=True,text=True,timeout=45)
    if check and r.returncode: raise RuntimeError(r.stderr)
    return (r.stdout+r.stderr).strip()
def command(serial,cmd,*extras,activity=False):
    kind=['start','-n',PKG+'/.ProbeActivity'] if activity else ['broadcast','--include-stopped-packages','-n',PKG+'/.Control']
    return adb(serial,'shell','am',*kind,'--es','cmd',cmd,*extras)
def logs(serial):
    return adb(serial,'logcat','-d','-v','brief','-s','CountsProbe:I','*:S')
def save(serial,name,extra=''):
    pathlib.Path('evidence',serial+'-'+name+'.txt').write_text(extra+'\n'+logs(serial)+'\n')
if __name__=='__main__':
    s=sys.argv[1]; task=sys.argv[2]
    if task=='init':
        print(adb(s,'install','-t','probe/build/outputs/apk/debug/probe-debug.apk'))
        print(adb(s,'shell','dpm','set-device-owner',PKG+'/.Admin'))
        adb(s,'logcat','-c')
        command(s,'query',activity=True)
        command(s,'grant',activity=True)
        command(s,'watch',activity=True)
        info=''
        for prop in ['ro.build.version.sdk','ro.build.fingerprint']:
            info+=prop+'='+adb(s,'shell','getprop',prop)+'\n'
        for role in ['DIALER','SMS']:
            info+=role+'='+adb(s,'shell','cmd','role','get-role-holders','android.app.role.'+role)+'\n'
        dump=adb(s,'shell','dumpsys','package',PKG)
        info+='\n'.join(l for l in dump.splitlines() if any(p in l for p in ['READ_SMS:','READ_CALL_LOG:','installerPackageName=']))
        save(s,'init',info);print(info);print(logs(s))
    else:
        command(s,task,*sys.argv[3:]);time.sleep(.5);print(logs(s))
