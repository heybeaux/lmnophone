<!-- SPDX-License-Identifier: Apache-2.0 -->
# Policy seam

Package: `org.plainphone.policy`. All decisions run as JVM tests without Android.

## Policy engine slice

`PolicyEngine.decide(PolicyInput): PolicyPlan` is pure. Lane D supplies live `InstalledPackage` and `FloorIdentity` facts. These are internal trusted adapter inputs: app intent extras and self-declared filters must never become OS-managed facts. Every identity also requires a live system-app flag. Inventory matches both model and fingerprint.

The plan carries the target hidden set, explicit unhide set, never-hide dependencies, emergency UI subset, lock-task packages/features, restrictions, ordered readback steps, and expected final state. Inventory and floor outrank classifier and allowlist choices. Conflicts/refused destruction are returned for logging without sensitive data. `allowedDestruction` is a guard result only, never an instruction to execute external input. The current engine emits hides and emergency repairs, not suspend/disable/uninstall operations.

Application order is revoke journal, restrictions, floor, never-hide guard, hides, lock task, then home. Locked boot emits only the first four stages; the floor stage includes restoring emergency UI membership. Lane D reads back each stage and stops handback on failure. Emergency repair always remains available. Every unhide must use the mandatory synchronous component flip and readback; discretionary approval/session starts must pass the battery guard before changing policy.

`previousLockTask` means the protected list with session relaxations removed. Unknown builds keep that list plus emergency UI, permit only user-app sweeps, and return the added/version-changed package set. Unknown + both invalid policies runs no sweep and keeps hidden state except protected dependencies. The compiled baseline uses the floor, matching inventory, Messages, Plainphone, and every restriction. Last-known-good validation is performed by `PolicyStore` in lane D.

Session access comes from `SessionController.access()` with a fresh boot reading. The engine validates the deadline and exact Play/inventoried Settings package set; expired, rebooted, corrupt-policy and locked-boot inputs add no session access. Hashing, persistence, and actual Android readback remain separate adapters.

## Verification

Core tests precede implementation in git history. Policy tests similarly ran red before implementation. Tests cover base tasks 4.2, 4.11 and the decision portion of 4.13: inventory/floor precedence, spoofed identities, every destructive guard operation, emergency UI membership, known/unknown/corrupt policy, changed packages, apply/readback order, locked boot, reapproval, and deterministic output. Device behavior and emergency release gates are not claimed by JVM tests.

Run using the shared Gradle build:

```sh
cd app
./gradlew :core:test :policy:test :core:check :policy:check
```

## PIN/recovery slice

`PinVault` receives `CredentialStore`, `KeyDerivation`, `SecretEntropy`, and `RunningClock`. The store must atomically compare the durable revision and replace the whole credential record. A failed clock checkpoint, missing record, or failed write yields no authority. Lockout notices are durable obligations (`pendingNotices`) for lane D's text queue; successful login does not erase them.

`prepareCredential` returns a hash-only credential and a one-time recovery-code presentation. Lane D supplies cryptographically secure entropy and PBKDF2-HMAC-SHA256. The vault enforces a 16-byte salt and at least 600,000 iterations. Each holder and each credential channel has an independent counter. Entries during delay are not verified or counted. Remaining delay uses proven time, so reboot/offline/wall-clock changes cannot shorten it.

`authenticate` binds fresh authority to an exact action/binding and credential generation. `consume` is single-use, process-local and boot-bound, and expires exactly two minutes after the PIN entry using boot elapsed time. Backwards boot readings deny it; epoch repairs do not extend it. Run consumption and the following action on the same serialized dispatcher as every credential change. These objects must never come from outside intents or be deserialized. Recovery accepts uppercase hex and grouping spaces/hyphens, rejects other characters, and replaces only its holder's PIN/code and raises that generation atomically; it grants no action authority or wait skip. Waiting-change cancellation on a generation change will be connected in that controller's later slice.

These tests use synthetic secrets and a deliberately cheap fake derivation. They verify logic, not the production cryptographic adapter. Profile/contact/FRP data and the waiting-change transaction extension remain for the waiting controller slice.

