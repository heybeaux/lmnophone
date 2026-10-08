## Why

Beaux and the trusted person want a calm phone that runs only the apps a trusted person has approved, like Wisephone. They want it without a subscription and without trusting a closed vendor. Neither of them tinkers with phones, so it has to work for strangers.

The research showed this can be built as one Android app on a normal Pixel. It also found two problems the original plan missed:
- Google now blocks unapproved device-management apps during QR setup.
- The "block installs" switch also blocks updates.

So we spike the risky parts on an emulator first, then build the MVP. Architecture and evidence: `docs/decisions/0001-architecture.md`.

## What Changes

- **Phase 1, emulator spike (throwaway code in `spikes/`).** Answer feasibility questions Q1–Q8 in `docs/feasibility-questions.md`; Q9–Q11 are answered when cheap. No real phones. Spike results can change the design. If they change ADR 0001, Beaux re-approves before the MVP starts.
- **Phase 2, MVP for two phones.** A single Kotlin app, "Plainphone", that is both:
  - the phone's **Device Owner**: it hides unapproved apps, runs approved apps in lock task mode, and blocks the known escape routes;
  - its **home screen**: big, plain, no search, no feeds.
- **A trusted person** approves apps on the phone with a PIN. The user can ask for an app, and the request goes to the trusted person by text message.
- **911, calls and texts can never be blocked.** This is enforced in code and by a test that blocks any release.
- **Web links are blocked.** Browsers are hidden and links open a plain "This link is blocked" screen. The leftover web access inside approved apps is told to the trusted person in plain words.
- **App updates keep working.** The Play Store stays installed for background updates but can't be opened by the user.
- **Setup** is done by a helper with a computer, following a written guide (adb). A friendlier web-based setup comes later.
- **Out of scope:** a remote web portal, a server, a DNS/VPN filter (spiked only), GrapheneOS support, app-store distribution, paid services, and any real-phone work without Beaux's approval for that phone.

## Capabilities

### New Capabilities
- `device-lockdown`: Device Owner policies that stop the user from escaping (safe mode, reset, adb, users, settings, sideloading), and what happens on reboot.
- `approved-apps`: the allowlist. Hiding unapproved apps, adding and removing apps under supervision, catching apps installed without approval, and keeping approved apps updated.
- `calm-home-screen`: the home screen, the always-present Phone/Messages/Emergency buttons, and the small built-in settings (Wi-Fi, volume, brightness).
- `trusted-person-controls`: trusted-person PIN, app requests by text, recovery code, and turning Plainphone off.
- `web-containment`: hidden browsers, the blocked-link screen, the per-app "web leak" note, and honest disclosure of leftover risk.
- `essential-calling-and-texting`: guarantees for 911, calls, SMS/MMS/RCS and emergency alerts.
- `guided-setup`: how a helper sets up a phone, what the user and trusted person see, and checks before handover.

### Modified Capabilities
- None. This is the first change in the repo.

## Impact

- **New code:**
  - `spikes/` (throwaway emulator experiments)
  - `app/` (Kotlin Android app, minSdk 30 because factory reset protection and several restrictions need it)
  - `docs/setup-guide.md`
- **Dependencies:** AndroidX only. Google TestDPC and Headwind MDM are used as reference code (both Apache-2.0).
- **License:** Apache-2.0 recommended. Beaux decides.
- **External systems:** Google Play stays on the device for updates. No servers. No new accounts or paid services.
- **Policy gates:**
  - Any work on a physical phone needs Beaux's approval for that phone.
  - Developer verification registration (2027) is a later spend decision.
  - The MVP is not distributed publicly.
