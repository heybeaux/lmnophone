<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Plainphone UI

The merged first slice provides the Lexend theme and U1 home screen. Slice 2 adds
U4 More, U5 app requests and results, U6 blocked-link states and the approved U7
Wi-Fi messages. Settings controls, U2/U3 notices, U8 About and setup remain later slices. Slice 3 adds trusted-person,
self-managed and U9/U10 waiting/opening screens. The source of truth is `docs/ux/design-tokens.md` and
`docs/ux/flows-user.md` at the repository root.

## Host contract

- Wrap `HomeScreen(state, onEvent)` in `PlainphoneTheme` and give it the safe
  content area after Android's status/navigation bars. The host owns bar colours
  and dark-icon configuration.
- Supply formatted time, full date, short date, ordered apps, and at most one
  selected `HomeNotice`. Notice priority and clocks belong to the view-model/policy
  lanes; this screen only formats the supplied notice using string resources.
- App IDs are opaque and should be unique. Duplicate IDs remain renderable;
  `OpenApp(id)` returns the supplied ID unchanged for either entry.
  `OpenPhone` means the ordinary system dialer; there is no home emergency event.
- `MonthlyCheck` takes the trusted people's names as a list, in supplied order.
  The UI joins names using resources; an empty list hides the dependent notice.
  `TurnOffWaiting` takes the formatted clock time plus `HomeNoticeDay` (date and
  supplied days-from-today). The UI adds tomorrow, a weekday for days 2–6, or a
  short date for day 7 onward. Same-day values display only the time. The host
  owns date arithmetic and refreshes; the screen never reads the clock.
- `OpenNotice` opens the corresponding detail screen, including the full notice
  text and any cancellation action. The home box may ellipsize its visible text;
  its text semantics retain the full label.
- `HomeNotice.Cancelling` displays “Cancelling…”. Its unchanged `OpenNotice`
  event must route to U9c, Still cancelling. The host owns saving and retrying.
- Only the other-apps list scrolls on supported viewports. Measurement probes are
  excluded from the semantics tree, and the chosen layout supplies the controls.
  Short lists sit at the bottom. Overflow starts at the first supplied app,
  with later apps revealed by scrolling upward. A 12 dp gap separates the list
  from the top zone and another separates it from Messages. Positional list keys tolerate duplicate
  IDs. The fallback order preserves the three-line notice by moving the clock
  inline before reducing the notice to two lines.

The upstream Lexend file is unchanged in `res/font/lexend.ttf`. Its licence ships
in `assets/fonts/OFL.txt`; the `lexend_notice` string is available for About.

## Verification

From `app/`:

```sh
./gradlew :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug
./gradlew :ui:assembleDebugAndroidTest
```

Home has 19 fixtures at font scales 1.0, 1.3, 1.5, 1.8 and 2.0 (95 goldens) on a
360×640 device, reserving 32 dp for the status bar and 48 dp for navigation.
These cover cancelling, eight apps, duplicate IDs, singular/plural minutes,
zero/one/two trusted people, and same-day/tomorrow/weekday/short-date wording.
Three additional 200% fixtures at heights 560, 540 and 480 exercise the
two-line notice, half-row and whole-page fallbacks below the supported minimum.
The inline clock with a three-line notice is covered at 360×640 and 200%.
`verifyPaparazziDebug` compares pixels against those files; ordinary unit-test
execution alone is not the golden comparison command. To intentionally refresh
the files, run `:ui:recordPaparazziDebug`, inspect every changed image, and submit
the changes for review. Fixtures contain only synthetic display data.

`HomeScreenTest` is an emulator instrumentation test. Install the generated
`ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk` on a dedicated
lane-owned emulator using `adb -s <owned-emulator> install -r -t ...`, then run:

```sh
adb -s <owned-emulator> shell am instrument -w -r \
  org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
```

