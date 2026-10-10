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

`onStart()` must run before supervised session work. With the combined elevation store, run both controllers' startup before publishing authority; either order is safe (see the shared-journal section below). It revokes all potential relaxation first, runs the protected-state check/readback, then clears the journal. No credential data or clock is needed for recovery. `start` writes an opening journal before access, checks battery (5% minimum unless charging), opens only Play or the inventoried Settings/same-UID set, and reads back. All end triggers write closing direction, revoke, check, read back, then clear. Any failed step leaves handback unavailable. The adapter implements only the spec's protected checks and preserves ordinary setting changes; emergency access never waits for those checks. Mandatory restrictions are never lifted.

The crash test injects failure before and after every journal write/revoke/open boundary and at protected-check/readback boundaries, reconstructs the controller, and proves revocation is first. This is a JVM journal-order proof, not evidence of Android durability or the power-loss mitigation. If Plainphone cannot run, the disclosed custody limit still applies.


## Foundation review fixes

- `SessionJournal.restore(id, kind, packages, deadline, phase, matchingInventory)` is the public storage adapter path. Check schema/checksum and decode scalar fields first. Supply only the inventory matching the live model/fingerprint; invalid scope becomes `Damaged` and requires full revocation. Restoration is data, not permission to bypass `SessionController.onStart()`. `SessionAccess` has value equality so independently decoded snapshots support compare-and-set.
- An end request latches closure before any journal write. Failed writes/revocation cannot make `access()` live again; `tick()` retries restrictive startup recovery until checks, readback and journal clearing succeed. Restart retains the existing revoke-first contract.
- Plainphone is always in the final lock-task list, including unknown builds and locked boot with no saved UI list. Ordinary approval entries cannot grant Play or matching-inventory Settings/sibling access. Emergency-floor precedence remains intact; session access still requires the bounded controller path.
- The added tests reproduce the Verifier's F1/F2 and all five Lead findings before their fixes. The public restore path is also exercised by a fake that stores only plain fields, with independent decode and CAS.


## Play candidate intake and signer identity

`ApprovalBinding(packageName, signingCerts)` snapshots a nonempty set of current signing-certificate digests. `matches` requires the exact package and complete signer set; extra, foreign, or missing signers fail. It is identity data, not an approved entry or action authority. Lane D supplies verified package facts, never request text or app-provided intent extras.

`PlaySessionMachine.begin(SessionController.access(), now)` starts immutable candidate intake only for a live, exact Play session with at most ten minutes remaining. Feed `installed` the callback's original session ID, actual installed identity, fresh boot time, and fresh controller access on the same serialized dispatcher used by the controller. Stale IDs are ignored. An ended, expired, rebooted, revoked, replaced, or backwards-clock session accepts no new candidates. A changed deadline cannot extend intake. All session end triggers close intake; `onRestart` closes any retained state. Candidates remain unapproved data after closure, and cannot reopen Play.

`PlaySessionMachine` covers identity binding and session event filtering within base 4.7. `AppApprovalController` below now supplies the supervised persistent transaction over these candidates and `PermissionChoices`. `StoredPolicy.approved` is legacy package-only data and is ignored for authorization. Merely appearing in `candidates` never permits unhiding an app. Android effects and the self-managed elevation path still require integration.

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
NEEDS_ATTENTION failure. Route damaged records to WaitingReconciler (below); do not continue forward from a damaged
record or treat this diagnostic alone as a completed lockdown repair.

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
Android execution remains separate work. Lost-record reconciliation and recovery-code skip are below.

The waiting gate now checks credential dependencies before the UNKNOWN intent maturity guard.
An unreadable intent store therefore cannot postpone restrictive dependency cancellation and its
texts, while UNKNOWN still prevents forward commit.

## Policy re-apply during turn-off

Every periodic (15-minute), package-event, locked/unlocked boot and post-action policy pass must
read the current highest-valid-revision waiting operation on the shared serialized dispatcher and
pass it as `PolicyInput.waitingOperation`. Do not derive this input from UI state, an old timer payload
or a cached committing flag. Re-read immediately before applying; do not apply an old full-lockdown
plan after a turn-off transitions to COMMITTING.

For a valid COMMITTING turn-off, `PolicyEngine.decide` emits only `EMERGENCY_FLOOR`: repair the live
trusted OS floor by unhiding/enabling/unsuspending it and reading that back. It emits no restrictions,
hides, lock-task changes, Home assignment or other lockdown stages. Ordinary approvals, inventory-only
exceptions and session grants add no actions. Caller-requested destructive operations are refused.
The empty plan collections do NOT mean to set an empty lock-task list or clear restrictions: execute
only emitted stages. `ExpectedPolicyState` now uses nullable fields: null means outside this plan,
so neither write nor assert that value. Only the floor readback sets are populated in this mode.

Pending, cancelled, completed, rolling-back and missing/invalid operation records do not select this
exception. They follow normal policy computation; a missing record still requires the separate
lost-record reconciliation workflow. After observed Device Owner removal, stop scheduling Device Owner
policy writes and process any pending completion receipt instead.

`SessionController.onStart` must still revoke temporary authority first. Its adapter's
`revokeAllRelaxations`, `protectedStateCheck` and `readBackPolicy` must use the same fresh operation-aware
rule: a committing turn-off must not be re-locked by session revocation or protected-state repair.
All other protected-state checks follow this rule too. These are mandatory Lane D wiring requirements;
the pure JVM tests cover plan selection and simulated re-apply between sequencer steps, not Android
integration. Lane D should immediately re-tick the sequencer on PROGRESS instead of waiting 15 minutes.

Completion SMS remains a queued effect. Lane D must verify SEND_SMS remains usable after Device Owner
removal before claiming delivery. If it does not, the receipt protocol needs an explicitly reviewed
pre-removal notification state; do not silently label a pre-removal message as completed turn-off.


## Lost operation records and PIN Repair

`WaitingReconciler` handles the lost-record path in change 2.3, regardless of the lost change's kind.
It ignores any valid operation, including pending and committing operations. On lost state it revokes
temporary authority first and checks live ownership. Observed Device Owner absence means OFF without
guessing the lost change or manufacturing a completion receipt. Unknown ownership holds the slot.

While still Device Owner, reconciliation uses the current atomic `CredentialState.recoveryProfile`:
every PIN holder's reset-protection userId, the optional backup userId, and active `FrictionSettings`.
The holder map must exactly match the current credential slots. PIN authentication and PIN/code
replacement preserve this profile. A missing profile is not filled with defaults: it produces
NEEDS_ATTENTION and keeps the slot held. Schema migration/enrollment must persist verified profile
data with the credential record; do not construct it from a pending patch. Lane D's guided setup must
write a complete profile before enrollment finishes, so a fresh phone can always be reconciled.
Handover and mode-change credential commits must update that profile atomically with the active slots. Identifiers are sensitive;
keep them out of logs and screenshots. Their diagnostic representation is redacted.

The input port must supply live OS facts and the complete active policy store on the common serialized
dispatcher. The reconciler discards any supplied waiting operation and session access before computing
the normal policy plan. It applies that plan, the full reset-protection list (including the platform's
required broadcast), and active friction settings, then reads all three back. Friction restoration
must not reset consumed daily debits or adopt a pending countdown/limit. A failed effect/readback
returns a typed reason for the needs-attention/Repair screen and leaves the slot held. Invalid policy,
missing emergency dialer, and locked-boot partial inputs cannot claim complete recovery; retry with
full policy inputs after unlock. Classifier conflicts and refused destructive requests do not prevent
recovery when readbacks pass: the applied plan keeps its diagnostics and needsAttention flag for the
normal attention UI, while the lost slot is released. Do not interpret RECOVERED as clearing those
policy diagnostics. A refused action remains refused. Calls, Emergency and alerts remain available
through every failure.

`WaitingReconciliationStore.lostRecord()` distinguishes confirmed loss of both copies from a temporary
read failure. Temporary/unclassifiable I/O failure returns no loss token and keeps the gate held. For
confirmed loss, durably reserve a stable, never-reused episode ID, distinct from the initial storage
generation, and return that same ID across restarts until recovery completes. All adapters must use
this one store/dispatcher for ordinary waiting writes, credentials, reconciliation and policy effects.

After all readbacks and a fresh credential-revision/owner check, `completeReconciliation` must compare
the still-lost episode and current credential revision, then atomically install a vacant waiting state,
its `RecoveryReceipt`, retained delivery identities/addresses, and the durable audit entry. No pending
credential patch is applied. The receipt names the recovered people, mode, friction and ACTIVE state;
lane B supplies the words/names. It never says the lost change was cancelled or failed to finish. Send
it to every current holder, including the self-managed user, with immediate outbox scheduling. A failed
write reports no recovery. Restart after success observes the valid state and does not enqueue again.

Recovery starts a new `WaitingState.storageGeneration` equal to the loss ID, with revision zero. The
adapter must durably fence all old-generation copies even if they have higher numeric revisions.
Ordinary CAS compares **generation and revision**; a revision-only comparison is now invalid. Ordinary
writes preserve the generation and recovery outbox. `acknowledgeRecovery(key)` removes only the delivered
outbox entry; the adapter retains the audit entry. This storage transaction and generation fence need
Lane D crash/fsync validation; the JVM fake demonstrates the required before/after outcomes only.

`prepareRepair(person)` binds a PIN action to the current loss episode and current holder. Authenticate
that exact `RepairRequest.action` with PinVault, then pass the fresh grant to `repair`. It consumes the
person-bound, single-use authority and invokes exactly the same reconciliation. Failed attempts need
another fresh PIN for another manual Repair. Stale episodes, wrong/expired/replayed grants and valid
waiting operations cannot be repaired into a vacant slot. Repair never starts or skips a waiting change,
never removes Device Owner and never adopts pending values. Automatic startup reconciliation needs no
PIN; the PIN requirement applies to the explicit Repair action.

This slice implements the pure recovery/Repair protocol and its storage/effect contracts. Android
recovery execution, delivery durability and the other waiting-change kinds remain unfinished.
The bounded turn-off recovery-code skip is below. Total storage unavailability remains held with needs-attention; it is not guessed vacant.


## Recovery-code skip of a pending turn-off

Configure the single live `WaitingChangeController` with `WaitingSkipSupport`, using the same PinVault,
credential store, waiting store and serialized dispatcher as all other actions. Without this explicit
transaction support, skip is refused. There is no default two-write implementation. The UI must show
that exact pending turn-off and ask for skip confirmation, the selected person's recovery code and a
new PIN. Pass the displayed request ID to `skip`; a stale ID never selects a newer operation.

