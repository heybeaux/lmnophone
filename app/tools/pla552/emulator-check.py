# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Run only after provisioning owned emulator-5780 and enrolling the synthetic PIN 111111.
Build/install PermissionFixtureManifest.xml, seed it in the starter list, and add a
synthetic private files/permission-marker containing synthetic-data. Does not send or call.
"""
from ui_driver import *
fixture = 'org.plainphone.permissionfixture'

def wait_label(label):
    for _ in range(15):
        if label in labels(): return
        time.sleep(.5)
    raise AssertionError('not reached: '+label)

def controls():
    adb('shell', 'input', 'keyevent', 'HOME');wait_label('More')
    tap('More');tap('PIN settings', scroll=True);pin();tap('Apps on this phone',scroll=True)

def check_states(mic):
    dump=adb('shell','dumpsys','package',fixture)
    for permission,granted in [('CAMERA','true'),('RECORD_AUDIO','true' if mic else 'false')]:
        line=next(line.strip() for line in dump.splitlines() if 'android.permission.'+permission+': granted=' in line)
        assert 'granted='+granted in line and 'POLICY_FIXED' in line, line
        print(line,flush=True)
    assert not re.search(r'User 0:.*hidden=true',dump)
    assert adb('shell','run-as',fixture,'cat','files/permission-marker')=='synthetic-data'

def add(mic):
    tap('Permission test app')
    tap("Remove from the person using this phone's phone");tap('Remove');pin();wait_label('Removed.');tap('OK')
    assert re.search(r'User 0:.*hidden=true',adb('shell','dumpsys','package',fixture))
    tap('Add a hidden app',scroll=True);tap('Permission test app',scroll=True);tap('Next');tap('Add anyway')
    wait_label('What can Permission test app use?')
    tap('Next');assert 'What can Permission test app use?' in labels()
    tap('Camera: Allow',scroll=True);tap('Microphone: '+('Allow' if mic else "Don't allow"),scroll=True)
    # Next can proceed with exactly these two choices despite the undefined permission.
    print('Camera and Microphone chosen; checking undefined permission does not block Next.',flush=True)
    tap('Next');tap('Add Permission test app');pin();wait_label('Done. Permission test app is on the home screen.')
    tap('OK');check_states(mic)
    print('Remove/add with explicit choices and fresh PIN passed; saved fixture data retained.',flush=True)

controls();add(False)
tap('Permission test app');wait_label('Camera: Allowed');wait_label('Microphone: Not allowed')
assert not any('Change what' in label for label in labels())
print('Production page matches fixed camera Allow / microphone Deny; no Change action.',flush=True)
adb('shell','am','force-stop','org.plainphone')
adb('shell','am','start','-n','org.plainphone/.MainActivity','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
controls();tap('Permission test app');wait_label('Camera: Allowed');wait_label('Microphone: Not allowed')
check_states(False);tap('Go back')
print('Readback and private data survived app process restart.',flush=True)
add(True)
print('Both CAMERA and RECORD_AUDIO allowed through remove/add. No tested group was refused.',flush=True)