Run on a 360×640 dp emulator. Home and user-screen tests force 200% text using
`DeviceConfigurationOverride.FontScale(2f)`, independent of system settings.
They check bottom anchoring, pinned actions, scroll behavior, duplicate IDs,
cancelling, absence of an emergency control, event routing, a unique full
accessible notice, and reaching pinned actions below the minimum viewport.
The Home bounds regression checks eight notices (including all four turn-off date
forms) at those five scales: initial first-row clearance, full reveal and taps of
four apps, and pinned Messages/Phone/More. The suite also invokes Android
accessibility scrolling to reveal later apps;
user-screen cases cover name joins, host validation, IME entry, history/retry,
blocked-link isolation and pinned Wi-Fi actions. Never use a physical device or
an unqualified connected-device Gradle task for this lane.

## Slice 2 host contract

All five entry points in `org.plainphone.ui.user` use plain Kotlin state/events:
`MoreScreen`, `AskForAppScreen`, `AppRequestResultScreen`, `BlockedLinkScreen`, and
`WifiMessageScreen`. Wrap them in the theme and supply the safe content area.
Task bodies scroll; actions normally remain pinned. The approved large-title
fallback described below scrolls the whole task page.
The request screen also consumes IME insets. More is a scrolling menu with no
primary action. Resource copy comes from U4–U7; names are synthetic in fixtures.

- `MoreMode.Trusted` receives names in setup order and the host-selected request
  recipient. UI joins the menu names with “and” and the explanation with “or”.
  Empty names suppress the dependent trusted-person row. The host routes one
  person to PIN and two people to Who are you. Self-managed rows emit distinct
  Add app / Your settings events; the host supplies the wait and requires PIN.
- `OpenEmergencyDialer` belongs only to More. The host must use the system dialer
  with the emergency number prefilled, never an automatic call. The number uses
  nonbreaking hyphens. No screen in this module launches an activity.
- For requests, the host enforces the 280-character limit, at least one letter,
  five-per-day quota, fixed recipient, save/send/retry and rate limits. Each edit
  emits `MessageChanged`; feed the accepted value back into state. `canSend` is a
  host decision, not inferred from text. Supply `charactersRemaining` only after
  200 characters. History is supplied newest-first and displays at most ten.
  Requests are plain text, never app identities or approval instructions.
- The sent result takes the saved request history as well, because the approved
  copy promises delivery status below. Supply that history with the sent result.
  Only a supplied Delivered status says the recipient's **phone** got it; no
  state claims they read it. Retry events carry opaque record IDs for host checks.
- `BlockedLinkUiState` accepts only the sanitized registrable host / ASCII host
  and sanitized SMS preview from the link/device adapters. Never pass raw URL
  input or derive facts from text here. The host supplies TooLong for input over
  2,000 characters, handles unusual letters and strips userinfo, query and fragment
  before confirmation. There are no selectable links, clipboard actions, WebViews,
  launch APIs or admin navigation. Send is emitted only from confirmation. Failed
  sharing offers OK, not a retry queue. The host owns isolation and drops link data.
- Wi-Fi messages expose Forget network and OK only. They retain the approved
  sign-in prohibition; the unapproved optional sign-in window is not exposed.

Neither a displayed receipt nor a button event proves a policy change, Android
permission effect, SMS delivery or fresh authority. Those belong to the host.
Permission edit/withdrawal and typed NoticeLine handoffs recorded on the policy
issue apply to later screens; this slice does not wire those controllers.

## Carried home review notes

- Added zero/one-app goldens and exercised the “and”/“or” joins through More at
  both text sizes and in emulator assertions. Weekdays use FULL_STANDALONE.
- The positional-key comment now states the accepted focus/press-state tradeoff.
- The 560/540 dp stress cases remain below the supported minimum. At 360×640,
  overflow opens with a complete first row; a partial following row can indicate
  more content. Every app can be fully revealed and tapped by scrolling.
