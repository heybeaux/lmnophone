# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Cursor, Copilot and others) working in this repository. Humans are welcome to read it too: it is the fastest tour of the code.

LMNOPhone is an Android app that becomes a phone's **Device Owner** and its **home screen**. It hides every app a trusted person hasn't added, keeps the approved apps in lock task mode, and never blocks calls, texts or 9-1-1. The people using it are not technical. Read [README.md](README.md) first for the why.

The project used to be called Plainphone. Code names such as the `org.plainphone` package, the `:plainphone` module and `PlainphoneAdminReceiver` keep the old name until the package-rename slice, which moves the app to its planned ID, `dev.heybeaux.lmnophone`. Until then the code uses `org.plainphone`. Both names mean the same project.

> If you work inside the maintainers' own agent team, your team policy also applies. Where it is stricter than this file, it wins.

## Read before you change anything

| If you are touching… | Read first |
|---|---|
| Anything | This file, then [docs/mvp/lanes.md](docs/mvp/lanes.md) (modules and the interfaces between them) |
| Behaviour | The spec in [openspec/changes/](openspec/changes/) (`specs/<capability>/spec.md`) and its `design.md` |
| Screens or any text a user sees | [docs/ux/README.md](docs/ux/README.md) (principles and word list), the matching `docs/ux/flows-*.md`, and [docs/ux/design-tokens.md](docs/ux/design-tokens.md) |
| Why it's built this way | [docs/decisions/0001-architecture.md](docs/decisions/0001-architecture.md) |
| One module | That module's `README.md` in `app/<module>/`, where there is one |

## Repo map

```
app/                  Gradle root (run every Gradle command from here)
  core/               :core        Kotlin/JVM  shared value types
  policy/             :policy      Kotlin/JVM  policy engine and every state machine
  links/              :links       Kotlin/JVM  browser classifier, link decisions
  ui/                 :ui          Android lib, Compose: screens, theme, tokens, Lexend font
  device/             :device      Android lib: DevicePolicyManager, lock task, storage, SMS
  plainphone/         :plainphone  Android app: admin receiver, activities, view-models
  gradle/libs.versions.toml        the shared version catalog (see Conventions for the :ui exception)
openspec/changes/     specs: proposal.md, design.md, tasks.md, specs/<capability>/spec.md
docs/ux/              words, flows, design tokens, mockups
docs/decisions/       architecture decision records
docs/                 setup guide, verification matrix, feasibility questions, reviews
research/             early research; leads only, not truth
spikes/               throwaway experiments; never import from them
site/                 static landing page (HTML/CSS, no tracking); not published yet. GitHub Pages and DNS need a board decision
```

## Commands

Prerequisites: JDK 17 and an Android SDK with platform 36, found through `ANDROID_HOME` or `app/local.properties` (`sdk.dir=...`, not committed). Run everything from `app/`.

| What | Command |
|---|---|
| Everything: tests, then the debug APK | `./gradlew test assembleDebug` |
| `:core` | `./gradlew :core:test :core:check` |
| `:policy` | `./gradlew :policy:test :policy:check` |
| `:links` | `./gradlew :links:test :links:check` |
| `:ui` unit tests | `./gradlew :ui:test` |
| `:ui` screenshot tests | `./gradlew :ui:verifyPaparazziDebug` |
| `:ui` record new screenshots | `./gradlew :ui:recordPaparazziDebug` (see the rule below) |
| `:device`, `:plainphone` unit tests | `./gradlew :device:test :plainphone:test` |
| `:device` instrumented tests (emulator only) | `ANDROID_SERIAL=emulator-5554 ./gradlew :device:connectedDebugAndroidTest`. These tests live on the device branches (`mvp/device`, `release/*`) and aren't on `main` yet. |
| Lint | `./gradlew :ui:lint :device:lint :plainphone:lint` |
| Validate specs | `openspec validate --all` (from the repo root) |

