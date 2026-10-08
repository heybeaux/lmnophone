## Context

- **Users.** The first phones belong to Beaux and the trusted person, in [town], Canada. Neither is technical. Each is likely the other's trusted person.
- **Goal.** A calm phone that runs only approved apps, with the trusted person deciding which. It should feel calm, not punished.
- **Architecture.** `docs/decisions/0001-architecture.md` (revision 2) gives the leading candidate:
  - a Device Owner app that is also the home screen
  - on a certified stock Android phone with long security support
  - the phone model is Beaux's decision
- **Evidence.** In `research/evidence/`.
  - Every requirement traces to a source or a named test in `docs/verification-matrix.md`.
  - Revision 1 was reviewed in PLA-3. This revision answers findings F1–F17.
- **Team policy constraints.**
  - Emulator only, until Beaux approves a specific phone.
  - No remote repository.
  - No paid services.
  - Every artifact gets a review from the other model family.

## Goals / Non-Goals

**Goals:**
- **One app (one APK).** The Device Owner, home screen, trusted-person screens and blocked-link screen live in a single package.
- **No unapproved app can be opened** through any route we tested. Leftover routes are named and accepted in writing by Beaux.
- **Plainphone never gets in the way of 9-1-1**, ordinary calls, texts, or emergency alerts.
- **Approved apps stay up to date.** If they stop updating, the trusted person sees it.
- **Every state can be recovered from**, without a developer: lost PIN, interrupted setup, a Plainphone crash or update, turning it off, and a wipe.
- **Setup takes about 30 minutes for a helper.** The clock runs from the first adb command to the green handover check, on a phone that is already backed up, reset and on Wi-Fi.

**Non-Goals:**
- **Remote portal and server.** A later change; hosting means spend.
- **DNS/VPN filter.** Spike Q9 only. It is not counted as protection.
- **Watching the user.** No time limits, usage reports, or monitoring of messages or location. We restrict; we do not watch.
- **Not supported:** work profiles, multiple users, tablets, GrapheneOS, and any phone model Beaux hasn't approved.
- **Never "no unapproved code runs".** The guarantee is "no unapproved app can be opened" (D2).

## Decisions

### D1. Lock task with in-task blocking, plus hiding

**Hiding.** Unapproved apps are hidden with `setApplicationHidden`.

**Lock task.** Approved apps run in multi-app lock task mode with these features:
- `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO`
- `BLOCK_ACTIVITY_START_IN_TASK` (API 30)
- never `OVERVIEW`

Without the block flag, Android lets any resolvable screen start inside an approved app's task, which includes Settings and Play (PLA-3 F1, documented).

**Exceptions.** Any system screen a normal day needs, such as permission prompts or the in-call screen, is found by Q1. It is added by package name to a reviewed **UI exceptions list**, and each entry gets a test proving nothing else opens.

*Alternative:* hiding alone. Not assumed safe; it would need its own escape review and a revised ADR.

### D2. App updates: Play kept but out of reach; promise "can't be opened"

**Play stays.** The Play Store stays installed, enabled and signed in to the **user's** account.
- It is never in lock task outside a supervised session.
- `DISALLOW_INSTALL_APPS` is never set permanently; AOSP shows it also blocks updates.

**Setup checks** that Play auto-update is on (helper step, shown on screen). It also records a baseline of each approved app's version.

**Staleness.** Trusted-person mode shows "Last updated" for each app. If no approved app **and** Play services have updated in 30 days, it says "Updates may be stuck" and offers a supervised "Check updates" session.

**Unapproved installs.**
- Any app installed by any route stays hidden until approved.
- Plainphone hides it on the package-added broadcast, and also re-checks at boot and every 15 minutes.
- If hiding fails, lock task still stops the app from opening.
- The time from install to hidden is measured in test S7.
- **Accepted and disclosed:** an unapproved app may run background code for a short time.

**Supervised Play session (a small state machine):**
- `idle → active(token, deadline) → closing → idle`
- **Start:** a fresh PIN entry.
- **Deadline:** 10 minutes, stored and checked against elapsed time since boot.
- **Ends on** any of:
  - the screen turning off
  - Plainphone restarting
  - a reboot
  - leaving Play
  - the deadline passing
- **Closing** removes Play from lock task and checks the policy has been restored (readback), before the user sees the phone.
- **Approving** the new app ("Allow") needs a fresh PIN entry. The approval is tied to the app's package name and signing-certificate fingerprint.
- **Late events** from an ended session are ignored. Any app it installed stays hidden until the trusted person approves it.

*Alternative:* a permanent `DISALLOW_INSTALL_APPS` lifted only in update windows. Rejected unless Q2 forces it, because Play and Play services would go stale.

### D3. Web: hide general browsers, catch other links, disclose

**General browser** means an app that:
- answers a test link to an unknown host (`https://plainphone-probe.invalid/`), **or**
- can hold the browser role.

