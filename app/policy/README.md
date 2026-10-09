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

`AppApprovalStore` is one atomic state/outbox/audit boundary. Use the same serialized coordinator as credentials, sessions, waiting changes, package callbacks and every policy writer; no callbacks may re-enter a transaction. D supplies unique operation IDs that are never reused. Failed writes never count as persistence. Constructors validate record/permission completeness and committed journal/payload/receipt consistency; adapters also validate the schema, checksum and revision, returning null on unavailable or invalid storage instead of creating a blank approval store. Enrollment initializes the empty store explicitly. Outbox acknowledgements may remove delivered receipts only after their pending activation journal is cleared, and must preserve every other field under CAS; durable audit remains D's responsibility.

On every restart, session revocation runs first. A PREPARING record is abandoned after the full current policy is applied/read back, so partial permission writes cannot make the app approved. A COMMITTED record represents an already-authorized durable approval: `resume` retries only its activation after fresh facts/readbacks, without generating another receipt or restoring a PIN grant. Until the journal clears, **ordinary `PolicyEngine` passes exclude the pending app**, even at locked boot or when the build is unknown. Only the controller's validated activation plan includes it. A crash or false return after unhide can leave a partially applied physical state; normal policy excludes/rehides the pending app, and the next startup revokes before retrying. No successful handback is claimed for an incomplete activation. Missing/changed facts, failed readbacks or low battery keep activation pending. A current trusted person can instead withdraw that exact pending approval using the recovery path below; no stale or missing fact automatically authorizes removal.

### Signer-bound policy input migration

`PolicyInput.approvalState` now carries the validated persistent app records, and `installedIdentities` carries fresh exact current signer sets. `PolicyEngine` ignores the old package-only `approved` set; it cannot be used to migrate trust automatically. A missing state means no ordinary app approvals. Duplicate/missing installed facts or mismatched signer sets fail closed; matching-signer app updates keep their existing approval. Invalid/pending ordinary approvals also lose inherited lock-task membership at locked/unknown boot, including system-installed apps whose approval state is missing, empty or PREPARING. System installation and never-hide status alone confer no UI authority. This UI pruning does not newly hide system apps on unknown builds; explicit emergency UI, Plainphone and Messages keep precedence. For UI pruning only, the last stored inventory's minimum UI remains exempt so an unknown build can retain those entries from the previous UI list. An unmatched inventory adds no new minimum-UI entry and supplies no sweep, never-hide or session rules. Every D policy writer, including session/protected-state checks and waiting reconciliation, must supply this state and fresh identities. `StoredPolicy.approved` remains legacy decoding data only, not an authorization source. Existing permission choices remain stored for protected-state checks; an update never creates an implicit Allow for a new runtime permission.

This slice completes the **supervised pure-JVM transaction path** for Add an app, not a device demonstration. D still implements storage durability/CAS, live facts/classifier input, permission effects, synchronous unhide flush and complete readbacks, fixed-recipient outbox dispatch, startup/dispatcher wiring and device validation. Lane B supplies the reviewed permission copy. Self-managed elevation, removal of fully activated approvals/permission edits, and the remaining waiting/mode/people workflows are separate work. No production Android capability or delivery claim follows from the fake effects in these tests.


### Withdrawing a stuck COMMITTED approval

`prepareWithdrawal(person)` returns a process-local `AppApprovalWithdrawalRequest` for the exact current COMMITTED approval. Show its pending package and approval ID for confirmation, obtain a **fresh** PIN for the request's action, and call `withdrawPending(request, confirmedApprovalId, authority)`. Either current trusted person may do this; it does not reuse the approval PIN, accept another person's grant, reset a credential, reapprove a changed signer, or infer an uninstall from a missing manifest. Self-managed withdrawal needs future elevation integration. No wait is introduced: this is the existing reversible app-removal rule applied to a pending activation.

Withdrawal revokes session access first, then saves a WITHDRAWING journal with a credential-revision CAS. The saved intent binds the approval ID, the withdrawing actor, a unique withdrawal receipt ID and the holder-generation snapshot. D must bind fixed delivery identities **at this intent save**, even though the withdrawal receipt enters the outbox only at completion. Preserve the original approval record and receipt until then. The new `AppApprovalReceiptKind` distinguishes APPROVED from WITHDRAWN, and `approvalId` links the withdrawal receipt to the original approval. Existing APPROVED records decode with `approvalId == id`. Update/validate the storage schema for the new phase, optional intent receipt and receipt fields; do not coerce unknown phases to COMMITTED or initialize invalid records.

Once WITHDRAWING is durable, `onStart` revokes first and `resume` can only finish withdrawal, including after credentials, mode, clock epoch or boot change. It never retries the old activation or asks for the consumed PIN again. The controller applies and reads back ordinary policy with session access removed and the target excluded by the pending journal. It re-reads the plan, current package/version/signer facts and build before completing; a changed target invalidates even an otherwise identical policy readback. It then atomically removes the approval, clears the journal and appends the fixed WITHDRAWN receipt, returning WITHDRAWN rather than APPROVED. The original receipt remains intact; outbox acknowledgement/audit rules still apply. Retries cannot create another withdrawal receipt. Nothing uninstalls the app, clears its data or writes permissions. A later approval requires a newly reviewed request and fresh authority.

Cleanup does not depend on matching the old manifest or old signer. A complete installed-package snapshot can establish absence; null manifest alone cannot. Cleanup is not battery gated because it reduces access. Locked boot, a missing system dialer, duplicate target package facts, concurrent committing/rolling-back waiting changes, unreadable storage and failed writes/readbacks keep the slot held. An installed target must be hidden and outside UI/unhide sets before completion; required emergency/never-hide UI is never destroyed to release a slot. Therefore a system target on an unknown build can still require a supported inventory before withdrawal completes, because unknown-build policy forbids newly hiding system apps. D should surface this held outcome as needs-attention with a retry after the prerequisite changes, not as successful removal. This is a safety limit, not automatic stale-entry recovery.

D uses the same serialized, non-reentrant coordinator, vault and session controller as approval. Every policy reader excludes a WITHDRAWING target, and every writer preserves its intent, receipts and other approvals. Crash probes cover both sides of the intent and completion saves, revocation, policy effects and readback; they establish JVM ordering only. D still owes real A/B durability, fresh snapshot completeness, physical containment/flush/readback, delivery and startup tests.
