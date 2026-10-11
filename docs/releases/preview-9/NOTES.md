# LMNOPhone preview 9 — test candidate

Startup now uses a cheaper conversion for certificate hashes. The same facts,
policy stages and readbacks still run. One existing diagnostic line now reports
where each reapply pass spends its time, so the spare can measure its unlock delay.
No screen, approved app, emergency route or recovery behavior changes.

How to test: restart, unlock, and check how soon Home appears. Open More →
Emergency 9-1-1 and look at the keypad; do not press Call. The assigned driver
records the `PlainphoneBoot` stage timings. Emulator results and build details
are in [verification](evidence/verification.md); these are not spare results.

Known limits:

- A dialer opened outside LMNOPhone cannot be identified and restored after a
  crash. Phone remains one tap from Home. Fallback containment is pending.
- If saving the calling-screen record fails, only the current process remembers
  the screen it opened. A record read/write failure does not stop startup or
  opening Phone/Emergency.
- Trusted-person controls and other unwired actions retain the build-5
  explanation. Counts cover supported Phone and SMS/MMS data, not RCS or other apps.
- The checking-screen emergency fallback is host-tested only; the spare has the
  preferred dialer. This is a test preview, not full D1 acceptance.

VersionCode `9` (`0.1.0-preview-9`). Spare installation awaits the Lead’s single
review and the assigned device driver.
