# LMNOPhone preview 14 — test candidate

Home no longer waits for the system-package snapshot used to skip unchanged
package events. Capture is queued on the existing serial dispatcher after the
full verified pass publishes the Home gate and refresh. An intervening pass or
invalidation cancels the pending capture. Until capture finishes, package events
require a full pass, including events received during capture but handled later.

A completed snapshot still requires fresh targeted package, lock-task, floor,
Home and launcher readback before skipping an eligible component-change event.
No emergency routes, approved apps, restrictions or recovery rules change.

How to test: restart and unlock, then inspect how soon Home settles. The driver
records `home shown`, separate `packageSnapshot pass=... ms=...` lines and full
pass counts. Change an eligible hidden system app component after capture to
check `skipped=unchanged-since=...`; before capture, expect a full pass. Open
More → Emergency 9-1-1 and inspect the keypad without pressing Call.

Known limits carried forward:

- A dialer opened outside LMNOPhone cannot be identified and restored after a
  crash. Phone remains one tap from Home. Fallback containment is pending.
- Calling-screen storage failures leave only an in-process record; they do not
  stop startup or opening Phone/Emergency.
- Trusted-person controls and other unwired actions retain the build-5
  explanation. Counts cover supported Phone and SMS/MMS data, not RCS or other apps.
- The checking-screen emergency fallback remains host-tested only.
- Continuing package events can still cause full passes. This is a test preview,
  not full D1 acceptance or a measured spare-device speed improvement.

VersionCode `14` (`0.1.0-preview-14`), same application ID and signer.
The Lead owns the single review and the physical spare.
