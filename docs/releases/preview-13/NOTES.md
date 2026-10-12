# LMNOPhone preview 13 — test candidate

A system app's repeated component-change notification can now avoid a full policy
pass when a targeted read proves it still matches the last verified full pass.
This avoids an unnecessary Home refresh. Mixed-package bursts, app installs,
removals and updates still get a full pass, as do protected packages, read errors,
changed state, pending recovery and locked runs.

The check also reads current floor/Home identities and launcher eligibility.
A visible app that gains a launcher must get a full plan, even if its application
flags stayed the same. Building coverage adds a package-state snapshot to each
successful full pass; spare-device timings must establish the net benefit.

How to test: restart and unlock, then inspect how soon Home settles. The driver
records full-pass counts and the final pass end time, plus logs marked
`skipped=unchanged-since=<id>`. Open More → Emergency 9-1-1 and inspect the keypad
without pressing Call. No emergency call is part of this test.

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

VersionCode `13` (`0.1.0-preview-13`), same application ID and signer.
The Lead owns the single review and the physical spare.
