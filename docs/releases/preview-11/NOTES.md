# LMNOPhone preview 11 — test candidate

Hide-only checks read installed-user membership and the live hidden flag without
also fetching enabled and suspended state that those checks do not use. Every
pre-write check, post-write readback and independent final verification remains
live. There is no package-state cache. Emergency floor, never-hide guard, Home,
lock task, recovery and the checking-screen fallback are unchanged.

How to test: restart, unlock, and check how soon Home appears. Open More →
Emergency 9-1-1 and inspect the keypad without pressing Call. The assigned driver
records the `PlainphoneBoot` stage lines, including HIDES hidden/shown query time
and NEVER_HIDE_GUARD shown query time. Emulator samples do not establish speedup.

Known limits carried from preview 10:

- A dialer opened outside LMNOPhone cannot be identified and restored after a
  crash. Phone remains one tap from Home. Fallback containment is pending.
- Calling-screen storage failures leave only an in-process record; they do not
  stop startup or opening Phone/Emergency.
- Trusted-person controls and other unwired actions retain the build-5
  explanation. Counts cover supported Phone and SMS/MMS data, not RCS or other apps.
- The checking-screen emergency fallback remains host-tested only.
- Package-change passes are not coalesced. This is a test preview, not full D1 acceptance.

VersionCode `11` (`0.1.0-preview-11`). See [verification](evidence/verification.md)
for separate build, emulator install, and exercise evidence. Lead owns the single
opposite-model review and the spare install. No physical device was accessed here.