An app that only opens links to its own website (Android App Links) is **not** a browser and can be approved.

**Always hidden:** general browsers, the Google app, Gemini, other app stores, and the Wi-Fi sign-in page app. The sign-in page app can be allowed only for one supervised session.

**Plainphone takes the browser role**, and setup checks that it holds it (Q4).

**Link handling:**

| Link | What happens |
|---|---|
| An approved app's own verified App Link | Opens that app |
| Any other http/https link | Plainphone's blocked-link screen |
| `intent:` links, custom schemes, explicit components | Open only if they lead to an approved app |
| Custom Tabs | No provider exists, so apps fall back to their own behavior. That is tested in Q4 |

**Blocked-link screen.**
- Shows the real host, after parsing.
- Removes user/password parts, query strings and fragments before showing or sending.
- Offers "Send to [trusted person]" by text, after the user confirms.
- Runs in a separate activity with **no admin capability**. It ignores incoming extras.

**Web-leak notes.**
- Each catalog note records the app version it was reviewed against.
- When the app has updated past that version, trusted-person mode says "reviewed for an older version".

### D4. Emergency: two lists, behavioral gate, no call filtering

**Never-hide list.** These packages are never hidden, suspended, disabled or uninstalled, and none of their components is ever disabled:
- the system dialer
- Telecom
- the cell broadcast receiver
- Google Play services
- the default SMS app

The policy engine refuses such changes by construction.

**Lock-task UI list.** The packages whose screens may appear:
- Plainphone
- approved apps
- the system dialer, plus the in-call screen package if it is separate
- validated UI exceptions from D1

Play services and Telecom are **not** on it unless Q1 proves they must be. Each entry gets a test proving Settings and the browser can't be reached through it. Same-UID packages are listed and checked.

**Incoming calls.** Plainphone never filters, blocks or hides incoming calls. E-Comm calls back automatically after a 9-1-1 hang-up.

**Emergency button.** The home screen always has one. It sends `ACTION_DIAL tel:911` explicitly to the system dialer, and checks the target can be resolved.

**Release gate.** An instrumented UI test that:
- taps Emergency from: the lock screen, home, inside an approved app, in the middle of setup, during a PIN lockout, during a Play session, and after Plainphone restarts;
- checks that the system dialer's emergency screen is in front each time;
- runs an emulator-simulated call through Telecom.

A deliberately broken build (the button resolves to nothing) **must fail** this gate. Proof of a real carrier call and location needs a real phone and a safe test procedure agreed with the carrier. The emulator never stands in for it.

### D5. Trusted person on the device

**PIN.**
- At least 6 digits.
- Stored as a PBKDF2-HMAC-SHA256 hash with ≥600,000 iterations and a random salt.

**Wrong-guess counter.**
- Stored in device-protected storage, so it survives restarts.
- After 5 wrong guesses, each further wrong guess adds a delay, doubling each time and capped at 1 hour, so the real trusted person is never locked out for days.
- A reboot restarts the current delay in full.
- After 10 wrong guesses the trusted person gets a text. This is best effort: a failed text never changes what is enforced.

**Recovery code.**
- 128 bits of randomness, shown once as 6 groups of letters.
- Stored only as a hash and usable once.
- Wrong entries are rate-limited like the PIN.
- Replacing it is atomic: the new PIN, the new code and invalidating the old code happen together, or not at all.
- The person who keeps it is named during setup: the trusted person, not the helper.

**Screen protection.** Admin screens use `FLAG_SECURE`. Backup is disabled. Nothing secret goes to logs.

**Fresh entry for big actions.** A PIN or the recovery code is needed again for each of: approve, remove, Play session, maintenance mode, turning off. Admin activities are not exported, and nothing from outside can mark a session as authenticated.

**App requests.**
- The user's text is shown as "From [user]: …", plain and unformatted.
- Limits: 280 characters, 5 requests a day.
- Sent to the trusted person's fixed number. If sending fails, Plainphone says so and keeps the request on the list.

**Maintenance mode.**
- Needs a PIN.
- Lifts `DISALLOW_DEBUGGING_FEATURES` for up to 30 minutes, so a helper can `adb install -r` a newer **Plainphone build signed with the same key**.
- Uses the same session rules as D2, then re-locks and checks the policy.
- This is Plainphone's only update path in the MVP. Android refuses updates signed with a different key.

### D6. Our own settings screens

The block flag (D1) stops Android Settings panels from opening inside our task. So Plainphone builds these with its Device Owner powers:
- Wi-Fi: join and forget networks, using `WifiManager.addNetwork`. Device Owners are exempt from the API 29 deprecation, which Q1 confirms.
- Bluetooth pairing
- volume and ringtone
- brightness
- text size
- battery

**Fallback.** If something can't be built, it becomes a "helper task" in maintenance mode. It does not become a hole in the lock.

### D7. Setup: helper custody, then lock

