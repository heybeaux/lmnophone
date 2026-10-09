# ADR 0001: How Plainphone locks a phone down

- Status: **Accepted as the plan of record.** Beaux approved it on 2026-10-08 at 21:28 UTC, through PLA-1 confirmation `eb7a21ba` on plan revision 1, together with the OpenSpec change `add-plainphone-spike-and-mvp`. Option A is still a candidate that the spike's go/no-go conditions below must confirm.
- Date: 2026-10-08
- Author: Plainphone Lead (Claude)
- Reviewer: Verifier & Escape Artist (OpenAI model)
  - Revision 1 was reviewed in [PLA-3](/PLA/issues/PLA-3): request changes, with 3 blockers, 12 major and 2 minor findings.
  - Revision 2 was reviewed in [PLA-4](/PLA/issues/PLA-4): request changes, with N1–N11 (1 blocker, 8 major, 2 minor).
  - Revision 3 was reviewed in [PLA-7](/PLA/issues/PLA-7) (R1–R8). Revision 4 was reviewed in [PLA-8](/PLA/issues/PLA-8) (S1–S5).
  - Revision 5 addresses S1–S5 and was reviewed in [PLA-9](/PLA/issues/PLA-9) (T1–T4). Revision 6 addresses T1–T4.
- Evidence: `research/evidence/*.md`. The DeerFlow report is leads only.
- Confidence labels from the evidence files carry over here:
  - **documented**: a primary source says so
  - **code-confirmed**: AOSP source shows it
  - **inferred**: follows from sources, but not tested
  - **unknown**: needs a test

## Who this is for

Beaux and the trusted person, in **[town], Canada**. Neither tinkers with phones. "Works for strangers" means three things:
- calls, texts, 9-1-1 and their essential apps keep working;
- recovery from mistakes doesn't need a developer;
- the leftover risks are written down in plain words before they agree to anything.

## Decision in one paragraph

**Leading candidate, not yet proven:**
- One Android app that is the phone's **Device Owner** and its **home screen**.
- Runs on a **certified stock Android phone with long security support** (model to be chosen by Beaux).
- It hides every app the trusted person has not approved, and runs approved apps in **multi-app lock task mode** with **in-task launches blocked**.
- It applies the anti-escape restrictions below.

We do not build a custom ROM. We do not ship a launcher-only version. The emulator spike decides whether this candidate becomes the decision. The go/no-go conditions are listed at the end, and any fallback that weakens containment or changes daily use needs a revised ADR and Beaux's approval.

## Options compared

