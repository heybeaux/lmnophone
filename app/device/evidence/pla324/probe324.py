# PLA-324 AVD probe, adapted from the Verifier's PLA-322 probe.py. Emulators only; never places a call.
# Usage: ANDROID_SERIAL=emulator-NNNN python3 probe324.py <case> [out-dir]
import os, pathlib, re, shlex, subprocess, sys, time
SERIAL = os.environ['ANDROID_SERIAL']
OUT = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else '.')
OUT.mkdir(parents=True, exist_ok=True)
def a(*args): return subprocess.run(['adb', '-s', SERIAL, *map(str, args)], capture_output=True, text=True).stdout.strip()
def sh(*args): return a('shell', shlex.join(map(str, args)))
def snap():
    s = sh('dumpsys', 'activity', 'activities')
    top = re.search(r'topResumedActivity=ActivityRecord\{\S+ u0 (\S+) t(\d+)', s)
    st = re.search(r'mLockTaskModeState=(\w+)', s)
    tasks = re.search(r'mLockTaskModeTasks=(.*)', s)
    return f"lockTask={st[1] if st else '?'} top={top[1] + ' t' + top[2] if top else '?'} lockTasks=[{(tasks[1] if tasks else '').strip()[:160]}]"
def home():
    sh('am', 'start', '-n', 'org.plainphone/.MainActivity'); time.sleep(2)
def kill():
    p = sh('pidof', 'org.plainphone'); sh('run-as', 'org.plainphone', 'kill', '-9', p); return p
def find(text):
    sh('uiautomator', 'dump', '/data/local/tmp/ui.xml')
    m = re.search(r'text="' + re.escape(text) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', sh('cat', '/data/local/tmp/ui.xml'))
    if not m: raise SystemExit(f'{text!r} not on screen')
    x1, y1, x2, y2 = map(int, m.groups()); return (x1 + x2) // 2, (y1 + y2) // 2
def tap_text(text): sh('input', 'tap', *find(text))
spot = {}
def hold():
    sh('am', 'broadcast', '-n', 'org.plainphone/.boot.TestHook', '-a', 'org.plainphone.test.HOLD', '--ez', 'hold', 'true'); time.sleep(1.5)
def locate(text): spot['xy'] = find(text)
def sample(t0, n=40, dt=.25):
    out = []
    for _ in range(n):
        out.append(f'+{time.monotonic() - t0:.2f}s {snap()}'); time.sleep(dt)
    return out
def trace(label, before, stimulus, after=None):
    sh('cmd', 'statusbar', 'collapse'); home(); before(); a('logcat', '-c')
    lines = [f'{label} before: {snap()}']
    t0 = time.monotonic(); stimulus(t0)
    lines += sample(t0)
    if after: lines += after()
    sh('uiautomator', 'dump', '/data/local/tmp/ui.xml')
    lines.append('screen text after: ' + ' | '.join(re.findall(r'text="([^"]+)"', sh('cat', '/data/local/tmp/ui.xml'))[:12]))
    lines.append('--- logcat (PlainphoneBoot, ActivityTaskManager START)')
    lines += [l for l in a('logcat', '-d', '-s', 'PlainphoneBoot:I', 'ActivityTaskManager:I').splitlines()
              if 'PlainphoneBoot' in l or 'START' in l]
    OUT.joinpath(label + '.txt').write_text('\n'.join(lines) + '\n')
    with open(OUT / (label + '.png'), 'wb') as f:
        f.write(subprocess.run(['adb', '-s', SERIAL, 'exec-out', 'screencap', '-p'], capture_output=True).stdout)
    print('\n'.join(lines[:4] + ['...'] + [l for l in lines if 'PlainphoneBoot' in l][-6:] + [lines[40] if len(lines) > 40 else '']), flush=True)
nothing = lambda *_: None
def kill_then(*start):
    def f(t0): kill(); sh('am', 'start', *start)
    return f
CASES = {
    # The Verifier's repro: a dialer opened from outside LMNOPhone right after the kill.
    'external-dial': (nothing, kill_then('-a', 'android.intent.action.DIAL')),
    'external-emergency': (nothing, kill_then('-a', 'com.android.phone.EmergencyDialer.DIAL')),
    # The user taps Home's Phone / Emergency, then Home's process dies while that screen is opening.
    'home-phone-kill': (lambda: locate('Phone'), lambda t0: (sh('input', 'tap', *spot['xy']), kill())),
    # The checking screen (held by the debug TestHook) opens the emergency keypad; Home dies at once.
    'checking-emergency-kill': (lambda: (hold(), locate('Emergency call')), lambda t0: (sh('input', 'tap', *spot['xy']), kill())),
    # Same, with two digits typed (never an emergency number, never dialled) before the process dies.
    'checking-emergency-digits': (lambda: (hold(), locate('Emergency call')),
                                  lambda t0: (sh('input', 'tap', *spot['xy']), time.sleep(.6), sh('input', 'text', '12'), kill())),
    'external-emergency-digits': (nothing, lambda t0: (kill(), sh('am', 'start', '-a', 'com.android.phone.EmergencyDialer.DIAL'),
                                                       time.sleep(.3), sh('input', 'text', '12'))),
    # In-call stand-in (no call placed): Home dies, the debug TestHook tells the new process a call is
    # ongoing, and the Phone app stands in for the in-call screen. Recovery must take the in-call path
    # (TelecomManager.showInCallScreen, which shows nothing when there is no real call).
    'in-call-standin': (nothing, lambda t0: (kill(), sh('am', 'broadcast', '-n', 'org.plainphone/.boot.TestHook',
                                                        '-a', 'org.plainphone.test.IN_CALL', '--ez', 'on', 'true'),
                                             sh('am', 'start', '-a', 'android.intent.action.DIAL'))),
    # The Verifier's pinned.py: Home dies, Settings opens, and the shell pins Settings's task before the
    # restart pass finishes. Shell authority stands in for a pin; no restricted-user route is claimed.
    'pinned': (nothing, lambda t0: (kill(), sh('am', 'start', '-W', '-n', 'com.android.settings/.Settings'),
                                    spot.__setitem__('pin', sh('am', 'task', 'lock', re.search(
                                        r'topResumedActivity=.*com.android.settings/\S* t(\d+)',
                                        sh('dumpsys', 'activity', 'activities'))[1])))),
    'home-phone-open-kill': (lambda: (tap_text('Phone'), time.sleep(2)), lambda t0: kill()),
}
if __name__ == '__main__':
    case = sys.argv[1]
    before, stim = CASES[case]
    trace(case, before, stim)
