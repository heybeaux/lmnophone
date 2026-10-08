## Context

- **Users.** Beaux and the trusted person in [town], Canada. Neither is technical. Each is likely the other's trusted person.
- **Architecture.** `docs/decisions/0001-architecture.md` (revision 3) names the leading candidate:
  - a Device Owner app that is also the home screen;
  - running on a certified stock Android phone with long security support.
- **Evidence.**
  - Source files: `research/evidence/`.
  - Device results so far come from the spare Motorola ([PLA-5](/PLA/issues/PLA-5), `spikes/device-moto/RESULT.md`):
    - **A1 / Q3: yes.** Our unapproved, test-only Device Owner app was set up over adb on a certified Android 15 Motorola. No allowlist message appeared.
    - **A2: yes.** `dpm remove-active-admin` and `clearDeviceOwnerApp()` both returned the phone to unmanaged with no wipe.
    - Within 8 minutes of first boot, with Wi-Fi and no account, the committed A0 table shows 7 pushed installs: 4 Digital Turbine games (`com.dti.motorola`), plus Google One, Fit and Wallet via Play. Facebook comes preloaded. The count still needs reconciling with PLA-5's comment, which said 5 games.
- **Reviews.** Revision 3 answers PLA-4 (N1–N11) on top of PLA-3 (F1–F17). Every requirement traces to `docs/verification-matrix.md`.
- **Policy constraints:**
  - Physical work only on the board-approved spare (serial SPARE_DEVICE_SERIAL), within its rules: Settings resets only, no recovery wipes, test-only DPC.
  - No remote repo.
  - No paid services.
  - Cross-model review.

## Goals / Non-Goals

**Goals:**
- **One app.** One APK contains the Device Owner, home screen, trusted-person screens and the blocked-link screen.
- **No unapproved app can be opened** through any tested route, from the first interactive frame after unlock. Leftover routes are named, and Beaux accepts them in writing.
- **Emergency first.** Plainphone never gets in the way of 9-1-1, calls, texts or emergency alerts, in any state, including while Plainphone cannot run.
- **Updates.** Approved apps keep updating, and the trusted person does a supervised update check once a month.
- **Recovery.** Every state has a written recovery path. The last resort is a recovery wipe plus the trusted person's Google account.
- **Simple setup.** A helper finishes setup in 30 minutes or less, measured as defined in guided-setup.
- **Temporary access is custody.** Any temporary relaxation of the lockdown is a supervised session in the trusted person's hands. We do not promise it closes itself if Plainphone cannot run.

**Non-Goals:**
- Remote portal or server.
- DNS or VPN filter (Q9 only).
- Monitoring.
- GrapheneOS.
- Phones Beaux hasn't approved.
- Wi-Fi networks that need a sign-in page.
- Any adb-based maintenance mode.
- Promising that no unapproved code ever runs.

## Decisions

### D1. Lock task with in-task blocking, plus hiding

Unapproved apps are hidden with `setApplicationHidden`. Approved apps run in multi-app lock task mode with these features:
- `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO`
- `BLOCK_ACTIVITY_START_IN_TASK`
- never `OVERVIEW`

Any system screen a normal day needs goes on a reviewed **UI exceptions list**. Q1 finds these screens. Each exception gets negative tests.

The home activity starts lock task before it draws its first interactive frame. Restrictions, hidden apps and lock-task settings are saved by Android and survive a reboot.

### D2. Supervised sessions are custody, with a journal written first

**Purpose.** A supervised session is the only way the lockdown is ever relaxed. The MVP has two kinds:
- **Play:** add an app, or the monthly update check. Deadline 10 minutes.
- **Settings:** for anything Plainphone's own settings screen doesn't cover. Deadline 5 minutes.

**Contract** (trusted-person-controls):
1. A fresh PIN is required to start.
2. The relaxation and its deadline are written to a journal in device-protected storage **before** anything is relaxed.
3. Only the session's packages are added to lock task. Debugging, factory reset and safe mode are never relaxed.
4. The session ends on Done, screen off, the deadline, or leaving the session's packages.
5. On ending, Plainphone revokes the relaxation, reads the policy back, and then clears the journal.
6. On every Plainphone start, including a Direct-Boot-aware `LOCKED_BOOT_COMPLETED`, Plainphone revokes anything still in the journal before doing anything else.

