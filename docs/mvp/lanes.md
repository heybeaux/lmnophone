# MVP lanes, modules and interfaces

Owner: Plainphone Lead. Board direction: Beaux, Telegram 4496/4498 (2026-10-09). Parent issue: PLA-15. Lane issues: D0 skeleton PLA-44, A Policy PLA-46, B UI PLA-47, C Link PLA-49, D1 Device PLA-48.

The MVP is built in four parallel lanes. Each lane owns one or more Gradle modules and the API those modules export. Lanes build against the seams below. To change another lane's API, ask its owner on their lane issue; the Lead settles disagreements.

**Specs to build to** (both approved by Beaux):
- `openspec/changes/add-plainphone-spike-and-mvp/` (specs, design, tasks 4.x and 5.x)
- `openspec/changes/add-copy-test-product-decisions/` (PLA-30: two trusted people, self-managed mode, 24-hour waits, privacy steps)

**Look and words:** `docs/ux/design-tokens.md`, `docs/ux/mockups/`, and `docs/ux/flows-*.md`. There is no Emergency button on the home screen (Telegram 4487).

## Module map (Gradle root: `app/`)

| Module | Kind | Lane / owner | Contents |
|---|---|---|---|
| `:core` | Kotlin/JVM, no Android | A (Policy) | Shared value types only: `PackageName`, `Mode` (`TRUSTED`, `SELF_MANAGED`), `TrustedPersonId`, `WindowKind` (`PLAY`, `SETTINGS`, `APPROVE`, `UPDATE`, `WEB`, `WIFI_SIGNIN`), `RunningClock` (the proven running-time clock interface), `BuildInventory` model, `NeverHideList`, `CertDigest` (64 lower-case hex; approvals bind package + signer, PLA-46 review). |
| `:policy` | Kotlin/JVM, no Android | A (Policy) | The policy engine and every state machine, as pure logic with JVM unit tests. |
| `:links` | Kotlin/JVM, no Android | C (Link & Browser) | Browser classifier, link matrix, blocked-link decision. |
| `:ui` | Android library, Compose | B (UI) | Screens, theme (tokens, Lexend), UI state and event types. No policy logic. |
| `:device` | Android library | D (Android Platform) | DevicePolicyManager, lock task, PackageManager facts, storage (atomic, Direct Boot), SMS, receivers, the unhide flush (PLA-22 mitigation B). |
| `:plainphone` | Android application | D (Android Platform) | Device admin receiver, activities, view-models that map `:policy` + `:device` → `:ui` state, wiring. |

**Dependency rule:**
- `:policy` and `:links` depend only on `:core`.
- `:ui` depends on nothing of ours.
- `:device` depends on `:core`, `:policy` and `:links`.
- `:plainphone` depends on everything.

`:core`, `:policy` and `:links` never import `android.*`.

**Toolchain** (matches the spikes): AGP 8.11.0, Kotlin 2.1.20, JDK 17, compileSdk 36, minSdk 30. Apache-2.0 headers on every source file. Lexend ships with its OFL licence file.

## Seams (names are binding; signatures are a starting point the owner may refine)

**`:policy` (lane A) exports:**
- `PolicyEngine.decide(input: PolicyInput): PolicyPlan`. Pure. The plan holds:
  - the hidden set and the lock-task package list;
  - lock-task features and user restrictions;
  - the apply order.
  The never-hide guard refuses any plan that touches the never-hide list (essential-calling).
  `PolicyInput.confirmedAbsentPackages` is fresh, positive evidence from lane D that a
  package is not installed for the managed user. Default empty means unknown, not
  absent. An omitted enumeration result, null UID, failed/unreadable query, unavailable
  user, or hidden/disabled state must never populate it. Any `installed` fact wins a
  contradictory absence report. Policy removes confirmed-absent minimum/inherited UI
  names from both the planned and expected lock-task lists, including unknown-build
  and locked-boot plans, while retaining Home and all never-hide/repair obligations.
  Lane D must apply and read back this exact reduced list before any permission
  window opens; locked boot's limited stages are not proof the full list was applied.
  Keep the strict live UID resolution of **every** remaining applied name. Do not
  filter null UIDs at the window gate. Installs cannot amend an already constructed
  plan or applied list: close/revoke the window before a fresh serialized policy
  pass can restore installed minimum UI from a matching inventory. Unknown builds
  cannot add it back from stale inventory. Package events and window effects use the
  same privileged coordinator. Device fact collection and actual adapter/install
  race verification remain lane D work; JVM contract fakes do not prove Android
  containment, including shared-UID behavior.
