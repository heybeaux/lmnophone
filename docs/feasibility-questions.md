# Feasibility questions for the emulator spike

- Revision 6, 2026-10-08. Ranked after the PLA-3 review (F15, F17), then updated after PLA-4 (N1–N11), PLA-7 (R1–R8), PLA-8 (S1–S5) and PLA-9 (T1–T3).
- Device results so far come from the spare Motorola (PLA-5, `spikes/device-moto/RESULT.md`): **Q3 adb: yes** and **way back without a wipe: yes**. Everything else is still open.
- Owner: Plainphone Lead. Spike author: Android Platform Engineer. Reviewer: Verifier & Escape Artist.
- The questions are ranked by how much a "no" would change the plan. Each test ID maps to requirements in `docs/verification-matrix.md`.

## Spike rules

- **Emulator only.** Use Google Play system images at API 35 and API 36 (or the newest). If the API 37 image exists, test that too.
- **Throwaway code.** Everything lives under `spikes/` and is labelled throwaway.
- **Reporting.** Each question gets a `spikes/<id>/RESULT.md` with:
  - the result: yes / no / partly
  - the image build ID, and app versions
  - exact steps
  - logcat excerpts and screenshots
  - for every positive result, a **negative control** (a configuration that should fail, and does)
- **[device]** marks parts that need a real phone. They wait for Beaux to approve a specific phone. The emulator never stands in for them. In particular, the emulator never proves real 9-1-1 delivery, carrier RCS, or a manufacturer's extra features.

## Go / no-go: run first

**Q3. Can our unapproved Device Owner be set up?** *(device answer for the Motorola XT2415, Android 15: adb **yes**, PLA-5 A1. QR not tested. Re-run for each new model or build.)*
- adb `dpm set-device-owner` right after the first-boot wizard, with no accounts.
- QR provisioning on the same image: do we see "App blocked to protect your device"?
- **[device]** Repeat both on the approved phone.

**Q1. Can lock task, with in-task launches blocked, carry a normal day?**
- **Device results so far** (PLA-5 A3, partial, on the XT2415):
  - **Flag off:** App info opened inside the task (leading to Permissions and Notifications), and so did the share chooser and Google Photos.
  - **Flag on:** App info and the share chooser were blocked, and so were runtime permission prompts. The Photos route was not run.
  - **Emergency dialer:** it needs `com.android.phone` on the lock-task list. It opened with the flag off; with the flag on it has not been re-run.
  - **Photo sharing:** Camera → Photos → Share failed with the flag off. A direct share to Messages was tested with **text only**.
- **Follow-ups for this question:**
  - with the flag on: the Camera thumbnail → Photos route; the allowlisted emergency dialer screen (no call placed);
  - pre-granting permissions with `setPermissionGrantState`, including whether the Device Owner may grant sensor permissions on this build;
  - attaching a photo from inside Messages' own picker with the flag on;
  - whether lock task comes back after a reboot;
  - the power menu, tested with the physical button;
  - emergency calling from the lock screen, which needs a secure lock screen that the board approves.
- Run everything below twice: with `LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK` on, and with it off.
- **A normal day must work, with the flag on:**
  - incoming and outgoing calls;
  - notifications;
  - each required app working with the permissions chosen at approval, with **no runtime prompts**;
  - a photo taken, and **attached as an image** in Messages by a route that works;
  - the power menu and reboot;
  - our own Wi-Fi screen (`WifiManager.addNetwork` as Device Owner).

  Prompts with the flag off are a diagnostic, not a pass criterion. If a sensor permission can't be granted, an optional app gets a feature warning; a must-have app fails Q4.
- **Nothing outside may open:** Settings, a Settings panel, or Play, launched from an approved app with an implicit intent, an explicit component, the share chooser, or no new-task flag.
- **Build the UI exceptions list:** list every package whose screen we must allow, every package sharing a UID with one, and run a negative launch test on each.
- *If no:* revise the ADR. Hiding apps without lock task is **not** assumed safe.

**Q2. Do approved apps keep getting updates while the user can't reach Play?**
- Tests S1–S4 and S7 from `research/evidence/app-updates-under-lockdown.md`.
- **Monthly check (R6):** SMS failure, no service, permission revoked, dual-SIM with no default selected. Confirm the reminder retries and the home-screen line appears after 7 days.
- **Update source (N10):** preinstalled Google apps rolled back with `adb shell pm uninstall-system-updates <pkg>`, so Play has a real update waiting. Run the same app and account without restrictions as the comparison. If Play offers no update, the result is **inconclusive**, not a failure. Nothing is published to Play.
- **Forced vs. unattended:** run the forced update job (`cmd jobscheduler`) as a diagnostic, then separately run 72 hours unattended with no forcing. Only the unattended run counts as automatic updating.
- **S7:** measure the time from install to hidden. Check whether a deep link or the share sheet can open the new app during that gap.
- **Newer API:** on API 37, test whether `DISALLOW_INSTALL_UNKNOWN_SOURCES_INCLUDING_REGISTERED_APP_STORES` exists and blocks new Play installs while updates still run.
- *If no:* revise the ADR. A weekly update window is **not** assumed acceptable.

