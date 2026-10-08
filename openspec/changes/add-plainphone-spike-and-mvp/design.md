## Context

- **Users.** The first phones belong to Beaux and the trusted person, two non-technical adults in the US. Each is likely the other's trusted person.
- **The product.** A phone that runs only approved apps, where the trusted person decides what is approved. It should feel calm, not punished.
- **Architecture.** Already argued in `docs/decisions/0001-architecture.md`:
  - a Device Owner app that is also the home screen
  - on an unlocked Pixel running stock Android
  - no custom ROM
  - no launcher-only mode
- **Evidence.** Lives in `research/evidence/`. Every claim about what Android allows cites a primary source there, or is listed as a spike question in `docs/feasibility-questions.md`.
- **Constraints from team policy:**
  - emulator only until Beaux approves a specific phone
  - no remote repo
  - no paid services
  - cross-model review of everything we write

## Goals / Non-Goals

**Goals:**
- One app, one APK. The Device Owner, home screen, trusted-person screens and blocked-link screen live in one package. Nothing separate can be uninstalled or disabled, and nothing has to talk to another of our packages.
- The user can't get to a browser, social media, or new apps through any route on the threat list, except the leftover risks we state openly.
- 911, calls and texts always work.
- Approved apps stay up to date without anyone having to remember.
- A helper can set up a phone from a written guide in under 30 minutes. After that, the user and trusted person never see a technical word.

**Non-Goals:**
- Remote control from another phone or the web. A later change. It needs a server, which needs hosting, which is a spend decision.
- Content filtering by DNS or VPN. Spiked only (Q9).
- Time limits, usage reports, monitoring of messages or location. We restrict; we do not watch. This also keeps us clear of the "monitoring" exclusion in Google's DPC allowlist policy.
- Work profiles, multiple users, tablets, non-Pixel phones, GrapheneOS.

## Decisions

### D1. Lock task mode plus hiding, not hiding alone
- Unapproved apps are hidden with `setApplicationHidden`, so they disappear from intents, the share sheet and App info.
- Approved apps also run in multi-app lock task mode, so anything that slips through can't open.
  - Lock task blocks Settings, Overview, Quick Settings and the Play Store.
  - Lock task features: `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO`. Never `OVERVIEW`.
- *Alternative considered:* hiding alone. Rejected, because Settings can't safely be hidden and its deep links are a large escape surface.
- *Depends on:* Q1. If lock task can't carry a normal phone day, we fall back to hiding alone plus a stricter escape review.

### D2. App updates: keep Play, but out of reach
- The Play Store stays installed, enabled and signed in. It is never on the home screen or in lock task, and we never set `DISALLOW_INSTALL_APPS` permanently. (AOSP code shows that restriction blocks updates too.)
- **Catching unapproved installs.** On every package-added broadcast, Plainphone hides any app that is not on the allowlist. It also re-checks the whole allowlist at boot and once an hour.
- **Adding an app.** The trusted person:
  1. enters the PIN
  2. picks "Add an app"
  3. Plainphone opens the Play Store in a supervised session (temporarily in lock task)
  4. after the install, they confirm "Allow"
  5. the session ends when they leave Play or after 10 minutes
- *Alternative considered:* a permanent `DISALLOW_INSTALL_APPS`, lifted only during updates. Rejected for the MVP, because Play and Google Play services would stop updating between windows.
- *Depends on:* Q2.

### D3. Web: hide browsers, catch links, be honest
- Hide every browser, the Google app, Gemini and other app stores.
- Plainphone handles `http`/`https` VIEW intents itself. It shows: "This link is blocked. You can ask [name] to look at it with you." It does not provide Custom Tabs.
- Every catalog entry carries a short web-leak note, for example: "Opens some web pages inside the app (help pages, articles)." The trusted person sees it before approving.
- Leftover risks are shown on one plain screen during setup, and again in trusted-person mode:
  - the web inside approved apps
  - Wi-Fi sign-in pages
  - recovery-mode wipe
- *Alternative considered:* keep one browser for sign-in pages and restrict it with a VPN. Deferred; it depends on Q4 and Q9.

### D4. 911 is outside our control by design
- **Protected packages.** A fixed list:
  - the system dialer (`TelecomManager.getSystemDialerPackage()`)
  - the Telecom and Phone services
  - the cell broadcast receiver
  - Google Play services
  - the default SMS app
- **Never hidden, suspended or removed.** The policy engine refuses any change that would hide, suspend or remove a protected package. It refuses them by construction, not by convention.
- **Always reachable.** The dialer is always in lock task, and `LOCK_TASK_FEATURE_KEYGUARD` is always set. The home screen always shows an "Emergency" button that opens the system dialer with 911 filled in.
- **Release gate.** An instrumented test asserts all of the above. If it fails, the release fails.