- `AppApprovalEffects`: `openPermissionWindow(identity): Boolean` removes the pending app from applied lock-task access and Home, reads those exclusions back, then unhides with synchronous flush and live readback. `closePermissionWindow(identity): Boolean` re-hides and reads back; it never restores access. Policy calls `setPermission` only inside this window, all DENY before ALLOW, and requires `readPermissions` to work both visible and hidden. Every attempted open is followed by close, including failure. Pending PREPARING/EDITING recovery hides before startup gates; both ports enforce the live never-hide guard. Lane D implements these effects and `containFailedActivation(packages: Set<PackageName>): Boolean`: idempotently remove every supplied discretionary package from lock task and Home, hide it, and return true only after reading all three exclusions back. After a failed activation, policy supplies the immutable intersection of the attempted activation plan’s unhide and lock-task sets, minus its never-hide and emergency UI sets. Every pending phase (including PREPARING, COMMITTED, EDITING, WITHDRAWING and REMOVING) carries one target-only containment obligation until its outcome is verified and durably completed. `withPendingContainment` guards every effectful recovery entry before hide, session, battery, permission and plan gates. It reconstructs the singleton from the durable journal, excludes known protected packages, and still calls the adapter when facts are null or throw. The adapter's live floor guard remains mandatory. Unrelated approved apps are never included in this pre-gate cleanup. Failed, throwing or unverified containment returns NOT_READY and keeps the journal and receipts; verified containment may then return LOW_BATTERY. Refused withdrawal authorization remains non-mutating. Authorized withdrawal keeps containment through plan and session recovery. The journal has no prior-permission snapshot, so edit withdrawal completes restrictively using `withdrawalPlan`, apply/readback and a plan/facts recheck, without a battery gate. One completion CAS removes the approval, saves `removedApps[name] = op.record.identity`, clears pending and appends the fixed WITHDRAWN receipt. It never writes permissions, opens a permission window or activates/unhides the target; no choices are reconstructed. Failed, throwing or unverified steps return NOT_READY and retain WITHDRAWING and its receipts. Fresh `prepareReapproval` works after restart; `preparePermissionEdit` remains unavailable for the removed app. New-app withdrawal, removal and PREPARING drop also complete restrictively. Every unsuccessful final activation contains the attempted activation plan's discretionary set and retains its pending direction. The joint-store `save()` contract is unchanged: true acknowledges durability; false does not. `applyPolicy` still contains its own partial failures; `readBackPolicy` stays read-only.
- `UpdateInstaller.committedSessionPending(sessionId: Int): Boolean?`: lane D supplies read-only
  committed-session presence (true pending, false confirmed gone, null unknown). Pending submissions
  block replacement until version readback confirms installation or the session disappears. Startup
  abandons only uncommitted own sessions; commit memory belongs to one adapter instance. The internal
  `UpdateProtocol` constructor now also requires `bootTime: () -> BootTime?`, a pure monotonic read
  without storage/checkpoint work. After durable use-up, a deadline-bearing submission past its
  deadline is marked FAILED by result-only CAS without install or refund; its receipt is retained.
  Supervised submissions have no deadline. Update remains disabled; lane D implements these seams
  before any separately reviewed production wiring.
- `SessionController`: the supervised-session and elevation contract (start, end, revoke-on-start from the journal). Journal and policy persistence go through interfaces that lane D implements: `JournalStore`, `PolicyStore`.
- `PinVault`: PIN and recovery-code logic.
  - Separate counters and the exact lockout schedule.
  - Hashing goes through a `KeyDerivation` interface (PBKDF2 is implemented by D).
  - Supports one or two trusted people.
  - `replaceRecoveryCode(person, authority, action)` consumes a fresh person/action-bound
    PIN grant and atomically replaces only the recovery verifier and its counter. PIN,
    PIN counter and credential generation are preserved. `credentialWaits(person)`
    reads both remaining waits without making an attempt.
  - Preview enrollment now returns `PinEnrollmentResult.Created(OneTimeRecoveryCode)`;
    its code can be taken once and is cleared on an abandoned callback. The host shows
    enrollment, rotation and recovery codes under FLAG_SECURE, then clears its buffer.
    Recovery resets the credential but grants no subsequent action authority; preview
    add/remove and turn-off still ask for a fresh PIN.
- `WaitingChangeController`: the 24-hour waits (turning off, trusted-people changes, mode switches) and cancel.
  `tick` keeps its default forward behavior. Preview hosts pass `turnOffAvailable = false`
  only from Android's test-build flag: a mature pending turn-off closes as CANCELLED
  through the existing terminal cancellation path, with zero restore steps. The
  existing CANCELLED_AFTER_ROLLBACK receipt identifies this no-effect preview result;
  it is not a rollback of a committing turn-off. No new PIN authority is introduced.
- `ElevationController` (self-managed mode), `PlaySessionMachine`, `MonthlyCheck`, `EmergencyFloor` and baseline rules, `NoticeLine` priority.
- `TimeWindowQuota`: the generic "open for a few minutes" quota. The **web window** (5×/day × 5 min, extendable) and **Wi-Fi sign-in** (3 per 24 h) are built behind a flag that stays off. Their OpenSpec change isn't approved yet; Wi-Fi sign-in comes after the MVP core.

`AppRequests.remove(id): Boolean` deletes the saved request text and retains only its ID/day for
quota and replay protection. True requires an exact store read-back. `RequestBook.removed` holds
these content-free entries; schema 2 reads schema 1 without changing existing requests/recipients.
Late SMS results for removed IDs do nothing. UI mapping consumes `RequestView.available` before
publishing a request count; an unreadable store has no count.

**`:links` (lane C) exports:**
- `BrowserClassifier(self: PlainphoneIdentity).classify(facts: List<PackageFacts>, catalog: BrowserCatalog, neverHide: NeverHideList, inventoryKnown: Boolean): Classification`.
  - `PlainphoneIdentity` is the exact package name plus the exact set of signing-certificate digests (`CertDigest`).
  - `inventoryKnown = false` leaves system matches unhidden (`KEEP_SYSTEM_NO_INVENTORY`), but they still can't be approved.
- `LinkDecider(linkCatcher: PackageName).decide(intent: IntentFacts, approved: Set<PackageName>): LinkDecision`.
  - Possible results: `OpenIn(pkg)`, `BlockedScreen(WEB_LINK | NOT_APPROVED | CHOOSER)`, `NoHandler(...)`.
  - `approved` includes the lock-task UI exceptions.
- `PackageFacts` and `IntentFacts` are plain data that lane D fills from PackageManager. Lane C never calls Android.
  - `IntentFacts.resolvers` is what `queryIntentActivities` returns for the intent as dispatched: one `Resolver` per activity, with no package deduplication. Each `Resolver` carries `verifiedForHost`.
  - `IntentFacts.explicitComponent` is true when the sender named a component, as opposed to only `setPackage`.
  - `IntentFacts.resolved` (`Resolution.Activity | Chooser | None`) is what Android reported it would launch, including saved defaults and priorities.
  - `IntentFacts.intentUri` says how the sender handled an `intent:` URI.
  - `IntentFacts.linkCatcherPreferred` is true once lane D has set the persistent preferred activity for `VIEW http/https`.
  - Facts that contradict each other give `NoHandler(INCONSISTENT_FACTS)`.
  - Details: `app/links/README.md`. Lead accepted these refinements on PLA-49.