**Who does what.** Setup is a helper-custody phase, guided by a script plus screens on the phone:
1. Back up the phone.
2. Factory reset.
3. Enable adb.
4. Run the script. It checks the phone model is on the approved list, the bootloader is locked, and verified boot is green. Then it installs Plainphone and runs `dpm set-device-owner`.
5. Follow the screens on the phone.

**Once the PIN exists, resuming setup needs it.** Setup can't be re-entered after handover.

**Before Ready, Plainphone checks all of these:**
- Key attestation reports the bootloader locked and verified boot green.
- Reset protection is set to the trusted person's `userId` and reads back correctly.
- adb is off (readback) and the restriction is applied.
- The browser role is held.
- The emergency self-test passes.

**Getting the `userId`.** The trusted person gets theirs from Google's People API "Try it" page, using `people.get` on `people/me`. New Google accounts may need 72 hours before they work for reset protection.

### D8. Policy is data, stored safely and applied in order

**Storage.**
- The policy is written atomically (temp file → fsync → rename).
- It is checked against a schema version before use.
- A last-known-good copy is kept.
- A minimal Direct-Boot-safe copy sits in device-protected storage: the setup-complete flag, never-hide list, lock-task list and PIN counter.

**Order of application:**
1. restrictions
2. never-hide guard
3. hides
4. lock-task packages and features
5. home screen preference

**Readback.** Each step is read back. On any failure: retry, then fall back to last-known-good, then show the trusted person "Plainphone needs attention".

**When it runs:** `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, package events, after trusted-person actions, and every 15 minutes.

**Deadline.** The policy is fully in place within 60 seconds of boot completing (Q6).

**Before first unlock.** Restrictions and hidden-app state are stored by Android itself. Only the Direct-Boot copy is read.

**Testability.** The policy engine is a pure function, so it can be unit-tested.

### D9. Codebase

- **Platform:** Kotlin and Jetpack Compose. Minimum SDK 30; target SDK is the latest stable.
- **Accessibility:** large text, WCAG 2.2 AA contrast, one main action per screen.
- **Data:** no analytics and no crash upload.
- **Reference code:** TestDPC and Headwind (both Apache-2.0), cited in file headers. Not forked.

## Risks / Trade-offs

- **[Allowlist blocks adb setup on real phones]** → Q3 on one approved phone. Not an automatic switch to GrapheneOS: B must pass the same go/no-go list on a Pixel.
- **[The block flag breaks a normal day]** → Q1, run with the flag on and off. The UI exceptions list, each entry with negative tests. Otherwise a revised ADR.
- **[Play stops updating]** → setup checks, staleness warning, supervised "Check updates" (D2). If Q2 fails outright, a revised ADR.
- **[Must-have apps break without a browser]** → Q4 with Beaux and the trusted person's real app list. A failure here is a go/no-go failure, even if Q1 and Q2 pass.
- **[Approved apps leak web; the Wi-Fi sign-in page]**
  - The sign-in page is hidden by default; the trusted person can allow it per session.
  - Web-leak notes for each app version.
  - What's left is accepted in writing by Beaux.
- **[A recovery-mode wipe]** → reset protection with the trusted person's `userId`, as a gate before handover. If it isn't supported, the promise is changed and Beaux accepts that exact risk.
- **[Lockout]** → capped delays, a recovery code, and the trusted person's Google account (reset protection) as the independent way back.
- **[Plainphone bug breaks calling]**
  - The never-hide guard.
  - The behavioral gate with a deliberately broken build.
  - Emulator first, then one phone Beaux approves.
- **[Short support life (Motorola XT2415: March 2027)]** → the hardware requirements in ADR 0001. Beaux picks the phone.
- **[Manufacturer extras (Secure folder, Cloned Apps…)]** → an escape review for each brand, on a real phone of that brand, before it is supported.
- **[Google policy changes]**
  - Developer verification comes in 2027; adb is exempt.
  - There is a free option for up to 20 devices, to evaluate.
  - The Lead watches for changes.

## Migration Plan

**No existing installs.**

**Rollout:**
1. Emulator.
2. Beaux approves phone 1.
3. Beaux's phone.
4. Two weeks of use.
5. Beaux approves the trusted person's phone.

**Rollback:**
- the trusted person's "Turn Plainphone off", which resumes after interruptions (Q7);
- otherwise, a factory reset after a backup.

## Open Questions

**For Beaux:**
1. Which phone model, and should we buy new or reuse? Your moto g power 5G (2024) loses security updates in March 2027.
2. Who is the trusted person for each phone? Do they have a Google account at least 3 days old?
3. License: Apache-2.0?
4. What are each phone's must-have apps (bank, maps, ride-share…)?
5. Do you accept the stated leftover risks for the MVP?
6. Which carrier and plan? It needs data, for RCS and MMS.

**For the spike:** Q1–Q14 in `docs/feasibility-questions.md`.