Skip is available only in TRUSTED mode and only while PENDING. The new PIN must meet the existing
six-ASCII-digit minimum. Self-managed mode, absent holders, stale requests, terminal/committing/rollback
states, unknown intent data and insufficient battery cannot replace credentials or skip. Charging
satisfies the existing low-battery gate. The controller checks these conditions again in the successful
recovery-code callback before invoking the transaction. Skip bypasses the 24-hour time gate only; it
never bypasses cancellation, storage validity or battery requirements.

Cancellation takes precedence. Both persisted REQUESTED intent and the live controller's unresolved
cancel latch cause cancellation/retry instead of skip. A dependency change already present before skip
cancels the operation at once, just as the ordinary waiting tick does. Only after these checks does the
skipper replace starter authority. The original starter metadata remains for audit, while `skipper`,
`skipperGeneration`, `authorityPerson` and `authorityGeneration` identify the new committing authority.
Those fields survive all restore checkpoints and completion. Later credential changes cannot reverse
a committed turn-off. Only turn-off waits are implemented by this controller today.

PinVault's internal recovery verification path is shared with forgotten-PIN recovery: identical code
normalization, per-person recovery counters, lockout schedule and tenth-failure notice. Wrong/delayed
attempts persist through the existing credential store and never modify the operation. A PIN lockout
does not block a valid recovery code. An ordinary `PinVault.recover` call still only resets the selected
PIN/code and never skips an operation. No reusable recovery authority is exposed.

For a successful skip, `WaitingSkipCommitter` must atomically compare the current credential revision,
waiting storage generation/revision, matching PENDING request and CLEAR cancellation intent, then save:

- the selected person's new PIN verifier, new recovery-code verifier, reset counters and incremented
  credential generation, preserving every other holder and the active recovery profile;
- the operation in COMMITTING with the skipper recorded;
- a SKIPPED receipt with the new current recipient generations and corresponding fixed delivery
  addresses, preserving existing waiting texts and recovery receipts.

This is one logical durable transaction across the credential and operation records. A write-ahead
transaction/replay scheme is an adapter choice, but all readers must see all old values or all new
values, including after interruption between physical writes. Do not acknowledge two independent
writes as atomic. Run transaction recovery before PinVault, waiting controllers or policy readers.
Do not allow re-entrant UI callbacks or another live waiting controller inside this serialized action.
Lane D must prove crash/fsync behavior; the JVM fake verifies the required all-or-nothing observations.

Only the successful transaction returns `WaitingSkipResult.Committing` with the newly generated
recovery code, for one-time display to the skipper. A failed transaction returns NOT_READY and exposes
no new code or committing success. A crash after a durable commit resumes COMMITTING; resubmitting skip
reports Already in progress and does not regenerate or replay a code. No plaintext recovery code is
stored or logged. Existing one-time-code custody/display limits also apply here.

The skip does not itself restore settings or remove Device Owner. Feed the committed request to
TurnOffController under the already documented operation-aware policy rules. No Android or completion
SMS guarantee is added by these pure JVM tests.

## Runtime permission choices (base 4.14)

`PermissionChoices` is a pure choice/readback model for base approved-apps "Play sessions add and approve apps, with permissions decided at approval". `PermissionManifest` binds the complete installed runtime-permission list to package name, the full current signing-certificate set, package version and build fingerprint. Lane D supplies these facts from the installed package plus verified per-build grant capability; an app request, a permission-name heuristic or an unverified sensor assumption is not a capability source. Supply human-readable labels and affected-feature descriptions for the permission screen and app listing. If feature-specific copy is unavailable, explain the unavailable permission in plain words without inventing app features.

The screen creates a `PermissionSelection` with an explicit Allow or Deny for **every** runtime permission. There is no implicit Allow, Deny, permission-group expansion or carry-forward choice for a newly requested permission. Missing/extra choices or changed live facts block planning. Enumeration order alone does not invalidate a review. An unsupported or unknown Allow blocks planning and names the affected permissions; never silently convert it to Deny or report it granted. Show the capability/feature explanation from the manifest and obtain an explicit Deny to continue on a build that cannot grant it. A resulting Deny plan retains the unavailable-feature listing (with unsupported status when known).

Each `PermissionPlan.writes` entry maps to a Device Owner `setPermissionGrantState` Allow/Denied operation; there is no DEFAULT/reset operation. D must re-read installed identity/facts immediately before writes, serialize package/approval events, apply all choices, then collect fresh readbacks and facts. `verify` checks every selected permission against an explicit managed GRANTED or DENIED state. DEFAULT, UNKNOWN, missing entries, mismatches and changed package facts cannot pass. Extra readback entries do not grant anything. D must report failed writes separately; `Verified` means only that the supplied permission postconditions match. It is not a completed approval transaction or device evidence.

The permission-choice model supplies **no authority and no allowlist mutation**. Its approval/permission-change caller must consume the correct fresh PIN or live bound elevation, re-check package identity and browser eligibility, durably persist the choice record with approval/audit/receipts, close package access as required, and only then expose an approved app. A partial permission write must keep a new app unapproved/hidden and be retried/read back under that transaction; never infer approval from `Ready` or `Verified`. The supervised persistence port and transaction are described below; self-managed timing, device persistence/delivery, Android sensor capability and device Add an app validation remain separate work. These immutable snapshots can be reused for protected-state checks, but a fresh runtime snapshot is required for each verification.

## Persistent supervised app approval

`AppApprovalController` joins `PlaySessionMachine` candidate data, `PermissionChoices`, the shared `PinVault` and `SessionController`. Call `onStart` before preparing or processing any approval. `prepare(play, package, person, selection)` reviews an installed candidate and returns a process-local request whose unique action is used for a **new** PIN entry. `approve` consumes that exact person's action-bound authority once. Candidate history is data only: a retained candidate after Play ends can still be reviewed with a new PIN, but an event cannot approve anything itself. The controller rejects self-managed mode until elevation integration exists. Browser, session-only and never-hide packages are ineligible, as are unverified build/identity/permission facts. Low battery refuses discretionary unhide without gating emergency repairs.

The supervised Add an app sequence is:

1. Consume the PIN authority and close/revoke all session access through the shared `SessionController`, including its protected-state readback. Play cannot remain reachable through this approval.
2. Persist a PREPARING journal; keep the candidate hidden and read that back. Apply every explicit permission choice and verify the complete permission readback against freshly read identity/manifest facts.
3. Re-check actor generation, mode/people structure, package and policy eligibility, same boot and battery. Atomically save the signer-bound app record, COMMITTED journal and receipt to every current trusted-person slot. The store compares the current credential revision as well as the approval revision. D retains fixed recipient delivery identities with this transaction; later PIN replacement must not discard a queued receipt.
4. Apply the complete policy with that committed record, performing the mandatory synchronous unhide flush. Re-read policy and permission state and the installed facts. Clear the journal only after those checks. Only then return APPROVED. The receipt describes a durably saved approval; it does not assert that every activation effect or SMS delivery has completed.

`AppApprovalStore` is one atomic state/outbox/audit boundary. Use the same serialized coordinator as credentials, sessions, waiting changes, package callbacks and every policy writer; no callbacks may re-enter a transaction. D supplies unique operation IDs that are never reused. Failed writes never count as persistence. Constructors validate record/permission completeness and committed journal/payload/receipt consistency; adapters also validate the schema, checksum and revision, returning null on unavailable or invalid storage instead of creating a blank approval store. Enrollment initializes the empty store explicitly. Outbox acknowledgements must preserve any receipt referenced by a pending journal until that journal clears, and must preserve every other field under CAS; durable audit remains D's responsibility.

On every restart, session revocation runs first. A PREPARING record is abandoned after the full current policy is applied/read back, so partial permission writes cannot make the app approved. A COMMITTED record represents an already-authorized durable approval: `resume` retries only its activation after fresh facts/readbacks, without generating another receipt or restoring a PIN grant. Until the journal clears, **ordinary `PolicyEngine` passes exclude the pending app**, even at locked boot or when the build is unknown. Only the controller's validated activation plan includes it. A crash or false return after unhide can leave a partially applied physical state; normal policy excludes/rehides the pending app, and the next startup revokes before retrying. No successful handback is claimed for an incomplete activation. Missing/changed facts, failed readbacks or low battery keep activation pending. A current trusted person can instead withdraw that exact pending approval using the recovery path below; no stale or missing fact automatically authorizes removal.

### Signer-bound policy input migration

`PolicyInput.approvalState` now carries the validated persistent app records, and `installedIdentities` carries fresh exact current signer sets. `PolicyEngine` ignores the old package-only `approved` set; it cannot be used to migrate trust automatically. A missing state means no ordinary app approvals. Duplicate/missing installed facts or mismatched signer sets fail closed; matching-signer app updates keep their existing approval. Invalid/pending ordinary approvals also lose inherited lock-task membership at locked/unknown boot, including system-installed apps whose approval state is missing, empty or PREPARING. System installation and never-hide status alone confer no UI authority. This UI pruning does not newly hide system apps on unknown builds; explicit emergency UI, Plainphone and Messages keep precedence. For UI pruning only, the last stored inventory's minimum UI remains exempt so an unknown build can retain those entries from the previous UI list. An unmatched inventory adds no new minimum-UI entry and supplies no sweep, never-hide or session rules. Every D policy writer, including session/protected-state checks and waiting reconciliation, must supply this state and fresh identities. `StoredPolicy.approved` remains legacy decoding data only, not an authorization source. Existing permission choices remain stored for protected-state checks; an update never creates an implicit Allow for a new runtime permission.

This slice completes the **supervised pure-JVM transaction path** for Add an app, not a device demonstration. D still implements storage durability/CAS, live facts/classifier input, permission effects, synchronous unhide flush and complete readbacks, fixed-recipient outbox dispatch, startup/dispatcher wiring and device validation. Lane B supplies the reviewed permission copy. Self-managed elevation and the remaining waiting/mode/people workflows are separate work. Supervised permission edits use the flow below. Fully activated app removal and reapproval use the flow below. No production Android capability or delivery claim follows from the fake effects in these tests.


### Withdrawing a stuck COMMITTED approval

`prepareWithdrawal(person)` returns a process-local `AppApprovalWithdrawalRequest` for the exact current COMMITTED approval or EDITING permission change. Show its pending package and approval ID for confirmation, obtain a **fresh** PIN for the request's action, and call `withdrawPending(request, confirmedApprovalId, authority)`. Either current trusted person may do this; it does not reuse the approval PIN, accept another person's grant, reset a credential, reapprove a changed signer, or infer an uninstall from a missing manifest. Self-managed withdrawal needs future elevation integration. No wait is introduced: this is the existing reversible app-removal rule applied to a pending activation.