**Q4. Do the real must-have apps work, and is the web contained?**
- **Must-have apps:** use Beaux and the trusted person's actual list where it installs on the emulator. Current list (Telegram 4446/4448/4451): Beaux: Slack, Spotify, maybe Telegram. the trusted person: Gmail, Outlook.
  - Web-leak leads to check: Telegram's in-app browser; where Gmail and Outlook open links in HTML mail (in-app browser, Custom Tab, or handed to Android); Gmail's Chat and Meet tabs; whether Outlook's sign-in, and adding a Google account to Outlook, needs a web page that hidden browsers break. Test sign-in, signing in again, password reset, payment or 3-D Secure approval, notifications and updates, all with every general browser hidden and the block flag on.
- **Browser role and classifier:** does Plainphone get the browser role? Run the classifier against fixtures: a general browser, an app that only opens its own domain, and the never-hide packages.
- **Link matrix:** verified App Links, ordinary links, `intent:` URIs, custom schemes, Custom Tabs fallback.
- **Wi-Fi sign-in page (N5, go/no-go):** with CaptivePortalLogin hidden, an ordinary WPA2 network must connect and reach the internet. A network that needs sign-in must show Plainphone's message and allow forgetting it, with calls and Emergency unaffected.
- **Classifier fixtures (N3):** Plainphone itself, a general browser, an app that only opens its own domain, an http-only viewer, an app with a URL bar but no web intent filter, and the never-hide packages. Also an explicit intent to a hidden browser, and navigation inside an app's WebView, which is expected to be not intercepted.
- **Other reachable screens:** Google account, help and privacy screens reachable from Messages and Play services menus; keyboard GIF, search and help; accessibility tutorials; photo and file pickers.
- **Hidden Google app and Gemini:** what breaks? Do Circle to Search or Lens still appear?
- *A failure on a must-have app is a go/no-go failure, even if Q1 and Q2 pass.*

## Required before handover (Beaux's phone)

**Q5. Does the baseline escape list hold?**
- Escape list items: E01, E04, E05 (including a previously authorised adb host), E06, E12, E13, E15, E21, E25, E29.
- The Verifier's vectors F1–F14 from the PLA-3 review.

**Q6. Do boot, Plainphone replacement and policy damage recover safely?** *(N4: the result must be "no unapproved app opened in the foreground at any point during recovery", apart from the disclosed unknown-build exceptions: new routes inside allowed packages, and shared-UID eligibility. The user-installed emergency-filter fixture must fail unconditionally.)*
- Reboot and stay at the lock screen. Install a package before the first unlock. Kill Plainphone between policy steps. Update Plainphone to a build signed with the same key. Feed it a truncated policy and a policy with an unknown version.
- Unlock immediately after a reboot, and try to open an unapproved app with a queued notification, an explicit intent and a deep link before Plainphone's home screen appears.
- With both policy copies corrupt: the emergency-safe baseline must apply.
- **Unhide then sudden power loss (PLA-22).** On API 35 and 36 emulators, unhiding any app and then losing power within about 10 s, before Android writes its package settings, leaves system_server crashing on every boot. A phone in that state can't call 9-1-1, and only a recovery wipe brings it back. Hiding is safe, so the dangerous step is the start of a session. Mitigation B (a synchronous PackageManager flush right after every unhide) booted 3 of 3 times on the emulator. Before handover:
  - the Verifier reviews the result;
  - mitigation B is re-tested on the target build with a hard power-off;
  - a stress run (repeated session starts with a forced power-off) passes.
  A device test can brick the phone, so it needs Beaux's OK for that phone plus a recovery-wipe procedure, with Google accounts removed first so reset protection doesn't lock the phone.
- **Floor trust (T1).** An unapproved user-installed fixture declares the emergency-dialer filter. It must never join the floor or become openable, on a supported build, on an unknown build, and on an unknown build with corrupt policy.
- **OTA and unknown build (S1).** These need fixtures on our own emulator. Move from build A (with an inventory) to build B (without one). Set up B so that:
  - a new essential dependency matches the browser heuristic;
  - a package that was hidden on A becomes the emergency dialer.

  Then check, on B:
  - lock task is kept;
  - floor packages are unhidden;
  - no system package is newly hidden;
  - the notices and texts go out.

  Next, corrupt both policy copies and damage the journal, and reboot before first unlock. Check that the floor-only baseline applies, and that calls, the in-call screen, simulated SMS and alerts, and Emergency all still work. Finally, install a reviewed B inventory through the signed local updater and repeat the checks.
- A Plainphone update over local Wi-Fi that crashes on purpose: check the "Plainphone can't run" recovery path, and that emergency access still works.
- Check that hidden apps and restrictions survive a Play update of an allowed app, a Play update of a hidden app, and a system image update.