**`:ui` (lane B) exports:** one `@Composable` per screen, with this shape:
- `XScreen(state: XUiState, onEvent: (XEvent) -> Unit)`.
- `XUiState` and `XEvent` are plain Kotlin types owned by lane B.
- `RequestRecipientUiState.readFailed` distinguishes unavailable saved recipient data from a failed edit. The host supplies it after a failed initial read; the screen offers OK/Back instead of editing unknown data.
- `AboutUiState.isDeviceSecure` is a host-supplied `KeyguardManager.isDeviceSecure` fact (default false); only true shows the lock-screen emergency instruction. The UI does not query Android.
- Optional communication-count UI: `HomeUiState.missedCalls` / `unreadTexts` are
  nullable host-supplied integers. Host passes null immediately when off or
  unavailable; UI shows only positive values, capped visually at 99+.
- `PhoneSettingsScreen(PhoneSettingsUiState, onEvent)` is the PIN-free destination
  for `MoreEvent.OpenPhoneSettings`. State has `showCommunicationCounts = false`,
  `missedCallsUnavailable = false`, `unreadTextsUnavailable = false`; events are
  `Back` and `ShowCommunicationCountsChanged(enabled)`. Host owns navigation,
  saved preference, permission handling and fresh count reads. Unavailable
  status lines show only while enabled. This slice adds no data source.
- `home.NeedsAttentionScreen(NeedsAttentionUiState, onEvent)` displays an optional
  `PendingChange(kind, committing)`. `PendingChangeKind.TurnOff(time, day)` requires
  the same host time/day snapshot as Home; `People` and `Setup` need no date.
  `CancelChange` opens `WaitingChangeScreen` at `WaitingStage.ConfirmCancel` with
  a null context, without a PIN. The host rechecks the live change before acting;
  `Back` returns home. Committing state removes Cancel. Calls and Emergency stay
  reachable through host navigation; this screen does not launch either.
- `user.AboutScreen(AboutUiState, onEvent)` displays U8 with host-supplied installed
  `versionName`, `trustedPeople`, `helper` and resource-backed `productName`. `AboutEvent.Ok` returns to More.
  The preview supplies sample identities; it offers no helper-number or SOS action.
- Covers: home (with the single notice line), More, ask for an app, blocked link, Wi-Fi messages, the trusted-person screens ("Who are you?", PIN, approvals, permissions, people, waits and cancel), Plainphone settings, setup, turning off, About, and "What Plainphone can't stop".

`PhoneSettingsUiState.pendingChange` is the optional current U9 description.
`PhoneSettingsEvent.OpenWaitingChange` opens the existing PIN-free cancel route;
the host reads the same waiting store as Home and rechecks the request on tap.

**`:device` / `:plainphone` (lane D):** implements `JournalStore`, `PolicyStore` and `KeyDerivation`. It also provides:
- `PolicyApplier`, which applies a `PolicyPlan` through DPM and reads it back;
  its device-internal `DevicePolicyPort.packageHidden` reads only installed-user
  membership and the live hidden flag for hide-only work. Null means confirmed
  absent; unreadable queries throw. Pre-write checks, post-write readbacks and
  independent final verification all remain live, with no state cache. Visible
  and never-hide packages still use the full package-state reader.
  `PackageSystemPort.packageStates()` supplies the same live installed-user coverage as
  `packages()` without signer data; the Android port omits `GET_SIGNING_CERTIFICATES`
  and keeps all hidden/disabled flags and the installed-user filter. The preview
  uses `LivePackageReader.readInstalled()` (typed installed/disabled data only),
  with two snapshots bracketing the same floor queries. Its projection preserves
  all existing merge/order/disabled rules. Full `read()` retains signers and probes
  for links/approval. An optional `ReapplyTimings` constructor parameter records
  fixed snapshot1/probes/snapshot2/merge labels; probes is absent in the light read.
  `BootReapply.retryPending` reports an unacknowledged flip-notice save. The
  preview runtime must not cover a late boot trigger while that retry is pending.
  `ReadbackPolicyApplier` and `BootReapply` accept an optional `ReapplyTimings`
  collector for elapsed-time diagnostics. The preview runtime shares one collector
  through input assembly, writes, readbacks and the post-reapply hook, resetting
  it per serialized pass. Timing does not change the plan or readback contract;
- `PackageFactsSource`;
- `PlaySessionDispatcher` in `device.session` owns one `ElevationController` and one
  `SessionController` on `JointAuthorityStore.elevationStore`. `request` consumes a
  fresh Play-specific PIN; start/tick/cancel/end and package events stay serialized
  with other privileged work. The boot integration must use its `sessions` instance
  on that same record. `ReadbackPlayEffects` accepts policy-produced baseline/open
  plans, validates that only Play gains access, and uses `ReadbackPolicyApplier`.
  These adapters are not yet wired into the preview runtime. The existing inventory
  and active-friction prerequisites still refuse incomplete configuration; no
  synthetic inventory or recovery identity is a runtime substitute. Suspended or
  disabled Play refuses opening until its prior state can be restored durably;
- the SMS sender;
- view-models mapping engine output to `XUiState`.

**What the `:ui` screens leave to lane D** (from the lane B slice 2 reviews, PLA-109 and PLA-110). Each one needs a test in lane D's integration work:
- **Send and retry may arrive twice.** A greyed-out button doesn't stop two taps that land before the screen redraws: the Verifier showed both `BlockedLinkEvent.Send` and the request `Retry` events can fire twice. The SMS sender must ignore a repeat of a send that is already under way, for link confirmations, app requests and retries.
- **Names are cleaned when saved.** Trusted-person names reject or strip bidi controls (U+202A–202E, U+2066–2069, U+200E, U+200F, U+061C), zero-width characters and line separators when saved. `:ui` also strips bidi controls when it shows a name, but that only backs this up.
- **Hosts that aren't plain ASCII show as punycode, with the unusual-letters warning.** `pay​pal.com` (with a zero-width space) must not reach the screen as "paypal.com".

