# preview-2a: build notes (PLA-144)

Installed on the spare Moto (<spare>, Android 15) on 2026-10-10 at 00:51 UTC (Fri 9 Oct, 17:51 PDT).
`testOnly=true`. Built from `release/preview-2`, tag `preview-2a`.

## What works
- Plainphone is the Device Owner and the home screen. The Home key always comes back to it.
- Lock task is on. You can only open Phone, Messages, Camera and Maps, plus the emergency screens.
- Every other app is hidden. The setup script (`app/tools/preview-2/setup.sh`) sets the list, not the phone.
- Before Home shows, Plainphone applies its settings and checks each one (read-back). If a check fails, Home shows
  "Plainphone needs attention".
- More > Emergency 9-1-1 and Phone both open the dialer, where 9-1-1 can be called. Nothing calls by itself.

## Not in this build yet
- No PIN, recovery code, approvals, "Ask for an app", link rules, waits or a trusted person. Each of these opens
  "Not in this test build yet", and nothing is changed or sent.
- The names on the More screen (Sam, Jo) are sample text.
- Plainphone sets no restrictions, so nothing blocks safe mode or factory reset. Android's own Device Owner defaults
  (no extra work, clone or private profiles) still apply.
- After a restart, Android keeps the hidden apps, Home and the lock task list, and Plainphone applies its settings
  again when Home opens. The full boot path, checked before Home shows (including Direct Boot), is preview-2b
  (Saturday 10 Oct).
- No Google account on the phone. The tester can add a Google account back themselves whenever they want Play updates.
- The lock screen has no Emergency button, because the spare has no PIN (by rule). To call 9-1-1 from the lock screen,
  swipe up, then use Phone or More > Emergency.

## Safety checks (evidence in `evidence/`)
| Check | Emulator (API 35) | Spare |
|---|---|---|
| 1. 9-1-1 typed in, no call placed | More, Phone, lock-screen button (temporary PIN) | More, Phone; lock screen has no button without a PIN |
| 2. `remove-active-admin` removes Plainphone, hidden apps come back | 13 of 13 back | 70 of 70 back |
| 3. Each unhide followed at once by its save flip (mitigation B, best effort) | unhide and flip seen in the file 3 ms after the apply (the hide control was also prompt, so this does not isolate the flip) | flip ran and read back straight after the unhide; storage not observable without root |

## Known risk (unchanged by this build)
Mitigation B is best effort, not a guarantee. On Android 15 the package-settings write that records an unhide is queued,
not forced to storage (PLA-185 F1, PLA-25). If power is lost just after an unhide, the phone can come back with that app
hidden again. In the worst case (PLA-22) it can get stuck restarting. The board accepted that risk for the spare only, and
the spare may be erased from recovery if it gets stuck (1A consent).

## Way out
`adb -s <spare> shell dpm remove-active-admin org.plainphone/.PlainphoneAdminReceiver`. This brings back every app
and ends lock task. Then run `adb uninstall org.plainphone`.
