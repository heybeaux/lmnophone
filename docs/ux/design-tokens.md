# Plainphone design tokens: black and white

Owner: Product & UX Designer. Task: [PLA-36](/PLA/issues/PLA-36). Status: **ready for the MVP build
([PLA-15](/PLA/issues/PLA-15))**, revised after the [PLA-37](/PLA/issues/PLA-37) review. Mockups: [mockups/plainphone-screens.html](mockups/plainphone-screens.html).

Beaux's brief (Telegram 4485): *"Home screen/screens are black and white only. Simple. Effective."* The look borrows
the font, greys and spacing from Generosity Catalyst (GC) and the layout from Beaux's reference image: a short
text-only list of apps in large type, low on the screen where a thumb reaches, with lots of white space above.

This file sets the look. The words stay as written in the flow files ([README.md](README.md)); nothing here changes
copy, except the approved Home revision below and where a flow said "red" (see
[What changed in the flow files](#what-changed-in-the-flow-files)).

**Home revision, [PLA-285](/PLA/issues/PLA-285), approved by Beaux:** center plain-text "More"; remove its
subtitle, caret and border, and use whitespace instead of decorative horizontal rules between Home elements.
The 64 dp touch target, accessible name and More navigation remain. Other screens keep their existing design.

## The rules

1. **Black, white and grey only**, on every screen Plainphone draws: home, More, the user's screens, trusted-person
   mode, setup, the blocked-link and Wi-Fi screens, and the off flow.
2. **No colour.** There is no "accent", no red for danger, no green for success. If someone proposes colour, it
   goes in the [exceptions table](#colour-exceptions) with a written reason, and the Lead decides. Today the table
   is empty.
3. **Never use colour alone to mean something.** That is already true, since there is none. State is shown by fill
   (black or white), a word, or a tick (✓).
4. **No app icons on the home screen.** Names only, as text. Plainphone's own screens use no icons either; the only
   glyphs are `›` (opens a page), `‹` (back), `✓` and `^`.
5. **No Emergency button on the home screen** (Beaux, Telegram 4487: "I don't want users bumping it by
   accident"). 9-1-1 is always reachable, never from one accidental tap on the home screen. See
   [Calling 9-1-1](#calling-9-1-1).
6. **Readable first.** Every pair of text and background meets WCAG 2.2 AA at the sizes below. Everything scales
   with Android's text size up to 200% (sizes are in `sp`).

## Font: Lexend

| | |
|---|---|
| Family | **Lexend** (Bonnie Shaver-Troup, Thomas Jockin and others). Designed to be easier to read. |
| Licence | SIL Open Font License 1.1, which allows bundling the font in any software, including an Apache-2.0 app. Its conditions, and what the build does about each: **(1)** the copyright line and licence text go with the font: ship `OFL.txt` in the app and list "Lexend – SIL Open Font License 1.1" in About this phone's open-source notices; **(2)** the font is never sold on its own, and Plainphone doesn't sell it; **(3)** a modified font can't use the Reserved Font Name "RevReading Lexend": ship the upstream file unchanged (no subsetting), and if it's ever subset, rename it; **(4)** the licence covers the font only, not Plainphone's code, and Plainphone's Apache-2.0 licence doesn't cover the font. |
| File | `Lexend[wght].ttf`, one variable file, weight axis 100–900 (google/fonts `ofl/lexend`). A copy and its licence are in `mockups/fonts/` for the mockups. |
| Weights used | **400** (Regular) for body text and the clock. **500** (Medium) for app names, titles and buttons. Nothing else. |
| Android | Put the file in `res/font/`, define a `FontFamily` with the 400 and 500 instances, set it as the app theme's font. Don't download it at runtime (Plainphone has no network for that). |
| Fallback | If the file fails to load: Android's default sans-serif, same sizes. Never a serif. |
| Numbers | Lexend's default figures. The clock uses tabular figures (`font-feature-settings: "tnum"`) so it doesn't jiggle. |

Beaux's reference image uses a serif. He asked for GC's font, so the default is Lexend. If he wants the serif look
after seeing the build, that is a one-line theme change.

## Palette

| Token | Hex | GC name | Use |
|---|---|---|---|
| `ink` | `#000000` | Black | Text, app names, filled buttons, focus ring, selected choice |
| `paper` | `#FFFFFF` | White | Screen background, text on black |
| `ink-2` | `#545454` | Gray 600 | Second lines ("Opens the dialer"), body notes |
| `ink-3` | `#757575` | Gray 500 | Borders of fields and outline buttons, the `›` and `^` glyphs, the time on the request list. **On white only.** |
| `line` | `#AFAFAF` | Gray 400 | Disabled text only. Never for anything the reader must read. |
| `fill-2` | `#E8E8E8` | Background Tertiary | Pressed and selected rows, dividers, disabled button fill |
| `fill-1` | `#F6F6F6` | Gray 50 | Boxes inside a screen: the notice line, the host box on the blocked-link screen, the request card, the SMS preview |

**Left out on purpose:** GC's blue `#276EF1`, purple `#7356BF`, green `#3AA76D`, orange `#ED6E33` and their tints.
GC's blue focus outline becomes a black one.

### Contrast (WCAG 2.2; AA needs 4.5:1 for text, 3:1 for large text and control borders)

| Text ↓ / background → | `paper` #FFF | `fill-1` #F6F6F6 | `fill-2` #E8E8E8 | `ink` #000 |
|---|---|---|---|---|
| `ink` #000 | 21.0 ✓ | 19.4 ✓ | 17.1 ✓ | – |
| `ink-2` #545454 | 7.6 ✓ | 7.0 ✓ | 6.2 ✓ | 2.8 ✗ |
| `ink-3` #757575 | 4.6 ✓ | **4.3 ✗** | 3.8 ✗ | 4.6 ✓ |
| `line` #AFAFAF | 2.2 ✗ | 2.0 ✗ | 1.8 ✗ | 9.6 ✓ |
| `paper` #FFF | – | – | – | 21.0 ✓ |

So:
- `ink-3` text sits on white only. On `fill-1` boxes, second lines use `ink-2`.
- `line` is for disabled text, which WCAG exempts, and nothing else.
- On black (primary buttons, the trusted-person band), text is `paper`. A second line on black is also `paper` at a
  smaller size, not grey.

### Dark theme

Not in the MVP. Plainphone's screens stay white when Android's dark theme is on (`forceDarkAllowed=false`, dark
status-bar icons kept). If Beaux asks for a dark home screen later, it is the same tokens swapped (`ink`↔`paper`,
`fill-1` → `#1A1A1A`), and it needs its own contrast check.

## Type scale

**Every size is in `sp`, including the clock,** so all text follows Android's text size (calm-home-screen: "follow
the system font scale"). Line heights are in `sp` too, so they follow the same curve. Nothing Plainphone draws gets
smaller when the text size goes up. When space runs short, the layout changes instead (see
[When the screen is short](#when-the-screen-is-short)).

**How big things really get.** Android 14 and later scale large text less than small text. The 200% column below
comes from Android 15's own table (AOSP `core/java/android/content/res/FontScaleConverterFactory.java`,
`android15-release`, key `2f`: 8→16, 10→20, 12→24, 14→26, 18→30, 20→34, 24→36, 30→38, 100→100 sp→dp), with straight-line
interpolation between points (`FontScaleConverterImpl.java`). So a 32 sp app name is about 40 dp at 200%, not 64.
Use these numbers for layout checks; the build confirms them on the phone.

| Token | Size / line at 100% (sp) | At 200% (dp) | Weight | Colour | Used for |
|---|---|---|---|---|---|
| `clock` | 64 / 72 | 68 / 75 | 400, tabular | `ink` | Time on the home screen |
| `date` | 18 / 24 | 30 / 36 | 400 | `ink-2` | Date under the clock. At 200% it shortens to "Thu 8 Oct" |
| `app` | 32 / 40 | 40 / 47 | 500 | `ink` | App names on the home screen |
| `title` | 28 / 36 | 37 / 43 | 500 | `ink` | Screen titles. Written to fit 2 lines at 200% on a 360 dp screen. A long name may push one to 3 lines; that is allowed. Never cut a title or shrink it |
| `row` | 22 / 28 | 35 / 37 | 500 | `ink` | Menu rows (More, Plainphone settings, the trusted-person menu) |
| `body` | 18 / 26 | 30 / 37 | 400 | `ink` | Body text on every screen |
| `button` | 18 / 24 | 30 / 36 | 500 | `paper` on `ink` | Primary button labels |
| `small` | 16 / 22 | 28 / 35 | 400 | `ink-2` | Second lines under rows and buttons ("Opens the dialer" in `paper`), hints, counters |
| `host` | 24 / 32 | 36 / 40 | 500 | `ink` | The link's host on the blocked-link screen (not monospace; Lexend is clear enough, and the ASCII rule in U6 handles look-alikes) |

Nothing is smaller than 16 sp. No italics. No all-caps (harder to read). Underline only for text links.

## Spacing and shape

4 dp grid, as in GC.

| Token | Value | Use |
|---|---|---|
| `s1` | 4 dp | Between a row's name and its second line |
| `s2` | 8 dp | Inside small groups |
| `s3` | 12 dp | Between list rows on menus |
| `s4` | 16 dp | Box padding; between paragraphs |
| `s6` | 24 dp | Screen side padding; between a title and body |
| `s8` | 32 dp | Between sections |
| `s12` | 48 dp | Large gaps, e.g. above the app list when there's room |
| `radius-box` | 16 dp | Boxes and cards (GC's card radius) |
| `radius-pill` | 999 (full) | Buttons and text fields |
| `hairline` | 1 dp `fill-2` | Dividers between menu rows |
| `border` | 1.5 dp `ink-3` | Outline buttons and text fields (3:1 or better on white) |
| `focus` | 2 dp `ink` outline, 2 dp gap | Keyboard and switch-access focus (GC's blue ring, made black) |

**Touch targets** are at least 48 × 48 dp; home-screen app rows are at least 56 dp tall. **Shadows:** none. Dialogs
and sheets use a `hairline` border instead. **Motion:** fades only, 150 ms, and none when Android's "Remove
animations" is on. No bounce, no parallax, no animated clock.

## Components

### Home screen (U1)

Two zones. The top zone holds the time, the date and, when one applies, the notice line; the app list sits low,
where a thumb reaches. There is no Emergency button and nothing filled: the screen is black text on white.

```
┌──────────────────────────────┐
│ ▔▔ status bar (Android) ▔▔   │
│ 10:42                        │  clock
│ Thursday 8 October           │  date
│                              │  32 dp
│ ┌──────────────────────────┐ │
│ │ Ask Sam to do the monthly│ │  notice line, only when one applies (fill-1 box)
│ │ phone check            › │ │
│ └──────────────────────────┘ │
│        (white space)         │
│ Spotify                      │  other apps, in {tp}'s order; scrolls if long
│ Slack                        │
│ Camera                       │
│ Messages                     │  always here, pinned
│ Phone                        │  always here, pinned, nearest the thumb; 9-1-1 is dialled here
│             More             │  centered plain text; 64 dp touch target
└──────────────────────────────┘
```

- **App names:** `app` style, left-aligned, 24 dp from the edge, rows 56 dp minimum with `s3` between. The whole
  row is the touch target, edge to edge. No icon, no divider, no badge.
- **Order:** the other apps in the order the trusted person set (or the user, in self-managed mode). Then
  **Messages**, then **Phone**, always last and always visible. This follows the reference image, which ends the list
  with Messages and Phone, and it puts the two everyday apps nearest the thumb.
- **Scrolling:** only the other apps scroll, inside their own area, upward. The clock, the date, the notice line,
  Messages, Phone and More never scroll off. If space runs short, see
  [When the screen is short](#when-the-screen-is-short).
- **More:** "More" in `row` style, centered horizontally and vertically in the existing full-width inset
  touch target (24 dp side margins, 64 dp minimum height). No subtitle, caret, border or idle fill at any text
  size. Keep the normal pressed and keyboard-focus feedback. The text supplies its accessible name. Tapping
  opens More (U4); there is no search field or swipe-up drawer.
- **Home separators:** whitespace only. No decorative horizontal divider rules between the clock, notice,
  apps, Messages, Phone and More. The conditional notice keeps its existing `fill-1` box.
- **Notice line:** a `fill-1` box, `radius-box`, `body` text in 500 weight, `›` at the end in `ink-2` (not `ink-3`,
  which is for white only). At most one, chosen by the priority order in [U1](flows-user.md#the-notice-line). At
  most 3 lines; if longer it ends with "…", and the whole box is the touch target, so the full text and any Cancel
  are on the screen it opens.
- **No** wallpaper, widgets, dots, badges, search or suggestions.

#### When the screen is short

**Smallest supported screen: 360 × 640 dp**, counting Android's status and navigation bars. The target phone is
412 × 915 dp. The same rules apply when someone raises Android's *Display size*, which makes every screen smaller in
dp. The build checks the largest Display size together with 200% text on the target phone.

The other-apps area must always show **at least one full app row (56 dp)**. When it can't, these steps apply in
order, each only if still needed:

1. The time and date go on one line: the time in `title` style, the date beside it in `date` style ("Thu 8 Oct").
   Both keep growing with text size; they just stop stacking.
2. The notice line is cut to 2 lines (it still ends with "…" and `›`).
3. The other-apps area may shrink to half a row (28 dp). It still scrolls; the cut-off name shows there's more.
4. Below the smallest supported screen only: the whole home screen scrolls as one page. This is the one case where
   Phone and Messages can need a scroll, and it is outside what Plainphone supports.

**Never:** shrink any text below its scaled size, or shrink Messages, Phone or More.

**Why the clock gives way before the notice** (PLA-47 review, 2026-10-09). The notice is how the person learns that
something about their phone is about to change, and a 2-line cut can remove the words that say so ("A change to how
this phone is…"). The clock still reads at a glance on one line. So the clock stops stacking first.

**When the notice is cut** (at 3 lines, or 2 at step 2), "Tap to cancel." is often the part that goes. That is
acceptable, because the meaning is in the first words. Three things still have to be true:
- the `›` and the whole box still show it can be tapped;
- TalkBack reads the **full** line, never the cut one;
- the screen it opens (U9) shows the full sentence and the Cancel button.

**Check at 360 × 640 dp and 200%, with the longest notice showing.** Measured in mockup frame 10 (a browser
render using the 200% sizes above, so illustrative):

| Part | dp |
|---|---|
| Top 24 + time 75 + date 36 + gap 24 | 159 |
| Notice, 3 lines | 143 |
| Messages 56 + Phone 56 + gap 8 + More 64 (always one line) + bottom 8 | 192 |
| **Total without the other apps** | **494** |

With a 32 dp status bar and a 24 dp gesture bar the content is 584 dp. That leaves 90 dp for the other apps, or 66 dp
with a 48 dp three-button bar.

**The build measured more than this.** Real Lexend line heights exceed the illustrative browser table.
Do not size the layout from this table: Compose measures the actual scaled text. At 360 × 640 and 200%,
the inline clock and, for a long notice, two-line notice fallbacks may apply. More is always one line;
there is no subtitle-removal step. The Home goldens at 100%, 130%, 150%, 180% and 200% are the layout evidence.

### Calling 9-1-1

**No Emergency button on the home screen** (Beaux, Telegram 4487; calm-home-screen "No emergency control on the home
screen"). No single tap anywhere on the home screen starts the emergency dialer or a call. 9-1-1 is always reachable
(essential-calling-and-texting) by these routes, and the words in About this phone (U8) and the setup handover (S9)
list them:

1. **Phone**, the last name on the home screen: Android's own dialer. Type 9-1-1 and press call. From inside an app,
   press Home, then Phone.
2. **More → Emergency 9-1-1**, the first row of More (U4). See below.
3. **The lock screen's "Emergency call"** (stock Android).
4. **Press the power button 5 times** (Android's Emergency SOS), **only if** feasibility question Q14 confirms it
   works on this phone while Plainphone is running. Until then no screen mentions it.

**The "Emergency 9-1-1" row in More**
- The **first row** of More, in the normal menu-row look: "Emergency 9-1-1" in `row`, second line "Opens the dialer
  with 9-1-1 typed in" in `small`, `›` in `ink-3`. A 16 dp gap below it sets it apart from the other rows. No fill,
  no colour: it's found by its words and by being first.
- "9-1-1" never breaks across lines (non-breaking hyphens, U+2011, or one unbreakable span).
- One tap opens Android's dialer with 9-1-1 typed in (`ACTION_DIAL` for `tel:911`, sent to the system dialer
  package). It never calls by itself: the person still presses the dialer's call button. So reaching a call takes
  three deliberate steps from the home screen (More, the row, call), and a stray tap costs nothing.
- **No long-press** shortcut on the home screen. Nobody would find it in an emergency, and it's easy to trigger by
  accident.

The dialer, the lock-screen emergency dialer and the SOS screen are Android's, not Plainphone's, so they keep their
own colours. That is listed under [what Plainphone doesn't draw](#what-plainphone-doesnt-draw).

### Buttons

| Kind | Look | Use |
|---|---|---|
| **Primary** | `ink` fill, `paper` text, `radius-pill`, full width, 56 dp minimum, pinned to the bottom | One per task screen |
| **Secondary** | Text link: `ink`, 500, underlined, 48 dp tall touch area, centred above the primary | At most one |
| **Outline** | White, `border`, `ink` text, `radius-pill` | Two equal choices side by side, e.g. "Not now" next to "Find it in the Play Store" |
| **Disabled** | `fill-2` fill, `line` text | e.g. "Send to Sam" before anything is typed |
| **Pressed** | Filled: 80% opacity. Outline and rows: `fill-2` background | – |

**Turning off and other serious steps** use the same black primary button as everything else. They stand out by
their words ("Yes, turn it off"), by the extra confirmation screens and by the 24-hour wait, not by red.

### Menu rows (More, Phone settings, trusted-person menu)

`row` text, optional second line in `small`, `›` in `ink-3` on the right, 64 dp minimum height, `hairline` between
rows. Pressed: `fill-2`. Text links at the bottom of a menu (e.g. "About this phone") are secondary links.

### Boxes

`fill-1` background, `radius-box`, `s4` padding, no border, no shadow. For: the notice line, the host on the
blocked-link screen, the request card, the SMS preview, the "Needs you" list, the waiting change on U9. Anything grey
inside a box (second lines, `›`) is `ink-2`, never `ink-3`.

### Text field

White, `border`, `radius-box` (a multi-line box isn't a pill), `body` text, label above in `small` 500 `ink`, hint in
`ink-3`. Focused: 2 dp `ink` border. Counter under it in `small`.

### Choices (Allow / Don't allow)

Two outline buttons side by side. Selected: `ink` fill, `paper` text, with "✓ " before the word. Not chosen yet: both
outline. So the state shows by fill and by the tick, never by colour.

### Trusted-person mode band

Every screen in trusted-person mode has a black band across the top, under the status bar: **"For Sam"** in `paper`,
`small` 500, 40 dp tall. It tells anyone looking at the phone that it's in Sam's hands right now. That is part of
"support, not surveillance": nothing about this mode is hidden from the user. The band is the only large black area
apart from buttons. In self-managed mode the band reads **"Your settings"** instead.

### Status and lists

Ticks and words, never colour: "Sam's phone got it ✓", "Added ✓", "Not added", "Didn't send – Try again" (the "Try
again" part is a text link). Times and dates in `small` `ink-2`.

### Status bar and navigation bar

Drawn by Android. Plainphone sets a white status bar with dark icons (`windowLightStatusBar=true`) and a white
navigation bar with dark buttons, so the whole screen is white from top to bottom, as in the reference image.

## What Plainphone doesn't draw

These keep their own look. They are outside Plainphone's control unless the optional grayscale switch below is on.

- Every added app (Spotify, Slack, Messages, Phone, Camera…).
- Android's dialer (from Phone or the Emergency 9-1-1 row), its emergency dialer on the lock screen, and the
  Emergency SOS screen, including their call buttons.
- The status bar icons (battery, signal), which Android may colour, for example a low battery.
- The Play Store and full phone settings during a session; Android's emergency alerts.

## Colour exceptions

| Where | Colour | Reason | Decided by |
|---|---|---|---|
| (none) | | | |

To add one, write the reason here and ask the Lead. Default answer: no.

## Optional: turning the whole phone grey

The idea (from the brief): Android's colour-correction setting can turn every app grey, a known anti-distraction
trick. Not a board question; nice to have.

**What the sources say (Android 15, AOSP `android15-release`):**

- The setting is two values in `Settings.Secure`: `accessibility_display_daltonizer_enabled` = 1 and
  `accessibility_display_daltonizer` = 0 (`DALTONIZER_SIMULATE_MONOCHROMACY`). Sources: `core/java/android/provider/Settings.java`
  and `core/java/android/view/accessibility/AccessibilityManager.java`.
- **Plainphone can't set it with its Device Owner rights alone.** `DevicePolicyManager.setSecureSetting` rejects any
  name not on a fixed list: `default_input_method`, `skip_first_use_hints`, `install_non_market_apps`, and for a
  Device Owner also `location_mode`. Being on the list doesn't guarantee success (deprecated entries can still throw),
  and demo users are an exception. The daltonizer settings aren't on it. Source: `SECURE_SETTINGS_ALLOWLIST` and
  `SECURE_SETTINGS_DEVICEOWNER_ALLOWLIST` in `services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java`.
- **Likely route: grant one extra permission at setup.** `WRITE_SECURE_SETTINGS` has protection level
  `signature|privileged|development|role|installer` (`core/res/AndroidManifest.xml`). A `development` permission can
  be granted from the helper's computer with `adb shell pm grant <plainphone package> android.permission.WRITE_SECURE_SETTINGS`.
  Setup already uses the helper's computer, so this is one more line in the setup script. Plainphone could then
  switch grayscale on and off itself.
- **Not tested.** Unknowns: whether the grant survives a Plainphone self-update; whether Motorola's build honours the
  setting the same way; whether the grant widens what a bug in Plainphone could change (it lets the app write any
  secure setting, which the protected-state check would need to watch). An emulator test by the Android Platform
  Engineer is tracked as a separate, low-priority issue. It doesn't block the MVP.

**If the test passes, proposed design:**

- A row in trusted-person mode: **"Grey screen"**, second line **"Show every app in black and white"**, with
  On/Off. Default: off.
- In supervised mode, turning it **on** is instant (it's stricter); turning it **off** needs the PIN, like other
  trusted-person actions. In self-managed mode the user can turn it on any time; off follows the "looser waits"
  rule.
- The user can always see it's on, because everything is grey. "About this phone" gains one line: "The screen is
  grey to make apps less tempting. {tp} can turn this off."
- Photos and video look grey too. The Camera note says so, and it's the reason the default is off.

## Spec wording for the Lead

Settled. The Lead changed calm-home-screen to "large text labels and no icons" and gave exact text for the two
proposed notice lines ([PLA-39](/PLA/issues/PLA-39), commit `bf3a7b8`). The Lead also removed Emergency from the home
screen in the spec, with the invariant "9-1-1 is always reachable, and never from an accidental tap on the home
screen" (commit `59b61b9`). The spec doesn't fix where Phone and Messages sit; this design puts them last, nearest
the thumb. The "SHALL NOT show a search bar" rule stands: the reference image's "Search tools" field is replaced by
More.

## What changed in the flow files

- [README.md](README.md) principle 4: "No red except the Emergency button and the one turn-off confirmation" is now
  "No colour at all"; pointer to this file.
- [flows-user.md](flows-user.md) U1: Emergency is black, not red; the app list is text only, low on the screen, ending
  with Messages and Phone; More sits below Phone.
- [flows-limits-and-off.md](flows-limits-and-off.md): the "Yes, turn it off" button is no longer red.
- After the [PLA-37](/PLA/issues/PLA-37) review:
  - [flows-user.md](flows-user.md) U1: all four notice lines in the spec's priority order, the waiting-change lines
    and "Already in progress"; the short-screen rule now points here instead of shrinking the clock. U3 shows a
    waiting change with its PIN-free Cancel. U7 always shows a waiting change at the top. New **U9** (a change is
    waiting: cancel, confirm, still cancelling, cancelled, already in progress) and **U10** (self-managed
    countdown and Start). U8's list of texts now includes waiting changes and "needs attention".
  - [flows-setup.md](flows-setup.md) S8: ✓ and ✗ with words, no green.
  - [copy-test-packet.md](copy-test-packet.md): no red; a note that the sketches are rough and the look is here.
- After Beaux's decision to take the 9-1-1 button off the home screen (Telegram 4487):
  - [flows-user.md](flows-user.md) U1: no Emergency button; the notice line sits under the date. U4: "Emergency
    9-1-1" is More's first row. U8 (About this phone): a "To call 9-1-1" section.
  - [flows-setup.md](flows-setup.md) S5, S8 and S9: Phone and Messages are always there; the self-test is "Emergency
    self-test"; the Ready screen tells the helper how 9-1-1 is reached, to show {user}.
  - [flows-trusted-person.md](flows-trusted-person.md): T1 and T5 no longer mention an Emergency tile.
  - [copy-test-packet.md](copy-test-packet.md): screen 1 without the button.

## Mockups

[mockups/plainphone-screens.html](mockups/plainphone-screens.html) (open in any browser; the font is bundled), and a
PNG of the same page attached to [PLA-36](/PLA/issues/PLA-36). Screens: home (normal, with a notice line, and at
200%), calling 9-1-1 (About this phone), More with the Emergency 9-1-1 row, Ask for an app, Link can't open, Wi-Fi
sign-in page, "For Sam", home on the smallest supported screen at 200% (frame 10), and "A change is waiting"
(frame 11). Names follow the copy-test packet (Margaret uses the phone, Sam looks after it, Alex is the helper).

The mockups are drawn in a browser. The 200% frames use Android 15's real 200% sizes, but they are illustrative: they
show the layout rules, not proof that Android lays it out the same way. The proof is on the phone, in the MVP build
([PLA-15](/PLA/issues/PLA-15)) and the final review ([PLA-16](/PLA/issues/PLA-16)).
