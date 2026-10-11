# LMNOPhone preview 6 — draft

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
- The A1 correction is scheduled for freeze; it is not included in this prep.
- This remains a test preview. Trusted-person controls and other unwired actions
  retain the build-5 explanation. Counts cover the supported Phone and SMS/MMS
  data, not RCS or other apps.

This preparation is emulator-only. The Lead reviews the frozen candidate once;
the assigned device driver owns any subsequent physical installation.

[Download the draft test build (gzip)](/api/attachments/1bbe2338-8690-470a-8d3b-0581c395f072/content).
Build and emulator details: [verification](evidence/verification.md).
