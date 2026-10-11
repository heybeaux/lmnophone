# LMNOPhone preview 6 — candidate

Build 6 adds crash recovery for the Phone and emergency keypad opened from
LMNOPhone. If LMNOPhone restarts while one of those screens is open, it brings
that screen back inside the managed Home mode. Calm Home, More and optional
missed-call / unread SMS and MMS counts remain from build 5.

To try it: open Phone, or More → Emergency 9-1-1. Check that the keypad opens.
Do not press Call. Crash-recovery tests are performed on the development
emulator; no action from the tester is needed to trigger a crash.

Known limits:

- A dialer opened from outside LMNOPhone cannot be identified and restored after
  a crash. Phone remains one tap from Home.
- Fallback containment is still pending.
- The A1 correction is included: a failed calling-screen record read or write
  no longer stops startup or opening Phone/Emergency. If saving fails, only
  the current process retains which calling screen it opened.
- This remains a test preview. Trusted-person controls and other unwired actions
  retain the build-5 explanation. Counts cover the supported Phone and SMS/MMS
  data, not RCS or other apps.

This candidate is emulator-only. The Lead reviews the frozen candidate once;
the assigned device driver owns any subsequent physical installation.

[Download the candidate test build (gzip)](/api/attachments/045ed326-dcb5-4b6b-8633-ceae82a6beba/content).
Build and emulator details: [verification](evidence/verification.md).

VersionCode: `6` (`0.1.0-preview-6`).
APK SHA-256: `4806bada7b7bd0f827312972b8ff4a98af361df5c5299d625fa25a4a41cd5ace`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
Freeze delta and emulator results: [freeze verification](evidence/freeze/verification.md).
