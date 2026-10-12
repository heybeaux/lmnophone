# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned emulator only: queue existing boot receiver during the real unlock pass."""
import subprocess,time,selectors,json,re
from pathlib import Path
out=Path('docs/releases/preview-15/evidence')
def a(*args):return subprocess.check_output(['adb','-s','emulator-5880',*args],text=True,timeout=60).strip()
assert a('emu','avd','name').splitlines()[0]=='pla363-policy-a35'
a('reboot');time.sleep(2);a('wait-for-device');a('root');a('wait-for-device')
assert a('shell','id','-u')=='0'
stream=subprocess.Popen(['adb','-s','emulator-5880','logcat','-b','events','-v','brief'],stdout=subprocess.PIPE,text=True,bufsize=1)
sel=selectors.DefaultSelector();sel.register(stream.stdout,selectors.EVENT_READ)
trigger=None
try:
 deadline=time.monotonic()+60
 while time.monotonic()<deadline:
  if not sel.select(1):continue
  line=stream.stdout.readline()
  if 'uc_send_user_broadcast' in line and 'android.intent.action.USER_UNLOCKED' in line:
   trigger=line.strip()
   result=a('shell','am','broadcast','--receiver-foreground','-a','android.intent.action.LOCKED_BOOT_COMPLETED','-n','org.plainphone/.boot.BootReceiver')
   print(result,flush=True)
   break
 assert trigger,'unlock event not observed'
finally:
 stream.terminate();stream.wait(timeout=5)
time.sleep(6)
log=a('logcat','-d','-s','PlainphoneBoot:I','*:S')
(out/'queued-boot.txt').write_text(log+'\n')
(out/'queued-boot-trigger.json').write_text(json.dumps(dict(trigger=trigger,action='Explicit LOCKED_BOOT_COMPLETED to existing BootReceiver immediately after real USER_UNLOCKED broadcast; root host on owned emulator only',broadcast_result=result),indent=2)+'\n')
full=re.search(r'reapply reason=user-unlocked .*pass=(\d+) ',log)
assert full,'no user-unlocked full pass'
id=full[1]
skip=re.search(r'reapply reason=(?:locked-boot|boot) skipped=covered-by='+id+r'\b',log)
capture=re.search(r'packageSnapshot pass='+id+r' .*captured=true',log)
assert skip and capture and skip.start()<capture.start(),'required interleaving not observed'
print('PASS: covered boot skip before user-unlocked snapshot pass='+id)