Withdrawal revokes session access first, then saves a WITHDRAWING journal with a credential-revision CAS. The saved intent binds the approval ID, the withdrawing actor, a unique withdrawal receipt ID and the holder-generation snapshot. D must bind fixed delivery identities **at this intent save**, even though the withdrawal receipt enters the outbox only at completion. Preserve the original approval record and receipt until then. The new `AppApprovalReceiptKind` distinguishes APPROVED from WITHDRAWN, and `approvalId` links the withdrawal receipt to the original approval. Existing APPROVED records decode with `approvalId == id`. Update/validate the storage schema for the new phase, optional intent receipt and receipt fields; do not coerce unknown phases to COMMITTED or initialize invalid records.

Once WITHDRAWING is durable, `onStart` revokes first and `resume` can only finish withdrawal, including after credentials, mode, clock epoch or boot change. It never retries the old activation or asks for the consumed PIN again. The controller applies and reads back ordinary policy with session access removed and the target excluded by the pending journal. It re-reads the plan, current package/version/signer facts and build before completing; a changed target invalidates even an otherwise identical policy readback. It then atomically removes the approval, clears the journal and appends the fixed WITHDRAWN receipt, returning WITHDRAWN rather than APPROVED. The original receipt remains intact; outbox acknowledgement/audit rules still apply. Retries cannot create another withdrawal receipt. Nothing uninstalls the app, clears its data or writes permissions. A later approval requires a newly reviewed request and fresh authority.

Cleanup does not depend on matching the old manifest or old signer. A complete installed-package snapshot can establish absence; null manifest alone cannot. Cleanup is not battery gated because it reduces access. Locked boot, a missing system dialer, duplicate target package facts, concurrent committing/rolling-back waiting changes, unreadable storage and failed writes/readbacks keep the slot held. An installed target must be hidden and outside UI/unhide sets before completion; required emergency/never-hide UI is never destroyed to release a slot. Therefore a system target on an unknown build can still require a supported inventory before withdrawal completes, because unknown-build policy forbids newly hiding system apps. D should surface this held outcome as needs-attention with a retry after the prerequisite changes, not as successful removal. This is a safety limit, not automatic stale-entry recovery.

D uses the same serialized, non-reentrant coordinator, vault and session controller as approval. Every policy reader excludes a WITHDRAWING target, and every writer preserves its intent, receipts and other approvals. Crash probes cover both sides of the intent and completion saves, revocation, policy effects and readback; they establish JVM ordering only. D still owes real A/B durability, fresh snapshot completeness, physical containment/flush/readback, delivery and startup tests.


### Removing and reapproving a fully activated app

`prepareRemoval(package, person)` requires an existing approval and an empty operation slot. It returns a process-local `AppRemovalRequest` bound to the exact approval payload and ID, the current trusted person, credential/structure generations and boot. Show its package and approval for confirmation, collect a **fresh** PIN for `request.action`, then call `removeApproved(request, confirmedApprovalId, authority)`. Either holder can remove an app approved by the other. A stale request cannot remove a replacement approval, and requests are consumed once and discarded on restart. Self-managed authority is still unsupported.

After session revocation and protected-state readback, one credential-checked CAS saves REMOVING plus the typed REMOVED intent receipt and fixed recipient generations. D fixes delivery addresses at that save, as for WITHDRAWING. This phase reuses the withdrawal cleanup/readback path and all its held outcomes, including the unknown-build system-app limit. It never resumes activation. Low battery does not prevent removal. Only successful cleanup and final CAS return REMOVED: that write removes the approval, clears the journal, appends the fixed REMOVED receipt and saves the removed app's signer binding in `removedApps`. Retries do not mint another receipt. No uninstall, data erase or permission write is requested; data preservation on Android remains an adapter test obligation.

An activated app's latest APPROVED or PERMISSIONS_CHANGED receipt may already have been delivered and acknowledged. Accordingly `AppApprovalOperation.receipt` is nullable **only for REMOVING**. Preserve it if still queued; never recreate an acknowledged receipt. PREPARING, EDITING, COMMITTED and WITHDRAWING still require it. The existing `withdrawalReceipt` field holds the terminal receipt for both restrictive phases: WITHDRAWN for WITHDRAWING, REMOVED for REMOVING. Its `approvalId` always names the original approval. Schema validation must reject missing/wrong terminal receipt kinds and mismatched journal payloads, and must preserve referenced queued receipts until completion.

`removedApps` is durable review provenance, not an approval or permission grant. It is disjoint from active approvals and grants no policy access. After removal, including after a restart, `prepareReapproval(package, person, selection)` permits review of that already installed app without reinstalling it or restoring a Play session. It requires the saved exact signer binding, fresh installed/build/manifest facts, a complete new permission selection and a new person/action-bound PIN through the usual `approve` transaction. A same-signer update can be reviewed with its current manifest; a replacement signer requires new Play candidate intake. The marker is removed atomically with the newly committed approval. Abandoning PREPARING leaves the marker available for another fresh review; the marker never restores old PIN authority or permission choices. Withdrawal of a never-activated initial approval does not create this marker; withdrawal of a saved permission edit preserves it because the app was already approved before the edit.

D must version the combined record for REMOVING, REMOVED, nullable original receipt and `removedApps`, validating every field and preserving the map through all other writes and receipt acknowledgements. A validated older schema can supply an empty removed-app map; it must not infer entries from package-only legacy approvals. Continue using the shared non-reentrant coordinator and startup revocation order. The JVM tests cover intent/completion crash boundaries, acknowledged receipts, failed effects/CAS, stale requests and removal followed by fresh reapproval after reboot. They establish no physical containment, Android durability or SMS delivery guarantee.


### Editing an approved app's permissions

`preparePermissionEdit(package, person, selection)` reviews an existing active approval with fresh installed/build/manifest facts and a complete Allow/Deny selection. The signer binding must match the existing approval exactly; a same-signer update may supply a new current manifest. Browser, floor, session-only and unsupported build/policy cases remain ineligible. The process-local `AppPermissionEditRequest` captures the old approval ID and full payload, the new permission plan, person, credential/structure generations and boot. Show the target and choices, confirm `request.approvalId`, obtain a fresh PIN for `request.action` (`app:permissions`), and call `editPermissions`. Either holder can act; stale requests cannot replace a newer edit or approval. No waiting period is introduced.

The edit first revokes sessions and checks protected state. One credential-checked CAS then replaces the stored approval with the new record, saves EDITING, and queues a PERMISSIONS_CHANGED receipt to all current holders with fixed delivery identities. The new record/receipt ID is unique; the receipt's `approvalId` references the prior record. Its wording must describe **saved permission choices**, not completed permission effects or activation. Old queued receipts remain intact; acknowledged receipts are not recreated. All ordinary policy passes exclude the pending target immediately, but physical hiding still depends on effects/readback.

`resume` on EDITING hides and reads back the target before permission writes, rechecks fresh eligibility before every write, writes every choice, and verifies all permission states and live facts. A CAS advances to COMMITTED without creating another receipt. The ordinary committed activation path then verifies permissions, applies unhide with flush/readback, rechecks facts, and clears the journal. Only complete activation returns PERMISSIONS_CHANGED. Neither partial writes nor the intent receipt count as successful application. Low battery holds discretionary activation/edit processing; a fresh-PIN withdrawal remains available for restrictive cleanup.

On restart, revocation runs first and process-local requests disappear. A durable EDITING direction resumes its saved choices, including after a credential, mode, epoch or boot change; it never restores a PIN grant or silently returns to older choices. Failed writes/readbacks, unknown builds or changed package facts hold the target and slot. `prepareWithdrawal`/`withdrawPending` can explicitly withdraw an EDITING or COMMITTED edit with new authority, using the existing restrictive cleanup and a separate WITHDRAWN receipt. Withdrawing an edit also saves removed-app signer provenance so the previously active app can be freshly reapproved without reinstalling. Required-UI and unknown-build system-app cleanup limits still apply.

D must version and validate EDITING and PERMISSIONS_CHANGED. PREPARING accepts only an APPROVED action receipt; EDITING accepts only PERMISSIONS_CHANGED; COMMITTED, WITHDRAWING and REMOVING may refer to either action kind. Only REMOVING permits an already-acknowledged null action receipt. Preserve all referenced receipts until the operation clears and preserve removedApps in every writer. EDITING shares the full journal/payload consistency checks and startup coordinator with approval. The JVM tests cover fresh authority, both holders/last action, 14 crash boundaries, partial multi-permission writes, receipt/CAS failures and withdrawal/reapproval. Android effects, containment, durable storage and delivery still require lane D validation.

## Elevation timing, grant and debit proposals

`ElevationMachine` is the pure state-model portion of copy-change task 2.6. **It does not authorize runtime access.** `ElevationState`, `ElevationCountdown`, `ElevationGrant`, targets/actions and transition outcomes are immutable data/proposals, not `FreshAuthority` or controller-issued capabilities. They are not capabilities. The controller lifecycle and approval integration described below validate their own process-local ownership; Update is still unavailable.

`ElevationLimits` accepts only a 10-minute–24-hour countdown and 0–3 daily turns, defaulting to 10 minutes and 2. `request` proposes one countdown after the future controller validates the user's fresh PIN for the chosen kind and exact binding. Another countdown/grant is BUSY. `tick` projects readiness; it never starts access. A longer configured countdown extends the existing countdown from its original start; a later shorter value cannot accelerate it. A lapsed countdown cannot be revived by changing settings. Shortening countdowns or increasing limits still require the separate waiting-change controller; this model does not authorize settings changes.

`start` requires the exact ID and target, the completed countdown and an explicit confirmation strictly before the five-minute Start window closes. It rechecks the current daily limit and returns the debit and grant in **one replacement state**. The deadline is fixed from Start: Play/Update ten minutes, Settings/Approve five minutes. Settings and Play alone propose package access; Approve binds an exact package/signer set and Update binds a SHA-256 digest of verified staged bytes plus version. `closePackageAccess` preserves the grant and deadline, including when an updater closes sessions. Done and expiry discard the grant without refunding its debit. `onRestart` discards both grant and countdown; callers must physically revoke package access before exposing the resulting state or performing other startup work.

