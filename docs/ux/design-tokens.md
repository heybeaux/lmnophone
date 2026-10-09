# Plainphone design tokens: black and white

Owner: Product & UX Designer. Task: [PLA-36](/PLA/issues/PLA-36). Status: **ready for the MVP build
([PLA-15](/PLA/issues/PLA-15))**, revised after the [PLA-37](/PLA/issues/PLA-37) review. Mockups: [mockups/plainphone-screens.html](mockups/plainphone-screens.html).

Beaux's brief (Telegram 4485): *"Home screen/screens are black and white only. Simple. Effective."* The look borrows
the font, greys and spacing from Generosity Catalyst (GC) and the layout from Beaux's reference image: a short
text-only list of apps in large type, low on the screen where a thumb reaches, with lots of white space above.

This file sets the look. The words stay as written in the flow files ([README.md](README.md)); nothing here changes
copy, except where a flow said "red" (see [What changed in the flow files](#what-changed-in-the-flow-files)).

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
5. **Emergency is black-filled, white text, and alone** at the top of the home screen. See
   [Emergency](#emergency).
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
| `ink` | `#000000` | Black | Text, app names, filled buttons, Emergency, focus ring, selected choice |
| `paper` | `#FFFFFF` | White | Screen background, text on black |
| `ink-2` | `#545454` | Gray 600 | Second lines ("Opens the dialer", "Ask for an app, settings, help"), body notes |
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
- On black (Emergency, primary buttons, the trusted-person band), text is `paper`. A second line on black, such as
  "Opens the dialer", is also `paper` at a smaller size, not grey.

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
| `title` | 28 / 36 | 37 / 43 | 500 | `ink` | Screen titles. At most 2 lines at 200% |
| `row` | 22 / 28 | 35 / 37 | 500 | `ink` | Menu rows (More, Plainphone settings, the trusted-person menu) |
| `emergency` | 22 / 28 | 35 / 37 | 500 | `paper` on `ink` | "Emergency 9-1-1" (line 1 of the Emergency button) |
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
| `s12` | 48 dp | **No tappable control within this distance of Emergency** (see [Emergency](#emergency)) |
| `radius-box` | 16 dp | Boxes and cards (GC's card radius) |
| `radius-pill` | 999 (full) | Buttons, Emergency, the More bar, text fields |
| `hairline` | 1 dp `fill-2` | Dividers between menu rows |
| `border` | 1.5 dp `ink-3` | Outline buttons, text fields, the More bar (3:1 or better on white) |
| `focus` | 2 dp `ink` outline, 2 dp gap | Keyboard and switch-access focus (GC's blue ring, made black) |

**Touch targets** are at least 48 × 48 dp; home-screen app rows are at least 56 dp tall. **Shadows:** none. Dialogs
and sheets use a `hairline` border instead. **Motion:** fades only, 150 ms, and none when Android's "Remove
animations" is on. No bounce, no parallax, no animated clock.

## Components

### Home screen (U1)

Two zones. The top zone holds what never moves; the app list sits low, where a thumb reaches.

```
┌──────────────────────────────┐
│ ▔▔ status bar (Android) ▔▔   │
│ 10:42                        │  clock
│ Thursday 8 October           │  date
│                              │  ≥ 48 dp
│ ╭──────────────────────────╮ │
│ │     Emergency 9-1-1      │ │  black, white text
│ │     Opens the dialer     │ │
│ ╰──────────────────────────╯ │
│                              │  ≥ 48 dp (usually much more)
│ ┌──────────────────────────┐ │
│ │ Ask Sam to do the monthly│ │  notice line, only when one applies (fill-1 box)
│ │ phone check            › │ │
│ └──────────────────────────┘ │
│        (white space)         │
│ Spotify                      │  other apps, in {tp}'s order; scrolls if long
│ Slack                        │
│ Camera                       │
│ Messages                     │  always here, pinned
│ Phone                        │  always here, pinned, nearest the thumb
│ ╭──────────────────────────╮ │
│ │ More ^                   │ │  outline bar, where the reference has "Search tools"
│ │ Ask for an app, settings,│ │
│ │ help                     │ │
│ ╰──────────────────────────╯ │
└──────────────────────────────┘
```

- **App names:** `app` style, left-aligned, 24 dp from the edge, rows 56 dp minimum with `s3` between. The whole
  row is the touch target, edge to edge. No icon, no divider, no badge.
- **Order:** the other apps in the order the trusted person set (or the user, in self-managed mode). Then
  **Messages**, then **Phone**, always last and always visible. This follows the reference image, which ends the list
  with Messages and Phone, and it puts the two everyday apps nearest the thumb.
- **Scrolling:** only the other apps scroll, inside their own area, upward. The clock, Emergency, the notice line,
  Messages, Phone and More never scroll off. If space runs short, see
  [When the screen is short](#when-the-screen-is-short).
- **More bar:** full width, `radius-pill`, `border`, white fill, 64 dp tall. "More" in `row` style with a `^` after it
  (`ink-3`), and the second line "Ask for an app, settings, help" in `small`. It sits where the reference has
  "Search tools". **There is no search field:** calm-home-screen says the home screen SHALL NOT show a search bar,
  and with a short list there is nothing to search. The `^` is a hint that more is below; tapping opens More (U4).
  There is still no swipe-up drawer.
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

1. The More bar drops its second line.
2. The notice line is cut to 2 lines (it still ends with "…" and `›`).
3. The time and date go on one line: the time in `title` style, the date beside it in `date` style ("Thu 8 Oct").
   Both keep growing with text size; they just stop stacking.
4. The gap above Emergency goes from 24 to 16 dp. Nothing above it is tappable.
5. The notice line is cut to 1 line.
6. The other-apps area may shrink to half a row (28 dp). It still scrolls; the cut-off name shows there's more.
7. Below the smallest supported screen only: the whole home screen scrolls as one page, Emergency first. This is
   the one case where Phone and Messages can need a scroll, and it is outside what Plainphone supports.

**Never:** shrink any text below its scaled size, shrink Emergency, Messages, Phone or More, or cut the 48 dp below
Emergency.

**Check at 360 × 640 dp and 200%, with the longest notice showing.** Measured in mockup frame 10 (a browser
render using the 200% sizes above, so illustrative). On a 360 dp-wide screen, "Emergency 9-1-1" at 35 dp doesn't
fit on one line and wraps, so the button is about 129 dp tall, not 92:

| Part | dp |
|---|---|
| Top 8 + time and date on one line 43 + gap 16 | 67 |
| Emergency (label wrapped) | 129 |
| Gap below Emergency | 48 |
| Notice, 2 lines (after steps 1–4) | 106 |
| Gap 8 + Messages 56 + Phone 56 + gap 8 + More 64 + bottom 8 | 200 |
| **Total without the other apps** | **550** |

With a 32 dp status bar and a 24 dp gesture bar the content is 584 dp. After steps 1–4 that leaves 34 dp for the
other apps, less than a row, so step 5 cuts the notice to 1 line (about 37 dp less): **about 71 dp**, one row, the
rest scrolling. That's frame 10. With a 48 dp three-button bar it's about 47 dp, so step 6 also applies. Either way
Emergency, Messages, Phone and More stay in view. On the target phone (412 × 915 dp) at 200%, only step 1 is needed:
frame 3 shows 225 dp for the other apps with the longest notice at 3 lines.

### Emergency

- Black pill (`ink`), full width minus 24 dp each side, at least 72 dp tall; it grows with its text (about 92 dp at
  200% on the target phone; on a 360 dp-wide screen the first line wraps and it's about 129 dp, which is fine). Line 1 "Emergency 9-1-1" in `emergency`. If it has to wrap, it breaks only between "Emergency" and "9-1-1": the number is never split (use non-breaking hyphens, U+2011, or keep "9-1-1" as one unbreakable span). Line 2 "Opens the dialer" in `small`, `paper`. Padding 10 dp top
  and bottom, 24 dp sides.
- It is the **only filled shape on the home screen**. That is what makes it stand out, instead of red.
- **No tappable control within 48 dp above or below it.** Above it are only the time and date, which aren't
  tappable: the gap is 48 dp at normal sizes, 24 dp at 200%, and 16 dp on short screens. Below it there are always at least 48 dp of empty space
  before the notice line or the first app. In the normal layout the gap below is much larger, because the app list
  sits low. A thumb reaching for Phone can't reach Emergency.
- One tap. No long-press, no swipe, no confirm screen. It opens the phone's own dialer with 9-1-1 typed
  (`ACTION_DIAL`), so a bump never calls: the user still has to press the dialer's call button.
- The dialer is Android's, not Plainphone's, so its call button keeps the dialer's own colour. That is fine and is
  listed under [what Plainphone doesn't draw](#what-plainphone-doesnt-draw).

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
apart from Emergency and buttons. In self-managed mode the band reads **"Your settings"** instead.

### Status and lists

Ticks and words, never colour: "Sam's phone got it ✓", "Added ✓", "Not added", "Didn't send – Try again" (the "Try
again" part is a text link). Times and dates in `small` `ink-2`.

### Status bar and navigation bar

Drawn by Android. Plainphone sets a white status bar with dark icons (`windowLightStatusBar=true`) and a white
navigation bar with dark buttons, so the whole screen is white from top to bottom, as in the reference image.

## What Plainphone doesn't draw

These keep their own look. They are outside Plainphone's control unless the optional grayscale switch below is on.

- Every added app (Spotify, Slack, Messages, Phone, Camera…).
- Android's dialer after Emergency, including its call button.
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

Handed to the Lead in [PLA-39](/PLA/issues/PLA-39), with the two proposed notice lines from U1 (a waiting mode or
friction change, and the self-managed monthly line). Neither item changes a promise; they are wording.

1. **"with large labels and icons"** ("Plain home screen", in both the approved spec and the
   `add-copy-test-product-decisions` delta). Beaux asked for no icons. Suggested wording: "with large text labels
   and no icons". Until the spec says so, text-only meets "large labels"; the icons are the only gap.
2. **Order of Phone and Messages.** The spec says the home screen shows Emergency, Phone and Messages and that they
   are never pushed off-screen; it doesn't fix their place. This design puts them last in the list, nearest the
   thumb, and keeps them fixed. No spec change is needed; noted so reviewers don't read it as a change.

The "SHALL NOT show a search bar" rule stands: the reference image's "Search tools" field is replaced by More.

## What changed in the flow files

- [README.md](README.md) principle 4: "No red except the Emergency button and the one turn-off confirmation" is now
  "No colour at all"; pointer to this file.
- [flows-user.md](flows-user.md) U1: Emergency is black, not red; the app list is text only, low on the screen, ending
  with Messages and Phone; More is the bottom bar. The words on the home screen don't change.
- [flows-limits-and-off.md](flows-limits-and-off.md): the "Yes, turn it off" button is no longer red.
- After the [PLA-37](/PLA/issues/PLA-37) review:
  - [flows-user.md](flows-user.md) U1: all four notice lines in the spec's priority order, the waiting-change lines
    and "Already in progress"; the short-screen rule now points here instead of shrinking the clock. U3 shows a
    waiting change with its PIN-free Cancel. U7 always shows a waiting change at the top. New **U9** (a change is
    waiting: cancel, confirm, still cancelling, cancelled, already in progress) and **U10** (self-managed
    countdown and Start). U8's list of texts now includes waiting changes and "needs attention".
  - [flows-setup.md](flows-setup.md) S8: ✓ and ✗ with words, no green.
  - [copy-test-packet.md](copy-test-packet.md): no red; a note that the sketches are rough and the look is here.

## Mockups

[mockups/plainphone-screens.html](mockups/plainphone-screens.html) (open in any browser; the font is bundled), and a
PNG of the same page attached to [PLA-36](/PLA/issues/PLA-36). Screens: home (normal, with a notice line, and at
200%), Emergency close-up, More, Ask for an app, Link can't open, Wi-Fi sign-in page, "For Sam", home on the
smallest supported screen at 200% (frame 10), and "A change is waiting" (frame 11). Names follow the copy-test
packet (Margaret uses the phone, Sam looks after it, Alex is the helper).

The mockups are drawn in a browser. The 200% frames use Android 15's real 200% sizes, but they are illustrative: they
show the layout rules, not proof that Android lays it out the same way. The proof is on the phone, in the MVP build
([PLA-15](/PLA/issues/PLA-15)) and the final review ([PLA-16](/PLA/issues/PLA-16)).
