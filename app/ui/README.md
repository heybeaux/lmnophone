<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Plainphone UI

The merged first slice provides the Lexend theme and U1 home screen. Slice 2 adds
U4 More, U5 app requests and results, U6 blocked-link states and the approved U7
Wi-Fi messages. Settings controls, U2/U3 notices, U8 About, U9/U10 waiting/opening
details, trusted-person flows and setup remain later slices. The source of truth is `docs/ux/design-tokens.md` and
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
  The list starts at its bottom, with the last supplied app above Messages and
  a 12 dp gap separating the two groups. Positional list keys tolerate duplicate
  IDs. The fallback order preserves the three-line notice by moving the clock
  inline before reducing the notice to two lines.

The upstream Lexend file is unchanged in `res/font/lexend.ttf`. Its licence ships
in `assets/fonts/OFL.txt`; the `lexend_notice` string is available for About.

Home uses centered plain-text More with a 64 dp minimum touch target and 24 dp
side margins. No subtitle, caret, idle border or decorative Home divider rules;
pressed/focus feedback and `OpenMore` remain. See `evidence/pla285-calm-home.md`
for the approved visual revision and before/after evidence.

## Verification

From `app/`:

```sh
./gradlew :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug
./gradlew :ui:assembleDebugAndroidTest
```

The 83 committed goldens include 18 home cases and 22 user-screen cases at font
scales 1.0 and 2.0 on a
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
The suite also invokes Android accessibility scrolling to reveal earlier apps;
user-screen cases cover name joins, host validation, IME entry, history/retry,
blocked-link isolation and pinned Wi-Fi actions. Never use a physical device or
an unqualified connected-device Gradle task for this lane.

## Slice 2 host contract

All five entry points in `org.plainphone.ui.user` use plain Kotlin state/events:
`MoreScreen`, `AskForAppScreen`, `AppRequestResultScreen`, `BlockedLinkScreen`, and
`WifiMessageScreen`. Wrap them in the theme and supply the safe content area.
Task bodies scroll; primary and secondary actions remain below the scroll area.
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
- A partly visible preceding name is still not guaranteed at exact row-height
  multiples; the 560/540 dp stress cases remain below the supported minimum.
- Date/relative-day consistency remains the host's responsibility: both values
  must come from the same date snapshot. UI deliberately does not read the clock.
  Locale-specific short month names (including British “Sept”) are retained;
  short-date strings wrap rather than shrink.
- Some verbatim long confirmation titles take three lines at 200% on 360 dp.
  Copy is preserved and the body scrolls; no text is shrunk or ellipsized to meet
  the tokens' two-line title target. This copy/layout conflict needs UX review.

## TalkBack emulator inspection (2026-10-09)

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
## Optional communication counts

`HomeUiState.missedCalls` and `unreadTexts` accept fresh reliable host values.
Pass null when switched off or unavailable. Zero (and defensive negative input)
shows no number; positive values show 1–99 or 99+. TalkBack receives the full
count through plural resources, in one row action. Other apps cannot take counts.
The measured bottom zone includes any wrapped number before layout fallbacks.

Route `MoreEvent.OpenPhoneSettings` to `PhoneSettingsScreen`. Its state defaults
off; `ShowCommunicationCountsChanged(enabled)` requests a host update and `Back`
returns to More. No local toggle/count persistence or data reads. The host feeds
back saved state and availability; flags only show explanatory lines when on.
No PIN is involved. This minimal screen contains only Back, the switch, coverage
and optional status lines; broader settings remain later work.

`CommunicationCountsTest` checks boundary values, exact spoken plurals, one
control per row, host-owned switch events, More navigation, and actual text/row
bounds across 100/130/150/180/200% × no/long notice × off/both 99+.
`CommunicationCountsScreenshotTest` adds 65 goldens on 360×640 with system-bar
space: 50 Home states and 15 settings states. See `evidence/pla292-counts.md`.
