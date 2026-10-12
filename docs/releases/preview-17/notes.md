# Build 17 — PIN setup and entry

This preview adds a real, persistent PIN through the trusted-person row in More.
A correct PIN opens the existing settings menu. Its actions are not implemented
in this preview and show “Not in this test build”. No PIN is included in the build.

## Try it

1. Open **More**, then the trusted-person row.
2. First time only: enter a PIN of at least six digits in both fields and tap
   **Continue**. Choose a test PIN you can remember; there is no PIN recovery or
   PIN-change route in this preview. Keep the PIN out of issue notes and screenshots.
3. Enter a different PIN: it is refused. Enter the correct PIN to open **Your settings**.
4. Choose any menu action: it shows **Not in this test build**. Go back, then finish
   or return Home. Opening the route again requires the PIN again.
5. Restart the phone. The PIN remains set, and entry is still required.

## Limits

The PIN is a real authority record that later builds will read. Existing or damaged
records are never replaced by setup. Five wrong PIN attempts start the existing
lockout schedule. Leave and reopen the screen after the displayed delay to retry.
Recovery-code setup/use, changing the PIN, waiting-change starts, people changes,
mode changes, turn-off and Update are unavailable. No reset-protection account
profile is created or read.

The reviewed waiting-change wiring 1–4 is included but dormant: no operation is
started, so no pending line appears on Home and there are no pending/cancel tester
taps. The pending-change route needs a reset-protection profile and is deferred.
Wiring 4b Back handling remains outside this build. The Update adapter remains
unwired with its existing findings on [PLA-381](/PLA/issues/PLA-381).

This is the existing restriction-free preview starter plan. PIN setup must not
change approved apps, the emergency floor, Device Owner, lock task or device
restrictions. The controls screen grants no Android settings or app access.
It closes access when it leaves the foreground. Its screenshot protection remains
enabled, so screenshots of that activity are black; evidence uses cleared-field
UI hierarchy text and visible Home instead.

The earlier `blocker.md` and `evidence/checkpoint.md` describe superseded scopes
and a version-16 compilation checkpoint. This document records the Lead’s final
PIN-only scope on [PLA-387](/PLA/issues/PLA-387).
