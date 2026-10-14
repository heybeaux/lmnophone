# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned emulator-5964 only. See README for setup. Never calls or sends."""
from ui_driver import *
fixture = 'org.plainphone.permissionfixture'
permissions = ['CAMERA', 'ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION', 'RECORD_AUDIO',
               'POST_NOTIFICATIONS', 'READ_MEDIA_IMAGES', 'READ_MEDIA_VIDEO', 'READ_MEDIA_VISUAL_USER_SELECTED']

def wait_label(label):
    for _ in range(20):
        if label in labels(): return
        time.sleep(.5)
    raise AssertionError('not reached: '+label)

def controls():
    adb('shell','input','keyevent','HOME');wait_label('More')
    tap('More');tap('PIN settings',scroll=True);pin();tap('Apps on this phone',scroll=True)

def check_states(allow):
    dump = adb('shell','dumpsys','package',fixture)
    for permission in permissions:
        matches = [line.strip() for line in dump.splitlines() if 'android.permission.'+permission+': granted=' in line]
        if permission == 'READ_MEDIA_VISUAL_USER_SELECTED' and not matches: continue
        assert matches, permission
        line=matches[0]
        assert 'granted='+str(allow).lower() in line and 'POLICY_FIXED' in line, line
        print(line,flush=True)
    assert not re.search(r'User 0:.*hidden=true',dump)
    assert adb('shell','run-as',fixture,'cat','files/permission-marker') == 'synthetic-data'

def add(allow):
    tap('Permission test app',scroll=True)
    tap("Remove from the person using this phone's phone",scroll=True);tap('Remove');pin();wait_label('Removed.');tap('OK')
    assert re.search(r'User 0:.*hidden=true',adb('shell','dumpsys','package',fixture))
    tap('Add a hidden app',scroll=True);tap('Permission test app',scroll=True);tap('Next');tap('Add anyway')
    wait_label('What can Permission test app use?')
    for group in ['Camera', 'Location (where the phone is)', 'Microphone', 'Notifications', 'Photos and videos']:
        tap(group+': '+('Allow' if allow else "Don't allow"),scroll=True)
    tap('Next');tap('Add Permission test app');pin()
    wait_label('Done. Permission test app is on the home screen.');tap('OK')
    check_states(allow)
    print('PASS five groups '+('Allow' if allow else 'Deny')+' through production UI; saved marker retained',flush=True)

if __name__ == '__main__':
    controls();add(True);add(False)
    adb('shell','am','force-stop','org.plainphone')
    adb('shell','am','start','-n','org.plainphone/.MainActivity','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
    controls();tap('Permission test app',scroll=True)
    check_states(False)
    print('PASS runtime grants and fixture data survived restart; no calls or sends',flush=True)
