# Give-back proof — API 35

Author-run engineering evidence for PLA-607, not reviewer approval or spare-device acceptance.
The development APK retains build 61 and `testOnly`; hashes and host counts are in
`artifact-and-host-tests.txt`. The assigned base is `44b4c8e1`.

Built: debug APK and separate instrumentation APK. Installed/exercised: a private
`-read-only` `pla140-a35` emulator on a checked free 59xx port, whose AVD name was
read back. No physical phone was addressed. No call was placed. Settings and the
stock dialer were opened with intents; the dialer stayed idle.

The fixture sets a fixed Contacts permission to DENIED, hides Contacts and
Calendar, blocks uninstall for Contacts, and sets `no_config_vpn`. The preview
setup script establishes the starter list and lock task. The before observations
show both apps hidden, grant state 2 (DENIED), Device Owner present and lock-task
features 53 with the emergency UI still in the permitted list.

- `normal-run.txt`: every inventory item DONE, result OFF. `normal-after.txt`:
  apps visible, no POLICY_FIXED permission flag, lock task NONE and Android Home
  chooser. `normal-verify.txt`: observed ownership absent and durable COMPLETED.
- `restart-*`: after three durable item results the **test APK** writes its crash
  witness, requests an emulator reboot and kills its process. The production
  startup path resumes after user unlock and reports OFF. Verification checks
  both the witness (`crash=true`) and COMPLETED with actual ownership absent.
  The final journal contains only DONE entries. No manual Retry drives this run.
- `settings-open.txt`, `dialer-open.txt`, and restart equivalents record successful
  activity launches after removal. The stock launcher is explicitly opened by
  give-back; future Home presses offer Android's chooser, without LMNOPhone's
  disabled Home alias. Its separate app launcher remains available.

Host proof: ordered writes and readbacks, failure/exception at every action,
counted failures retaining ownership, acknowledged checkpoint ordering, crash
resume after each item, failed storage, disabled apps, mitigation B failure,
visible-before-grant ordering, battery refusal, successor refusal and removal
last. Existing authority reconciliation tests cover lost record -> normal
lockdown. Ten Paparazzi images cover singular/long counts at 1.0/1.3/1.5/1.8/2.0;
every new image was visually inspected. All six OpenSpec changes validate.

Emulator findings corrected during implementation: empty lock-task policy reads
Android's neutral GLOBAL_ACTIONS default (16); clearing persistent Home alone
leaves the HOME role able to restore LMNOPhone; and normal launcher checks must
wait until user unlock. The final adapter handles all three. Android's settings
and permission state are read back; no failed assertion was disabled.

This is not FRP hardware acceptance, not the ten-trial power-loss matrix, not a
physical-phone removal, and not public/store rollout. Unsupported emulator FRP
has no restorable item; other FRP errors fail inventory. The supplied pending
release note preserves those test-phone limits.

Reproduce from `app/`: build `:plainphone:assembleDebug` and
`:plainphone:assembleDebugAndroidTest -PpreviewTestRunner=org.plainphone.giveback.GiveBackInstrumentation`.
Choose a free private 59xx emulator, verify its AVD name, and run the existing
preview setup script against that explicit target. Install the androidTest APK
only on that emulator. Invoke `am instrument -w -e phase PHASE
org.plainphone.test/org.plainphone.giveback.GiveBackInstrumentation` with an explicit
`adb -s "$TARGET"`. Phases are `prepare`, `inspect`, `run`, `crash`, `verify`, and
`journal`. Preparation deliberately exits the process after saving a synthetic
PENDING record. Start MainActivity to read back LOCKED, then capture `inspect` and
`capture.py "$TARGET" before-label`. Only `run`/`crash` move it to COMMITTING, inside
the real serialized dispatcher via test-APK reflection. After `crash`, wait for
`sys.boot_completed=1` and the automatic give-back log before `verify`; attempting
instrumentation while Android's activity service is still starting is invalid.
Do not use `connectedAndroidTest`. Reprovision this disposable emulator before
another full trial. Never install the test fixture on the spare.

The captured policy text is an allow-filtered extract of `dumpsys device_policy`;
package evidence includes `pm list packages -u` versus visible packages and the
per-user hidden/permission fields. Raw dumps remain in run-owned private scratch.
No serial, account identifier, contact or message content is committed.