`consume` checks the ID, boot, strict deadline, kind and binding. Approve/Update consumption drops the grant; Play records an approved package/signer and can process other distinct apps from that same session until the original deadline. For Play, `ElevationAction.Approve.playGrantId` and its binding are **trusted input facts**, not proof: the controller must obtain them from validated `PlaySessionMachine` candidate history, compare fresh installed facts, and end Play package access on return to Plainphone. Callers must never fill that field from a user request or treat matching text as proof of installation. Settings grants cannot authorize approval or update. A consume proposal still requires the final action and grant mutation to share the mandated atomic/serialized boundary; evaluating the pure function alone consumes nothing durably.

`ElevationLedger` retains at most three unique debits. Availability uses a rolling 24 hours of proven running time, never wall time or calendar days. A different epoch treats each old debit as taken at epoch zero. Reboots preserve debits, while countdowns, Start windows and grant deadlines use boot elapsed time and end on reboot. Clock repair does not alter a live boot deadline. A current-epoch debit ahead of the clock conservatively delays availability until a full day after that reading, with saturated arithmetic. A zero limit has no next available turn. When lowering the limit below the number of active debits, the next-turn time accounts for every debit that must expire.

Missing/damaged debit storage must **not** decode as `ElevationLedger()`; only explicit enrollment initializes an empty ledger. `ElevationLedger.repair(now.proven)` proposes three fresh debits, so even the maximum limit has no turns left. D must validate schema/checksum and durably replace the damaged list before any start; failure to save the repair keeps starts blocked. The model cannot verify storage persistence. IDs for real grants/debits must be globally unique and never reused. Every transition and debit requires a successfully persisted `RunningClock.checkpoint`; unavailable readings return NOT_READY for time-dependent proposals.

Required controller work after this model review:

- Consume and bind a fresh self-managed PIN at countdown creation; cancel on invalid authority/mode/credential changes as required, and never restore authority from serialized countdown/grant data.
- Serialize starts; compare current mode, credentials, ledger and operation slot, then persist debit, grant and the Play/Settings session journal together before any relaxation. Failed writes grant nothing and cannot refund a previously saved debit.
- Gate access on policy, battery, current inventory and successful effect/readback. Every model end/expiry/access-close must drive real revocation; a data transition alone does not close a package.
- Revalidate the live grant, full binding and current installed/staged facts immediately at the approval-write/updater-commit boundary. Persist consumption or the Play approved-package entry with the effect, and record required receipts. No check-then-later-commit shortcut is permitted.
- Revoke first on every restart, discard all temporary authority, preserve durable debits, and test crashes at all combined-record/effect boundaries.

The fourteen JVM tests cover timing boundaries, restart/reboot/epoch behavior, bounds/overflow, lower limits and damaged-ledger proposals, two Play approvals, single-use Approve/Update decisions and immutable proposals. They do not prove crash-atomic controller/storage behavior or Android containment. Generic web/Wi-Fi quotas are still unimplemented and their product flags remain off.

## Durable elevation lifecycle (Play and Settings)

`ElevationController` now owns the self-managed Play/Settings countdown and session lifecycle.
`prepare` creates an opaque process-local request naming a globally unique countdown ID, target,
current person, credential generation, structure generation and boot. Authenticate with its `action`;
`request` consumes that exact person's fresh PIN capability once and saves the countdown with a
credential-revision CAS. The countdown starts at the checkpoint **after** authority consumption.
Start does not require another PIN, but rechecks the saved person/generations, self-managed mode,
clock, current limits, matching build inventory, unlocked valid policy, system dialer, battery and
absence of a committing/rolling-back waiting operation or pending app operation. Ordinary pending
waits do not block the countdown. IDs must never be reused, including after cancellation or restart.

`ElevationStore` is an abstract persistence port for a combined device-protected record containing the ledger, temporary state,
person binding and **the same session journal** used by the other policy controllers. Start writes
the debit, fixed grant and OPENING session journal in **one durable credential-checked CAS** before
calling `SessionEffects.open`. That effect retains its existing unhide/flush/readback contract.
Only after readback, live checks and the OPEN write can `access()` return a `SessionAccess` for
`PolicyInput`. Start is serialized; a second tap cannot debit or open again. Failed effects revoke
and close without refund. A CAS never acknowledges a partial write, and every writer must preserve
the combined record. A false CAS is not acknowledged: storage may hold the old or the new record, so
the caller reads again and reports NOT_READY (see "Store write results" below). An unrelated credential-counter revision
can cause CAS refusal without invalidating the saved countdown; a credential-generation or structural
change invalidates its authority.

D must implement this as a versioned combined schema with validated identities, enum values, timing,
debit membership, grant/journal ID, kind, scope and deadline agreement, and phase/access consistency.
Decode each active session scope through `SessionJournal.restore` against matching live inventory;
invalid scope or invalid combined data returns null. Validate all fields before constructing a record.
Only initial enrollment may use an empty ledger. Null or unreadable combined storage is conservatively
replaced by three fresh debits, after revocation and a proven clock checkpoint; failed repair blocks
all starts. `compareAndSet(null, ...)` must fence the exact damaged/missing observation and must never
overwrite a concurrent valid writer. A schema migration must retain valid debit history; it must not
silently create an empty ledger for a missing new field.

All involved controllers, vaults, sources, store writers, callbacks and effect completions must run
on **one non-reentrant serialized coordinator**. Method synchronization is not cross-controller
serialization. Use this controller for self-managed session starts. Construct `SessionController` with the
**same ElevationStore instance** and its required current-mode callback. That overload refuses
supervised `start` and `access` in self-managed or unknown mode, and owns the journal projection
described below. Do not wire a raw journal-only `JournalStore` over the combined record, or maintain
independent journal copies. The legacy constructor is for installations without elevation storage. Route session end events here while an
elevation owns the session, and route every application/process/Direct-Boot start through
`ElevationController.onStart` before publishing authority. Startup revokes first, then checks/readbacks
and clears temporary state while retaining debits; it does not read credential storage. Do not call
`onStart` to represent an ordinary return from Play: that would intentionally end the whole grant.

Done and deadline end the grant. Screen-off, incoming-call and leaving-package events close package
access, save CLOSING before effects, revoke, check/read back, then clear the journal. A local closing
latch suppresses authority even if saving CLOSING fails; `tick` retries the restrictive direction.
Returning from Play can retain the grant and candidate history up to its original deadline. The
controller never reopens that package access. Call `tick` at deadlines and after failed cleanup or
changed clock/credentials/policy; a null `access()` alone is not physical revocation. Calls and alerts
must stay independent of successful cleanup. A closed grant confers no session access.

The controller owns its `PlaySessionMachine` state, created only from its successfully opened Play
session. `installed(eventSessionId, package)` is an adapter-only actual package-added event, not a UI
command. It reads unique current package/signing facts from the trusted complete snapshot and
validates the event against the live session; no candidate identity or prebuilt history comes from
callers. Duplicate or missing identity facts refuse intake. `candidate(package)` is **review data
only**, retained after package access closes but requiring the same live grant and current signer.
It cannot authorize approval, and becomes unavailable at grant expiry, restart or signer change.
The Android adapter still owes proof of event/snapshot completeness and installed-during-session
provenance; these JVM tests do not establish Android package observation or containment.

**Approval integration:** Play and exact-app Approve consumption now use the optional
`ElevationApprovalStore` and `AppApprovalController` path below. Update requests still return null;
no persisted intent or pure proposal authorizes an installer call.

The fake-effect tests cover fresh authority, atomic Start and credential races, explicit Start and
limits, damaged repair, scope/battery/mode/clock changes, owned candidate intake, fixed deadlines,
reboot and startup without credentials, and crashes on both sides of every Start/close write and
effect boundary. Android storage atomicity, startup intervals, background containment, hidden-package
permission operations and delivery still require lane D validation. Web/Wi-Fi features remain off.


### Shared session journal writes

The combined-store `SessionController` constructor installs `ElevationSessionJournal`, a policy-owned
projection. Its CAS reads the full combined record, compares the expected journal, and writes the
replacement journal **and** `grant.packageAccessOpen = false` together for restrictive writes. It
preserves the exact debit ledger and grant deadline. It refuses to open/reopen or replace an elevation's
session, and refuses a supervised opening while an elevation countdown or closed grant remains.
An active journal without an elevation belongs to a supervised session; the record validator now
allows that combination, but never an active journal alongside a countdown.

Both controllers share the persistence port's process-local revocation latch. Revocation sets it
before effects or writes; failed CLOSING/clear CAS or readback suppresses access through either
controller until cleanup succeeds. No failed write needs to corrupt persisted fields to invalidate
runtime authority. The store must be a single instance on the non-reentrant coordinator, not two
wrapper instances over the same file. No adapter-defined journal update algorithm is needed.

`SessionController.onStart()` can safely run before or after `ElevationController.onStart()` at
application/Direct-Boot startup. The former can restrict an existing elevation without invalidating
its combined record; the latter ends temporary authority and preserves its existing debits. Neither
startup order causes a valid ledger to be repaired or adds debits. If combined storage is genuinely
invalid, the journal projection returns Damaged and refuses to fabricate a ledger; elevation startup
owns the conservative repair. Both startup paths can revoke without reading credential storage.

The supervised approval path uses this same projection for `sessions.onStart()`: its journal clear
preserves debit history. Self-managed approvals are still refused by AppApprovalController. Future
self-managed approval integration must close package access while retaining the live grant and must
atomically consume that grant with the approval write; this fix does not add that authority path.
Shared-store tests exercise both startup orders with credentials unavailable, legacy close, failed
close/clear CAS with cross-controller suppression, supervised mode admission, conflicting opening,
and the existing supervised approval suite over the combined store.


Restrictive elevation intent is monotonic within the running controller: once Done or deadline has
requested terminal cleanup, a later screen-off/call/leave event cannot downgrade it to retaining the
grant. A terminal request can strengthen an earlier package-only close. Failed startup or damaged-record
recovery also leaves a recovery-required latch: subsequent `tick` calls retry revoke/check/readback and
repair instead of stopping permanently at NOT_READY. Until success, preparation and all authority remain
unavailable. Process death still requires startup revocation and never restores a grant.


The supervised journal projection exposes every elevation-owned active journal as CLOSING, even
when the underlying elevation journal is OPEN. It is visible for restrictive cleanup but cannot
be returned as supervised access after a mode/structure change. Compare-and-set compares that same
projected view, then atomically updates the freshly read full record; it never writes the projection
back as an independent record. Ordinary supervised sessions still expose their actual phase.