## Proven clock

`ProvenRunningClock` implements the core `RunningClock` seam through `ClockStateStore`, a boot-time supplier, and an epoch supplier. Lane D supplies live monotonic time/boot identity, checksum validation, and atomic compare-and-set storage. Epoch IDs must never be reused. A null boot identity or failed write returns no time authority. Repeated checkpoints add each interval once; new boots add only time since that boot started. Corruption, backwards readings and overflow repair to a fresh zero epoch anchored now. Schedule checkpoints at least every five minutes and on every state transition/debit.


## Supervised session journal

`SessionController` requires `JournalStore`, `SessionEffects`, `RunningClock`, a matching inventory (or null), and `AuthorityConsumer` (implemented by `PinVault`). Start authority is bound to `PrivilegedAction("session:${kind.name}", sessionId)`. Use a new non-reused session ID for each request; callbacks carry that ID. The same serialized dispatcher owns all controllers and storage writes.

`onStart()` must run before any other work. It revokes all potential relaxation first, runs the protected-state check/readback, then clears the journal. No credential data or clock is needed for recovery. `start` writes an opening journal before access, checks battery (5% minimum unless charging), opens only Play or the inventoried Settings/same-UID set, and reads back. All end triggers write closing direction, revoke, check, read back, then clear. Any failed step leaves handback unavailable. The adapter implements only the spec's protected checks and preserves ordinary setting changes; emergency access never waits for those checks. Mandatory restrictions are never lifted.

The crash test injects failure before and after every journal write/revoke/open boundary and at protected-check/readback boundaries, reconstructs the controller, and proves revocation is first. This is a JVM journal-order proof, not evidence of Android durability or the power-loss mitigation. If Plainphone cannot run, the disclosed custody limit still applies.


## Foundation review fixes

- `SessionJournal.restore(id, kind, packages, deadline, phase, matchingInventory)` is the public storage adapter path. Check schema/checksum and decode scalar fields first. Supply only the inventory matching the live model/fingerprint; invalid scope becomes `Damaged` and requires full revocation. Restoration is data, not permission to bypass `SessionController.onStart()`. `SessionAccess` has value equality so independently decoded snapshots support compare-and-set.
- An end request latches closure before any journal write. Failed writes/revocation cannot make `access()` live again; `tick()` retries restrictive startup recovery until checks, readback and journal clearing succeed. Restart retains the existing revoke-first contract.
- Plainphone is always in the final lock-task list, including unknown builds and locked boot with no saved UI list. Ordinary approval entries cannot grant Play or matching-inventory Settings/sibling access. Emergency-floor precedence remains intact; session access still requires the bounded controller path.
- The added tests reproduce the Verifier's F1/F2 and all five Lead findings before their fixes. The public restore path is also exercised by a fake that stores only plain fields, with independent decode and CAS.


## Play candidate intake and signer identity

`ApprovalBinding(packageName, signingCerts)` snapshots a nonempty set of current signing-certificate digests. `matches` requires the exact package and complete signer set; extra, foreign, or missing signers fail. It is identity data, not an approved entry or action authority. Lane D supplies verified package facts, never request text or app-provided intent extras.

`PlaySessionMachine.begin(SessionController.access(), now)` starts immutable candidate intake only for a live, exact Play session with at most ten minutes remaining. Feed `installed` the callback's original session ID, actual installed identity, fresh boot time, and fresh controller access on the same serialized dispatcher used by the controller. Stale IDs are ignored. An ended, expired, rebooted, revoked, replaced, or backwards-clock session accepts no new candidates. A changed deadline cannot extend intake. All session end triggers close intake; `onRestart` closes any retained state. Candidates remain unapproved data after closure, and cannot reopen Play.

This additive slice covers identity binding and session event filtering within base 4.7. It does **not** implement persistent approval, permission application, unapproved-install policy reconciliation, or the end-to-end Add an app scenario. Until that transaction lands, `StoredPolicy.approved` remains the earlier package-only foundation seam and must not be treated as the final signer-bound approval store. The subsequent approval transaction must re-read current package facts, match the binding, reject browser/session-only candidates, consume fresh authority, and persist permissions plus approval before unhide/readback. Merely appearing in `candidates` never permits unhiding an app.

