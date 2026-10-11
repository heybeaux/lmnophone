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
- `AppApprovalEffects`: `openPermissionWindow(identity): Boolean` removes the pending app from applied lock-task access and Home, reads those exclusions back, then unhides with synchronous flush and live readback. `closePermissionWindow(identity): Boolean` re-hides and reads back; it never restores access. Policy calls `setPermission` only inside this window, all DENY before ALLOW, and requires `readPermissions` to work both visible and hidden. Every attempted open is followed by close, including failure. Pending PREPARING/EDITING recovery hides before startup gates; both ports enforce the live never-hide guard. Lane D implements these new effects; the final `applyPolicy` activation contract stays the same.
- `SessionController`: the supervised-session and elevation contract (start, end, revoke-on-start from the journal). Journal and policy persistence go through interfaces that lane D implements: `JournalStore`, `PolicyStore`.
- `PinVault`: PIN and recovery-code logic.
  - Separate counters and the exact lockout schedule.
  - Hashing goes through a `KeyDerivation` interface (PBKDF2 is implemented by D).
  - Supports one or two trusted people.
- `WaitingChangeController`: the 24-hour waits (turning off, trusted-people changes, mode switches) and cancel.
- `ElevationController` (self-managed mode), `PlaySessionMachine`, `MonthlyCheck`, `EmergencyFloor` and baseline rules, `NoticeLine` priority.
- `TimeWindowQuota`: the generic "open for a few minutes" quota. The **web window** (5×/day × 5 min, extendable) and **Wi-Fi sign-in** (3 per 24 h) are built behind a flag that stays off. Their OpenSpec change isn't approved yet; Wi-Fi sign-in comes after the MVP core.

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
- Optional communication-count UI: `HomeUiState.missedCalls` / `unreadTexts` are
  nullable host-supplied integers. Host passes null immediately when off or
  unavailable; UI shows only positive values, capped visually at 99+.
- `PhoneSettingsScreen(PhoneSettingsUiState, onEvent)` is the PIN-free destination
  for `MoreEvent.OpenPhoneSettings`. State has `showCommunicationCounts = false`,
  `missedCallsUnavailable = false`, `unreadTextsUnavailable = false`; events are
  `Back` and `ShowCommunicationCountsChanged(enabled)`. Host owns navigation,
  saved preference, permission handling and fresh count reads. Unavailable
  status lines show only while enabled. This slice adds no data source.
- Covers: home (with the single notice line), More, ask for an app, blocked link, Wi-Fi messages, the trusted-person screens ("Who are you?", PIN, approvals, permissions, people, waits and cancel), Plainphone settings, setup, turning off, About, and "What Plainphone can't stop".

**`:device` / `:plainphone` (lane D):** implements `JournalStore`, `PolicyStore` and `KeyDerivation`. It also provides:
- `PolicyApplier`, which applies a `PolicyPlan` through DPM and reads it back;
- `PackageFactsSource`;
- the SMS sender;
- view-models mapping engine output to `XUiState`.

**What the `:ui` screens leave to lane D** (from the lane B slice 2 reviews, PLA-109 and PLA-110). Each one needs a test in lane D's integration work:
- **Send and retry may arrive twice.** A greyed-out button doesn't stop two taps that land before the screen redraws: the Verifier showed both `BlockedLinkEvent.Send` and the request `Retry` events can fire twice. The SMS sender must ignore a repeat of a send that is already under way, for link confirmations, app requests and retries.
- **Names are cleaned when saved.** Trusted-person names reject or strip bidi controls (U+202A–202E, U+2066–2069, U+200E, U+200F, U+061C), zero-width characters and line separators when saved. `:ui` also strips bidi controls when it shows a name, but that only backs this up.
- **Hosts that aren't plain ASCII show as punycode, with the unusual-letters warning.** `pay​pal.com` (with a zero-width space) must not reach the screen as "paypal.com".

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