A close also latches its **effective** retention decision after checking live conditions, before
any CLOSING write or effect. If low battery or another failed live condition requires ending the
grant, that decision stays terminal through retry even when the trigger later recovers or the
CLOSING journal no longer has open-access preconditions. Successful terminal cleanup returns ENDED,
clears candidate data and preserves the debit.


### Periodic tick ownership

With the combined store, an initialized supervised controller's `tick` returns IGNORED for an
elevation-owned active journal while the current mode is explicitly SELF_MANAGED and no local/shared
revocation is pending. It never returns elevation access as supervised authority. Run the elevation
controller's tick as well: it owns the grant's credential, battery, policy, clock and deadline checks.
A shared periodic dispatcher may tick both controllers after both startup paths succeed.

This exception does not apply to genuine supervised journals left over after a mode change, to
TRUSTED/unknown mode, to damaged storage, or to an already requested revocation. Those cases still
close/retry. `JournalStore.ownedByElevation()` is an ownership query on the same serialized coordinator;
legacy stores default false, and the policy-owned combined projection supplies it without changing
the durable schema. Existing app-return, screen-off/call and startup routing is unchanged.


## Live elevation approval consumption

Copy-change task 2.6: construct one `ElevationApprovalStore` whose `elevationStore` is the exact
shared instance used by both session controllers. Pass that approval store to `ElevationController`
and pass that controller to `AppApprovalController`. Existing constructor wiring stays compatible;
without the combined port, elevated approval and Approve countdowns remain unavailable.

`prepareElevated(package, selection)` reads the controller's owned grant and actual Play history.
The UI supplies only the app under review and complete permission choices. Play requires an observed
installed package with the same fresh complete signer set; Approve requires its exact target. A caller's
`PlaySessionState`, candidate ID, grant record or person cannot authorize this path. The fresh PIN was
already consumed for the exact person/action/countdown at request. `approveElevated(request)` accepts
only that approval controller's process-local request once. It closes Play access through the elevation
owner, retaining the original live grant/history/deadline, and never calls either startup method during
ordinary approval. Approve starts no package access and consumes its grant once.

The existing transaction hides/readbacks the candidate, saves PREPARING, applies each chosen
permission and reads it back. The new final port joins COMMITTED app record, receipt/outbox and grant
consumption in one atomic durable write, comparing both record revisions and credential revision.
For Play, this records the approved package and preserves the fixed deadline for other observed apps.
For Approve, it removes the grant/person binding. Both preserve the debit. All sources and writers must
share the same non-reentrant serialized coordinator; per-method synchronization does not replace it.

The port invokes the supplied final check once, synchronously inside its transaction after any waits,
then compares and writes without yielding. That check repeats manifest/installed/signing facts,
permission readbacks, battery, credential generation/structure/mode, owned grant, boot and strict
deadline. Only the exact internally owned PREPARING operation (revision, record, manifest, choices
and receipt identity) is accommodated; ordinary tick and unrelated pending operations still fail closed.
The adapter must not implement two writes or save a consumption intent followed by an approval write.

Failures before COMMITTED publish neither approval nor receipt. Recovery discards PREPARING and
revokes temporary authority; COMMITTED recovery finishes existing idempotent activation without
consuming again or issuing another receipt. Every process/Direct-Boot start must revoke elevations
before publishing other authority. A failed effect or restart never refunds a debit or extends a deadline.

`ElevatedAppApprovalTest` exercises both grant kinds, forged history, unobserved and replaced signers,
replayed requests, stale state, exact PREPARING scope, final-boundary clock/credential/mode changes,
epoch repair, permission/readback failures and crashes before/after the joint write. JVM fakes establish
the pure-policy contract, not Android storage atomicity, package-event completeness or hidden-package
permission support. Those require lane D integration and independent verification.

## Update controller protocol (production remains disabled; PLA-136)

`ElevationController.submitUpdate()` takes no caller grant, ID, session or facts. It uses its own
process-local PIN-bound grant, closes package access with policy readback, reads `UpdateInstaller`
facts, proposes use through `UpdateCommitPolicy`, then calls `UpdateUseStore.commitUpdateUse`.
Inside that transaction the final callback reads the session facts again and checks ownership,
credentials, policy, boot and the strict deadline after blocking work. Only a successful joint write
is followed immediately by one installer commit. No public consume-then-act operation exists.

`UpdateUseStore` is the device adapter's combined store port. On the same coordinator as credentials,
elevation, sessions and mode changes it must reject reentrant controller mutations, while allowing
`finalCheck` to read credential/elevation stores, policy input and the clock, and to call
`PinVault.consumeFor`, re-entrantly on that coordinator thread. It must:

1. Compare the exact elevation, credential and update records/revisions.
2. Finish blocking work, invoke `finalCheck` synchronously exactly once, then compare again.
3. Write the consumed elevation and `SUBMITTED{sessionId, sha256, versionCode, receipt}` record in one
   durable transaction, without yielding. Preserve the ledger and original deadline (also retained
   in the submission for evidence). Reject outstanding submissions and pending self-checks.

`UpdateSubmission.receipt` is required durable evidence: `UpdateReceipt` carries a stable ID (the
originating grant/request ID), actor person and generation, and a copied, immutable map of every
recipient's generation. The actor is the self-managed elevation holder or the supervised PIN holder.
The same joint transaction must freeze delivery destinations for those generations, following the
approval PREPARING receipt pattern. Queueing only in memory after installer commit is insufficient:
the process can die immediately. On startup reconcile and deliver the submission notice to every
captured trusted person, using its stable ID for deduplication and durable delivery acknowledgments.
Never resolve an old receipt to a replacement person's current address. Preserve outstanding receipts
in a durable outbox when a later submission replaces the record; result/self-check CAS writes retain
the original receipt. A receipt describes submission/use-up, not confirmed installation. Delivery
does not authorize installation or gate policy progress.

A false check refuses before any write. A false after a passing check is not acknowledged: the pair
may have landed, so the controller reports NOT_READY, keeps no retry path, and startup reads what
landed. A crash exposes the original pair or the complete pair.
The supervised path uses the same transaction, preserving the elevation record unchanged and using
the exact request's fresh person-bound PIN authority inside `finalCheck`, after the final hash read.
Requests are process-local and cannot be restored. Supervised preparation refuses any OPENING, OPEN
or CLOSING session without effects or journal writes; finish it through `SessionController` before
preparing Update. Missing/damaged stores fail closed.

`UpdateInstaller.finalFacts()` reads the SHA-256 through `Session.openRead` **after the last write**.
Package, exact signer set and version must come from the verified immutable private staged file whose
hash equals that session hash. The controller checks exact self identity, valid signature, strictly
newer installed version and exact grant/request digest plus version. The session ID and all facts
must agree across the two reads. A same-version reinstall is refused by policy.

Startup uses the internal `UpdateProtocol.onStart` wrapper: abandon every own installer session,
then call `ElevationController.onStart` to revoke all temporary authority, then reconcile the saved
submission. For SUBMITTED **or FAILED**, installed version equal to the staged version means
INSTALLED; a readable higher or lower version means FAILED. FAILED remains recoverable because an
already-committed platform install may finish after our restart. Unreadable version (`null`) means
NOT_READY with no update-record write; retry on the next startup. INSTALLED is written before the
idempotent post-update self-check; its separate PENDING/PASSED/FAILED result survives/retries a crash.
A result callback must match the saved session; success for SUBMITTED or FAILED requires equal
version readback and runs the journaled self-check. Completed INSTALLED results cannot run it again.
Route **only terminal** installer statuses to `onResult`; never translate `STATUS_PENDING_USER_ACTION`
into failure. Never commit on startup, replay a saved submission, refund a turn or extend a deadline.

An internal `ElevationController.onStart()` triggered by tick/close recovery invalidates Update.
The adapter must rerun the full `UpdateProtocol.onStart` wrapper before Update becomes available.

`ElevationController.updateNeedsAttention()` exposes whether the last saved submission is INSTALLED
with a FAILED self-check, including after restart. It only reads the journal. When true, the adapter
must display **Plainphone needs attention** and durably queue the failure notice to every trusted
person, with stable deduplication and delivery retry. Do not present installation success as a passed
health check or promise rollback. Missing state/NOT_READY remains an unavailable state, not evidence
of health. This slice adds neither a UI action nor production wiring.

A thrown installer call returns COMMIT_UNCERTAIN: authority stays spent and SUBMITTED stays durable,
since the platform may already have accepted the install. Startup/readback resolves it. An ambiguous
store exception disables this process's Update path until startup recovery. Package closure failures
hold the shared revocation gate. Neither failure creates retry authority.

**Release gate:** no public constructor or factory installs `UpdateProtocol`. `prepare(Update)` and
both submission paths still refuse in production, even if the approval adapter is present. JVM tests
wire the internal seam to exercise the full controller. Lane D can implement the exported ports now;
Lead must arrange Claude review of this GPT-authored slice and the cross-model adapter review before
any later change exposes production wiring or an Update UI action. This work does not self-approve.

**Known gaps from the proved installer protocol (PLA-86 / PLA-126):** the deadline binds authority
use-up, not platform install start. G2: a paused process may call commit after the deadline, only for
the exact hash-bound, pinned-signer, newer self package. G1: a crash between use-up and commit spends
the turn and installs nothing; startup abandons the open session. G3: no session write after the final
hash read is a code rule, not a platform guarantee. The store transaction is not atomic with Android.
Evidence: `mvp/device:spikes/installer-boundary/README.md` and its API 35 emulator transcripts
`evidence/pm-session-rules-api35.txt`, `evidence/app-self-update-api35.txt` (2026-10-09).

Lane D still owns local host/port checks, no redirects, 100 MB / 2 minute bounds, private staging,
Android APK signature verification, pinned signer and exact self package, `setAppPackageName`, the
post-update handover self-check and no rollback promise. Pure policy performs none of those Android
or download effects. Device tests must prove storage atomicity, exclusive session ownership, the
last-write/hash rule, startup abandonment and both crash sides against the real adapter.

`UpdateControllerTest` covers final fact substitutions, duplicates, stale/restored authority,
credential/structure/mode/boot/deadline changes, pauses before and after use, both crash sides of the
joint write and commit, ambiguous exceptions, statuses/readbacks, closure and self-check recovery.
`UpdateCommitPolicyTest` retains the pure proposal checks. JVM fakes prove the controller contract,
not Android atomicity or the adapter's hash/signature implementation.

## Trusted-person enrollment and patch model (first people-change slice)