Test-first evidence: the new digest and Play tests failed to compile before implementation in `e5d3b69` and `ab61386`. They exercise immutable identity/candidates, exact signer matching, stale and duplicate callbacks, replaced signers, all session end triggers, exact deadlines, reboot, rollback, revocation, replacement access, and restart.

## Monthly checks and app staleness

`MonthlyCheck` exposes immutable scheduling and completion transitions. Calendar dates govern this advisory monthly schedule only; they never open Play, skip elevation, or grant authority. Initial setup supplies a due date and unique cycle ID. Completion records the calendar date and schedules one calendar month later (including end-of-month clamping).

Call `reminder` for every current trusted person. It allows the due-date attempt and up to seven following daily retries, without catch-up bursts. Persist the returned state atomically before dispatch. The `MonthlyReminderKey(cycleId, recipient, attemptDate)` is stable across restart/replay of a pending attempt. A definitive failure followed by a new daily retry receives a different attempt date and key, so outbox deduplication cannot suppress that retry. Lane D's durable outbox must deduplicate/reconcile this key: a pending request is a replay of the same logical message, never permission to send another SMS blindly. Report failure only when the send definitively failed; uncertain outcomes remain pending. After the retry window no new dispatch is requested. Delivery callbacks are scoped to the cycle, person and attempt date. Actual carrier delivery and exactly-once SMS behavior are not proven by these JVM transitions.

The overdue line appears strictly more than seven days after the due date regardless of text delivery. Its sole tap action is `EXPLAIN`; self-managed wording addresses the user. The check's separate start flow must use an ordinary supervised Play session or a Play elevation in self-managed mode. Only that flow may call `openedFromCheck` after its session opens. Never attach an arbitrary existing Play session. Its `MonthlyReview` is process-local and must not be restored. Completion requires the exact live original session and a fresh PIN grant for `completionAction(review)`. The caller supplies fresh monotonic readings/current controller access on the shared serialized dispatcher, atomically compares the previous state before committing the returned replacement, and removes the notice only after that write succeeds. Failed persistence requires a new PIN; consumed authority is not restored. These are state transitions, not an Android storage/outbox implementation.

Unresolved update failures persist across checks until explicitly marked resolved during an authenticated completion. A recorded failure and resolution cannot conflict. `AppVersionRecord` retains verified version and last-update facts for each approved app; `AppStaleness.observe` tracks how long the same version/update timestamp has been observed. After thirty unchanged days, the Play-services note is neutral and makes no claim about newer versions or phone security. These records do not change approvals or visibility. A signer change still follows the separate reapproval rule.

This slice covers the decision/state portions of base 4.7 and 4.12, including failed/delivered reminders, daily bounds, restart replay, clock rollback, overdue notice and completion conditions. End-to-end SMS durability, Play elevation, permission/approval transactions and device behavior remain separate integration work.

## Typed notice selection

`NoticeLine.decide` returns one optional home line, plus pending/in-progress change details for the needs-attention and Plainphone settings screens. Priority is needs-attention, pending waiting change, self-managed elevation, then overdue monthly check. Completed/cancelled operations disappear; committing/rolling-back operations expose only an already-in-progress action. Waiting projections carry a unique request ID, kind, durable phase and proven remaining time. Elevation projections carry a grant ID and remaining countdown; callers must omit lapsed/used grants. These projections do not create operations or grant authority.

`MonthlyNotice.kind` replaces `MonthlyNotice.text`: the policy returns `SUPERVISED_OVERDUE` or `SELF_MANAGED_OVERDUE`. Lane B supplies localized wording, names, counts and duration formatting for all notice kinds. No placeholder names are returned by monthly policy. Rendering at the largest font without hiding Phone/Messages remains lane B's responsibility.

