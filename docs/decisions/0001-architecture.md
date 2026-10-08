# ADR 0001: How Plainphone locks a phone down

- Status: **Proposed** (needs Beaux's approval, together with the OpenSpec change `add-plainphone-spike-and-mvp`)
- Date: 2026-10-08
- Author: Plainphone Lead (Claude). Reviewer: Verifier & Escape Artist (OpenAI model).
- Evidence: `research/evidence/*.md`. The DeerFlow report (`research/2026-10-08-deerflow-feasibility.md`) was used only for leads.

## Decision in one paragraph

Build Plainphone as **one Android app that is set as the phone's Device Owner and is also its home screen**. Run it on an **unlocked Google Pixel running stock Android** for the MVP. It hides every app the trusted person has not approved, runs the approved apps in **multi-app lock task mode**, and applies the anti-escape restrictions listed below. We do **not** build a custom ROM. We do **not** ship a launcher-only version. GrapheneOS stays a candidate for a later version, decided by evidence rather than preference. Two findings could still sink this plan, and the spike must answer them first (Q1 and Q2 in `docs/feasibility-questions.md`).

## Options compared

| | A. Device Owner + launcher, stock Pixel | B. Device Owner + launcher, GrapheneOS | C. Custom ROM | D. Launcher only |
|---|---|---|---|---|
| Can the user escape? | Hard. Safe mode, factory reset from Settings, OEM unlock, adb, extra users and launcher changes can all be blocked by documented APIs (see "Controls"). A recovery-mode wipe is still possible; FRP limits what it gets them. | Same, plus a relocked bootloader with GrapheneOS keys. | Hardest. Limits are built into the OS. | Easy. Settings, safe mode, changing the default launcher and adb all work. |
| Banking and payment apps | Work. Certified device. | Some fail. GrapheneOS is not Google-certified and does not spoof the checks (grapheneos.org/usage). | Many fail (same certification problem). | Work. |
| RCS / iPhone group texts | Work through Google Messages. | Work only in the Owner profile, with extra permissions for Play services (grapheneos.org/usage). | Depends on the build. | Work. |
| 911 location | Carrier plus Google's Emergency Location Service. | Carrier only. Google ELS runs inside Play services and is very likely missing (inferred). | Depends on the build. | Carrier plus ELS. |
| Setup for a non-technical person | Factory reset, then a helper with a computer runs one adb step (see Risk 1). | Unlock, flash, relock, then adb. More steps; needs a computer. | Flash a ROM per device model. | Install an app. |
| Our ongoing work | One app. Google ships OS updates for 7 years on Pixel 8 and later. | One app, plus GrapheneOS compatibility questions. | Rebuild and re-sign per device for every Android security release. | One app. |
| Google policy risk | DPC allowlist (Risk 1); developer verification in 2027 (Risk 4). | Neither applies. | Neither applies. | Developer verification. |

**Why not C (custom ROM).** It costs the most, breaks banking apps, and gives us a per-device build treadmill. The closest commercial product does not do it either: Techless's own help center says Wisephone is "Samsung hardware with our WiseOS software and configurations", secured by "Samsung Knox" and "MDM security". Unenrolling it returns the phone "to a standard Android operating environment". The DeerFlow report's claim that Wisephone is a "de-Googled AOSP fork" is wrong. (Source: techless.zendesk.com, quoted in `prior-art-and-competitors.md`.) Light Phone, Punkt and Mudita do ship their own OS, but they are hardware companies.

**Why not D (launcher only).** It is a suggestion, not a lock. The user can change the default launcher, open Settings, boot into safe mode, or use adb. That is not what Beaux and the trusted person asked for.

**Why A before B.** For two non-technical US users, the things that hurt most day to day are banking apps, group texts with iPhones, and 911. Option A wins on all three. Option B wins on privacy and on Google policy risk. If Risk 1 turns out to block us on stock Android, B is the fallback, and we would then have to accept its costs.

## Controls the design relies on (all documented or code-confirmed)

| Goal | Control | Source |
|---|---|---|
| The Device Owner can't be removed by the user | A Device Owner "cannot be deactivated by the user… It also cannot be uninstalled" | DevicePolicyManager reference |
| Only approved apps can open | `setApplicationHidden` on everything not approved; `setLockTaskPackages` + lock task mode ("Other apps can't launch new activities") | DPM reference; developer.android.com lock task guide |
| No factory reset or OEM unlock from Settings | `DISALLOW_FACTORY_RESET`. AOSP `OemLockService` also forces "OEM unlocking" off while it is set | UserManager; AOSP OemLockService.java (code-confirmed) |
| No safe mode, adb, extra users, sideloading | `DISALLOW_SAFE_BOOT`, `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` | UserManager reference |
| No reset-and-walk-away | `setFactoryResetProtectionPolicy` tied to the trusted person's Google account (API 30, needs Play services) | DPM / FactoryResetProtectionPolicy reference |
| 911 always works | Never hide or suspend the system dialer; always include it in lock task; always set `LOCK_TASK_FEATURE_KEYGUARD` (AOSP `LockTaskController` lets emergency calls through only with it) | AOSP LockTaskController.java (code-confirmed) |
| Texting works | `setDefaultSmsApplication` (API 29) to Google Messages; never hide Google Play services | DPM reference; support.google.com/messages/answer/9363493 |

The full list of 29 escape routes, with the control and leftover risk for each, is in `research/evidence/webview-and-escape-threats.md`, Part 2.

## Risks that change the plan

**Risk 1. Google now blocks unapproved Device Owner apps during QR/NFC setup.** Google's help page "Approved Android Enterprise device policy controllers allowlist" says: "Only DPCs verified and approved by Android Enterprise are permitted to install apps during enterprise device enrollment… The installation of any non-approved DPCs… will be blocked", and the phone shows "App blocked to protect your device". The page gives no start date and does not say whether it covers adb. It also excludes "Solutions developed exclusively for the purpose of device monitoring". Plainphone restricts rather than monitors, but approval is not guaranteed. (support.google.com/work/android/answer/16694822, fetched and checked by the Lead on 2026-10-08.)
- **Effect:** the friendly "scan a QR code at setup" path may be closed to us on stock Android. The adb path (`dpm set-device-owner` right after a factory reset, before any account is added) is documented in AOSP and is very likely not covered, but that is unproven.
- **Plan:** the MVP sets up through adb, done by a setup helper. That is Beaux plus the team for the first two phones. Later we can wrap adb in a web page that talks to the phone over USB, the way GrapheneOS's web installer works, so a helper only clicks buttons. We can also apply to Google for allowlisting. Whether the allowlist blocks adb on a real Pixel needs a real device, which Beaux must approve.

**Risk 2. `DISALLOW_INSTALL_APPS` also stops app updates.** AOSP `PackageInstallerService.createSessionInternal` throws "User restriction prevents installing" for every install session, before it checks whether the install is an update. The javadoc adds that it "also prevents device owners and profile owners installing apps". The Lead read this directly from AOSP `main` on 2026-10-08. Hiding the Play Store also stops its updates, and Google Play services would go stale with it. No Android API means "updates yes, new installs no". This answers the brief's make-or-break question. The DeerFlow report's plan to "block the Play Store with DISALLOW_INSTALL_APPS" would have left banking and messaging apps unpatched.
- **Plan:** never set `DISALLOW_INSTALL_APPS` permanently. Keep the Play Store installed, enabled and signed in so it can update apps in the background, but leave it out of lock task and off the home screen so the user can't open it. Allowed apps that try to open Play links are blocked. If a new app appears anyway (for example, pushed from play.google.com on a computer), Plainphone hides it right away; lock task already stops it opening. The trusted person adds apps by unlocking Play for one supervised session. Whether Play keeps updating in the background under lock task is unproven; it is spike question Q2.

**Risk 3. The web can't be fully removed.** Android WebView can't be removed: with no WebView provider, apps that use it crash. Traffic from an app's in-app browser uses the same app identity (UID) as its normal traffic. Browser intents can't be redirected with `addPersistentPreferredActivity` (DPM docs: "invoking the browser… cannot be configured").
- **Plan, in three layers:**
  1. Hide every browser, the Google app and Gemini. Plainphone itself handles `http/https` links and shows "This link is blocked. Ask [trusted person]."
  2. Vet apps for web leaks before they go on the list. Each catalog entry gets a plain "web leak" note that the trusted person reads before approving.
  3. Optional DNS filtering through an always-on lockdown VPN. This is spiked now and shipped only if the spike says it is reliable.
- **Leftover risk we will state openly:** some web access inside approved apps, the Wi-Fi sign-in page used by hotels and airports, and a recovery-mode wipe.

**Risk 4. Google developer verification.** Google says verification for sideloaded apps on certified devices starts 2026-09-30 in four countries (not the US) and expands "globally… in 2027" (developer.android.com/developer-verification). Before any US release we will need to register, which may cost money. That is a spend decision for Beaux, and it does not block the MVP for Beaux and the trusted person.

## Build vs. reuse

No one has published an open-source "trusted person controls the allowlist" dumbphone. The enforcement layer does exist, though: Headwind MDM (Apache-2.0, Device Owner launcher plus a self-hosted panel, with lockdown and kiosk features in its paid Enterprise edition) and TimeLimit (GPL/AGPL, parental allowlist with Device Owner). We will **build one small Kotlin app** and borrow patterns from Google's TestDPC and Headwind (both Apache-2.0). We will not fork either, for three reasons:
- Headwind is built around a server and an enterprise feature set.
- Its lockdown features are closed.
- TimeLimit's license would force our licensing choice.

**Recommended license: Apache-2.0.** This keeps TestDPC, Headwind, RethinkDNS and Unlauncher code available to us. It is a lasting choice, so Beaux decides.

## Hardware

- **MVP:** an **unlocked** (not carrier-locked) Pixel, 9a or newer, on stock Android. These have 7 years of updates from launch (support.google.com/pixelphone/answer/4457705).
- **Not chosen yet:** the exact model, whether to buy new phones or reuse Beaux's and the trusted person's, and when to touch any real phone. All three are board decisions.
- **Samsung is not ruled out for later.** Wisephone runs on Samsung with Knox. We have not evaluated Knox (free vs. paid tiers), and it is not needed for the MVP.

## What would change this decision

1. **Q1 fails:** multi-app lock task can't support a normal phone day (calls, notifications, permission prompts, camera). Then we fall back to hiding apps without lock task, and re-check every escape route that lock task was covering.
2. **Q2 fails:** Play can't update approved apps unless the user can reach it. Then updates move to Plainphone's own updater, which works only for apps outside Play, or to a supervised weekly "update window".
3. **Real-device test shows the allowlist blocks adb provisioning on stock Pixel.** Then option B (GrapheneOS) becomes the MVP base, and Beaux must accept its costs.