`IncomingPersonEnrollment.confirm` captures the incoming holder's contact, PIN/recovery verifiers,
reset-protection ID, all nine enrollment confirmations and the account-check witness. Use
`PinVault.prepareCredential` for the incoming person's own PIN and show its generated recovery code
once before confirmation. The pending model retains no plaintext PIN/code and never writes an active
credential slot. The adapter must allocate a new, durably reserved, never-reused `TrustedPersonId`.
Names, numbers and account IDs are sensitive persisted data; model diagnostics redact them.
Confirmations are local-flow attestations, not proof of SMS delivery or Google account correctness.

`TrustedPersonPatch.add`, `handOver` and `remove` calculate immutable inputs for the future waiting
operation. All require supervised mode and an active recovery profile. Add enforces the two-holder
limit; removal cannot delete the last holder; handover always names the outgoing holder as starter.
A helper or a current holder records account checks. No factory authenticates a caller or starts a wait.

The patch records the structure generation and exactly the starter/removed-slot credential generations.
`dependenciesMatch` is the pending-state guard; counter changes and an unrelated holder's recovery do
not invalidate it. The internal `replacementForCommit` calculates a candidate using the latest active
record. It alters only the named slots and associated reset-protection IDs, keeps unrelated credentials,
counters, contacts, pending notices, backup and friction, and increments revision/structure once.
Credential recovery now preserves the selected holder's contact alongside replacing their secrets.

The model alone does not enable a people-change action. The waiting integration below adds the
single slot, fresh authority, 24-hour proven time, cancellation and skip transaction. The remaining
committing controller must persist direction, set/read back the candidate's full
reset-protection list including backup, then atomically apply the patch; rollback must persist intent
before restoring the current active list. It must recognize an already-applied patch on restart rather
than retrying calculation to reinstall the enrollment PIN. Fixed-recipient receipts and platform effects
still need the narrow controller/adapter contract and crash tests. Until that exists, no caller may treat
this model or a calculated candidate as permission to update the active credential store.

`TrustedPersonPatchTest` covers this model boundary with synthetic contacts/accounts and a fake hash
port. It does not establish Android persistence, readback, account validity or completion behavior.

## People waits and recovery handover (second people-change slice)

`WaitingChangeController.prepareAdd`, `prepareHandOver` and `prepareRemove` produce a process-local
preparation owned by that controller. `startPeopleChange` requires confirmation and a fresh PIN grant
bound to that exact preparation and starter. Handover's starter is always the outgoing holder. Start
rechecks the patch's full dependency set, initializes the never-reused cancel intent, then writes PENDING
and its fixed-recipient STARTED receipt together. Incoming credentials remain outside the active store.

All kinds share the existing waiting journal and slot. Pending waits mature only at 24 hours of proven
running time, including epoch repair and reboot rules. Pending dependency changes cancel on the next
serialized tick; the adapter must dispatch that tick immediately after any credential change. Ordinary
PIN recovery does not skip a wait. PIN-free cancellation, its durable intent, missing-record holds and
stale-request checks are shared with turn-off. People changes do not unhide anything, so the battery
guard specific to discretionary unhide does not delay their commit decision. Turn-off's guard remains.

`skip` uses the existing joint recovery transaction for all supported pending kinds. It checks every
old dependency before recovery and atomically rotates only the skipper, queues SKIPPED and enters
COMMITTING. Skipper authority replaces starter authority; removed/replaced slots remain explicit patch
dependencies. Once committing, ordinary credential changes do not cancel the operation.

`startHandOverWithRecovery` is the outgoing-code alternative. The local screen must name the exact
handover and separately confirm starting and skipping its wait. It checks only the outgoing holder's
code, uses the vault's independent recovery counters, and requires a new PIN. The new optional
`WaitingHandOverCommitter` port must atomically join that credential rotation, STARTED and SKIPPED
receipts with distinct keys, and the COMMITTING record. It compares the same active credential store,
waiting generation/revision, vacant/terminal slot, and reserved request's CLEAR cancel intent. It must
publish either all old values or all new values after a crash. With no explicitly wired support this
alternative is unavailable; the older turn-off skip port does not imply support. No new code is returned
until success, and incoming credentials still grant nothing. This port needs lane D implementation and
independent durability tests under its own gate before product wiring.

`WaitingText.recipientContacts` freezes each recipient's contact alongside the generation when queued.
The operation retains its original snapshot for restrictive cancellation when credentials cannot be read,
even after the original STARTED receipt is acknowledged. A null legacy contact explicitly means the
address is unavailable: report delivery unavailable/gave up, never resolve it from a later slot. A removed
holder's already-queued receipt retains its original address; later events address only current holders.
`LockoutNotice` is audit/source-event data, not a receipt with fixed delivery destinations. Preserve
removed-slot notices for audit without treating that removed slot as a current recipient. The adapter
must broadcast a notice to the holders current when it queues the receipt and freeze that snapshot;
it must neither retarget nor discard an already-queued receipt. Delivery failure never gates policy.

`TurnOffController` rejects people operations before any effects, including when Device Owner is
absent. The normal policy plan and emergency floor remain intact in every people phase. The executor
below supplies forward/rollback policy. Do not expose people-change product actions until all adapters
and the shared coordinator are implemented and independently verified.

`PeopleWaitingTest` covers the start/wait/cancel/skip boundary, recovery-code handover, counter
separation, exact deadlines and repaired epochs, shared-slot contention, crash boundaries for ordinary
and joint writes, fixed/legacy/removed contacts, kind isolation and unchanged locked/unlocked policy.
These JVM fakes establish policy behavior, not Android durable storage, account validity or FRP.

## People forward commit and fresh-PIN rollback (executor and adapter contract)

`PeopleChangeController.tick(requestId, direction)` accepts only a durable people operation whose ID
and COMMITTING/ROLLING_BACK direction match the scheduled work. It cannot start, skip or mature a
wait. Keep the normal lockdown and emergency floor throughout; call the common startup revocation
before dispatching any controller, and run `WaitingReconciler` when the journal is lost.

Forward tick calculates the patch against the latest credential record and checkpoints the clock. It
then sets the exact future reset-protection list, including backup, broadcasts the change and reads the
list back. Each synchronous effect is surrounded by current-journal and credential-revision checks.
Any failure leaves COMMITTING and old holders active; report "Reset protection not updated – call
the helper" for reset-effect/readback failures. Do not claim which account a wipe would accept.

Only successful readback permits `PeopleChangeCommitter.applyPatch`. The adapter atomically compares
the credential revision and journal generation/revision/direction, then saves the complete patched
credential record with raised structure generation and `AppliedPeopleChange(requestId,
waitingGeneration, structureGeneration)`. No partial holder set or separately written marker is valid.
The executor reads the credential record back before reporting APPLIED; this result is not COMPLETED.
On the next tick, a matching marker permits only completion, never another patch or reset-list write.
Every credential-only writer must preserve the marker, including PIN/code recovery, counters and
notices. Thus recovery of the incoming PIN after a crash cannot reinstall its original setup PIN.

Completion uses `PeopleChangeCommitter.move` to compare both current records and atomically queue
COMPLETED plus fixed recipient generations/contacts from one current credential snapshot. If this
write fails, retry completion with the existing marker. An already-queued receipt is never rebuilt or
retargeted. The marker may remain after completion; the next successful people patch replaces it.

`prepareStop` is available only to a current holder while COMMITTING and before patch application.
`stop` consumes a fresh, exact-action PIN capability from the preparation's process owner. Its joint
`move` persists ROLLING_BACK, stopper identity/generation and a fixed-contact receipt before any
restoring effect. A failed write does not authorize restoration. Once this intent is durable, schedule
only ROLLING_BACK ticks, including after restart. Each retries set/broadcast/readback of the *current
credential store's* reset-protection list, then atomically saves CANCELLED with its receipt. Readback
failure stays ROLLING_BACK. A failed clock cannot prevent restrictive rollback. After patch application,
neither a new nor an already-prepared stop can undo the new holders.

Lane D must implement and verify these concrete boundaries before product wiring:

- `PeopleChangeCommitter` and `WaitingHandOverCommitter` use the same credential/journal storage
  and non-reentrant serialized coordinator as every vault, waiting, cancellation and reconciliation
  writer. Compare both records inside one atomic transaction; do not separately acknowledge a compare,
  direction, holder swap, marker or receipt. No asynchronous effect may outlive the serialized action.
- Persist schema/checksums and current storage generation. Fence every acknowledged journal state
  and joint transaction against an older surviving replica. Highest-valid-checksum alone is insufficient:
  if rollback r8 was acknowledged and only committing r7 survives, return lost/unreadable and reconcile,
  never r7. Credential replicas need the corresponding protection against resurrecting outgoing holders.
- Serialize the complete immutable patch/enrollment, fixed contact maps (including explicit nulls),
  skipper/stopper metadata and applied marker. Incoming slot IDs and cancel/request IDs are durably
  reserved and never reused. Enrollment attestations originate in the trusted local flow.
- Implement exact reset-list set, required broadcast and readback without guessing account correctness.
  Run fault injection before/after every write/readback, both sides of each transaction, startup, partial
  replica loss and joint-store recovery. An old forward callback must not run after rollback or reconciliation.
- Preserve already-queued receipts and separate removed-slot audit notices from current recipients.
  Migrate legacy missing contacts as explicit unavailable delivery data, never a new lookup target.

When both operation copies are lost before or after the patch, the existing reconciler treats the
credential store as truth. It restores the corresponding active list plus backup and reports who is now
in charge; it never claims the lost change was cancelled. The applied marker does not revive a lost
operation or confer authority to a new one in a different journal generation.

`PeopleChangeControllerTest` exercises these contracts with fake effects/stores, directed callbacks,
transaction races and crashes at forward/rollback boundaries. It proves pure-policy behavior only.
Android persistence, broadcast/readback and device fault evidence belong to the assigned lane D
integration under its own gate; Update and its separate installer boundary remain unavailable.

### Mode switches: enrollment and waiting contract (first slice)

`WaitingChangeController.prepareSelfManaged` and `prepareSupervised` produce
process-owned `WaitingStart` preparations. Authenticate the **current** starter
against that preparation's exact action, then call `startModeSwitch` with the
local confirmation. Never deserialize a preparation or accept one from an
intent. `IncomingSelfManagedEnrollment` records the user's PIN/recovery and
account checks without requiring a trusted-person name/number or test text.
The opposite direction reuses the full `IncomingPersonEnrollment` contract.
The adapter must reserve new, never-reused holder IDs in either direction.