**Q7. Can turning Plainphone off finish without a wipe, and resume after interruptions?** *(device: releasing without a wipe is **yes** for the test-only spike, PLA-5 A2. The full restore list is still open.)*
- Kill the process after each item on the turn-off restore list, and again just before and just after Device Owner removal, then reboot. Check that it resumes, that "Off" is reported only when every step is done, and that photos and messages survive.
- Test that every item on the restore list is read back. Test that another home screen and the dialer resolve before Device Owner is removed. *If turning off can't avoid a wipe:* revise the promise and get Beaux's approval. Never fall back to a wipe silently.

**Q10. Does reset protection with the trusted person's `userId` block setup after a recovery-mode wipe?**
- Get the `userId` from People API `people.get(me)` using Google's "Try it" page.
- Set the policy, read it back, and send the `FRP_CONFIG_CHANGED` broadcast.
- Wipe from recovery, try the user's own account (must fail), then the trusted person's (must succeed). Emulator first.
- **[device]** Needs a separate board-approved procedure on the target phone. Policy readback, or waiting 72 hours on a new account, does not replace this test. **Never on the spare Motorola** (policy forbids recovery wipes there).

**Q12. Can a supervised session be escaped or left open? (N1: servicing go/no-go)**
- Turn the screen off, take an incoming call, kill Plainphone, reboot, let the deadline pass in the middle of an install, and leave nested Play, account and help screens open.
- After each, try to reach Play, approve an app, or use adb.
- **Failure model:** use a build that crashes on every start while a session is journaled. Measure how long the relaxation stays live, checked continuously, not only after Plainphone reopens. Repeat before the first unlock.
- Run everything for **both** session kinds, Play and Settings. In a Settings session, also go through: search, app info and defaults, account, help and privacy screens, keyboards and accessibility, connected devices, and alert settings. Try to change the default SMS app, Plainphone's SMS permission and the keyboards. Then confirm the protected-state check restores them.
- **What gets recorded (R1):**
  - whether revocation runs first on every start where Plainphone can run, including locked boot. A *no* here is a no-go.
  - for a build that crashes on every start, how long and how much stays reachable. This is **reported against the custody limit Beaux accepted**. It is not a pass/fail of "cannot happen". If Beaux has not accepted that limit, an independent revocation mechanism becomes a G0 question.
- **Plainphone update from local Wi-Fi:** wrong signer refused, lower version refused, adb never enabled.

**Q14. Does the emergency gate behave correctly?**
- Run the D5 / essential-calling UI test in every listed state, including the negative-control build, which must fail.
- Run with the block flag on, during setup, during a PIN lockout and during a Play session.
- Check that Alert Ready–style cell broadcasts still show where the emulator can inject them. Where it can't, record **inconclusive**, which blocks release until covered. Negative controls: Phone (or the More emergency entry) doesn't open the system dialer, dialer missing from lock task, alert display disabled. Each must fail the gate.
- States: before first unlock, lock screen, home via Phone, inside an approved app via Home then Phone, setup, PIN lockout, supervised session, Plainphone update, turning off, after restart, Plainphone can't start.
- **[device] Power-button SOS (Telegram 4487):** on the XT2415, does pressing power 5 times open Emergency SOS while Plainphone's lock task is active, with BLOCK on? Stop at the SOS countdown screen and cancel it. **Never let it place a call.** Do this only after confirming, by a settings readback or a dry run with lock task off, that the countdown can be cancelled in time. If it can't be made safe, stop and report. If it doesn't work under lock task, report it before the gate.
- **[device, before handover]** Emergency dialer in every state without placing a call; an incoming call answered and its keypad sending tones; an alert **observed** on screen (a scheduled Alert Ready test or an approved test method), or else the phone stays in custody or Beaux records acceptance of a narrower promise; manufacturer emergency components on the never-hide list. A live 9-1-1 call only under a separate procedure agreed with E-Comm or the carrier and approved by Beaux.

**Q8. Do texting and calling defaults work?**
- `setDefaultSmsApplication` → Google Messages persists.
- SMS and MMS arrive under lock task (emulator console).
- **[device]** RCS on the chosen carrier, group texts with iPhones, and E-Comm callback-style incoming calls.

**Q13. What does the phone's manufacturer add?** **[device]**, brand-specific.
- On the approved phone's brand, go through each extra feature: on Motorola, Secure folder, Cloned Apps, Smart Connect, Sidebar/Gametime, back-tap launcher, MotoApps/App Box installers (`research/evidence/canada-and-motorola.md`).
- Decide hide, keep or test for each.

## Optional (do not block the MVP)

**Q9. Is a DNS filter reliable?**
- An always-on lockdown VPN, with per-app DNS policy.
- `getConnectionOwnerUid` attribution of an app's WebView traffic.
- Does the Wi-Fi sign-in page bypass the VPN?
- QUIC drop and HTTPS-record stripping.

**Q11. Does a GrapheneOS emulator build answer Q1, Q3 and Q6?** Only if it costs under a day. Only relevant if Beaux picks a Pixel.

## Decided by policy, not by testing

- **Plainphone never obstructs emergency access.** This is a requirement, enforced by the behavioral gate (Q14).
- **No market-size claims** until there is a primary source.
