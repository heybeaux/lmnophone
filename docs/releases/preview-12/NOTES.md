# LMNOPhone preview 12 — test candidate

Late boot and locked-boot broadcasts can use a successful full pass already
completed after this process observed the user unlocked. This avoids repeating
the same policy work. A locked, failed, unverified or attention pass cannot cover
one, nor can a pass with a pending flip-notice save. Package events, updates,
startup, unlock, the alarm and trusted-person changes retain their full passes.

Alarm scheduling, broadcast completion, locked Home startup, calling-screen
recovery, Home publication and count refresh remain active. The gate now wakes
the screen and dismisses the no-PIN keyguard immediately before every UI dump.
It still types only 9, 1, 1 and never presses Call.

How to test: restart and unlock, then inspect how soon Home settles. The driver
records `reapply reason=boot skipped=covered-by=<id>` (or `locked-boot`), full-pass
counts and the last pass end time after unlock. Open More → Emergency 9-1-1 and
inspect the keypad without pressing Call. This change does not establish a
measured improvement on the spare until the Lead records it there.

Known limits carried forward:

- A dialer opened outside LMNOPhone cannot be identified and restored after a
  crash. Phone remains one tap from Home. Fallback containment is pending.
- Calling-screen storage failures leave only an in-process record; they do not
  stop startup or opening Phone/Emergency.
- Trusted-person controls and other unwired actions retain the build-5
  explanation. Counts cover supported Phone and SMS/MMS data, not RCS or other apps.
- The checking-screen emergency fallback remains host-tested only.
- Package events retain the existing one-queued-pass merging; continuing events
  can cause further full passes. This is a test preview, not full D1 acceptance.

VersionCode `12` (`0.1.0-preview-12`), same application ID and signer.
See [verification](evidence/verification.md) for separate build, install and
exercise evidence. Lead owns the single review and the physical spare.