`ModeSwitchPatch` is immutable pending data. Persist the enrollment attestations,
source/target mode, all outgoing slot generations and structure generation in
the same operation record as the wait and its fixed-contact receipts. The source
mode stays authoritative throughout the pending wait. Every outgoing slot is
removed, so replacing either outgoing trusted person's credential cancels the
pending switch. An exact supervised recovery skip retains those removed-slot
dependencies and updates the skipper's generation in the existing joint
credential/operation transaction. Self-managed switches cannot skip.

Both directions use the single waiting slot, the existing durable cancel intent,
and 24 hours of proven running time. Pending preparation/start/cancel never write
the elevation store: the ledger and active countdown retain their existing age,
limits and deadline. Process restart still revokes temporary authority through
the existing elevation startup path without refunding debits. Wall-clock values
are absent from these APIs. Queued receipts keep their original recipient and
contact snapshots, including an explicit unavailable contact for a self-managed
holder without an SMS contact; do not send a pending incoming person a receipt
by looking up their enrollment as if it were active.

**Product mode-switch actions remain unavailable until lane D integration is verified.**
The executor is the existing `PeopleChangeController`, extended only when its
committer implements `ModeSwitchCommitter` and its effects implement
`ModeSwitchEffects`. Older people-only adapters continue to ignore mode patches.
`TurnOffController` always ignores them; `PolicyEngine` retains normal lockdown.

### Mode-switch completion and rollback adapter contract

Use one non-reentrant serialized coordinator and the **same** `ElevationStore`
instance for supervised sessions, elevations and `ModeSwitchCommitter`. Construct
it as `ElevationStore(theSameWaitingChangeStore)`; the mode executor rejects an
unbound store or one bound to another waiting source. The shared journal includes
both supervised package access and elevation authority.

The access gate combines an ordinary cleanup latch with a fresh read of the
**durable waiting operation**. A COMMITTING or ROLLING_BACK mode switch holds the
gate even when session/elevation startup or tick clears its own cleanup latch.
This also restores the gate before the first executor pass after process start.
An unreadable/missing waiting record holds it until reconciliation. Pending waits
keep ordinary session/elevation rules. Only a durable terminal state (or successful
lost-record reconciliation) clears the operation's contribution to the gate.

Initial provisioning must durably create an empty combined elevation record in
**both modes**, including supervised setup. Never initialize over an existing or
damaged ledger during startup, migration or switching. Missing/corrupt ledger
data in normal operation holds the switch until conservative elevation startup
repair succeeds. Lane D must test initial supervised setup followed by a switch
and immediate elevation, initial self-managed setup, and restart persistence.

A forward pass checks request, storage generation, direction, mode and structure:

1. Latch revocation for both live controllers. Durably clear the shared journal,
   countdown, grant and person binding in one elevation CAS, preserving every
   debit exactly. Read back the cleared record. Pending waits never do this.
2. Revoke physical temporary access and local session/Play/approval/update
   authority, then read back the protected policy. These effects must not mutate
   credential, waiting or elevation records or reenter another controller.
3. Set the target reset-protection list (incoming account plus existing backup),
   broadcast and read back the exact list. Recheck direction/credentials around
   every effect.
4. `applyModePatch` atomically compares credential, waiting and cleared elevation
   revisions, then writes mode, holders, recovery profile, structure generation
   and `AppliedPeopleChange` together. It leaves the exact elevation ledger
   untouched and the waiting record COMMITTING. Never use people-only applyPatch.
5. Read back the marker. A later pass rechecks/revokes temporary authority, then
   `finishMode` writes COMPLETED plus fixed-contact receipts, comparing all three
   records again. Terminal success releases the mode gate and executor cleanup
   latch; ordinary controller cleanup cannot release the mode gate.

The marker's historical name covers both people and mode patches. Preserve it
through every PIN/recovery/counter write. Replay recognizes request ID, waiting
storage generation, target mode/holder and the single raised structure generation;
it never restores the incoming setup PIN. Old shared session/grant authority is
cleared and physically read back before the swap; old fresh PIN tokens fail
against the new credential structure. The adapter must maintain this ordering
across processes and prohibit concurrent/reentrant privileged work.

Before the swap, a current holder's fresh PIN may request Stop, including the
self-managed user. Stop binds process, request, waiting generation, holder and
structure. It first writes ROLLING_BACK plus receipt. Rollback clears temporary
authority, restores/broadcasts/reads back the current credential store's account
list, then uses `finishMode` for CANCELLED. No PIN-free Cancel is offered once
committing; Stop is unavailable after the applied marker. Failures stay in their
durable direction. PIN replacement after commit does not stop the patch.

Lost waiting records use `WaitingReconciler` and current credentials as truth.
Revoke temporary authority without refunding debits; reapply/read back active
policy, reset protection and friction; fence pre-loss replicas before releasing
the slot. Report recovered mode/holders, never infer cancellation or replay
pending enrollment. Acknowledgements must survive loss of the newest journal
copy, not merely checksum validation.

Tests cover both directions, forward/rollback crashes, post-swap recovery,
terminal races, lost-record reconciliation, live controller authority, and
immediate new self-managed elevation with an exhausted preserved ledger. Fakes
prove ordering and policy behavior only. Lane D must independently prove Android
atomicity, persistent fencing, physical closure and readback before enablement.

## Self-managed friction settings (PLA-98)

`FrictionSettingsPatch` holds immutable source/target values, the current user's
credential generation and the structure generation. Only self-managed mode with
a valid recovery profile is eligible. The approved ranges remain 10 minutes–24
hours and 0–3 daily starts. Invalid values fail validation. Unchanged values yield
no preparation, PIN consumption or write. Both fields can change together in the
same direction. The approved spec describes individual setting edits, not a mixed
atomic form: the API rejects mixed-direction proposals without applying anything.
Offer separate edits, each with fresh authorization. Never silently split one
confirmation into an immediate looser component.

### Immediate stricter changes

`FrictionSettingsController.prepareStricter` returns a process-local, exact-action
request; `applyStricter` consumes the current user's fresh PIN once. IDs must be
unique across restarts and receipts. `FrictionSettingsStore.applyStricter` compares
credential revision, waiting storage generation/revision and elevation revision,
then atomically writes active friction, any extended live countdown, and a routine
receipt/audit with fixed recipients. A missed tick must not lose the countdown at
its old lapse time. Already-lapsed countdowns are never resurrected. Preserve the
exact ledger, including expired/foreign-epoch debits, grant, grant deadline,
session journal, credentials, profile/backup IDs, notices and applied markers.

A pending **friction** operation changes this transaction: the optional
`FrictionWaitingStore.tightenAndCancel` must also write that exact operation as
CANCELLED and its DEPENDENCY_CHANGED receipt/audit in the same write. The stricter
values can never coexist with an acknowledged still-pending older looser target.
Without this joint port, the edit returns NOT_READY. Other pending waiting kinds
are unchanged. COMMITTING/ROLLING_BACK operations hold stricter edits until their
terminal state; no supported settings writer changes the source underneath a
committing friction operation.

### Looser waiting changes

`WaitingChangeController.prepareFriction` accepts shorter countdowns and/or higher
limits. `startFrictionChange` requires confirmation and the exact fresh current-user
PIN and creates the shared PENDING operation plus start receipt. The immutable
patch, starting generation/structure and active source settings are dependencies.
For a combined looser target, the kind is SHORTEN_COUNTDOWN; the entire target
still waits together, and receipt/screen copy must describe **both** changed
values. A daily-limit-only target uses RAISE_DAILY_LIMIT. No pending target is an
active setting or an elevation authorization.

The existing waiting controller owns the one slot, exact 24-hour proven-running
age, clock epochs, PIN-free Cancel, durable cancel intents, delivery acknowledgment
and fixed-recipient state receipts. Unknown cancel intent or unreadable clocks
cannot mature a wait. After each PIN/recovery/structure mutation, the shared
coordinator must run dependency checking before displaying or dispatching another
operation, as for the other waiting kinds. A pending changed dependency cancels;
self recovery never skips. Stricter setting edits invalidate a pending friction
operation atomically, as above. Cancel remains available on normal and attention
screens. From COMMITTING, PIN-free Cancel reports Already in progress.

`FrictionWaitingController.finish(requestId)` accepts only this journal's exact
COMMITTING friction operation. It ignores other kinds/directions/IDs. After
COMMITTING, a PIN/recovery change does not stop the operation: the current
credentials and counters survive, while mode/structure/source friction are still
validated. There are no Android effects or independent cache-activation steps.

`FrictionWaitingStore.completeFriction` must compare all three record revisions,
waiting storage generation/request/direction and source friction in **one durable
transaction**, then atomically write target friction in the credential record and
COMPLETED plus the fixed-recipient completion receipt/audit in the waiting record.
The entire elevation record is unchanged, including its revision and exact debit
list. A running countdown retains its already-established duration; a shorter
setting applies to future countdowns. Grant deadlines never move. A raised limit
uses the preserved debits and may allow additional starts only after completion.

False is not acknowledged: the completion may have landed, so the executor returns
NOT_READY and the retry reads the journal. A crash reveals old settings + COMMITTING or target
settings + COMPLETED + receipt, never half activation. The terminal operation is
the replay marker; no separate credential-only applied marker is needed. Retry
recognizes COMPLETED without reapplying the target, even after a subsequent
stricter edit or PIN reset. A readback failure returns NOT_READY; retry reads the
durable journal. Acknowledging receipts cannot remove the audit or replay marker.
Never implement either transaction as sequential independently acknowledged store
writes. Fence stale replicas across **all** stores, even if the newest operation
copy disappears; checksum validity alone is insufficient.

### Persistence, startup and enablement

Use the exact shared ElevationStore instance constructed with the exact
WaitingChangeStore. Store substitutions are rejected. PinVault, settings,
waiting, elevation/session, approval and updater mutations/readbacks run on the
same non-reentrant coordinator; per-controller synchronization is not sufficient.
Every `ElevationLimits` consumer, especially request/tick/Start, must read active
`CredentialState.recoveryProfile.friction` there, never pending targets, cached UI
values or default settings after a failed read. Hold the action if active state is
unavailable. Setting writes do not initialize or repair the ledger.

Complete normal startup revocation and conservative ledger repair before enabling
settings. Do not restore process-local countdown/grant ownership after restart.
`FrictionSettingsPatch.restore` is a validated data decoder, never authorization:
decode the original starter/generation/structure/source/target, validate the
surrounding WaitingOperation, and reject invalid checksums/revisions/generations.
Do not reconstruct dependencies from current credentials or proposed active data.
Read the highest valid **fenced** journal revision, retaining immutable patch data
through every phase change and receipt. Null storage is damaged/unavailable,
never vacant.