### Preview permission addition (Location correction)

`PreviewPermissionAddition` takes two required device callbacks in addition to write/read:
`checkpoint(AppPermissionSnapshot): Boolean` and `rollback(AppPermissionSnapshot): Boolean`.
The runtime binds both to the same `PermissionGrantAttempts` instance. The checkpoint reads
fixed and actual runtime grants before writes. An unsuccessful addition restores those values
while excluded from Home and lock task, then re-hides; a refused restore returns CheckFailed.
A post-hide failure reopens only through the existing guarded permission-window effect.
Returned failure readback is refreshed after restoration. This does not journal a rollback across
process death; startup still hides unapproved apps as before.

`DpmPermissionGrantPort.policyState` corrects Android 15's foreground-only COARSE/FINE
readback using the actual runtime grant, only when DPM reports a fixed state and the package
is not suspended. DEFAULT remains DEFAULT. See `app/tools/pla564/README.md` for the source
and repeatable emulator check.

## Branches, worktrees, merges

| Lane | Engineer | Worktree | Branch |
|---|---|---|---|
| D0 skeleton | Android Platform Engineer | `../plainphone-device` | `mvp/skeleton` |
| A Policy | Policy Engineer (GPT) | `../plainphone-policy` | `mvp/policy` |
| B UI | UI Engineer (GPT) | `../plainphone-ui` | `mvp/ui` |
| C Link & Browser | Link & Browser Engineer (Sakana Fugu `fugu-ultra`; GPT-6 fallback) | `../plainphone-links` | `mvp/links` |
| D Device | Android Platform Engineer | `../plainphone-device` | `mvp/device` |

**Getting started:**
- Create your worktree from the main checkout: `git -C ~/projects/plainphone worktree add ../plainphone-<lane> -b mvp/<lane> main`.
- Worktrees share refs, so you never need `git fetch`. Don't run it while others work: concurrent fetches race on lock files.
- Push your branch to `origin` (private) as `heybeaux`. Never force-push `main`. Never commit credentials, account addresses, phone numbers or unredacted screenshots.
- **Before the skeleton lands:** lanes A and C may start in `app/core`, `app/policy` and `app/links` with a temporary standalone `settings.gradle.kts` in their module directory. Delete it when rebasing onto the skeleton.
- Don't touch other lanes' modules or the root build files. Ask lane D instead.

**Merge order** (the Lead merges into `main` with `--no-ff`):
1. D0 skeleton;
2. A and C, in small, tested slices whenever they're ready;
3. B;
4. D device integration.

**Review before every merge:**
- The Verifier & Escape Artist reviews the lane slice.
- Lanes A and B are written by OpenAI models, the same family as the Verifier. So the Lead (Claude) also does a code review of each of their slices, which keeps the cross-model rule. Lane C runs on Sakana Fugu (`fugu-ultra`, subscription until 2026-10-28), and lane D runs on Claude; the Verifier's review is cross-model for both. If lane C falls back to GPT-6, the Lead's code review applies to it too.

**A slice is ready when:**
- it's rebased on `main`;
- `./gradlew :<module>:test` passes, plus `:<module>:check` where it exists;
- new behaviour has tests written first;
- the slice comment on the lane issue lists the spec requirements covered.

**Gate:**
- The skeleton (D0) and lanes A, B and C don't wait for the go/no-go gate (PLA-14).
- Lane D's device integration (D1) waits for PLA-14, as Beaux directed. Any change to that is his call, asked on PLA-14.
- The Android Platform Engineer is the only one who drives the spare Motorola.

### Optional Home count host seam (preview branch)

`:device` exports `CommunicationCountPermissions.grant()` for the calling app's
READ_CALL_LOG and READ_SMS only. It never revokes. `:plainphone` independently
checks each permission and a successful count query; a DPM return is not proof.
The host owns a private `home_preferences` store (absent in preview-2), observation,
cancellation and the PIN-free Phone settings route. Counts are never persisted.

Revision 4 host freshness uses separate timer and query ports, a single combined
Messages statement, and read-only role/package/Home-target gates.
`PhoneSettingsUiState.unsupportedApps` carries the supported-app disclosure.
No policy-engine, emergency-floor or permission-grant interface changed.

Preview 13 readback seam: `PackageChangeReadback` captures live package flags for
system packages from a full preview plan, excluding its floor, guard, Home, lock
task and session packages. Its read-only `unchanged` check requires a clear
journal, live floor/Home exclusions, exact lock-task list, installed/hidden/
suspended/enabled state equality, and no newly launchable visible package.
`BootPassCoverage` independently requires the last full unlocked verified, ready,
non-attention pass, no pending save and no negative control. It admits only a
single `PACKAGE_CHANGED`; mixed bursts and all other package actions run fully.

### Preview 17 integration checkpoint

The preview keeps `Preview2Device.plan` and the existing starter list. Every pass
checkpoints waiting state before considering a covered boot/package skip; pending,
committing, rolling-back or unreadable authority forces a full pass. The Home
notice and cancel screen use runtime waiting state rather than preview sample data.
Home publication still precedes `captureAfterPublished`, with grouped package
eligibility unchanged when no waiting work is present.

A lost authority record remains held with needs-attention in this preview: the
reconciler receives no full-engine policy input, so it cannot replace the preview
plan or write reset-protection accounts. A committing turn-off cannot authorize
forward restore. This is an explicit fail-closed merge choice, not full D1 recovery.

