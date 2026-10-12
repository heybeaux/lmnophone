# LMNOPhone preview 16 — test candidate

A group of unchanged system-package CHANGED events can now reuse the verified
pass, just like a single event. Each event must have arrived after the snapshot,
and every package must pass fresh readback. Other actions, a changed or unreadable
package, locked state, absent coverage or pending retry still require a full pass.
A package skip keeps recovery checks and the pass ID without refreshing Home.
Grouped full-pass and skip logs include every package name and action.

How to test: restart and unlock. Record first Home time, full unlocked pass count,
and which grouped reasons skip or require a full pass. Confirm the first snapshot
still belongs to the user-unlocked pass. Open More → Emergency 9-1-1 and inspect
the keypad without pressing Call. The Lead records two spare unlock samples.

No emergency routes, approved apps, restrictions, visible copy or recovery rules
change. Package eligibility, fresh reads and captureAfterPublished are retained.

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

VersionCode `16` (`0.1.0-preview-16`), same application ID and signer.
The Lead owns the single review and the physical spare.
