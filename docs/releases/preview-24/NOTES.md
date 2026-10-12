# Build 24 — start and cancel a turn-off wait

More → For Sam and Jo now offers **Turn LMNOPhone off** through the existing
L4 screens, a separate fresh PIN and both confirmations. It starts the real
24-hour waiting record. Home's pending line and the Phone settings box now have
a usable start route. Every other menu destination remains unavailable.

The preview has no delivery addresses and sends no SMS. L4a explicitly says
"This test build doesn't send texts." The existing self-managed preview PIN
record needs no reset-protection profile for this turn-off wait. Sample names
remain Alex, Sam and Jo.

The smallest maturity mechanism closes a pending test-build turn-off before it
can become COMMITTING. After the ordinary dependency, cancel-intent and proven
24-hour checks, `tick(turnOffAvailable = false)` uses the existing cancellation
write with zero restore steps and its terminal CANCELLED_AFTER_ROLLBACK receipt.
This is cancellation before any restore, not rollback of an executing turn-off.
`FLAG_TEST_ONLY` selects it; the default/non-test path retains its previous
COMMITTING behavior and battery gate. The durable receipt supplies Home's line,
"Turning off isn't in this test build. Nothing changed." No turn-off effect port
is constructed or invoked by this path, and the existing preview plan remains.

## Exact spare test steps — Lead is the sole driver

1. Verify the downloaded artifact's uncompressed hash below and install in place
   over build 23. Read back `24 / 0.1.0-preview-24` and retain Device Owner and lock task.
2. On Home tap **More** → **For Sam and Jo**. If **Choose a new PIN** appears,
   enroll a test PIN of at least six digits in both fields and retain it privately;
   then enter that same PIN on **Your PIN**. There is **no built-in/default PIN**.
   If **Your PIN** appears immediately, use the already enrolled PIN instead.
   Do not clear or replace a spare credential. The last explicit driver record
   ([build 17](/PLA/issues/PLA-387#comment-ed73baf2-bcac-44a5-aed6-fc0fb834730a)) left enrollment
   to the tester; it does not prove nobody enrolled later. The author does not
   possess the spare's PIN. Recovery and PIN change are still unavailable.
3. Scroll **Your settings** to **Turn LMNOPhone off**. Read the introduction
   including the no-texts line; tap **Continue** → **Yes, turn it off**.
4. Enter the same PIN **again** at the fresh PIN screen. Tap **Continue** →
   **Start the 24-hour wait** on **Last check**. Expect **Waiting 24 hours**.
5. Tap **OK**. Home shows **LMNOPhone will turn off after … Tap to cancel.**
6. Tap **More** → **Phone settings** → the **A change is waiting** box.
   Tap **Yes, cancel it**, without entering a PIN. Expect **Cancelled**.
7. Tap **OK**, return Home, then Phone settings. The waiting line and box are gone.
   Do not advance time or exercise maturity on the spare.

If an uncertain storage write occurs, the app shows attention rather than falsely
claiming nothing changed. A second start returns the existing **Another change is
waiting** screen; **See the waiting change** opens the current PIN-free cancel view.

## Built / installed / exercised

- **Built:** debug/testOnly version 24. APK SHA-256 `400d839e07a1509cb747c4eb28810131bad780ab1c6100b272b9af7079aa7e0d`.
  Gzip SHA-256 `cbc14264d6f97824cbb1c1c0a82a8135a5a5e4642f134d45e0ea7c4c05b872b5`. Signer SHA-256 `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` matches build 23.
- **Installed:** owned API 35 `pla363-policy-a35`, `emulator-5880`; 23 → 24 in place
  at 2026-10-12T10:30:37Z. Final candidate installed/read back at
  2026-10-12T10:38:38Z. No physical device was used by the author.
- **Exercised:** on the final candidate, two UI starts with fresh PIN and both
  confirmations; Home/U7 pending presentation; PIN-free cancellation removing
  both; maturity with identical owner, LOCKED state, hidden/suspended sets,
  restriction keys and lock-task policy; terminal notice across process restart.
  The original emulator authority and clock files were restored byte for byte.
  The old random emulator PIN was unavailable, so a backed-up, temporarily clean
  enrollment was used; enrollment and starts were through UI, never a seeded wait.
- **Host:** 646 policy, 192 app and 26 focused device tests pass. Tests-first red
  checkpoint `315568d9`; the UI sequencing tests also failed before implementation.
  The uncertain-write assertion failed before its corrective mapping.
- **Screens:** 168 selected UI test methods pass, including Home, Phone settings,
  pending/cancel fixtures, 40 new screen cases at 1.0/1.3/1.5/1.8/2.0 on 360×640,
  and existing trusted-person/name variants. All 92 new/changed goldens inspected.
  New L4 screens use the approved LMNOPhone wording; unrelated old menu copy remains.
- **Specs:** `openspec validate --all` passes. The binding tick interface and
  preview copy exception are documented with this change.

Emergency gate evidence and final disposition are recorded in `evidence/verification.json`.
The gate is copied from preview 23 with version strings only. The retained root-AVD
host wrapper addresses the already documented shell-user `/sdcard` EFAULT; it
changes no app behavior or gate assertion. Emergency checks stop at exactly 911
in the dialer; no call button, live call or SOS action is used.

No public release. Actual turn-off, SMS delivery, other menu actions, D1 integration,
and recovery/profile enrollment remain outside this test increment. The terminal
preview notice stays until another waiting operation replaces its record. The
Lead owns the single review and spare install/start/cancel check on this issue.

[Release record](/PLA/cases/PLA-C86) · [Emulator gate record](/PLA/cases/PLA-C87).