### D5. Trusted person, on the device
- **PIN.** 6+ digits, hashed with PBKDF2. Wrong guesses cause growing lockouts timed on monotonic time. Repeated failures text the trusted person.
- **Recovery code.** Printed or written down at setup, and the only way back if the PIN is lost.
- **App requests.** The user writes "I'd like [app] because…". Plainphone texts it to the trusted person's number from the phone's own SMS, so there is no server. It also lists the request in trusted-person mode.
- **Turning Plainphone off.** Clears all restrictions, unhides everything, then removes Device Owner (Q7).
  - If Q7 fails, the off switch is a factory reset, and setup says so.
  - Two confirmations, in plain words.
- *Alternative considered:* a remote portal. A later change.

### D6. Our own settings screen
- Lock task blocks Settings, so Plainphone provides: Wi-Fi, Bluetooth pairing, volume and ringtone, brightness, text size, and battery.
- We use Android's settings panels where they open inside our task. Where they don't, we build our own screen with the Device Owner APIs.
- *Depends on:* Q1.

### D7. Setup through adb, by a helper
- **The steps.** Factory reset, skip accounts, enable debugging, run one script that installs the APK and runs `dpm set-device-owner`, then follow on-screen steps:
  1. name the trusted person
  2. choose the PIN
  3. sign in to Google
  4. choose apps
  5. turn debugging off (Plainphone then blocks it)
- **Why adb.** QR setup may be blocked by Google's DPC allowlist (ADR 0001, Risk 1).
- **Later.** The script will become a web page that talks to the phone over USB. That page is a later change.

### D8. Policy is data, applied idempotently
- The full policy lives in one versioned file in app storage:
  - the allowlist
  - the restrictions
  - lock task packages and features
  - the trusted person
- **When it is applied.** On boot, on every package change, after every trusted-person action, and hourly.
- **Why.** Drift (for example, an update that unhides a package) fixes itself. The policy engine is a pure function, so it can be unit-tested without a device.

### D9. Codebase
- Kotlin and Jetpack Compose. minSdk 30, targetSdk at the latest stable API.
- Large text, high contrast, one action per screen.
- **No network permission beyond what Android requires.** No analytics, no crash upload.
- **Reference code:** TestDPC and Headwind MDM (Apache-2.0), cited in file headers where we borrow.
- **Not forked:** Headwind assumes a server, and its lockdown features are closed.

## Risks / Trade-offs

- **[Google DPC allowlist also blocks adb setup on real Pixels]** → Q3 on a real device, after Beaux approves. Fallback: GrapheneOS, with ADR 0001 re-approval.
- **[Lock task can't carry a normal day: calls, permission prompts, camera]** → Q1 first. Fallback: D1's alternative.
- **[Play won't update while shut out of lock task]** → Q2. Fallback: a supervised weekly update window that the trusted person sees.
- **[Approved apps leak the web]** → web-leak notes, honest disclosure, and a DNS filter later if Q9 passes. The trusted person chooses apps knowing this.
- **[Recovery-mode wipe]** → factory reset protection tied to the trusted person's Google account. Setup tells both people what a wipe does and does not get you.
- **[Locked out: PIN lost, trusted person gone]** → recovery code. As a last resort, a factory reset with the trusted person's account.
- **[A bug in Plainphone breaks calling or bricks the phone]** → protected packages (D4), the release-gate test, emulator testing before any real phone, and Beaux approves each real phone.
- **[Google policy changes: developer verification 2027, DPC allowlist]** → watched by the Lead. Registration cost goes to Beaux when it applies.
- **[OEM skins (Samsung clone apps) add escape routes]** → the MVP is Pixel only. Other phones need their own review.

## Migration Plan

- There is no existing install base.
- **Rollout.** Emulator → Beaux approves phone 1 → Beaux's phone → two weeks of use → the trusted person's phone.
- **Rollback.** Trusted-person "Turn Plainphone off" (Q7), or a factory reset.

## Open Questions

For Beaux:
1. Which phones? New unlocked Pixels, or your current phones? Model?
2. Who is each phone's trusted person? Is one shared trusted person fine?
3. License: Apache-2.0?
4. Is the first app list for each phone known (bank, maps, ride-share)? It drives the spike's sample apps and the web-leak review.
5. Are you OK with the stated leftover risks, especially web pages inside approved apps, for the MVP?

For the spike:
- Q1–Q11 in `docs/feasibility-questions.md`.
