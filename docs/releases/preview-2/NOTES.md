# preview-2: build notes (PLA-144)

**preview-2** (tag `preview-2`): installed on the spare Moto (<spare>, Android 15) on 2026-10-10 at 18:57 UTC
(Sat 10 Oct, 11:57 PDT). APK sha256 `a875f134…4c1c8b1a`, `testOnly=true`, built from `release/preview-2` at `001a3d0`
(later commits are evidence and scripts only). It adds the boot path (preview-2b) to preview-2a.

preview-2a (tag `preview-2a`) was installed on 2026-10-10 at 00:51 UTC (Fri 9 Oct, 17:51 PDT).

## What works
- Plainphone is the Device Owner and the home screen. The Home key always comes back to it.
- Lock task is on. You can only open Phone, Messages, Camera and Maps, plus the emergency screens.
- Every other app is hidden. The setup script (`app/tools/preview-2/setup.sh`) sets the list, not the phone.
- Before Home shows, Plainphone applies its settings and checks each one (read-back). Until that check passes and lock
  task is on, the phone shows a plain "checking the phone" screen with only Phone and Emergency call. If a check
  finds a problem it can't fix, Home shows "Plainphone needs attention".
- After a restart, Plainphone runs the same apply and check before Home shows (preview-2b). Before the first unlock it
  restores only the emergency apps, its own lock task entry and the lock task features. The full apply runs once the
  phone is unlocked. It runs again on every app install or change, every 15 minutes, and on setup.
- More > Emergency 9-1-1 and Phone both open the dialer, where 9-1-1 can be called. Nothing calls by itself.

## Not in this build yet
- No PIN, recovery code, approvals, "Ask for an app", link rules, waits or a trusted person. Each of these opens
  "Not in this test build yet", and nothing is changed or sent.
- The names on the More screen (Sam, Jo) are sample text.
- Plainphone sets no restrictions, so nothing blocks safe mode or factory reset. Android's own Device Owner defaults
  (no extra work, clone or private profiles) still apply.
- No Google account on the phone. The tester can add a Google account back themselves whenever they want Play updates.
- The lock screen has no Emergency button, because the spare has no PIN (by rule). To call 9-1-1 from the lock screen,
  swipe up, then use Phone or More > Emergency.

## Safety checks (evidence in `evidence/`; preview-2b in `emulator-2b/` and `spare-2b/`)
| Check | Emulator (API 35) | Spare |
|---|---|---|
| 1. 9-1-1 typed in, no call placed | 2a and 2b: More, Phone, lock-screen button (temporary PIN; 2b after a locked restart) | 2a and 2b: More, Phone; lock screen has no button without a PIN |
| 2. `remove-active-admin` removes Plainphone, hidden apps come back | 2a: 13 of 13; 2b: 15 of 15 | 2a: 70 of 70; 2b: 42 of 42 |
| 3. Each unhide followed at once by its save flip (mitigation B, best effort) | 2a and 2b: unhide and flip in the file before the apply reported (the hide control was also prompt) | 2a and 2b: flip ran and read back straight after the unhide; storage not observable without root |

## Restart checks (preview-2b)
| | Emulator (API 35) | Spare |
|---|---|---|
| Restart (`adb reboot`) | Home drawn 92 ms after the first full read-back, in lock task (`restart.txt`) | Locked run, then full read-back at 11:59:05; Home drawn at 11:59:23 in lock task (`restart.txt`) |
| Locked restart (temporary PIN) | Before unlock: lock task on, locked runs read back, Home not drawn; after unlock Home drawn 106 ms after the full read-back (`check1.txt`) | not run (no PIN by rule) |
| Power cut during an unhide (`emu kill`, cold boot) | Home drawn only after the full read-back; the lost unhide was redone (`power-cut-unhide.txt`) | not run (no power cut on the spare) |

## Known risk
Mitigation B is best effort, not a guarantee. On Android 15 the package-settings write that records an unhide is queued,
not forced to storage (PLA-185 F1, PLA-25). If power is lost just after an unhide, the phone can come back with that app
hidden again. In the worst case (PLA-22) it can get stuck restarting. The board accepted that risk for the spare only, and
the spare may be erased from recovery if it gets stuck (1A consent). preview-2b narrows the ordinary case: after a
restart, the boot re-apply unhides the app again before Home shows (emulator power-cut test). It does not remove the
boot-loop worst case.

## Way out
`adb -s <spare> shell dpm remove-active-admin org.plainphone/.PlainphoneAdminReceiver`. This brings back every app
and ends lock task. Then run `adb uninstall org.plainphone`.
