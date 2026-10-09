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

Before the skeleton lands:

```sh
./spikes/emulator/apps/gradlew -p app/core :core:test :policy:test :core:check :policy:check
```

## PIN/recovery slice

`PinVault` receives `CredentialStore`, `KeyDerivation`, `SecretEntropy`, and `RunningClock`. The store must atomically compare the durable revision and replace the whole credential record. A failed clock checkpoint, missing record, or failed write yields no authority. Lockout notices are durable obligations (`pendingNotices`) for lane D's text queue; successful login does not erase them.

`prepareCredential` returns a hash-only credential and a one-time recovery-code presentation. Lane D supplies cryptographically secure entropy and PBKDF2-HMAC-SHA256. The vault enforces a 16-byte salt and at least 600,000 iterations. Each holder and each credential channel has an independent counter. Entries during delay are not verified or counted. Remaining delay uses proven time, so reboot/offline/wall-clock changes cannot shorten it.

`authenticate` binds fresh authority to an exact action/binding and credential generation. `consume` is single-use, process-local and boot-bound. Run consumption and the following action on the same serialized dispatcher as every credential change. These objects must never come from outside intents or be deserialized. Recovery replaces only its holder's PIN/code and raises that generation atomically; it grants no action authority or wait skip. Waiting-change cancellation on a generation change will be connected in that controller's later slice.

These tests use synthetic secrets and a deliberately cheap fake derivation. They verify logic, not the production cryptographic adapter. Profile/contact/FRP data and the waiting-change transaction extension remain for the waiting controller slice.

## Proven clock

`ProvenRunningClock` implements the core `RunningClock` seam through `ClockStateStore`, a boot-time supplier, and an epoch supplier. Lane D supplies live monotonic time/boot identity, checksum validation, and atomic compare-and-set storage. Epoch IDs must never be reused. A null boot identity or failed write returns no time authority. Repeated checkpoints add each interval once; new boots add only time since that boot started. Corruption, backwards readings and overflow repair to a fresh zero epoch anchored now. Schedule checkpoints at least every five minutes and on every state transition/debit.


## Supervised session journal

`SessionController` requires `JournalStore`, `SessionEffects`, `RunningClock`, a matching inventory (or null), and `AuthorityConsumer` (implemented by `PinVault`). Start authority is bound to `PrivilegedAction("session:${kind.name}", sessionId)`. Use a new non-reused session ID for each request; callbacks carry that ID. The same serialized dispatcher owns all controllers and storage writes.

`onStart()` must run before any other work. It revokes all potential relaxation first, runs the protected-state check/readback, then clears the journal. No credential data or clock is needed for recovery. `start` writes an opening journal before access, checks battery (5% minimum unless charging), opens only Play or the inventoried Settings/same-UID set, and reads back. All end triggers write closing direction, revoke, check, read back, then clear. Any failed step leaves handback unavailable. The adapter implements only the spec's protected checks and preserves ordinary setting changes; emergency access never waits for those checks. Mandatory restrictions are never lifted.

The crash test injects failure before and after every journal write/revoke/open boundary and at protected-check/readback boundaries, reconstructs the controller, and proves revocation is first. This is a JVM journal-order proof, not evidence of Android durability or the power-loss mitigation. If Plainphone cannot run, the disclosed custody limit still applies.