**What we promise (N1, R1).** If Plainphone cannot run, nothing closes the session automatically, whatever the cause: a real bad update, a storage fault, or a crash loop. The relaxation stays until a helper fixes the phone.
- The screen says "Stay with the phone until you tap Done".
- The phone is never handed back until the policy reads back correctly.
- Beaux must explicitly accept this limit (ADR go/no-go #7).
- Q12 measures what stays reachable in that state, and checks that revocation runs first whenever Plainphone can run. It does not prove the failure never happens.

**Session scope (R2).**
- **Play sessions** add only `com.android.vending`.
- **Settings sessions** add only the build's Settings package and the same-UID siblings listed in the build inventory.
- **On close,** a protected-state check restores anything that breaks a protection, while keeping ordinary changes such as ringtone or Wi-Fi. It covers: restrictions, lock task, the never-hide inventory, browser role, default SMS app, SMS permission, keyboards and accessibility services, and alert settings.
- **A damaged journal** means every relaxation is revoked.
- **The locked-boot path** uses device-protected data only.

**Why not adb maintenance.** It was cut (N2). Its scope can't be narrowed, and it relies on Settings and host authorisation.

### D3. App updates: Play kept but out of reach; per-app record; monthly check

**Play stays.** The Play Store stays installed, enabled and signed in to the user's own account. It is outside lock task except during a Play session. `DISALLOW_INSTALL_APPS` is never set permanently.

**Unapproved installs.**
- Any install, including first-boot preinstalls and Digital Turbine pushes, stays hidden until it is approved.
- Plainphone hides on the package-added broadcast, and also checks at boot and every 15 minutes.
- If hiding fails, the app may run in the background, with no time limit. This is disclosed.

**Update record and check (N9).**
- Plainphone records each approved app's version and the time it last updated.
- Once a month (with the due date and last-completed date stored, daily retries for 7 days, then a single line on the home screen: "Ask [trusted person] to do the monthly phone check"; R6) it texts the trusted person to run the update check, a Play session that opens Play's own update list. That list shows which updates are waiting and which have failed. Plainphone does not claim to know this itself.
- If Google Play services goes 30 days without an update, Plainphone texts the trusted person.

### D4. Web: classifier with self-exemption, scoped link promise, no Wi-Fi sign-in pages

**Classifier (N3).**
- Plainphone (exact package name and signing certificate) and never-hide packages are excluded first. A conflict is flagged for review, never hidden automatically.
- A package is a "general browser" if `queryIntentActivities(MATCH_ALL)` lists it for both http and https on the local probe `plainphone-probe.invalid` (never fetched), or if it is in the reviewed catalog of known browsers.
- Apps that only open links to their own verified domains are allowed.

**Link handling.**
- External http(s) links that Android would send to a browser go to Plainphone's blocked-link screen.
- Explicit intents to hidden browsers fail.
- Navigation inside an approved app's own WebView is **not** intercepted. This is disclosed.
- The blocked-link screen runs as a separate activity with no access to admin functions.

**Wi-Fi sign-in pages are unsupported (N5).**
- CaptivePortalLogin is hidden, with no exception session.
- When Android reports a captive portal, Plainphone shows: "This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi." It also offers "Forget this network".
- Q4 checks that normal Wi-Fi still works.

**Web-leak notes** are kept per app version.

### D5. Emergency: never-hide list including OEM components; behavioral gate; real-device handover checks

**Never-hide list.** The base list:
- the system dialer
- Telecom
- the cell broadcast receiver and its alert display
- Google Play services
- the default SMS app

Q13 adds the manufacturer's emergency components, such as Motorola's SOS and emergency information. The policy engine refuses to hide, suspend, disable or uninstall any of them, or to disable any of their components.

**Getting to Emergency.**
- Lock task always includes the dialer, and `KEYGUARD` and `HOME` are always set.
- Inside an approved app: press Home, then Emergency. The power-menu entry stays where the phone has one.
- When Plainphone can't run: the lock screen and the system dialer still work.

**Release gate.** The release gate covers every state that the emulator can reproduce. It must fail on three negative-control builds:
- the Emergency button goes nowhere;
- the dialer is missing from lock task;
- the alert display is disabled.

If an alert can't be injected, the result is recorded as "not verified".

**Before handover to Beaux or the trusted person (N8), on their actual model and build:**
- the emergency dialer opens in every state, without placing a call;
- an incoming call can be answered, and its keypad sends tones (E-Comm's automatic callback needs a keypress);
- the alert receiver is present;
- the manufacturer's emergency components are on the never-hide list.

A live 9-1-1 call needs a separate procedure agreed with E-Comm or the carrier, approved by Beaux. Until these checks pass, the phone stays in supervised test custody.

**Calls are never filtered.**

### D6. Trusted person on the device

**PIN (N7).**
- Stored as PBKDF2-HMAC-SHA256 with at least 600,000 iterations and a 16-byte salt.
- Failures 1–4 cause no delay. The 5th failure starts a 30-second delay, which doubles with each further failure, capped at 1 hour.
- Entries made during a delay are rejected without being counted.
- The failure count and the remaining delay are saved together in one write. A reboot keeps the *remaining* delay; it doesn't restart it.

**Recovery code.** 128 bits, stored only as a hash. It has its **own** failure counter, so a PIN lockout never blocks it. Using it changes the PIN and code together in one step.

**Fresh entry for every privileged action.** No external intent or extra can grant authority.

**App requests.** The request text is treated as untrusted. Approval is tied to the installed package and its signing certificate.

**Plainphone's own updates (N2).**
- The helper's computer serves the APK on the local Wi-Fi.
- The trusted person enters the PIN and the URL.
- **Binding (R4).**
  - The APK is downloaded to private storage: at most 100 MB, a 2-minute timeout, no redirects.
  - The staged file is checked: exact package name, valid signature, the pinned signer, and a strictly higher `longVersionCode`.
  - Exactly those bytes are installed through a PackageInstaller session with `setAppPackageName` set.
  - Any open session is closed and read back first.
  - After the update, Plainphone runs the handover self-check; installing successfully is not the same as starting successfully.
  - There is no rollback promise.
  - The key has an offline backup custodian, plus lost-key and compromised-key procedures.
- Plainphone checks that the APK's signing-certificate digest matches the one built into it and that its version is higher. Then it installs silently as Device Owner.
- No adb and no Settings are involved.
- Release builds are `testOnly=false` and `debuggable=false`.
- The signing key is held by Beaux, or by someone he names, never on a phone.
- A bad update falls back to the "Plainphone can't run" recovery path, which is tested in Q6 with a build that crashes on purpose.

**Turning off (N6).**
- Plainphone restores every change it recorded, each read back.
- Before removing Device Owner, it checks that another home screen and the dialer resolve.
- It resumes from a journal after a restart. If it finds it is no longer Device Owner, it treats turning off as done.
- It never silently falls back to a wipe.

### D7. Our own settings screens

Plainphone's own settings screen uses Device Owner APIs:
- Wi-Fi, through `WifiManager.addNetwork` (to be confirmed in Q1)
- Bluetooth pairing
- volume and ringtone
- brightness
- text size
- battery

Anything else needs a supervised Settings session. There is no generic "helper task" fallback.

### D8. Setup: helper custody, sweep before Wi-Fi, reset-protection IDs without secrets

**Order of steps:**
1. Back up the phone.
2. Reset it through Settings.
3. With the SIM removed and eSIM disabled, go through the first-boot wizard and skip Wi-Fi, accounts and app recommendations. The script refuses to continue if any network is active, or if there is no build inventory for this phone.
4. Turn on USB debugging.
5. Run the script. It checks that the model and build are approved, the bootloader is locked and verified boot is green, then runs `set-device-owner`.
6. Plainphone hides every package that isn't on the never-hide, essential or approved lists.
7. Only then does the phone join Wi-Fi.
8. Sign in to the user's own Google account and turn on Play auto-update.

**Reset protection (N6).**
- On their own device, the trusted person opens the People API `people.get` "Try it" page with `personFields=names`, checks that the name shown is theirs, and copies only the digits after `people/`.
- Plainphone shows the digits back for confirmation. It collects no tokens or passwords.
- The helper records that the trusted person can sign in from a device other than either Plainphone phone, and that their second factor isn't only on a Plainphone phone.
- A backup person's ID can be added. This is recommended when Beaux and the trusted person are each other's trusted person.

**Handover check.** Plainphone shows "Ready" only when all of these pass:
- Device Owner set
- restrictions applied
- adb and wireless debugging off
- key attestation passes
- reset protection read back
- browser role held
- never-hide list intact
- emergency self-test passes
- Play auto-update on
- preinstall sweep done

### D9. Policy storage: atomic, validated, built-in safe policy

- **Writing.** The policy is written atomically and checked against its schema. A last-known-good copy is kept.
- **Direct Boot copy.** A minimal copy is kept in device-protected storage: setup-complete flag, never-hide list, lock-task list, counters and the session journal.
- **Build inventory (R3).** One versioned inventory per supported model and build fingerprint, compiled into the APK. It lists:
  - never-hide dependencies: dialer, Telecom, in-call UI, cell broadcast service, receiver and alert display, Play services, Play Store, default SMS app, selected keyboard, permission controller, WebView provider, setup and account components, and manufacturer emergency components;
  - minimum UI entries.

  The inventory outranks the allowlist and the classifier. If the inventory is missing or invalid, there is no broad sweep and no Ready.
- **If both policy copies are invalid,** the **emergency-safe baseline** applies. It is compiled from the build inventory and does not depend on any copy that can change: never-hide dependencies, minimum UI entries, Messages, and every restriction. Plainphone then shows "Plainphone needs attention". The baseline helps only while Plainphone can run.
- **Order of application:** journal revoke → restrictions → never-hide guard → hides → lock task → home preference. Each step is read back.
- **When it runs:** at locked and unlocked boot, on package events, after trusted-person actions, and every 15 minutes.
- **The policy engine** is a pure function.

### D10. Codebase

- Kotlin and Jetpack Compose. minSdk 30.
- Accessibility: WCAG 2.2 AA, large text.
- No analytics.
- TestDPC and Headwind are reference code only (both Apache-2.0); no fork.
- Spike code stays under `spikes/`. MVP code goes in `app/` and starts only after approval.

## Risks / Trade-offs

- **[QR setup blocked by Google's DPC allowlist]** → adb works on a certified Motorola running Android 15 (device test A1). QR is not tested and not needed. Each new model or build is checked by the script and by Q3 on that model.
- **[The in-task block flag breaks a normal day]** → Q1, run with the flag on and off. The UI exceptions list has negative tests. Otherwise, a revised ADR.
- **[Play doesn't update unattended]** → Q2: the monthly check plus a Play services warning. If Q2 fails outright, a revised ADR.
- **[Must-have apps break]** → Q4 against the real app list counts as go/no-go.
- **[Sessions stay open if Plainphone can't run]** → custody, the journal, revoking at every start, a protected-state check before handback, and disclosure. Beaux's explicit acceptance is required. Without it, an independent revocation mechanism becomes a G0 question.
- **[A Plainphone update crashes]** → signature and version checks, the Q6 crash test, and the recovery path.
- **[Recovery wipe]** → reset protection with the trusted person's and a backup person's IDs. Their ability to sign in elsewhere is checked. FRP does not reinstall Plainphone.
- **[Preinstalls and pushed apps]** → sweep before Wi-Fi, then the package watcher.
- **[Web inside approved apps]** → web-leak notes and disclosure.
- **[Wi-Fi networks that need sign-in]** → unsupported, with a plain message.
- **[Short support life]** → the spare Motorola's security updates end in March 2027, and it is not a target phone. Beaux picks the target model.
- **[Manufacturer extras]** → Q13 on each supported build. Every item ends in a tested outcome or a risk Beaux has accepted.

## Migration Plan

- **Rollout order:**
  1. emulator
  2. spare Motorola (spike only)
  3. Beaux approves the target model
  4. Beaux's phone, after the real-device handover checks
  5. two weeks of use
  6. the trusted person's phone
- **Rollback:** turn Plainphone off (journaled and resumable). As a last resort: a recovery wipe plus the trusted person's account, with Beaux's approval for that phone.

## Open Questions

**For Beaux:**
1. Which target phone model?
2. Who is the trusted person for each phone, and who is the backup person?
3. License: Apache-2.0?
4. What are the must-have apps on each phone?
5. Do you accept the stated leftover risks, and the **custody limit**? (If Plainphone can't run during a supervised session, the session stays open until a helper fixes the phone.)
6. Which carrier and plan?
7. Who holds the release signing key, and who keeps its offline backup?
8. Do either of you rely on Wi-Fi that needs a sign-in page (hotels, hospitals, ferries, work), or on Wi-Fi calling where there is no mobile signal?

**For the spike:** Q1–Q14 in `docs/feasibility-questions.md`.
