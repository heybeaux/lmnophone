# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned-emulator-only clean accepted-33 to final-34 upgrade, preserving data."""
import os, subprocess, time, hashlib, json, datetime
from pathlib import Path
root=Path(__file__).resolve().parents[3]
s=Path(os.environ['PAPERCLIP_SCRATCH_DIR']);out=Path(__file__).with_name('evidence')
def a(*args):return subprocess.check_output(['adb','-s','emulator-5880',*args],text=True).strip()
assert 'pla363-policy-a35' in a('emu','avd','name')
a('shell','input','keyevent','KEYCODE_WAKEUP');a('shell','wm','dismiss-keyguard')
# Undo the temporary instrumented candidate on this test emulator, without wiping app data.
assert 'Success' in a('install','-r','-d','-t',str(s/'accepted33.apk'))
a('shell','am','start','-n','org.plainphone/.MainActivity');time.sleep(3)
p=a('shell','dumpsys','package','org.plainphone');assert 'versionCode=33 ' in p and 'TEST_ONLY' in p
assert 'mLockTaskModeState=LOCKED' in a('shell','dumpsys','activity','activities')
b=json.loads((s/'baseline33.json').read_text());assert hashlib.sha256(a('shell','cmd','wifi','list-networks').encode()).hexdigest()==b['saved']
apk=root/'app/plainphone/build/outputs/apk/debug/plainphone-debug.apk'
h=hashlib.sha256(apk.read_bytes()).hexdigest()
signer=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0/apksigner'
def cert(p):
    text=subprocess.check_output([str(signer),'verify','--print-certs',str(p)],text=True)
    return next(l.split(': ',1)[1] for l in text.splitlines() if 'certificate SHA-256 digest' in l)
assert cert(apk)==cert(s/'accepted33.apk')
assert 'Success' in a('install','-r','-t',str(apk));stamp=datetime.datetime.now(datetime.timezone.utc).isoformat()
a('shell','am','start','-n','org.plainphone/.MainActivity');time.sleep(3)
p=a('shell','dumpsys','package','org.plainphone');assert 'versionCode=34 ' in p and 'versionName=0.1.0-preview-34' in p and 'TEST_ONLY' in p
assert 'mLockTaskModeState=LOCKED' in a('shell','dumpsys','activity','activities')
assert 'DeviceOwner' in a('shell','dpm','list-owners')
text=f'build=34\nsubset=F1-F2-only\nversion=0.1.0-preview-34\napk_sha256={h}\ninstalled_utc={stamp}\nupgrade=accepted-33-to-34-in-place\nsame_signer=true\nTEST_ONLY=true\nDO=true\nLOCKED=true\nsaved_list_sha256={b["saved"]}\nsaved_list_unchanged=true\n'
for key,scope,setting in [('location','secure','location_mode'),('font','system','font_scale'),('ringtone','system','ringtone'),('bluetooth','global','bluetooth_on')]:
    assert a('shell','settings','get',scope,setting)==b[key]
    text+=key+'_unchanged=true\n'
(out/'install-readback.txt').write_text(text);print(text)