- Date/relative-day consistency remains the host's responsibility: both values
  must come from the same date snapshot. UI deliberately does not read the clock.
  Locale-specific short month names (including British “Sept”) are retained;
  short-date strings wrap rather than shrink.
- Some verbatim long confirmation titles take three lines at 200% on 360 dp.
  Copy is preserved and the body scrolls; no text is shrunk or ellipsized to meet
  the tokens' two-line title target. UX approved whole-page scrolling when the
  full title and one body line cannot fit above pinned actions (PLA-130).

## Historical TalkBack emulator inspection (2026-10-09)

This predates the Home overflow correction in PLA-156. The current list starts
at the first app and uses forward accessibility scrolling for later apps;
`HomeScreenTest` verifies that action on an emulator.

On the lane-owned API 35 emulator at 360×640, 200% Compose text, TalkBack
15.0.0.639625893: previous-item gestures moved from App 8 to the partially
visible App 7, then scrolled to earlier apps (down through App 2 in the captured
sequence). The clock/notice and Messages, Phone, More remained pinned. Evidence:
[`talkback-app7.png`](evidence/talkback-app7.png) and
[`talkback-earlier-app.png`](evidence/talkback-earlier-app.png). These synthetic
emulator frames contain no account or contact data. The green focus outline
belongs to Android's TalkBack overlay, not the Plainphone palette.

The first inspection used a sleeping Compose-test fixture, freezing its animation
clock and making automatic accessibility scrolls appear broken. Repeating with
`compose.mainClock.advanceTimeByFrame()` while driving emulator-native previous
item gestures demonstrated the original list works. No list-layout rewrite is
included. The incorrect reverse-axis-only assertion from the intermediate red
checkpoint was replaced with a behavior check through Android's actual
`ACTION_SCROLL_BACKWARD`; it reveals App 6 without moving Phone off screen.

For repetition, keep the Compose test clock advancing during interactive TalkBack
inspection. Shell-injected touch events may bypass accessibility exploration;
this check used the dedicated emulator's native touch events. No physical phone
was used. This verifies traversal/scrolling in the stated fixture, not every
TalkBack version or an end-to-end integrated launcher.

## Slice 3: trusted-person presentation

The `trusted` package exports state/event entry points for identity selection,
PIN/recovery, the trusted/self-managed menu, app review and permissions, app
ordering/details, requests, people changes, waiting changes, session checks,
Plainphone updates, self-managed openings/waits, history, and turning off.
Every entry point has 360×640 previews at 100% and 200%. All static copy is in
`trusted_strings.xml`, `trusted_extra_strings.xml` and `trusted_completion_strings.xml`, from T1–T8, P1/P3–P6,
M1–M8, U9/U10 and L4. The setup steps reused by M2c/M6 remain the setup slice;
Continue emits navigation intent, not completed enrollment.

Host obligations:

- Supply `AdminContext(userName, trustedPersonName)` for trusted-person screens;
  null `trustedPersonName` is self-managed. A null context on waiting-change
  screens is the PIN-free user route. No screen reads storage, starts a timer,
  opens an intent, sends a text, hashes a PIN, or changes policy.
- Admin activities must be non-exported and use `FLAG_SECURE`, with backup
  disabled. Apply those before composing these screens. The UI cannot enforce
  the activity boundary. PIN/recovery input is masked, host-owned, and never
  saved with `rememberSaveable`. Clear it on exit; show a new recovery code only
  once, after the atomic credential write succeeds.
- Choose identity before PIN entry for two people; skip selection for one or
  self-managed. Check only the chosen opaque ID. Supply lockout time/counter
  results; there are no counters or checks in Compose.
- Supply installed app identity and reviewed-version status independently of
  request text. Request text is plain, unmodified text, never a link. Requests
  cannot stand in for installed package/signing-certificate authority.
- Decide permission availability and `canContinue` in the view-model. Choices
  begin null; an unavailable permission has no buttons. UI events contain the
  supplied group ID and choice. The host requires fresh authority and performs
  the permission/app change atomically. Permission choices stack at large text.
