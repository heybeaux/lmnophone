# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Same temporary test credential and locked-boot readiness for both timing samples.
import subprocess,time,os,json,re
from pathlib import Path
A='adb'
S=__import__('sys').argv[2]
assert S.startswith('emulator-')
out=Path(__file__).parent/'emulator'
label=__import__('sys').argv[1]
def adb(*args,check=True):
 return subprocess.run([A,'-s',S,*args],capture_output=True,text=True,timeout=25,check=check).stdout.strip()
assert adb('emu','avd','name').splitlines()[0]=='pla353-ui-a35'
adb('reboot');time.sleep(5)
for i in range(150):
 boot=adb('shell','getprop','init.svc.bootanim',check=False)
 users=adb('shell','dumpsys','user',check=False)
 log=adb('logcat','-d','-v','epoch','-s','PlainphoneBoot',check=False)
 if boot=='stopped' and 'RUNNING_LOCKED' in users and 'locked=true' in log: break
 time.sleep(1)
else: raise RuntimeError('locked boot not ready')
(out/f'{label}-locked-boot.txt').write_text(log+'\n')
adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','input','swipe','540','1800','540','600','200');time.sleep(1)
adb('shell','input','text',os.environ['PLAINPHONE_TEST_PIN'])
# Device wall clock before submitting the credential; event log records the actual unlock transition.
start=adb('shell','date','+%s.%N')
adb('shell','input','keyevent','KEYCODE_ENTER')
for i in range(90):
 log=adb('logcat','-d','-v','epoch','-s','PlainphoneBoot')
 if 'home shown' in log: break
 time.sleep(.4)
else: raise RuntimeError('Home not shown')
activity=adb('shell','dumpsys','activity','activities')
assert 'mLockTaskModeState=LOCKED' in activity
assert 'org.plainphone/.MainActivity' in activity
# Allow BOOT_COMPLETED pass to arrive, without altering the timing sample.
time.sleep(5)
log=adb('logcat','-d','-v','epoch','-s','PlainphoneBoot')
events=adb('logcat','-d','-b','events','-v','epoch')
events='\n'.join(l for l in events.splitlines() if re.search('user.*(unlock|state)|uc_|boot_progress',l,re.I))
(out/f'{label}-boot.txt').write_text(log+'\n')
(out/f'{label}-unlock-events.txt').write_text(events+'\n')
result={'credential_submit_device_epoch':start,'lock_state':'LOCKED','log_lines':[l for l in log.splitlines() if 'home shown' in l or 'reapply reason=' in l]}
(out/f'{label}-timing.json').write_text(json.dumps(result,indent=2)+'\n'); print(json.dumps(result,indent=2));print(events)
