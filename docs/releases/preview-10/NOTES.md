# LMNOPhone preview 10 — test candidate

Home startup reads the live app list without collecting certificate identities
or probing browser handlers that the preview plan does not use. It still reads
the list twice around the emergency-floor queries. What the preview applies,
its readback checks, emergency access and recovery are unchanged.

How to test: restart, unlock, and check how soon Home appears. Open More →
Emergency 9-1-1 and inspect the keypad without pressing Call. The assigned driver
records the `PlainphoneBoot` stage lines, including the two package snapshots.

Known limits carried from preview 9:

- A dialer opened outside LMNOPhone cannot be identified and restored after a
  crash. Phone remains one tap from Home. Fallback containment is pending.
- Calling-screen storage failures leave only an in-process record; they do not
  stop startup or opening Phone/Emergency.
- Trusted-person controls and other unwired actions retain the build-5
  explanation. Counts cover supported Phone and SMS/MMS data, not RCS or other apps.
- The checking-screen emergency fallback remains host-tested only.
- Package-change passes are not coalesced. This is a test preview, not full D1 acceptance.

VersionCode `10` (`0.1.0-preview-10`). See [verification](evidence/verification.md)
for separate build, emulator install, and exercise evidence. Spare delivery
follows the Lead’s single review and the sole device driver.