- Supply menu rows, notices in priority order, app order, boundary-enabled move
  buttons, and allowed people-change rows. Never offer removal of oneself or
  the last person. Messages and Phone use `alwaysOn`, with no move/remove action.
- Supply `canSkip` only for a pending supervised change; never for self-managed.
  Supply `canStop` only for a stuck change that can still be stopped. The host
  owns those policy decisions and must recheck them when an event arrives.
  Recovery used to reset a PIN never skips a wait; the skip screen is a distinct
  route naming the pending change. Its final event is not a committed skip.
- Set sending before save/send/confirm work and reject duplicates at the host,
  including taps arriving before recomposition. New submit controls disable
  while sending. U5/U5c/U6b show `Sending…`; leaving a saved request stays enabled.
- Supply `Cancelled` only after durable cancellation, `Committing` when cancel
  is no longer possible, and `StillCancelling` on a failed save. UI never infers
  one from another. User cancellation has no PIN event. Supplied dates and time
  estimates use the policy clock; reboot/battery behavior remains outside UI.
- Session Continue routes to fresh PIN where required. Only host readback may
  select Restored. P2 is an Android notification owned by lane D, not an overlay
  drawn by these screens. Self-managed variants display the supplied closing
  time. Monthly-check completion requires its separate final confirmation.
- History is supplied newest-first for 90 days, with no URLs, app content or
  message text. Display strings for action/metadata/restored rows must come from
  the flow copy. Credential attribution and recipient status are rendered
  separately; records have no undo/delete action.
- Enrollment steps, Google account checks, address validation/update transport,
  and installation remain host/setup integration. `Off` is supplied only after
  Device Owner is gone. Failed turning off offers retry/details, never a wipe.

Validation: the send-label tests ran first on the dedicated API 35 emulator
(`emulator-5740`) and all three failed before the fix. New screen fixtures failed
compilation before the screen entry points existed. The trusted goldens cover
all fixture stages at 100% and 200%; named variants also cover
1.3/1.5/1.8/2.0 × one/two people × short/long synthetic names. They reserve 32 dp
status and 48 dp navigation bars, as the earlier goldens do.

UX resolved the checkpoint questions in `7ab89de` (PLA-130). Self-managed PIN,
permissions, app removal, recovery-code lockout and stuck handover use that copy.

- `RecoveryLocked` hides the code field. Supply `RecoveryLockoutKind` for PIN
  available, PIN waiting, or the supervised Skip route, plus formatted waits and
  helper name. `EnterPin` routes back to PIN; `Ok` on Skip returns to the pending
  change. Counters, delays, persistence and SMS decisions remain in the host.
- Self-managed app details show permission choices without a direct edit action.
  Removal routes to fresh PIN, then Removed; no wait or opening is consumed.
  Re-adding uses the hidden-app wait/opening path. `waitMinutes` is host supplied.
- A stuck self-managed handover retains the user's current authority. Set
  `changeToSelfManaged` for the opposite route's Stop confirmation. A Stop event
  still requires the current authority's PIN. No stuck/stopped SMS is sent when
  there are no current trusted people; the UI never sends SMS.
- Every task screen measures its real band/title/actions at the current bounds
  and text size. If the full title plus a body line cannot fit above the actions,
  band/body/actions share one scroll area. Secondary stays above primary.
  Otherwise actions remain pinned. Scroll and sending updates never switch the
  layout; screen identity, action presence or display/font/rotation changes
  remeasure. Keyboard padding is applied after this decision, so opening the IME
  keeps the focused field in the same composition. New tasks
  open at the top. Android's native scrollbar occupies the empty edge gutter and
  is excluded from accessibility; Compose supplies accessible scrolling.

See `evidence/slice3-ready.md` for the base slice checks and
`evidence/slice3-review-fixes.md` for the real-keyboard and footer regressions. The older
checkpoint report records the pre-answer state only.