A pending-change tap requests a cancellation confirmation without a PIN. After confirmation, re-read the current operation and call `confirmCancel`: a stale ID or terminal operation does nothing; a committing/rolling-back operation reports already-in-progress; only a matching pending operation yields `RequestCancellation`. The future waiting controller must serialize that request with commit, persist cancelled state and owed texts before showing cancellation, and retry failed writes without committing. Notice selection itself never emits a completed cancellation or sends a text. Keep cancellation reachable on both fallback screens even when the attention line masks it. Monthly taps only explain; elevation-ready taps request Start confirmation and never automatically open access.

Tests cover all 16 priority combinations, first look, all waiting kinds, hidden cancellation routes, stale/terminal/in-progress cancellation, supervised versus self-managed elevation, typed monthly notices and negative input validation. This is the notice decision/navigation portion of change 2.9; durable cancellation/texting and elevation validity remain controller work, not claimed complete by these projections.

## Waiting gate: turn-off start, maturity and cancellation

`WaitingChangeController` currently supports only the turn-off **waiting gate**. It creates pending state and later persists committing direction; it does not restore settings, remove Device Owner or report Off. All other waiting kinds, credential patches, supervised recovery-code skip, SMS cancel, lost-record reconciliation and forward/rollback effects remain subsequent work. Do not expose this as completed turn-off functionality.

`prepareTurnOff(starter)` reserves a unique request ID and cancel code through `WaitingIdentitySource`, whose durable allocator must never reuse either even after abandoned preparation. It returns a process-local `WaitingStart` with the exact authentication action; it grants nothing and must not be restored. `startTurnOff` requires both confirmations, matching current starter/mode/structure dependencies and a fresh grant. `PinVault.consumeFor` verifies the actual PIN holder, preventing a caller from attributing the wait to a different credential. Each preparation is consumed once after successful authority consumption; a failed write requires a new preparation and PIN.

`WaitingChangeStore` reads the highest-revision valid checksum copy of the operation/outbox tuple from two durable copies; initialized vacancy is `WaitingState()`, while missing/unreadable state is null and holds the slot. Every compare-and-set atomically persists the operation and its newly owed receipts with a higher revision. Lane D must capture each recipient's delivery address under that same atomic storage boundary; the model freezes recipient IDs/generations and the original operation per receipt, and the adapter must never resolve an old receipt to a replacement person's current address. Deliver/reconcile queued receipts on every startup with stable key deduplication, then acknowledge them durably. Receipt delivery never gates operation progress. Original codes and request context remain with queued receipts even after the slot is reused; do not log them. SMS cancellation is not enabled by having a code in the record.

`tick(requestId, battery)` re-reads the operation and ignores stale or terminal requests. It cancels on starter credential generation, mode or structure changes; an unrelated person's PIN generation does not cancel it. Maturity requires 24 hours of proven time, with old epochs conservatively anchored at new epoch zero. Clock failure grants no maturity. Battery below 5% without charging defers commit. Persisted committing/rolling-back phases keep the single slot and refuse PIN-free cancellation; this gate never changes their direction or re-checks post-commit dependencies.

`cancel(requestId)` needs no authority. It records a process-local latch and writes REQUESTED to the
separate `CancelIntentStore` before reading the operation or attempting the atomic cancelled-state/
receipt write. A failed intent write does not prevent attempting the restrictive operation write.
Only that operation write yields CANCELLED. Tick retries matching cancellation before maturity;
a persisted intent survives controller/process restart, even if the operation store was unreadable
or its write failed. Stale IDs cannot replace the matching intent or reverse terminal/committing direction.

`CancelIntentStore` is a required port; there is no volatile default. Initialize a durable CLEAR entry
before a new pending operation is written. REQUESTED is monotonic. Missing, damaged or unreadable
intent entries return UNKNOWN, which blocks maturity. Writes are idempotent and return true only
when durable. Lane D implements this in device-protected storage independently of the operation
copies, on the common serialized dispatcher. Request IDs are never reused. Do not delete an entry
for an active operation; terminal-entry garbage collection must never turn REQUESTED into CLEAR.
Existing pending records without an intent entry require explicit reconciliation, not guessed CLEAR.

