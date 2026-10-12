# LMNOPhone preview 15 — test candidate

After unlock, a late covered boot broadcast no longer cancels the pending
system-package snapshot for the same verified pass. The snapshot still runs
after Home publication. A new full pass, failed publication or retry re-check,
attention pass, or explicit invalidation still discards the old capture.

How to test: restart and unlock. Inspect the boot log for a covered boot skip
followed by `packageSnapshot` with the same user-unlocked pass ID. Record first
Home time and full unlocked pass count. Open More → Emergency 9-1-1 and inspect
the keypad without pressing Call. The Lead records two spare unlock samples.

The snapshot eligibility rules, fresh readbacks before a package skip and
receipt-time package-event checks are unchanged. No emergency routes, approved
apps, restrictions, visible copy or recovery rules change.

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

VersionCode `15` (`0.1.0-preview-15`), same application ID and signer.
The Lead owns the single review and the physical spare.
