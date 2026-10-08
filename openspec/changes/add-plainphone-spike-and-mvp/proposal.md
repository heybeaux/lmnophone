## Why

Beaux and the trusted person live in [town]. They want a calm phone that runs only the apps a trusted person has approved, like Wisephone, without a subscription and without trusting a closed vendor. Neither of them tinkers with phones, so it has to work for strangers.

The research suggests this can probably be built as one Android app on a certified stock phone. It is not proven. Two problems the original plan missed:
- Google now blocks unapproved device-management apps during QR setup.
- The "block installs" switch also blocks updates.

So we first test the risky parts on an emulator against go/no-go conditions, then build the MVP.

Architecture and evidence: `docs/decisions/0001-architecture.md` (revision 6, after cross-model reviews PLA-3, PLA-4, PLA-7, PLA-8 and PLA-9). First device results on a board-approved spare Motorola:
- adb setup of our unapproved Device Owner works on certified Android 15, and the phone can be released without a wipe;
- lock task with in-task launches blocked closes the App info and share-sheet leaks that appear without it (the Photos route has not been tested with it on yet). Runtime permission prompts are blocked too, so Plainphone plans to pre-grant permissions at approval; that is not tested yet.

## What Changes

- **Phase 1: emulator spike (throwaway code in `spikes/`).**
  - Answer the go/no-go questions Q1–Q4 and the pre-handover questions Q5–Q8, Q10, Q12–Q14 in `docs/feasibility-questions.md`.
  - No real phones.
  - If the results change ADR 0001 or any promise in it, Beaux re-approves before the MVP starts.
- **Phase 2: MVP for two phones.** A single Kotlin app, "Plainphone", that is:
  - the phone's **Device Owner**. It hides apps that aren't approved, runs approved apps in lock task mode, and blocks other apps' screens from opening even inside an approved app. It also blocks the known escape routes and names the trusted person in factory-reset protection.
  - the phone's **home screen**: big, plain, no search, no feeds.
- **A trusted person approves apps on the phone** using a PIN, with a fresh check for each important action. The user can ask for an app, and the request goes to the trusted person by text.
- **Plainphone never gets in the way of 9-1-1**, calls, texts or emergency alerts. It never filters incoming calls. A release-blocking test taps through the emergency flow, and must fail on a deliberately broken build.
- **Web links are blocked** unless they belong to an approved app. General browsers and the Wi-Fi sign-in page are hidden. Leftover web access is described to the trusted person in plain words and accepted in writing by Beaux.
- **App updates keep working** in the background. The Play Store stays installed but the user can't open it. The trusted person gets a monthly text to run a supervised update check. Plainphone updates itself from the helper's computer over local Wi-Fi, only if the update is signed with Plainphone's key. No adb maintenance mode.
- **Supervised sessions** (Play, Settings) are the only way the lockdown is relaxed. They are trusted-person custody, with a journal that is undone on every restart.
- **Wi-Fi networks that need a sign-in page are not supported** in the MVP.
- **Setup** is done by a helper with a computer, using a written guide and a script that checks the phone first. The phone isn't marked ready until a handover check passes.
- **Out of scope:** remote web portal, servers, DNS/VPN filter (spiked only), GrapheneOS, app-store distribution, paid services, and any real-phone work without Beaux's approval for that phone.

## Capabilities

### New Capabilities
- `device-lockdown`: Device Owner policies, lock task with in-task blocking, factory-reset protection, safe policy storage and boot recovery.
- `approved-apps`: the allowlist. "Can't be opened" guarantee, updates and staleness warnings, the supervised Play session.
- `calm-home-screen`: home screen, always-present Phone/Messages/Emergency, Plainphone's own basic settings.
- `trusted-person-controls`: PIN and recovery code, fresh authority for actions, app requests, the supervised session contract, Plainphone updates over local Wi-Fi, recovery when Plainphone cannot run, disclosure, turning off.
- `web-containment`: browser classifier (excludes Plainphone itself), link handling rules, the isolated blocked-link screen, unsupported Wi-Fi sign-in pages, web-leak notes per app version.
- `essential-calling-and-texting`: protections for 9-1-1, calls, texts and alerts, plus the behavioral release gate.
- `guided-setup`: setup guide and checking script, helper-custody setup, handover check.

### Modified Capabilities
- None. This is the first change in the repo.

## Impact

- **New code:**
  - `spikes/` (throwaway)
  - `app/` (Kotlin, minSdk 30)
  - `docs/setup-guide.md`
  - setup script
- **Dependencies:** AndroidX only. TestDPC and Headwind MDM are used as reference code (Apache-2.0).
- **License:** Apache-2.0 recommended. Beaux decides.
- **External systems:**
  - Google Play stays for app updates.
  - The trusted person's Google `userId` is needed for factory-reset protection. It is read from Google's People API "Try it" page; no new credential for us.
  - No servers. No new paid services.
- **Board decisions (2026-10-08):**
  - MVP target is the spare XT2415, and the daily-use phone is deferred.
  - License: Apache-2.0.
  - Carrier: Rogers.
  - Beaux is the helper and keeps the signing key.
- **Policy gates:**
  - Physical phones need Beaux's approval for each phone. The spare moto g power 5G (2024) is approved for spikes only (PLA-5). It is not a target phone: its security updates end in March 2027.
  - Developer verification (Canada: 2027; adb installs exempt) is a later decision.
  - No public distribution.
