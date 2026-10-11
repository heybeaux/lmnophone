# LMNOPhone preview 8 — test candidate

After a crash, Emergency opened from More comes back with 911 typed in the
keypad. It does not place a call. Home, More and the optional call/text count
switch otherwise stay as they were in build 7.

The recovery test needs a root process kill, so there is no matching test for
the spare. The immediate tap-and-crash recovery is emulator-checked; results are recorded in [verification](evidence/verification.md).
For an ordinary check, open More → Emergency 9-1-1 and look at the keypad.
Do not press Call.

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

VersionCode `8` (`0.1.0-preview-8`). 
Spare: installed in place 2026-10-11T20:56:31Z, gate `exit=0`, safety readback unchanged from build 7; the crash recovery itself stays emulator-checked ([spare readback](evidence/spare/readback.md)).