| | A. Device Owner + home screen, certified stock phone | B. Same on GrapheneOS (Pixel only) | C. Custom ROM | D. Launcher only |
|---|---|---|---|---|
| Can the user escape? | Hard on paper. Safe mode, factory reset from Settings, OEM unlock, adb, extra users and launcher change all have documented controls (table below). A recovery-mode wipe is still possible; FRP is what limits it (Q10). | Hard on paper, plus a relocked bootloader with GrapheneOS keys. FRP behavior on GrapheneOS is **unknown** (it relies on Google Play services). | Hardest. Limits are built into the OS. | Easy. Settings, safe mode, launcher change and adb all work. |
| Banking and payment apps | Expected to pass Google certification. **Not proven** with every browser hidden and Settings blocked; that is test Q4. | Some fail. GrapheneOS isn't Google-certified and doesn't spoof the checks (grapheneos.org/usage, documented). | Many fail. | Work. |
| Texting with iPhones (RCS) | Through Google Messages, on carriers that support it. In Canada: Rogers/Fido, Bell/Virgin, Telus/Koodo, Freedom and Public do; Fizz does not (Apple's carrier list, documented). | Conditional: works in the Owner profile with extra Play services permissions (grapheneos.org/usage#rcs). | Depends on the build. | Works. |
| 9-1-1 location | Carrier location (CRTC rules), plus Google's Emergency Location Service, which has been live in Canada since May 2024 (Google Canada blog, documented). | Carrier location. ELS probably missing (inferred). | Depends on the build. | Carrier plus ELS. |
| Setup | Factory reset, then a helper runs one adb step (Risk 1). | Unlock, flash, relock, then adb. | Flash a ROM for each phone model. | Install an app. |
| Our ongoing work | One app, plus an escape review for each phone brand (OEM extras). | One app, plus GrapheneOS compatibility work. | A rebuild for every security release, per phone model. | One app. |
| Google policy risk | DPC allowlist (Risk 1); developer verification (Risk 4). | Neither applies. | Neither applies. | Developer verification. |

- **Why not C.** It costs the most, breaks banking apps, and puts us on a per-phone build treadmill. The closest commercial product does not do it either. Techless's own help center says Wisephone is "Samsung hardware with our WiseOS software and configurations" with "Samsung Knox" and "MDM security", and that unenrolling returns the phone "to a standard Android operating environment" (documented). That does not prove we can match Knox using only public APIs. C only gets better if essential controls turn out to need OS changes **and** Beaux accepts the cost of maintaining a ROM.
- **Why not D.** It is friction, not a lock. It only wins if Beaux and the trusted person decide they want voluntary friction instead.
  - *Note (2026-10-09, PLA-30, pending Beaux's approval):* Beaux asked for voluntary friction as a **choice per phone**, not instead of A. It is built as "self-managed mode" on the same Device Owner app. See "Notes: option D as a mode, and who can relax the lockdown" below.
- **Why A before B.** For these two users, what matters most day to day is: the apps they need, texting with iPhone users, 9-1-1, and recovery that doesn't need a developer. A is expected to do better on all four, but none of that is proven until the spike and their real app list are tested (Q4). B gets better if privacy or independence from Google matters more to them **and** the same test list passes on B. B also needs a Pixel.

## Controls the design relies on

| Goal | Control | Source / confidence |
|---|---|---|
| Plainphone can't be removed by the user | A Device Owner "cannot be deactivated by the user… It also cannot be uninstalled" | DPM reference (documented) |
| Only approved apps open | `setApplicationHidden` on everything not approved. Lock task mode limited to approved packages. **`LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK` (API 30)** so that no other app's screen can start inside an approved app's task. | DPM reference; lock task guide ("can start new activities as long as the activity doesn't start a new task", so the block flag is needed) (documented). Whether a normal day still works with it on is **unknown** (Q1). |
| No factory reset or OEM unlock from Settings | `DISALLOW_FACTORY_RESET`. AOSP `OemLockService` clears the "allow OEM unlock" permission while it is set. It does **not** relock a bootloader that is already unlocked, so setup must check the bootloader. | UserManager (documented); AOSP 15 OemLockService L97–109 (code-confirmed, also checked by the Verifier) |
| No safe mode, adb, extra users or sideloading | `DISALLOW_SAFE_BOOT`, `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` | UserManager (documented) |
| A wipe doesn't hand the user a free phone | `setFactoryResetProtectionPolicy` with the **trusted person's Google `userId`**, then the `FRP_CONFIG_CHANGED` broadcast to Play services. The `userId` comes from People API `people.get(me)`. New accounts may take 72 hours. An empty list falls back to whichever accounts were on the phone, which would include the user's own. | developer.android.com/work/dpc/security (documented). Whether it really stops setup after a wipe is **unknown** (Q10, a gate before handover). |
| Plainphone never gets in the way of 9-1-1 | Never hide, suspend or disable the system dialer, Telecom, the cell broadcast receiver, Play services or the default SMS app. The dialer is always in lock task. `LOCK_TASK_FEATURE_KEYGUARD` is always set. Plainphone never filters incoming calls. A test that **actually taps through** the emergency flow blocks every release. | AOSP 15 LockTaskController L387–401 has a keyguard emergency exemption (code-confirmed). That is a rule for launching screens, **not** proof that a call goes through: real calls and location need a real phone. |
| Texting works | `setDefaultSmsApplication` (API 29) → Google Messages. Never hide Play services. The phone plan needs data for RCS and MMS. | DPM reference; support.google.com/messages/answer/9363493 (documented) |

The escape threat list is in `research/evidence/webview-and-escape-threats.md` (E01–E29), `research/evidence/canada-and-motorola.md` (Motorola extras), and the Verifier's review (F1–F17 in PLA-3).

## Risks that shape the plan

**Risk 1. Google blocks unapproved Device Owner apps during QR/NFC setup.** Google's "Approved Android Enterprise device policy controllers allowlist" page says "The installation of any non-approved DPCs… will be blocked" during enrollment (documented, checked by both reviewers).
- The page gives no start date, does not mention adb, and lists exclusions. Not being a monitoring app does **not** make us eligible on its own.
- **Device result (PLA-5 A1, 2026-10-08):** on the board-approved spare Motorola (XT2415-1, certified stock Android 15, patch 2026-08-01), our unapproved test-only DPC became Device Owner over adb: `Success: Device owner set to package com.plainphone.spike.dpc/.AdminReceiver`. Logcat showed no allowlist or integrity message.
  - This covers adb on this model and build only. QR was not tested.
  - The allowlist could still be extended to adb later, or behave differently on other phones. The setup script therefore checks the model and build, and Q3 is re-run for each new model.
- **Device result (PLA-5 A2):** `dpm remove-active-admin` and the app's own `clearDeviceOwnerApp()` both returned the phone to unmanaged without a wipe.
- **Plan:** the MVP is set up through adb by a helper.

**Risk 2. "Block installs" also blocks updates.** AOSP `PackageInstallerService.createSessionInternal` rejects every new install session for a restricted user before it checks whether the install is an update. The javadoc says the restriction "also prevents device owners and profile owners installing apps" (code-confirmed by the Lead and the Verifier).
- That Play's own update path is blocked too is a **strong inference** (S1).
- No API means "updates yes, new installs no" on API 35/36. A new constant, `DISALLOW_INSTALL_UNKNOWN_SOURCES_INCLUDING_REGISTERED_APP_STORES`, appears in the current UserManager reference marked "version 37.2". It is not in AOSP `main` as fetched, and is not assumed (Q2).
- **Plan:**
  1. Keep Play installed, enabled and signed in to the **user's own** account, so it can update apps in the background.
  2. Keep Play out of lock task and off the home screen.
  3. Hide any app that wasn't approved. This includes first-boot pushes.
     - On the spare Motorola, the committed A0 table records **7 pushed installs plus 1 preload** within 8 minutes of first boot, with Wi-Fi and no account: 4 Digital Turbine games; Google One, Fit and Wallet; and Facebook preloaded. The engineer's comment said 5 games; PLA-5 is asked to reconcile the count.
     - So Plainphone sweeps all apps while the phone has **no network at all**: the script checks this, and the SIM is inserted only afterwards.
  4. The trusted person runs a supervised update check once a month, in Play's own update list. Plainphone can't tell on its own whether a newer version exists.
- **What we promise:** an unapproved app can't be **opened**. We do not promise that one can't be **installed**, or that it never runs any code. If Beaux needs "never runs any code", this architecture can't promise it; see the go/no-go conditions.

**Risk 3. The web can't be fully removed.** WebView can't be removed. An app's in-app browser traffic shares that app's network identity. Browser links can't be redirected with `addPersistentPreferredActivity` (all documented).
- **Plan:**
  1. Hide general-purpose browsers, the Google app, Gemini, and **the Wi-Fi sign-in page (CaptivePortalLogin)**.
     - **Wi-Fi networks that need a sign-in page are unsupported in the MVP**, with no exception session. Plainphone says so plainly and offers to forget the network.
     - **Board decision for later (Beaux, Telegram 4481, 2026-10-08, PLA-17).** Beaux (Telegram 4481, 2026-10-08, PLA-17 card) approved the **direction**: a Wi-Fi sign-in window that Plainphone ships (a companion app with no admin rights; Android's sign-in app stays hidden), which the user can open alone once a trusted person has switched it on with the PIN. Limits: a 5-minute target per try, 3 tries in 24 hours, a text to the trusted person each time with no Wi-Fi name. He accepts that a hotspot can show other websites for the whole window and that some sign-in pages (Google account, bank app) won't work. It is built **after the MVP core**, in a follow-up OpenSpec change shared with the general web window (Telegram 4481, PLA-14), after an emulator spike and cross-model review, and Beaux approves that change before build. Details: `docs/wifi-signin-recommendation.md` revision 3.
       - This is an explicit exception to the fresh-PIN rule for relaxing the lockdown, and to web containment. It is **not** a D2 session: no device policy changes during use, so it avoids the observed PLA-22 unhide race. That claim depends on the spike's audit.
       - Fallbacks that unhide Android's sign-in app are **not** approved. Any fallback goes back to Beaux.
       - Wi-Fi calling: used sometimes. Checked on ordinary Wi-Fi before handover (task 6.3).
  2. Plainphone handles outside links that no approved app claims, and shows "This link is blocked". Navigation inside an approved app's own WebView is not intercepted.
  3. Every app on the list gets a web-leak note, reviewed per app version, that the trusted person reads before approving.
  4. A DNS filter is spiked only. It does **not** count as a protection in this MVP.
- **Told openly:**
  - web pages inside approved apps;
  - Google account or help screens inside required system components, if testing finds any;
  - a recovery-mode wipe.
- **Board decision on a found leak (Beaux, Telegram 4463, 2026-10-08).** Outlook 5.2639.2 (PLA-5 B-Q4): Privacy & Cookies opens Outlook's own WebView, which follows off-site links under lock task with BLOCK on, before sign-in and again after sign-out. Beaux **accepts** it. Outlook stays in the catalog for the trusted person, with a plain web-leak note in the catalog and setup guide.
  - The decision covers Outlook only. If a later Outlook version makes the route easier (for example a visible browse button), it goes back to Beaux.
  - **Rule for later gate calls.** Beaux: "The overall goal is to cut down on distractions, not make the phone unusable." A small web leak that takes deliberate effort, inside an otherwise useful app, is weighed against usability. It is not a failure by itself. Leaks that are easy to reach, or that change what an app is for, still go to Beaux.

**Risk 4. Google developer verification.**
- Canada is in Google's "2027 global" wave; no month is given (documented).
- Installs over adb need no verification (Google FAQ, documented).
- Google lists a free limited-distribution option for up to 20 devices, which could cover our two phones. To be evaluated, not assumed. Any paid registration is Beaux's decision.

**Risk 5. Phone support life.** Beaux's current phone, a moto g power 5G (2024), gets its last security update in **March 2027** and will not get Android 16. Motorola's own page says "Security updates will stop on March, 2027" and "Next OS: Android 15" (documented, checked by the Lead).
- "No Android 16" is our reading of Motorola's published plan. Motorola notes "Channels and regions may have different launch dates", so it needs confirming for the Canadian build.
- A locked-down phone people keep for years must not run out of patches within months. So it is **not** recommended as the MVP target.
- Beaux has made it a **spare** and approved it for device spikes (Telegram 4419). Rules: Settings resets only, no recovery wipes, test-only DPC. The spike is [PLA-5](/PLA/issues/PLA-5).

## Build vs. reuse

We did not find a published open-source "trusted person controls the allowlist" phone. The enforcement layer does exist:
- **Headwind MDM** (Apache-2.0): a Device Owner launcher plus a server. Its lockdown and kiosk features are in the paid Enterprise edition.
- **TimeLimit** (GPL/AGPL): a parental allowlist that can use Device Owner.

**Plan:** we build one small Kotlin app and borrow patterns from Google's TestDPC and from Headwind (both Apache-2.0). We do not fork either, because Headwind assumes a server and its lockdown features are closed.

**License: Apache-2.0**. Beaux decided this in Telegram 4446/4448.

**Plainphone's own updates.** No adb maintenance mode. The helper's computer serves a signed APK over local Wi-Fi. Plainphone checks that it is signed with the pinned key and has a higher version, then installs it silently as Device Owner after a fresh PIN entry. Release builds are `testOnly=false` and `debuggable=false`.

## Device results so far (spare Motorola XT2415, Android 15, [PLA-5](/PLA/issues/PLA-5))

- **A1 / Q3.** Our unapproved, test-only Device Owner was set up over adb.
- **A2.** It was released without a wipe.
- **A0.** At first boot, with Wi-Fi on, 7 apps were pushed and 1 was preloaded.
- **A3, lock task (partial):**
  - **With `BLOCK_ACTIVITY_START_IN_TASK` off,** three leaks opened inside the locked task: App info (leading to Permissions and Notifications), the share chooser, and Google Photos from the Camera thumbnail.
  - **With it on,** App info and the share chooser were blocked, and so were runtime permission prompts. The Photos route was **not run** with it on. So Plainphone plans to pre-grant permissions at approval; that is **not tested yet**.
  - **Emergency dialer:** blocked in both modes until `com.android.phone` was on the lock-task list. Then it opened with the flag off; it was **not re-run** with the flag on. The runtime emergency floor adds the system emergency-dialer package automatically.
  - **Direct share to Messages:** a **text** share worked. An image attachment was not tested.
  - **Quick Settings** showed no tiles.
  - **Still open:** reboot, power menu, lock-screen emergency, calls and SMS.

## Hardware

**Board decision (Beaux, Telegram 4446/4448, 2026-10-08, relayed by Scout).** The spare moto g power 5G (2024), XT2415-1, is the device for **both the spike and the MVP build**. Choosing a daily-use phone is deferred, and it needs its own OK before any handover. LineageOS may be considered later as an alternative base, but is not planned now.

**What that means:**
- The MVP is built and tested on the spare. It is **not** handed over for daily use on that phone, because its security updates end in March 2027.
- On the spare, MVP builds stay **test-only** (removable over adb) until the off switch (Q7) is proven on the device. A release build (`testOnly=false`) on the spare needs a separate board OK. This follows Scout's rule against locking the spare out of adb.

**Requirements for the eventual daily-use phone (Beaux decides later):**
1. Sold unlocked in Canada, Google-certified, stock Android.
2. At least 5 years of security updates left on the manufacturer's own page.
3. Bootloader locked, with verified boot confirmed by Plainphone during setup.
4. As few manufacturer extras as possible. Each brand adds escape routes; Motorola alone adds Secure folder, Cloned Apps, Smart Connect, Sidebar/Gametime and background installers (`canada-and-motorola.md`).

**Candidates:**
- **Pixel 9a or newer.** Fewest extras and 7 years of updates. Also keeps GrapheneOS (B) open.
- **Samsung A-series.** Wisephone's choice. Many extras; Knox is not evaluated.

**Support obligation (S1).** Each system update produces a new build, and each new build needs a reviewed inventory delivered as a signed Plainphone update.
- Until that inventory arrives, the phone runs in a safe mode for unknown builds:
  - lock task stays on;
  - the emergency floor is protected;
  - no system app is newly hidden;
  - the trusted person and the helper are told.
- Updates are postponed where Android allows; some security updates may still install.
- Beaux needs to accept this ongoing team obligation as part of choosing the phone model.

Nothing touches a real phone without Beaux's approval for that specific phone.

## Go / no-go: what would change this decision

The candidate becomes the decision only if, on the emulator, and later on one phone Beaux approves:

1. **A normal day works under lock task with in-task launches blocked (Q1).** That means: calls; notifications; each required app working with the permissions chosen at approval and **no runtime prompts**; a demonstrated way to attach a photo in Messages; the camera; and our own Wi-Fi screen. Runtime prompts with the flag off are a diagnostic only.
   - *If not:* a revised ADR. Hiding apps alone is **not** assumed safe; it needs its own escape review.
2. **Approved apps update without the user reaching Play (Q2).**
   - *If not:* a revised ADR. A weekly supervised update window is **not** assumed acceptable.
3. **Beaux and the trusted person's actual must-have apps work end to end (Q4):** sign-in, signing in again, password reset, payment approval, updates and notifications. All under the full policy, with no regular helper visits.
4. **Recovery works:** turning Plainphone off (Q7) and reset protection (Q10) both behave as the specs say.
5. **Leftover web access is limited to what the trusted person accepted in writing.**
6. **Setup and support effort stays within the guide's limits.**
7. **Beaux explicitly accepts the session custody limit.** If Plainphone can't run for any reason while a supervised session is open, nothing revokes the relaxation automatically, and it stays until a helper fixes the phone. The reason could be a real bad update, a storage or startup fault, or a crash loop.
   - **What Q12 is for.** Q12 measures what is reachable in that state, and confirms that revocation runs first whenever Plainphone *can* run. It cannot prove the failure never happens.
   - **Handback.** The phone is never handed back until the policy reads back correctly.
   - **If Beaux does not accept this:** an independent revocation mechanism becomes a G0 feasibility question, and the ADR is revised.

**If the adb setup route is blocked on a real phone (Q3):**
- This alone does not switch us to B.
- B must first pass the same list of checks (1–6), on a Pixel.

## Notes: option D as a mode, and who can relax the lockdown (PLA-30)

*Added 2026-10-09 by the Lead. Pending Beaux's approval of the OpenSpec change `add-copy-test-product-decisions`. The architecture (option A) does not change.*

Beaux's copy-test answers (card `c60170d6`, Telegram 4481) touch two promises in this ADR.

**1. Option D comes back as a mode, not as the architecture.**
- A phone is **supervised** (one or two trusted people) or **self-managed** (the user is their own trusted person).
- Self-managed mode keeps everything in option A: Device Owner, hidden apps, lock task with in-task launches blocked, restrictions, emergency floor, web containment. Only who can relax it changes.
- **It is friction, not a lock**, and Plainphone says so in those words. The user holds the PIN. Opening up needs a 10-minute wait, is limited to 2 times a day, closes at the session deadline, and can never be made permanent. Making it stricter is instant; making it looser waits 24 hours.
- Keeping the Device Owner is what makes this better than option D alone: the friction can't be skipped through safe mode, adb or a launcher change.
- Reset protection names the user's own account in this mode, so a recovery-mode wipe gives the user a plain phone. That matches the friction promise and is disclosed.

**2. Who can relax the lockdown.** "A supervised session is the only way the lockdown is relaxed" still holds, with these changes:
- **Two trusted people, as equals.** Either can approve apps and open sessions alone. Each has their own PIN, lockout, recovery code and reset-protection ID. Both get texts.
- **The user**, in self-managed mode only, through an elevation: a supervised session with friction, under the same journal, deadline, revoke-on-every-start and protected-state rules.
- **Changes that can't be undone afterwards wait 24 hours:** turning off, changing who the trusted people are, and switching to self-managed. Texts go out at once, and either trusted person (or anyone holding the phone) can cancel. In supervised mode, a trusted person's recovery code skips the wait. In self-managed mode, nothing does.
- **The user alone can never change who the trusted people are.**
- **PLA-22 mitigation B** becomes step 3 of the session contract, with a low-battery guard.
- The PLA-17 web and Wi-Fi sign-in windows would be a further relaxation path. They are a separate change.

**What this does not change:** go/no-go conditions 1–7, the emergency promises, the custody limit Beaux accepted (it now also applies, unwatched, to self-managed elevations, and that is disclosed), and the device approval rules.

## Review response (revisions 2 and 3)

Each PLA-3 finding, and where it is addressed:

| PLA-3 finding | Where addressed |
|---|---|
| F1 in-task launch | `BLOCK_ACTIVITY_START_IN_TASK` required (device-lockdown); design D1 and D6 |
| F2 911 gate | A behavioral gate with a deliberately broken test build that must fail it (essential-calling-and-texting) |
| F3 FRP | The trusted person's `userId`, read back, and a handover gate (device-lockdown, guided-setup); Q10 promoted |
| F4 browser classifier | General browsers vs. apps that only open their own website; link matrix (web-containment) |
| F5 never-hide vs. may-show-UI | Split into two lists; inventory plus negative launch tests (D4, device-lockdown) |
| F6 Play session | A step-by-step session design (approved-apps, D2) |
| F7 reactive hiding | Guarantee narrowed to "no launch"; measured; fail-safe (approved-apps) |
| F8 boot and policy | Direct Boot, atomic policy, last-known-good, readback (device-lockdown, D8) |
| F9 PIN | Persistent attempt count, a ceiling on delays, KDF parameters, recovery code entropy (trusted-person-controls) |
| F10 off switch | Resumable and authenticated; restores only what Plainphone changed (trusted-person-controls) |
| F11 setup | A helper-custody phase, bootloader check, live adb check (guided-setup) |
| F12 confused deputy | Untrusted input rules, separate link entry point (trusted-person-controls, web-containment) |
| F13 captive portal and system surfaces | Graded major; sign-in page hidden by default; system screens tested and disclosed |
| F14 updates | Setup checks, warnings when apps go stale, maintenance mode for Plainphone's own updates (approved-apps, trusted-person-controls). *Maintenance mode was replaced in revision 3; see N2.* |
| F15 triggers | The go/no-go list above |
| F16 wording | Fixed here; errata added to the evidence files |
| F17 matrix | `docs/verification-matrix.md`; gates re-ranked in `docs/feasibility-questions.md` |

**Revision 3 (PLA-4 N1–N11):**

| PLA-4 finding | Where addressed |
|---|---|
| N1 temporary authority while Plainphone is down | One shared contract for supervised sessions: journal written first, revoked at every start including locked boot, custody wording, Q12 as a go/no-go, no automatic-close promise (trusted-person-controls, D2) |
| N2 maintenance mode | adb maintenance cut. Updates come from the helper's computer over local Wi-Fi with a pinned signature and version check. Release builds non-debuggable. Signing-key custody named (trusted-person-controls, D6) |
| N3 classifier forbids Plainphone | Plainphone and never-hide packages excluded first. Probe uses `MATCH_ALL` plus a catalog review. Link promise limited to outside implicit links; in-app WebView not intercepted (web-containment, D4) |
| N4 boot interval and background code | Invariant from the first interactive frame. Built-in safe policy. No time limit promised for background code (device-lockdown, approved-apps) |
| N5 Wi-Fi sign-in page | Unsupported, with a plain message and "forget network" (web-containment) |
| N6 recovery and off switch | People API steps on the trusted person's own device. Their ability to sign in elsewhere is checked. Backup person option. Full restore list. Successor home screen and dialer confirmed before removing Device Owner. No silent wipe (device-lockdown, guided-setup, trusted-person-controls) |
| N7 PIN schedule | Exact delays. Rejected entries during a delay aren't counted. Remaining delay survives a reboot. Recovery code has its own counter (trusted-person-controls) |
| N8 emergency coverage | OEM emergency components on the never-hide list. All states, including "Plainphone can't run" and before first unlock. Alert negative control. Real-device handover checks with callback keypad (essential-calling-and-texting) |
| N9 update health | Per-app record, monthly texted update check in Play's own list, Play services 30-day warning; no claim to know about newer versions (approved-apps) |
| N10 Play test source | Preinstalled Google apps rolled back with `pm uninstall-system-updates`, compared against an unrestricted run. "Inconclusive" if Play offers no update. Publishing removed (feasibility Q2, tasks 1.2) |
| N11 evidence and wording | Canada evidence superseded notes. RCS works over Wi-Fi. "No Android 16" kept as an inference. Spare-device facts. No Q10 recovery wipe on the spare (evidence, ADR Risk 5) |

**Revision 4 (PLA-7 R1–R8):**

| PLA-7 finding | Where addressed |
|---|---|
| R1 crash-loop gate contradiction | One contract. The custody limit covers any reason Plainphone can't run, and Beaux must accept it explicitly. Q12 measures and does not "prove". ADR go/no-go #7, Q12, the matrix and task 3.5 now agree. |
| R2 Settings session scope | A table of packages per session kind. A protected-state check before handback restores protections and keeps ordinary changes. Q12 covers both kinds. In-session web content disclosed. (trusted-person-controls) |
| R3 inventory, baseline, sweep | One versioned build inventory, compiled into the app, lists the never-hide dependencies and minimum UI entries. The emergency-safe baseline is built from it. No inventory means no sweep and no Ready. A damaged journal means revoke everything. The sweep runs only with no network active, which the script checks, and the SIM goes in afterwards. (device-lockdown, guided-setup) |
| R4 updater binding | Exact package name, valid signature, pinned signer and a strictly higher `longVersionCode`. The exact bytes are staged in private storage and installed through a session bound to Plainphone's package name. Size, timeout and redirect limits. Install success is checked separately from a working start. Key custody, a lost-key procedure and a compromised-key procedure. No promise of rollback. (trusted-person-controls) |
| R5 Alert Ready at handover | The alert must be observed on the target build before handover. Otherwise the phone stays in custody, or Beaux accepts the narrower promise. An emulator that can't inject an alert gives "inconclusive", which blocks release. (essential-calling-and-texting) |
| R6 monthly check | Due date and last completion are stored. Daily retries for 7 days. If the check is overdue, a single line appears on the home screen. Completion needs a PIN after reviewing Play's list. SMS setup is tested end to end at handover. (approved-apps, trusted-person-controls, guided-setup) |
| R7 People API account selection | The trusted person picks the exact account in Google's picker, not by display name. New accounts wait 72 hours and are re-tested. The guide is dry-run with accounts that share a display name. (guided-setup) |
| R8 counts and references | A0 count corrected to 7 + 1, with a reconcile request to PLA-5. Q7 step count, Q14 and matrix design references fixed. The stale 60-second deadline removed. Task 2.1 split. |

**Revision 5 (PLA-8 S1–S5, plus device A3):**

| PLA-8 finding | Where addressed |
|---|---|
| S1 OTA and inventory lifecycle (approval blocker) | A runtime emergency floor that doesn't depend on any inventory or file. A system-update lifecycle: postpone, text on pending update, team-owned inventory delivered by signed update. A fail-safe mode for unknown builds that hides no new system apps. A baseline for unknown build plus corrupt policy. A return-to-normal condition. Q6 fixtures. Matrix rows. (device-lockdown, web-containment, design D9) |
| S2 emergency matrix (approval blocker) | Matrix rows, design D5 and the spec now say the same thing: alert observed, otherwise inconclusive, otherwise a recorded build-scoped exception that states the negative-control status. "Alerts always shown" qualified. (essential-calling-and-texting) |
| S3 protected-state check | A fixed table of invariants with readback, repair and failure action. Ordinary preferences are kept. A failed repair never blocks calls or Emergency. (trusted-person-controls) |
| S4 offline setup | Physical-SIM and eSIM steps. Every network type is checked. A plain stop when a model can't finish setup offline. The pre-sweep check is local only, with real call and SMS checks after reconnecting. Total helper time reported. (guided-setup) |
| S5 synchronisation | Home-screen notice-line exception and test. A single rule for when the overdue line appears, and its removal. Matrix rows 25/28. Design D8 account picker and SMS check. Design D9 Direct Boot list. Headers updated to revision 5. |
| Device A3 | The block flag is confirmed necessary. Permissions are decided at approval and pre-granted with `setPermissionGrantState`. `com.android.phone` is in the floor. Photo-sharing and reboot follow-ups are in Q1. |

**Revision 6 (PLA-9 T1–T4):**

| PLA-9 finding | Where addressed |
|---|---|
| T1 floor trust (major) | Floor members come only from OS-managed identities and `MATCH_SYSTEM_ONLY` queries, and must be system apps; a self-declared filter never counts. A separate emergency UI subset (system dialer, emergency dialer, in-call UI) is the only part on the lock-task list. "Unopenable" is qualified for the same-UID case. An untrusted-fixture scenario is added, plus a Q6 test. (device-lockdown, web-containment, design D9) |
| T2 A3 claims | Every copy now states only the recorded test cells: Photos not run with the flag on; the emergency dialer not re-run with it on; direct share was text only; pre-granting not tested. Q1 follow-ups added. |
| T3 Q1 gate | Daily-use pass is judged with the flag on: required functions with the permissions chosen at approval, no runtime prompts, and a demonstrated image attachment. Prompts with the flag off are diagnostic only. |
| T4 approval packet | Rewritten: requirements instead of absolute claims; custody without "rare"; background activity "without a guaranteed time limit"; the update obligation and the unknown-build residual risk; the monthly check and other recurring human work; no rollback, the compromised-key procedure, and recovery-code custody; exceptions are build-scoped; spare-device policy preserved. |
| S3/S4 leftovers | An alert-configuration row in the protected-state table (`DISALLOW_CONFIG_CELL_BROADCASTS`); "usable" changed to "handed back", with Emergency never gated; the eSIM step order fixed. |