The APK is written to `app/plainphone/build/outputs/apk/debug/plainphone-debug.apk`. Emulator setup is in the README under [Build it and run it on an emulator](README.md#build-it-and-run-it-on-an-emulator).

Prefer the narrowest command that proves your change. A `:policy` change needs `:policy:test`, not a full build.

## The module dependency rule

```
:policy, :links  → :core only
:ui              → nothing of ours
:device          → :core, :policy, :links
:plainphone      → everything
```

- `:core`, `:policy` and `:links` **never import `android.*`**. The build has no Android classes on their path, so a violation fails to compile. Don't "fix" that by adding an Android dependency.
- `:policy` decides; `:device` does. The engine returns a `PolicyPlan`; `:device` applies it and reads every setting back. Don't put decisions in `:device`, and don't put Android calls in `:policy`.
- `:ui` takes `XUiState` and emits `XEvent`. It holds no policy logic and never reads Android state itself.
- Interfaces between modules (names like `PolicyEngine.decide`, `LinkDecider`, `PolicyApplier`) are listed in [docs/mvp/lanes.md](docs/mvp/lanes.md). Changing one means changing that file in the same pull request and saying so.

## Conventions

**Source files**
- Every new source file starts with the licence header:
  ```kotlin
  // Copyright 2026 LMNOPhone contributors
  // SPDX-License-Identifier: Apache-2.0
  ```
  (`LICENSE` says "The LMNOPhone Authors". The project was called Plainphone before, so existing source headers and the root build files still say "Plainphone contributors" or "The Plainphone Authors" until the package-rename slice. All of these mean the same people; don't churn existing headers.)
- New versions go in `app/gradle/libs.versions.toml`, and module build files read them through `libs.versions.*` and `libs.plugins.*`. A few `:ui` dependencies (the Compose BOM, Paparazzi) are still pinned in `app/ui/build.gradle.kts`. Leave them unless your change is about them.
- Toolchain: JDK 17, AGP 8.11.0, Kotlin 2.1.20, compileSdk 36, minSdk 30.

**Tests first**
- Write the failing test, commit or show it failing, then write the code. Reviewers check the order.
- Pure logic is tested as JVM tests in `:core`, `:policy`, `:links`.
- Screens have Paparazzi tests at font scale 1.0 and 2.0. Text that grows must be checked across a range of scales and string lengths, not just 200%. Android scales large text non-linearly, so don't calculate sizes by hand.
- **Golden images:** only run `recordPaparazziDebug` when a screen is meant to change. Look at every changed image before you commit it. Never re-record to make a failing test pass.

**Look: design tokens**
- Black, white and grey only. Use the colours in `app/ui/src/main/kotlin/org/plainphone/ui/theme/PlainphoneTheme.kt` (`Ink`, `Paper`, `Ink2`, `Ink3`, `Line`, `Fill1`, `Fill2`), which match [docs/ux/design-tokens.md](docs/ux/design-tokens.md). No other `Color(...)` anywhere.
- Font: Lexend, shipped unchanged as `app/ui/src/main/res/font/lexend.ttf`, with its licence in `app/ui/src/main/assets/fonts/OFL.txt`. Don't subset, edit or replace it, and keep the licence file shipping with it.
- No icons. Text only, plus the glyphs `›`, `‹`, `✓` and `^`.
- Sizes in `sp` for text, so everything follows the phone's text size up to 200%. Touch targets at least 48dp; list rows and the primary button at least 56dp.
- One primary action per task screen: full width, pinned to the bottom. Body text scrolls vertically, never horizontally. Never cut text off.
- No Emergency button on the home screen. 9-1-1 lives in More.

**Words: plain-language copy**
- On-screen text comes **word for word** from `docs/ux/flows-*.md`. If the copy you need isn't there, don't invent it: leave a clear `TODO(copy)` and say so in the pull request.
- Never show these words to a user or trusted person: Device Owner, device admin, MDM, managed device, allowlist, whitelist, kiosk, lock task, package, APK, policy, restriction, adb, session, FRP, WebView, captive portal. The full table, with what to say instead, is in [docs/ux/README.md](docs/ux/README.md#words-we-use-and-words-we-never-show).
- No exclamation marks. No countdown timers or badges on the user's screens. Never "safe", "secure", "protected" or "blocked forever".
- Repeat a person's name instead of using a pronoun ("Ask Sam", not "Ask him").
- Strings go in Android string resources. Counts use `plurals`. The screens fill in names, counts and times; the rules are in the "Counts, names and days" section of [docs/ux/README.md](docs/ux/README.md).
- In tests and examples, use the sample names Sam, Jo and Alex. Never real people's names, numbers or addresses.

## Spec first (OpenSpec)

1. New behaviour, or a change to what the phone allows, starts as an OpenSpec change: `openspec/changes/<verb>-<thing>/` with `proposal.md`, `design.md` where needed, `tasks.md`, and spec deltas in `specs/<capability>/spec.md` (`## ADDED Requirements`, `### Requirement:`, scenarios).
2. Run `openspec validate --all`.
3. A maintainer approves the proposal. **Don't implement an unapproved proposal.** Feasibility spikes that answer one named question are allowed; put them in `spikes/<name>/` and label them throwaway.
4. Implement against the approved spec. In the pull request, list the requirements it covers and tick the matching items in `tasks.md`.

Bug fixes, tests, refactors and docs don't need a spec. If a "fix" changes what the user can or can't do, it does.

## Never do these

1. **No `DevicePolicyManager` calls outside `:device`.** The same goes for lock task calls, `setApplicationHidden`, user restrictions and role changes. Everything that changes the phone goes through `:device` and is read back.
2. **No colour outside the palette.** No new `Color(...)` values, no accent, no red for danger. The `lightColorScheme` in `PlainphoneTheme.kt` maps the palette tokens and is the only colour scheme: no default Material schemes, no dynamic (Material You) colour, nothing off the palette.
3. **Never place an emergency call.** No test, script or tool may start `ACTION_CALL` or `ACTION_CALL_EMERGENCY` to 911, 112, 999, 000 or any other emergency number, press a dialer's call button with one typed in, or trigger the power-button emergency SOS gesture. Emergency checks open the dialer with the number typed in (`ACTION_DIAL`) and stop there. Anything that does place a call uses a fake number (for example `555-0100`), never a real emergency or personal number.
4. **No commands against real phones.** Use emulators only. Always name the target with `adb -s emulator-NNNN` or `ANDROID_SERIAL`; never run a bare `adb` command while a physical device might be connected. Never run `dpm set-device-owner`, a factory reset, a flash, or an install on a physical phone. A human who owns a specific phone may authorise that phone, in writing, for a specific task. Nothing else counts as permission.
5. **Never weaken the floor.** Every floor and never-hide package (the Phone app, Messages, emergency dialing, emergency alerts, and the system parts they depend on, such as WebView, keyboards and the permission controller) stays unhidden and enabled. Code that could hide or disable one goes through the never-hide guard in `:policy`. Lock task is a separate list. It holds exactly the **lock-task UI list** from the device-lockdown spec: LMNOPhone, the approved apps, the emergency UI subset that `EmergencyFloor` returns (the system dialer, the emergency dialer and the in-call UI), and the minimum UI entries in the build inventory, plus a supervised session's packages while one is open. `PolicyEngine` builds this list; don't build it anywhere else. Never bulk-add the whole never-hide floor to lock task, because that would let WebView, keyboards and other system parts launch freely. And don't "fix" the turn-off path, which clears lock task on purpose.
6. **Don't lock yourself out.** Test builds stay `testOnly` so `dpm remove-active-admin` works. Applying `DISALLOW_DEBUGGING_FEATURES` ends your adb connection, so don't apply a full policy plan on an emulator you still need to drive.
7. **No secrets or personal data in the repo.** No credentials, account addresses, phone numbers, real names, or screenshots that show any of them. Check every image before you commit it.
8. **No analytics, crash reporters, ads or dependencies that phone home.** LMNOPhone's notifications are only the SMS texts listed in [docs/ux/README.md](docs/ux/README.md#texts-lmnophone-sends). Its one approved network use is the updater: after a fresh PIN, it downloads a signed LMNOPhone APK from the helper's computer on the local network (trusted-person-controls spec). Any other network use needs an approved spec first.
9. **Don't approve your own work**, and don't force-push `main`.

## Android facts that have caught us out

- Claims about what Android allows need a source (developer.android.com or AOSP code) or a test someone can repeat on an emulator. The early research in `research/` has been wrong before.

The next points come from our own emulator runs on API 35. Re-check them before you rely on them for another version.

- Removing the Device Owner on API 35 brings hidden apps back.
- Hidden apps still match intent queries made with `MATCH_UNINSTALLED_PACKAGES`.
- Android only accepts `dpm set-device-owner` on a phone with no accounts, and only one Device Owner at a time.
- Instrumented tests that run before the first unlock (Direct Boot) need a Direct-Boot-aware component in the test APK, not just the app flag.
- In our emulator runs, the reset-protection (FRP) calls failed, so emulator results say nothing about whether reset protection works. It must be tested on real hardware (Q10 in [docs/verification-matrix.md](docs/verification-matrix.md)), and that test hasn't passed yet.
