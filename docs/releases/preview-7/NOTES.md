# LMNOPhone preview 7 — installed on the spare

Build 7 starts restoring Home as soon as you unlock after a restart. Home no
longer waits for Android's boot-completed signal. Emergency on the checking
screen also has a fallback when the preferred emergency keypad is unavailable.
Home, More and the optional call/text count switch are unchanged from build 6.

Installed on the spare in place on 2026-10-11 at 19:39:50 UTC (data and settings
kept). After a restart, Home appeared **8.5–9.3 s** after unlock, down from
about 18 s on build 6 (two restarts). The checking screen, with its Phone and
Emergency call buttons, still shows for most of that time: the app process
itself starts about 4–5 s after unlock. See the [spare readback](evidence/spare/readback.md).

To try it: restart, unlock, and time how long Home takes to appear. Open Phone
or More → Emergency 9-1-1 and check the keypad. Do not press Call.

Known limits carried from build 6:

- A dialer opened outside LMNOPhone cannot be identified and restored after a
  crash. Phone remains one tap from Home. Fallback containment is pending.
- If saving the calling-screen record fails, only the current process remembers
  the screen it opened. A record read/write failure does not stop startup or
  opening Phone/Emergency.
- Crash recovery from More → Emergency returns to the plain Phone keypad,
  without 911 already typed.
- Trusted-person controls and other unwired actions retain the build-5
  explanation. Counts cover supported Phone and SMS/MMS data, not RCS or other apps.
- The checking-screen emergency fallback is host-tested only; the spare has the
  preferred dialer. This is a test preview, not full D1 acceptance.

VersionCode `7` (`0.1.0-preview-7`). See [verification](evidence/verification.md)
for build and emulator evidence, and the [spare readback](evidence/spare/readback.md)
for the install on the phone.