`device.authority.PinEnrollment.create(person, pin)` is the first-use PIN
adapter. It returns `CREATED`, `INVALID_PIN`, or `UNAVAILABLE`; clears the input
array on every path; refuses any existing authority record; and atomically creates
self-managed credentials, an empty wait and idle elevation through
`JointAuthorityStore.initialize`. It grants no authority and creates no recovery
profile. Authentication still uses `PinVault` and the shared credential CAS.
The preview UI exposes PIN setup and entry only; `More`'s other unavailable
routes remain covered by `TestBuildNavigatorTest`. Waiting-change starts still
need the separately deferred recovery-profile integration.

Preview 17 hosts `device.authority.PreviewPinAccess` through the common serial
`PolicyRuntime` dispatcher. It offers genesis-only PIN enrollment, then checks a
fresh PIN and immediately consumes its purpose-bound grant to open an informational
controls screen. The screen stores no grant and every privileged action remains
unhosted. `PinScreenFlow` ignores callbacks after close and rejects duplicate
submits; `PinAccessActivity` clears input and access on pause. Enrollment has no
reset-protection profile, starts no wait, and leaves the preview starter plan intact.

## Build 20 basic-settings seam

`ShellRoute.Brightness` and `ShellRoute.Battery` return to `PhoneSettings` on Done
or Back. `:ui` receives `BrightnessUiState(percent?, automatic, failed)` and
`BatteryUiState(percent?, charging?)`; it emits `BrightnessEvent` and Back only.
The host observes battery broadcasts only while Battery is visible. `:device`
exports `AndroidBrightness` through `BrightnessPort` and `BrightnessController`:
DPM-only writes with readback, a 26/255 manual minimum, and rollback attempted on
write/readback failure. A failed rollback is never called success; actual values
or unavailable state are shown. Automatic brightness remains Android-controlled;
manual controls are disabled until it is turned off. No permissions or policy
lists change. These seams implement the approved U7 subset in [PLA-395](/PLA/issues/PLA-395).

## Preview 21 U7 presentation and device seams

`SoundScreen(SoundUiState, (SoundEvent) -> Unit)` emits stream-specific SetLevel,
Adjust and Done. The host supplies only confirmed ring/media `VolumeUiState`
values and a failure flag. `PhoneSettingsEvent.OpenSound` opens this route.
`:device` owns `VolumePort` / `VolumeController` and `AndroidVolume`; the port
reads stream level/min/max and writes a requested level. Ring floor is 1,
media floor 0; failure attempts restoration and reports the final readback.
`BatteryUiState.status` distinguishes Charging, Charged and NotCharging.
No policy-engine, emergency, count or About seam changes.

Build 22 settings seams ([PLA-404](/PLA/issues/PLA-404)): `VolumeReading.adjustable`
is false for ring in silent/vibrate; `VolumeController.onOpen()` clears old failures
and reads fresh state. `SoundUiState` carries `ringFailed`, `mediaFailed` and
`ringAdjustable` separately. The host refreshes only while the page is resumed
and visible; UI only presents supplied state.

### Preview 27 Text size seam

`:device` supplies `TextSizeController(TextSizePort)` and `AndroidTextSize` for
U7. The port reads the actual system scale, checks write capability and reports
write success; the controller selects the approved steps and reads back every
attempt. `TextSizeState` contains the confirmed scale, enabled directions and
failure flag. `:ui` renders `TextSizeUiState` and emits `TextSizeEvent`
(Smaller/Bigger/Done); `:plainphone` formats the percentage, polls only while
the page is visible, and owns navigation. No policy or lock-task list changes.

## Preview 28 read-only Wi-Fi seam

The approved build-28 slice adds `WifiPort.read(): WifiFacts` and the pure
`WifiController.refresh()` presentation reducer in `:device/settings`, alongside
the existing basic-settings controllers. `AndroidWifi` reads enabled/connection
state and the latest platform scan, never starts scans or alters networks/settings.
Its page-open hook may grant only its own fine-location permission via DPM.
`:plainphone` refreshes while resumed on `ShellRoute.WifiSettings` and maps
`WifiState` to `WifiSettingsUiState`; `:ui` exposes Back only. These are preview
presentation seams, with no changes to policy-engine or lock-task contracts.

Preview 29 extends `WifiFacts` with nullable validation, captive-portal status and
an ephemeral network handle. `WifiController` takes a monotonic clock, resets its
observation on page open, connection change or unreadable/interrupted state, and
emits `WifiInternetStatus` after the 10-second grace period (portal is immediate).
`WifiState` / `WifiSettingsUiState` explicitly distinguish an overall read failure;
UI receives `WifiInternetUiStatus` and remains read-only. Names and handles are
never persisted. Policy and lock-task interfaces are unchanged.

### Preview 30 read-only Bluetooth seam

`:device/settings` exports `BluetoothPort.read(): BluetoothFacts`,
`BluetoothController.refresh()` and `AndroidBluetooth.onOpen()` (own
BLUETOOTH_CONNECT grant only). Only fully-on snapshots expose the sorted,
ten-entry paired-name list; exceptions discard all facts. `clear()` drops names
when hidden. `:plainphone` polls only while resumed on `ShellRoute.BluetoothSettings`;
`:ui` receives `BluetoothSettingsUiState` and exposes Back only. No policy or
lock-task interface changes. API 30 uses the normal legacy BLUETOOTH permission.

### Preview 31 Ringtone seam

`:device/settings` exports `RingtoneController(RingtonePort)` and `AndroidRingtone`.
The port lists built-in TYPE_RINGTONE choices, normalizes URIs, reads the actual
RINGTONE setting/title, checks the existing settings-write grant, and bounds
preview playback to five seconds. The controller selects without writing and
returns success from Done only after matching read-back (or no change).
`:ui` renders `RingtoneUiState` and emits Select/Done/Back; `SoundUiState` adds
`ringtoneTitle`, with `SoundEvent.OpenRingtone` opening `ShellRoute.Ringtone`.
The host stops previews on leave/pause, polls the grant only while visible, and
re-reads the title for Sound. No policy, lock-task or permission contract changes.

