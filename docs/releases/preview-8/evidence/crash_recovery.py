# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Usage: python3 crash_recovery.py <7|8> <owned-emulator> [race]
# Uses PAPERCLIP_SCRATCH_DIR; never presses Call. A settled kill may not trigger recovery.
import datetime, importlib.util, json, os, re, sys, time
from pathlib import Path
root=Path(__file__).resolve().parents[4]
version=sys.argv[1]
serial=sys.argv[2]
race=len(sys.argv)>3 and sys.argv[3]=='race'
assert version in ('7', '8') and serial.startswith('emulator-')
spec=importlib.util.spec_from_file_location('gate',root/'app/tools'/('preview-'+version)/'upgrade_check.py')
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
out=Path(os.environ['PAPERCLIP_SCRATCH_DIR'])/'emulator'/('build'+version+('-race' if race else '-crash'))
x=g.Gate(serial,out)
g.exact(x.adb('owned AVD','emu','avd','name').splitlines()[0],'pla353-ui-a35','owned build-7 AVD')
x.readback()
x.sh('wake','input','keyevent','KEYCODE_WAKEUP')
x.sh('unlock no-PIN emulator','wm','dismiss-keyguard')
x.home();x.tap('^More$');x.tap('^Emergency 9-1-1');time.sleep(2)
g.exact(x.digits(),'911','initial More Emergency prefill is exactly 911')
x.calls_empty();x.shot('before-kill.png')
record=x.sh('read opened record','run-as','org.plainphone','cat','/data/user_de/0/org.plainphone/files/opened-screen')
g.exact(record.split()[0],'PHONE' if version=='7' else 'EMERGENCY_911','persisted calling-screen route')
pid=x.sh('LMNOPhone process','pidof','org.plainphone');g.require(re.fullmatch(r'\d+',pid) is not None,'one process to kill')
x.sh('clear owned emulator log','logcat','-c')
if race:
 x.clear();x.home();x.tap('^More$')
 xy=g.tap_bounds(x.dump(),'^Emergency 9-1-1')
 x.sh('tap Emergency then immediately kill only LMNOPhone','sh','-c',
      "'input tap "+' '.join(xy)+"; su 0 kill -9 "+pid+"'")
else:
 x.sh('kill only LMNOPhone process as root','su','0','kill','-9',pid)
start=time.monotonic()
for _ in range(45):
 time.sleep(1)
 log=x.sh('read recovery log','logcat','-d','-s','PlainphoneBoot')
 if 'reopening ' in log: break
else:
 if race: raise g.GateFailure('automatic calling-screen recovery not observed within 45 seconds')
 value=x.digits();x.calls_empty();x.shot('after-kill-no-recovery.png');x.readback()
 summary={'time_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':int(version),'target':'<emulator>','timing':'after settled 911 keypad','kill':'su 0 kill -9 <LMNOPhone pid>','before_digits':'911','record':record.split()[0],'recovery_observed':False,'observation_seconds':45,'after_digits_raw':value,'current_calls':0}
 (out/'result.json').write_text(json.dumps(summary,indent=2)+'\n')
 print('OBSERVATION (not a recovery PASS): '+json.dumps(summary),flush=True)
 x.clear();x.home();sys.exit(0)
newpid=x.sh('new LMNOPhone process','pidof','org.plainphone');g.require(newpid!=pid,'new process after kill')
time.sleep(2)
value=x.digits()
if version=='7': g.empty_field(value)
else: g.exact(value,'911','recovered More Emergency prefill is exactly 911')
x.calls_empty();x.shot('after-recovery.png');x.readback()
(out/'recovery-log.txt').write_text(log+'\n')
summary={'time_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version':int(version),'target':'<emulator>','kill':'su 0 kill -9 <LMNOPhone pid>','timing':'immediate after tap' if race else 'after settled 911 keypad','before_digits':'911','record':record.split()[0],'process_replaced':True,'recovery_observed_seconds':round(time.monotonic()-start,2),'after_digits_raw':value,'current_calls':0,'reopen_log':[l for l in log.splitlines() if 'reopening ' in l]}
(out/'result.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary),flush=True)
x.clear();x.home()