`WaitingOperation.recipientGenerations` is an immutable start-time snapshot, persisted in every phase,
independent of outbox acknowledgement. Cancellation uses current credentials if readable, otherwise
this fallback; it also works if the clock is unavailable. Lane D must retain the corresponding original
delivery addresses alongside the operation snapshot, independently of STARTED text delivery/removal.
The new field is required on restore; do not synthesize an empty recipient list for an old record.

Durability limit: if BOTH the separate intent write and the cancelled-state write fail before anything
is saved, a process death cannot recover that unsaved user action. The live controller still blocks and
retries; it never reports Cancelled. This slice does not claim to solve total persistence failure or the
spec's lost-record reconciliation path. The independent intent port closes the restart gap when the
intent is durable and the operation write fails. Fault tests distinguish interruption before/after the
intent write. Android filesystem durability and failure correlation require lane D verification.

Calls/emergency must never depend on this gate, its storage, receipt dispatch or future restore effects.
Use the same serialized dispatcher as PinVault, credential writes and the eventual restore controller;
session startup revocation still runs first.

Tests are written first for person-bound authority, atomic start/receipts (including interruption before/after write), single-slot protection, fake proven-clock reboot/restart/epoch behavior, exact maturity, low battery, dependency cancellation, failed cancel/commit writes, stale callbacks, invalid storage/credentials, and receipt preservation/acknowledgement. These tests prove the bounded gate, not the unfinished turn-off sequence or Android durability.

Cancellation read failures retain the request IDs in the running controller until a valid operation
record can reconcile them. A matching pending cancellation is retried before maturity; stale IDs
cannot replace that intent or reverse a committing/rolling-back operation. This memory guard is
not durable acknowledgement: success still requires the atomic cancelled-state and receipt write.


## Turn-off restore sequencer

`TurnOffController.tick(requestId)` drives an already-COMMITTING waiting operation. It does not
start or skip a wait. Its first call after construction revokes temporary access, then checks live
Device Owner state and reads the operation. Stale IDs, pending/cancelled operations and rollback
records do not run forward effects. Unknown ownership or damaged operation storage yields a typed
NEEDS_ATTENTION failure. The separate lost-record reconciler is still unfinished: do not continue
forward from a damaged record or treat this diagnostic as a completed lockdown repair.

`WaitingOperation.restoredSteps` counts read-back categories in the same durable operation record.
Each tick restores one category, reads it back, then increments the count with a revisioned CAS.
A crash before that CAS repeats the same idempotent step; a crash after it resumes the next step.
Every required category is represented: restrictions, hidden apps, lock-task packages/features,
persistent preferred activities, input methods/accessibility, keyguard features, browser role,
changed SMS/dialer defaults, reset protection, and Play restrictions. The adapter must use the
complete durable original-settings inventory. Missing data fails; an unchanged category is a
verified no-op. Hidden-app restoration includes the required synchronous unhide flush/readback.

After all steps, the controller reads every category back again, then verifies another home app and
the system dialer resolve before requesting Device Owner removal. If final verification finds a
mismatch, it journals a rewind to that restore step; it does not remove ownership. Every removal
retry repeats these checks. There is no wipe port or fallback.

OFF means live ownership is absent, even if the removal call returned an uncertain result. Completion
and its receipt are written together; OFF with `journalPending=true` means that receipt still needs
retry. A lost record with absent ownership also reports OFF but cannot synthesize a completion text.
Current credential recipients are used when readable, otherwise the operation's immutable fallback.
A COMPLETED record with ownership still present is an attention failure, not proof of Off.

Lane D owns synchronous idempotent effects, original-settings inventory coverage and physical
readbacks. Use the common serialized dispatcher for all operations, policy application and effect
calls. Startup session revocation must precede this controller and may not unconditionally re-apply
full lockdown while a valid operation is COMMITTING. The final full readback also catches changes
introduced by restart revocation. Calls, emergency and alerts must not wait on this sequence.
Recovery-code skip, lost-record reconciliation and Android execution remain separate work.

The waiting gate now checks credential dependencies before the UNKNOWN intent maturity guard.
An unreadable intent store therefore cannot postpone restrictive dependency cancellation and its
texts, while UNKNOWN still prevents forward commit.