### Preview 32 nearby Bluetooth seam

`:device/settings` exports `BluetoothDiscoveryPort` and a pure
`BluetoothDiscoveryController`, with main-thread events, permission/adapter
availability checks, capped ephemeral results and cancellation on close.
`AndroidBluetoothDiscovery` grants its own SCAN permission (neverForLocation),
reads it back, observes discovery while visible, and never bonds or changes
Bluetooth/Location. API 30 reports unavailable: this preview's no-Location scan
path requires API 31+. `:ui` renders `BluetoothDiscoveryUiState`, emits
Back/LookAgain/Select events, and shows a name-only device page with the honest
pairing-not-ready line. `:plainphone` owns route selection and cancels on pause
or leave. No policy-engine or lock-task interface changes.

### Preview 33 Wi-Fi joining seam

`:device/settings` exports `WifiLocationController(WifiLocationPort, clock)` and
`WifiJoinController(WifiJoinPort, clock, onConnected)`. The first owns a durable
Location marker and call-aware cleanup; the second bounds each confirmed-network
join to 30 seconds. `WifiJoinSession` serializes them on the main thread, restores
marked Location at application startup and continues deferred cleanup after pause.
`AndroidWifiJoin` adds new configurations or selects existing ones unchanged.
`:ui` renders `WifiJoinUiState` and emits password/show/connect/cancel events;
`WifiSettingsUiState` adds Location status and selectable transient choices.
The Activity owns ephemeral password text and FLAG_SECURE while joining. Policy,
lock-task, emergency and authority interfaces are unchanged.

### Preview 34 review corrections

No interface changes. `WifiLocationController` warns after 60 seconds of unknown
call state while owned Location needs cleanup, and continues retrying without
turning Location off during a known active call. `quoteWifiValue` adds only outer
quotes so Android receives literal name/passphrase bytes. The connected Forget
and message-route implementation at `c3a64c60` is deferred: API 35 redacts both
current connection identity and CURRENT configurations at Location 0.

**Tap-time Wi-Fi Forget preview (PLA-460, accepted PLA-448):** device adapter
`WifiForgetPort.read(): WifiForgetFacts` supplies connected, validated, connectedId
and distinct savedIds. `removeConnected(id)` rechecks the current validated identity
before one removal. `WifiForgetController(port, location, now)` exports `forget`,
`refresh`, `leave`, `onOpen`, `pending`, and `failed`. `WifiJoinSession` serializes
it with joining and shared owned Location cleanup. UI gains `canForget`,
`forgetFailed`, and Forget/message events; the host owns eligibility and effects.

## Voice family seam (PLA-500, slice 2b)

- `:policy` exports `FamilyRules`: six-person bound and name/number validation.
- `:device` exports `FamilyStore` / `AndroidFamilyStore`, storing enabled state and ordered `FamilyPerson` entries in credential-encrypted `noBackupFilesDir/voice-family`. `FamilyPicture.Letter` is the current picture type; photo import requires a later private-asset type. No contact provider or backup.
- `VoiceRecipientSource.recipient(personId)` returns the enabled live recipient, including the number, or null. Send code must resolve by opaque id at the point of use. Numbers never enter voice UI state. Family drafts use `voice-family-drafts`; sample practice retains its existing directory. Removal, turn-off, and a number change erase the affected drafts first, fail closed if erasure fails, and do not expose recordings in trusted-person mode.
- `VoicePracticeController` accepts `people` and `draftStore` providers for restoration and draft isolation; `VoicePerson.spokenName` defaults to its display name. Family microphone permission is only granted from the PIN menu; the family recorder checks the existing grant.
- `:ui` exports `FamilyScreen(FamilyUiState, FamilyEvent)`. The host owns storage, validation, voice preview and live PIN-menu authority. Fields clear on pause. `HomeUiState.showFamily` and `HomeEvent.OpenFamily` carry the Home route; `ShellUiMapper.home` supplies the flag from the production snapshot.
- `FamilyMessages.intent` creates package-explicit `ACTION_SENDTO smsto:` for Google Messages without content or automatic sending. Lock-task policy is unchanged. `VoiceUiEvent.OpenMessages` emits only an action; MainActivity resolves the current recipient and launches it.

### Voice family correction (PLA-504)

`FamilyUiState.micFailed` distinguishes a refused/failed microphone grant from a
save error; `helperName` supplies the named helper for the approved error copy.
`FamilyEditor` preserves that helper across refreshes. Name, spoken-name and
number entry events redact their `toString()` output.

`VoicePrompt.OpeningMessages` and `MessagesUnavailable` map to the approved
Messages handoff speech. `AndroidVoiceSpeaker.speakBeforeAction` completes the
prompt before calling the host's existing Messages opener; stopping speech
cancels that pending action. Failed/unavailable speech falls through, with a
five-second backstop for a stalled engine. These are speech/navigation seams;
recipient resolution and the destination app remain in the existing adapter.

### Voice MMS send seam (PLA-499, slice 2a)

`:device/voice` exports `VoiceSendPort.send(VoicePerson, number): SendView`,
`view(personId): SendView?`, `seen(personId)` and `VoiceNumbers.number(personId)`.
`VoicePerson` never carries a number. `VoicePracticeController` accepts an
optional sender, injected people, `recordCapMs` and a `VoiceNumbers` lookup;
`canSend` requires both a sender and a normalized number for that person id,
looked up again at every check and at the explicit send (a failing lookup counts
as no number). The family list (PLA-500) plugs in as
`VoiceNumbers { id -> source.recipient(id)?.number }`. Only explicit Review Send or
Not sent Try again events produce `VoiceEffect.SendDraft`. Restoring, opening,
reading outcomes and handling interruptions never send. `VoicePlace.Screen.Send`
restores the current persisted answer, without restarting audio.

