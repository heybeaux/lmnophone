# Feasibility questions for the emulator spike

- Revision 2, 2026-10-08. Ranked again after the PLA-3 review (F15, F17).
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

**Q3. Can our unapproved Device Owner be set up?** *(cheap; run first)*
- adb `dpm set-device-owner` right after the first-boot wizard, with no accounts.
- QR provisioning on the same image: do we see "App blocked to protect your device"?
- **[device]** Repeat both on the approved phone.

**Q1. Can lock task, with in-task launches blocked, carry a normal day?**
- Run everything below twice: with `LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK` on, and with it off.
- **A normal day must work:** incoming call, outgoing call, notifications, a runtime permission prompt, a photo, sharing from Camera to Messages, the power menu, reboot, and our own Wi-Fi screen (`WifiManager.addNetwork` as Device Owner).
- **Nothing outside may open:** Settings, a Settings panel, or Play, launched from an approved app with an implicit intent, an explicit component, the share chooser, or no new-task flag.
- **Build the UI exceptions list:** list every package whose screen we must allow, every package sharing a UID with one, and run a negative launch test on each.
- *If no:* revise the ADR. Hiding apps without lock task is **not** assumed safe.

**Q2. Do approved apps keep getting updates while the user can't reach Play?**
- Tests S1–S4 and S7 from `research/evidence/app-updates-under-lockdown.md`.
- **Repeatable update source:** our own test app, published to the emulator's Play account through internal app sharing if that is available. Otherwise use a public app and pin the versions. Time limit: 24 hours of emulator time, with Play's update job forced.
- **S7:** measure the time from install to hidden. Check whether a deep link or the share sheet can open the new app during that gap.
- **Newer API:** on API 37, test whether `DISALLOW_INSTALL_UNKNOWN_SOURCES_INCLUDING_REGISTERED_APP_STORES` exists and blocks new Play installs while updates still run.
- *If no:* revise the ADR. A weekly update window is **not** assumed acceptable.

**Q4. Do the real must-have apps work, and is the web contained?**
- **Must-have apps:** use Beaux and the trusted person's actual list where it installs on the emulator. Test sign-in, signing in again, password reset, payment or 3-D Secure approval, notifications and updates, all with every general browser hidden and the block flag on.
- **Browser role and classifier:** does Plainphone get the browser role? Run the classifier against fixtures: a general browser, an app that only opens its own domain, and the never-hide packages.
- **Link matrix:** verified App Links, ordinary links, `intent:` URIs, custom schemes, Custom Tabs fallback.
- **Wi-Fi sign-in page:** with CaptivePortalLogin hidden, does normal Wi-Fi still connect? Does a portal network fail gracefully?
- **Other reachable screens:** Google account, help and privacy screens reachable from Messages and Play services menus; keyboard GIF, search and help; accessibility tutorials; photo and file pickers.
- **Hidden Google app and Gemini:** what breaks? Do Circle to Search or Lens still appear?
- *A failure on a must-have app is a go/no-go failure, even if Q1 and Q2 pass.*

## Required before handover (Beaux's phone)

**Q5. Does the baseline escape list hold?**
- Escape list items: E01, E04, E05 (including a previously authorised adb host), E06, E12, E13, E15, E21, E25, E29.
- The Verifier's vectors F1–F14 from the PLA-3 review.

**Q6. Do boot, Plainphone replacement and policy damage recover safely?**
- Reboot and stay at the lock screen. Install a package before the first unlock. Kill Plainphone between policy steps. Update Plainphone to a build signed with the same key. Feed it a truncated policy and a policy with an unknown version.
- Measure the time to full policy after `BOOT_COMPLETED` (the limit is 60 seconds).
- Check that hidden apps and restrictions survive a Play update of an allowed app, a Play update of a hidden app, and a system image update.

**Q7. Can turning Plainphone off finish without a wipe, and resume after interruptions?**
- Kill the process after each of the 6 turn-off steps, then reboot. Check that it resumes, that "Off" is reported only when every step is done, and that photos and messages survive.
- Test `clearDeviceOwnerApp`. *If it fails:* the off switch is a factory reset, and setup says so up front.

**Q10. Does reset protection with the trusted person's `userId` block setup after a recovery-mode wipe?**
- Get the `userId` from People API `people.get(me)` using Google's "Try it" page.
- Set the policy, read it back, and send the `FRP_CONFIG_CHANGED` broadcast.
- Wipe from recovery, try the user's own account (must fail), then the trusted person's (must succeed).
- **[device]** Confirm on the approved phone.

**Q12. Can the supervised Play session and maintenance mode be escaped or left open?**
- Turn the screen off, take an incoming call, kill Plainphone, reboot, let the deadline pass in the middle of an install, and leave nested Play, account and help screens open.
- After each, try to reach Play, approve an app, or use adb.

**Q14. Does the emergency gate behave correctly?**
- Run the D4 UI test in every listed state, including the negative-control build, which must fail.
- Run with the block flag on, during setup, during a PIN lockout and during a Play session.
- Check that Alert Ready–style cell broadcasts still show (if the emulator console can inject them).
- **[device]** A real 9-1-1 test, with a procedure agreed with the carrier and E-Comm.

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
