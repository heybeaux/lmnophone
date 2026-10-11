# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
import subprocess,time,os,re,json,sys
from pathlib import Path
adb='adb'
out=Path(os.environ['PAPERCLIP_SCRATCH_DIR'])/'profiles';out.mkdir(exist_ok=True)
def a(*args): return subprocess.check_output([adb,'-s','emulator-5880',*args],timeout=30,text=True)
assert a('emu','avd','name').splitlines()[0]=='pla363-policy-a35'
label=sys.argv[1]
a('reboot');time.sleep(3)
a('wait-for-device')
for _ in range(60):
 try:
  if a('shell','getprop','sys.boot_completed').strip()=='1': break
 except subprocess.CalledProcessError: pass
 time.sleep(1)
a('shell','input','keyevent','KEYCODE_WAKEUP')
a('shell','input','swipe','540','2000','540','500','500')
for _ in range(10):
    a('shell','uiautomator','dump','/data/local/tmp/pla363-lock.xml')
    xml=a('shell','cat','/data/local/tmp/pla363-lock.xml')
    if 'com.android.systemui:id/pinEntry' in xml: break
    a('shell','input','swipe','540','2000','540','500','500')
    time.sleep(.5)
else: raise AssertionError('PIN entry not visible')
a('shell','input','text',os.environ['PLAINPHONE_TEST_PIN'])
a('shell','input','keyevent','66')
for _ in range(60):
 log=a('logcat','-d','-v','epoch','-s','PlainphoneBoot:I','*:S')
 if 'home shown lockTask=LOCKED' in log: break
 time.sleep(1)
events=a('logcat','-d','-b','events','-v','epoch')
selected='\n'.join(l for l in events.splitlines() if any(x in l for x in ['uc_finish_user_unlock','uc_send_user_broadcast','ssm_user_unlock']))
(out/(label+'.txt')).write_text(selected+'\n'+log)
unlocked=re.search(r'^\s*([\d.]+).*uc_send_user_broadcast: \[0,android.intent.action.USER_UNLOCKED\]',events,re.M)
home=re.search(r'^\s*([\d.]+).*home shown lockTask=LOCKED',log,re.M)
assert unlocked and home,(bool(unlocked),bool(home))
result={'label':label,'unlock_to_home_ms':round((float(home[1])-float(unlocked[1]))*1000),'reapply':[l for l in log.splitlines() if 'reapply reason=' in l]}
(out/(label+'.json')).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