`:device/voice/send` exports `VoiceSender`, `VoiceSendStore`, `SendStatus`,
`SendProblem`, `CarrierMms` and `VoiceSendLimits`. `MmsPort` supplies carrier
settings, signal and permission readback, one explicit send and per-attempt
cleanup. `AndroidMmsPort` owns the default-data-subscription send, app-private
PDU provider grant and result receiver. `Sent` needs RESULT_OK and a complete
m-send-conf with OK status; unanswered attempts become `NotConfirmed` on restart
or after the wait bound. Reading a confirmed outcome completes any interrupted
deletion of its matching draft, never deleting a different recording.
`VoiceSendChannel` (Practice, Family) binds each sender to one draft folder and
its own send records; `VoiceSendEngine.get(context, channel)` keeps one sender per
channel, and the channel key rides in the sentIntent so a late or post-restart
answer reads and erases only that channel's drafts (a missing key means Practice;
an unknown key only releases the outgoing file). Family sending is wired in PLA-513 (below).

`:plainphone` supplies the build gate, people and number lookup, maps states to UI and
posts result updates on the main thread only while the screen is visible.
`:ui` takes `VoiceUiState` and emits Send/TryAgain/Done; it has no transport or
permission logic. Debug sending is opt-in through the shell-only test hook;
release stays practice-only until the family-list integration (slice 2b).
No policy-engine, emergency-floor or lock-task interface changes.

### Voice family send wiring (PLA-513, build 47)

`VoicePracticeHost` builds a `VoiceSendRoute` from the current mode: the family
list uses `VoiceSendEngine.get(context, VoiceSendChannel.Family)` and
`VoiceNumbers { id -> familyStore.recipient(id)?.number }`, looked up at every
check and every send; the practice keeps the PLA-499 test-build marker for
practice Sam. A side without a sender has no numbers, so nobody there gets Send.
`VoiceSendBuild.familySend()` is the gate: true in the debug build type, false
in release. `VoiceUiMapper.map(state, people, family, canSend, helper)` combines
the family flags (`fromHome`, `openMessages`, `pictureInInk`) with the send flags
(`canSend`, `practice`). Removal, turn-off and number changes still erase drafts
through `FamilyStore` before the list changes; the host reads the list again on
each `open()`.

### Voice family replies (PLA-527, slice 2c)

`:device/voice/replies` exports `AndroidMmsInbox` (reads `content://mms` inbox,
`/part` and `/addr` with READ_SMS only; never writes the provider), `ReplySelector`
(family-list sender match by normalized digits or equal E.164, `audio/*` parts only,
latest 5 per person, unplayed, oldest first), `ReplyPlayedStore` (played-state in
`noBackupFilesDir/voice-replies-played`, keyed by a salted hash of the message id,
never a number) and `VoiceReplyBook : VoiceReplies`. `FamilyErase` is the
`AndroidFamilyStore` erase step: removal, turn-off and a number change erase the
person's drafts, `voice-send-family/<id>.send` record and played-state, failing closed.
`VoicePracticeController` takes an optional `replies: VoiceReplies`, exposes
`hasNewMessage(id)` and `refreshReplies()`, and adds `VoiceEvent.Listen`,
`ReplyListen` on `VoiceState.Person`, and the effects `PlayReply` / `ReplyPlayed`.
`VoicePlayer.playReply` and `VoiceSpeaker.speakAll` / `speakThen` are new with
defaults; `AndroidVoicePlayer(context)` plays the part URI directly, without a copy.
`VoiceUiMapper.map(..., newMessage)` sets `VoicePersonUi.newMessage` (V1) and maps
`ReplyListen` to `VoiceUiListen` (Listen, Stop, PlayAgain; V12a). `:ui` emits
`VoiceUiEvent.Listen`. The practice never reads the message store. RCS replies stay in
Messages. No policy-engine, emergency-floor or lock-task interface changes.

`VoiceReplies.canPlay(personId, clip)` supplements the existing membership-only
overload with a default for existing adapters. `VoiceReplyBook` verifies the current
sender, part and latest-five membership, including replayed clips, before each start
and after narration. Its optional `readable(partId)` filter comes from
`AndroidMmsInbox.readable`, which opens the part read-only and rejects missing or
empty audio. The inbox scan has no unrelated-message cutoff. `ReplyUnavailable`
skips an unavailable clip without marking it heard; refused audio focus does not
discard valid clips. A live refresh drops removed/off queues and revalidates idle
Listen/Play again without starting playback.

## T5 preview app-list seam (PLA-514)

`PreviewAppListStore` in `:device` preserves the existing credential-encrypted
`starter-list.txt` and atomically stores `app-order.txt` alongside it. The
production `PreviewHomeApps.read` mapper and T5 use the same stored order.
`PreviewAppRules` in `:policy` refuses removal/movement of live protected entries.
`PolicyRuntime.previewApps`, `movePreviewApp`, and `removePreviewApp` serialize
preview reads and changes with policy passes. Removal consumes a fresh
app-bound PIN grant through `PreviewPinAccess.authenticateRemoval`, rechecks the
live floor and cancellation, saves the starter list, and runs normal re-apply.
Only successful re-apply readback permits the Removed screen. Activity pause
revokes pending UI work. These preview seams do not change the full-MVP APIs.

T4 preview ([PLA-517](/PLA/issues/PLA-517)) adds `PreviewAppRules.canAdd` and
`AndroidPreviewHiddenApps.read`: hidden, installed, enabled launcher entries only,
with live browser/catalog, floor, Settings, Home, store and installer exclusions.
Unreadable facts yield no list. `PolicyRuntime.previewHiddenApps` / `addPreviewApp`
use the same dispatcher; `PreviewPinAccess.authenticateAddition` consumes a fresh
`preview:add` grant for that app. Save re-reads eligibility and the selected
version/signers, then `PreviewAppListStore.add` publishes appended order before
starter membership under the common lock. Failed membership restores old order;
a crash before membership leaves the app hidden. Only ready re-apply readback
permits Added. This preview does not change permissions or the locked/floor plan.