Lost-operation recovery remains `WaitingReconciler`: active credential-store
friction is truth before and after the atomic completion. Never adopt a pending
patch or infer that an unknown operation cancelled/completed. Revoke temporary
access without refunding debits, apply/read back the active complete policy,
backup-inclusive reset protection and active friction, then atomically replace the
lost journal under a new storage generation with a recovered-state receipt.

### Store write results (PLA-160)

Every Boolean store write (`compareAndSet`, `commit*`, `move`, `applyPatch`, the friction
transactions, `initialize` and `migrate`) has one meaning. **True** is acknowledged: the new
record reads back. **False** is not acknowledged: the store may hold the previous record or the
new one. A writer that cannot read its own directory after the commit point cannot tell which,
and no protocol can. So a caller never treats false as "unchanged", "not saved" or a definite
refusal, never retries with the expected value it sent, and takes no permissive action. It reports
Unavailable, NOT_READY or NEEDS_ATTENTION (fail closed) and reads fresh on the next action. A store
still tries to resolve inside the write, never acknowledges a rollback, and never serves a lone copy
above the fence. `migrate()` is the startup readiness gate: nothing runs until it returns true.

Lane D follow-up PLA-100 owns settings transaction persistence/coordinator,
receipt delivery and product integration, including real storage failure,
stale-copy, restart and reboot tests. PLA-97 separately owns mode integration and
both-mode initial ledger provisioning. Settings stay unavailable until reviewed
policy **and** adapter integration; mode switching, Update and web/Wi-Fi retain
their existing gates. JVM fakes verify pure logic and ordering, not Android
atomicity or physical containment.

## Generic TimeWindowQuota (PLA-106)

This slice is an **immutable proposal model**, not a stateful controller or device
adapter. It neither grants physical access nor creates `SessionAccess` and is not
an input to `PolicyEngine`. `TimeWindowTransition.STARTED`/`EXTENDED` describe
candidate records, never durable or physical success. Existing elevation and
session rules are unchanged; in particular elevation deadlines cannot extend.

### Generic accounting contract

Configuration is explicit and fixed for each ledger: positive capacity, rolling
period in proven milliseconds, positive window length in boot milliseconds, and
whether extensions are allowed. There are no calendar-day resets or wall-clock
inputs. Each start costs one unit. An extension costs its positive integer number
of units, each adding one configured window length to the **old fixed deadline**.
All extension units are charged at the extension checkpoint. It cannot be partly
accepted when allowance is insufficient. Ordinary ticks never move the deadline.
This is a generic unit-based choice, not an approved web/Wi-Fi product decision.
Period and duration are independent; a configuration may allow charges to age
while a long window remains open. Product approval must explicitly consider that
choice and any maximum total session duration before adopting a configuration.

One window occupies the ledger at a time. A new start while any window is recorded
is BUSY, even past its deadline; cleanup and actual revocation must precede another
start. A window expires at `now >= deadline`, on boot change or elapsed time below
its start. Extension cannot revive an expired window. Early close, effect failure,
expiry and restart keep all debits. Only a later charged proposal prunes debits
that have fully aged; merely closing never refunds them.

The model reuses `RunningTime.since` and `ClockReading` from the shared clock. A
debit ages out at exactly its period of proven running time. Foreign epochs count
from current epoch zero; same-epoch future readings keep their full delay, with
remaining-time overflow saturated. Deadline/multiplication overflow is refused.
Clock repair cannot extend a boot deadline. An unavailable checkpoint yields
NOT_READY, never new access; adapters must stop publishing authority and revoke
existing access while retrying cleanup if time cannot be established. Restrictive
close/restart needs no clock. Revision exhaustion yields NOT_READY and requires
conservative generation recovery, never wraparound.

`provision` is explicitly first setup only. The caller must prove the ledger has
never been provisioned, with a durable setup marker. Null/missing/corrupt history
in ordinary startup is **not** first setup: `recover` proposes a full-capacity debit
from the current persisted clock under a new durably reserved generation. Failed
clock or repair persistence leaves requests unavailable. `restore` validates typed
records and copies the history; malformed decoding must use conservative recovery.
It does not check physical storage integrity or grant startup authority.

Every mutating command carries an expected generation/revision; close and extension
also carry the original window ID. Accepted changes raise the revision. Startup
raises it even with no recorded window, invalidating commands from the old process.
Window IDs are start revisions within never-reused storage generations. Old starts
remain stale after their debits expire. The version check alone cannot arbitrate
two copies of a snapshot: both can produce proposals, and **only one durable CAS
may win**. Retry the original command with its original expected version, never
silently substitute the newest revision for a duplicate UI callback.

### Mandatory adapter obligations before any consumer is enabled

Lane D must implement a reviewed coordinator/store/effect boundary before product
wiring. There are intentionally no pretend durable stores or effect executors in
this immutable slice. The integration must provide all of the following:

1. Use the existing proven clock and non-reentrant shared privileged coordinator.
   Checkpoint durably before transitions/debits. Serialize all controllers and
   processes, immutable config, record/schema/checksum and storage generation.
   Authenticate and bind any eventual consumer separately. UI flags and decoded
   `TimeWindow` records are never authority.
2. Atomically compare the complete expected generation/revision/config and save
   debit(s), fixed deadline and exact access journal together **before** relaxing
   access. Read back the exact acknowledged record, recheck its deadline and
   current ownership, then apply/read back the effect. Failed or uncertain write,
   readback or effect never publishes success. An acknowledged debit remains even
   if access never opens. Keep cleanup intent until physical closure reads back.
3. Fence all synchronous effects and callbacks against the current owner, process,
   generation, revision and window ID. A stale callback must neither reopen nor
   close a newer window. Commit an extension's extra debits/deadline atomically;
   if commit/readback fails, no extended authority may be published. Never retry
   an old proposal after a newer close/start. A mutex on this model is insufficient.
4. On every startup, hold the authority gate, load recorded temporary access,
   revoke it and read back protected policy before acknowledging a cleared window
   and publishing any authority. If storage is lost, reconcile all possible
   temporary access from active policy. Startup must not merely drop the journal
   and forget its physical effects. Keep debits through cleanup; failure holds the
   gate and retries. No deserialized window resumes a session.
5. Persist rollback-resistant acknowledgement fences: losing the newest copy must
   not resurrect a pre-debit, pre-extension, pre-close or pre-recovery record.
   Uncertain history uses full recovery, never an empty ledger. Provision markers
   and generation reservation need the same durability. Config changes cannot
   reset history; they require a separately reviewed migration preserving charges.
6. Before enabling any approved consumer, inject crashes before/after clock
   checkpoints, atomic debit/journal writes, readbacks, open/extend/revoke effects,
   cleanup writes, recovery and generation reservation. Cover stale surviving
   replicas, reentrant/concurrent starts, callbacks after close/restart, missing
   journal, failed writes/readbacks and exhausted quota. Verify actual access and
   ledger recovery, not just returned objects.

The JVM tests prove deterministic accounting, fake-clock reboot/epoch boundaries,
immutable snapshots, command fences and a single-winner CAS example. They **do not
prove device durability or the effect/crash matrix above**. A separately assigned
lane D follow-up PLA-107 owns the adapter contract and crash acceptance checklist;
its implementation must wait for a named approved consumer, a Lead assignment and
the existing integration gates.

### Disabled consumers

`DisabledTimeWindows` hard-codes both product flags OFF and returns DISABLED
without proposing a debit or window. It exposes only the disabled illustrative web
configuration (five units, five minutes each, extendable, rolling 24 proven hours)
and the Wi-Fi quota metadata (three per 24 proven hours). Wi-Fi duration is
unspecified, so no Wi-Fi window configuration is constructed. The web interpretation
of "day", extension charging, maximum session length, Wi-Fi duration and approval
flow remain product decisions for the unapproved change. There are no Android
browser/portal effects, product entry points or conversions to policy authority.
Tests retain browser/portal hiding and exclusion from unhide/lock-task plans on a
supported inventory, normal/baseline health and locked boot. Existing unknown-build
and emergency-floor exceptions continue unchanged.

## Stored elevation data

`ElevationCountdown.restore(id, target, startedAt, durationMillis)` and
`ElevationGrant.restore(id, target, startedAt, deadline, packageAccessOpen, approved)`
are public nullable decoders for validated scalar/value fields. Invalid combinations,
countdown overflow, and grants whose deadline differs from the target's fixed duration
return null. Approval sets are copied and immutable. Decode schema/checksum and nested
value types first; a null decoder result means damaged data, never an empty ledger.
These objects restore data only, never elevation authority or process-local ownership.
Continue to run controller startup revocation and binding checks; preserve the debit
ledger even when startup discards a restored countdown or grant.

## Stored waiting inputs

`IncomingPersonEnrollment.restore` and `IncomingSelfManagedEnrollment.restore`
accept `(person, credential, resetProtectionUserId, confirmations, witness)`.
They retain the saved credential verifiers and audit fields exactly, rejecting
incomplete confirmations, malformed account IDs, nonzero generations or used counters.
The trusted-person variant requires its saved contact; the self-managed variant requires
no contact. No `PreparedCredential`, plaintext PIN/recovery code or current state is needed.

`TrustedPersonPatch.restore(kind, starter, structureGeneration, dependencyGenerations,
removedSlots, incoming)` validates the add, handover or removal shape.
`ModeSwitchPatch.restore(starter, sourceMode, structureGeneration, dependencyGenerations,
incomingSelfManaged, incomingTrustedPerson)` requires the enrollment matching the switch
direction and one or two outgoing holders as appropriate. Both copy collections and retain
the original dependency generations. Neither re-derives a patch from current credentials.
A handover's other-holder witness need not be in its dependency map. Live eligibility,
PIN authority, the wait and commit checks still belong to the controllers; restoration
neither enrolls an active credential nor authorizes an operation.

## Self-managed app removal

The current holder may remove an active approval or withdraw a stuck committed
approval with a fresh action-bound PIN in either mode. The request captures its
mode, holder, credential/structure generations and boot; switching modes never
reuses that authority. Both operations follow the existing restrictive journal
and readback sequence, require no elevation or turn, and never uninstall the app
or refund debits. A self-managed hidden app can be reapproved only through the
existing live Play/Approve grant rules. Direct PIN-only reapproval and direct
self-managed permission editing remain unavailable. The audit asks the Lead to
resolve the latter against the approved action table before any new authority
path is implemented. Android persistence, effect readbacks and delivery remain
lane D obligations.