### Production-facing About and neutral identity (PLA-528)

`:plainphone` supplies `AboutUiState(versionName, versionCode, latest, sincePrevious, older, isDeviceSecure)` from package metadata and startup observation in Home preferences. `:ui` renders `ReleaseNoteUi` entries and browses Older versions without reading storage. `MoreUiState.pinHolderOnly` exposes the PIN host when there is no configured trusted identity; `AskForAppUiState.isSetUp` renders the truthful unavailable state. Voice controllers default to an empty people list; the runtime host supplies saved Family entries.

## App-request SMS slice (PLA-525)

`AppRequests` in `:policy` owns the five-attempt daily quota, validation, duplicate
attempt IDs, multipart result aggregation and interrupted-send uncertainty.
`RequestStore` and `RequestSmsPort` are its storage/transport ports. The `:device`
`RequestEngine` supplies one serialized controller per process, an atomic CE
`noBackupFilesDir/app-requests` file and explicit SMS sent-intent callbacks.
`RequestRecipient` is a separately configured app-request destination; it confers
no PIN authority and never comes from a guessed Family entry.

`:plainphone` owns `RequestsUiMapper`, More → `RequestsActivity`, the PIN-menu
history route and `RequestRecipientActivity`. The policy dispatcher consumes a
fresh PIN before viewing/changing the recipient. Pause invalidates the editor.
`:ui` `RequestsScreen` and `RequestRecipientScreen` take display state and emit
plain events; they do not send SMS, read storage or decide quota/authority.

### Preview permission page and add choices (PLA-552)

The self-managed preview uses `AndroidAppPermissions` / `AppPermissionReader`
(`:device`) to supply an exact-signer, version-fenced requested runtime inventory
and `PermissionGrants` readback to `PreviewAppsFlow` (`:plainphone`).
`appPermissionsUi` supplies `AppPermissionsUiState` to the read-only app page;
`appPermissionChoicesUi` supplies `PermissionsUiState` to T4c. The UI accepts
platform group labels for unknown groups, explicit read failures, and the
`previewAdd` copy variant (the preview does not implement timed openings).
`PermissionChoice.group` may be null when Android supplies an unfamiliar group;
its `label` remains display data, never grant capability.

The add port carries the selected snapshot and a complete group-choice map.
`PolicyRuntime` keeps fresh PIN, live protected-app/hidden-app eligibility,
`DeviceApprovalEffects`' permission window, `PermissionGrants` writes, hidden
readback, and final preview membership/apply on its existing serial dispatcher.
Only DONE plus matching readback can reach Added. A failed write retains its
requested group label even if the subsequent inventory is unreadable. No
supervised identity, credential migration, one-step self-managed edit, or
floor/role-holder permission action is introduced.

## Give-back executor

`GiveBackController.run(requestId, battery, retry)` consumes only an existing
TURN_OFF/COMMITTING operation. `GiveBackPort` inventories the actual preview DPM
writes and supplies idempotent restore/readback effects and successor checks.
`WaitingState.giveBack` stores the request-bound per-item results in the existing
device-protected authority record. `ReadbackGiveBackPort` applies the mandatory
unhide flush and refuses permission resets while hidden; `DpmGiveBackPort` owns
all new Android writes. `PolicyRuntime` routes committing turn-off before normal
boot, containment and update-policy writers. The test-build maturity refusal is
unchanged. See `app/tools/give-back/inventory.md` for the full write inventory.

## Play preview eligibility (PLA-620)

`ElevationController` may resolve the fixed Play package without a matching build
inventory. Settings, Approve and Update retain their inventory gate. Play still
requires fresh live input with VALID health, the system dialer, unlocked boot,
no conflicting approval or committing/rolling-back wait, self-managed credentials,
fresh PIN, countdown, explicit Start and the atomic debit/journal protocol.

`JointAuthorityStore.activeLimits()` uses `ElevationLimits()` only for readable
self-managed credentials with no recovery profile. Unreadable credentials and
profile-free supervised credentials remain closed; every stored profile retains
its own friction. This read does not create a profile or account identity.
The preview currently has no full policy-store provisioning: see the Play
emulator policy-health preflight before wiring an opening path.

The preview uses `PreviewStarterPolicyStore` only in Play's fresh
`LivePolicyInputAssembler` eligibility read. It strictly validates the existing
CE starter file under the writer's lock, is read-only, and returns either VALID
or BOTH_INVALID. Home's lenient reads and every preview policy application stay
unchanged. No full-engine policy is provisioned or applied.

`BootReapply.sessionFactory` lets the app provide the one shared session
controller. `PreviewSessionJournal` uses the legacy boot journal before PIN
enrollment and the policy-owned `ElevationSessionJournal` projection after any
combined authority record exists; damage never falls back to the legacy record.
`PolicyRuntime` constructs `PlaySessionDispatcher` once, uses its session controller
for boot revocation, and serializes PIN requests, Start, end events and package
intake. `PreviewPlayPlan.open` changes only Play visibility and lock-task membership
in the existing preview plan. `ReadbackPlayEffects.refresh` applies that same plan
to new package events without opening another session.

`PlaySessionView` supplies live countdown, Start-window and quota values to
`playOpeningUi`; `PlayActivity` owns the fresh PIN, ephemeral UI and navigation.
`PlayEvents` closes on screen off and ringing and checks running-time deadlines;
`PlayDeadline` is the alarm backstop. No debug receiver or debug source can create
or shorten a countdown. Emulator time acceleration is in androidTest only.
