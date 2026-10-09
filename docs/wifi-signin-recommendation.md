# Wi-Fi sign-in pages: Lead's recommendation (PLA-17)

Status: **revision 2, after the cross-model review ([PLA-28](/PLA/issues/PLA-28), request changes). Not
approved.** Until Beaux approves a recommendation, Wi-Fi that needs a sign-in page stays unsupported (design D4,
web-containment spec), and the MVP goes ahead on that basis.

Inputs:
- the emulator probe, `spikes/wifi-signin/RESULT.md` ([PLA-18](/PLA/issues/PLA-18));
- the boot-loop repro, `spikes/bootloop-repro/RESULT.md` ([PLA-22](/PLA/issues/PLA-22)), with mitigation B under
  review in [PLA-25](/PLA/issues/PLA-25);
- the UX flows, `docs/ux/wifi-signin-override.md` ([PLA-19](/PLA/issues/PLA-19));
- the review, [PLA-28 review document](/PLA/issues/PLA-28#document-review) (findings F1–F10);
- Beaux's rule for leaks (Telegram 4463): *"The overall goal is to cut down on distractions, not make the phone
  unusable."* That decision covered Outlook only.

## Short answer

1. **Allow sign-in pages for up to 5 minutes at a time, as an explicit exception that Beaux approves.** It is an
   exception to two current rules:
   - D2 rule 1: a fresh trusted-person PIN is needed to relax the lockdown;
   - web-containment: no sign-in pages.
2. **How it opens: a small sign-in window that Plainphone ships**, not Android's sign-in app. It is bound to that
   Wi-Fi network. No app is unhidden, so it avoids the boot-loop race that PLA-22 observed when an app is unhidden.
   It needs no Android notification or pull-down gesture.
3. **Who starts it: the user, with limits** (UX variant B). The trusted person switches the option on with a fresh
   PIN, and it is off by default. The PIN-only choice (variant A, with an optional trip pass) is a legitimate,
   lower-access alternative. Beaux chooses.
4. **The leak, said plainly:** *a Wi-Fi hotspot can offer access to other websites for the whole 5 minutes,
   including search and social sites.* Real sign-in networks usually block most sites until sign-in, but Plainphone
   can't tell a real one from a hostile one.
5. **When:** a follow-up change after the MVP's core works. First, an emulator spike must pass the acceptance tests
   below. Nothing is approved for building until then.

## What we learned

| Finding | Source | What it means |
|---|---|---|
| Plainphone can tell when the connected Wi-Fi needs sign-in (`NET_CAPABILITY_CAPTIVE_PORTAL` on a Wi-Fi `NetworkCallback`), with mobile data on or off | PLA-18 Q7 (emulator) | It can be offered only then. That is a trigger, not proof the network is honest |
| Android's sign-in app is a general-purpose browser while open: outside links, JS redirects, `target=_blank`, `window.open` and Google Search all load | PLA-18 Q4 (emulator; the fixture leaves the internet reachable behind the portal) | A hotspot can give full web access for the whole window. The time limit is the real limit |
| In Android's app, `VALIDATED` arrived about 1 s after Accept, and the probe's session closed 73 ms later. "Use this network as is" and "Continue anyway" also set `VALIDATED` | PLA-18 Q5 (emulator, Android's app, HTTP-only probe fixture) | Close-on-validated works there. The timing is **not** yet measured for our window |
| A Device Owner can't open Android's sign-in app with its sign-in state (`startCaptivePortalApp` is hidden; `CaptivePortal` has no public constructor). A dismissed notification only comes back after a Wi-Fi reconnect | PLA-18 Q3 (emulator; `javap` on android-36 `android.jar`) | Android's app means relying on its notification (UX path L2) |
| If Plainphone's process dies mid-session, Android's sign-in app stays reachable until Home is pressed | PLA-18 Q6a (emulator) | Android's app inherits the D2 custody limit |
| Unhiding an app followed by sudden power loss can leave system_server crashing on every boot, until a recovery wipe (which triggers FRP). It is a race: looped 8 of 9 at 2–5 s, booted 6 of 6 at 8 s or more. Hiding is safe. Mitigation B booted 3 of 3 | PLA-22 (emulator, API 35 and 36; AOSP `WRITE_SETTINGS_DELAY = 10*1000`) | Every session that unhides an app is exposed to this race. A user-started kind would be exposed more often, with nobody technical nearby |
| Android's own sign-in app binds its process to the Wi-Fi network, then inflates a WebView: `initializeWebView()` calls `mCm.bindProcessToNetwork(mNetwork)` before `setContentView` | AOSP `packages/modules/CaptivePortalLogin`, `CaptivePortalLoginActivity.java` (main), around L548–551 (code-confirmed) | A normal app can use the same public API (`bindProcessToNetwork`, API 23). Untested for our window, hence the spike |
| `LinkProperties.getCaptivePortalData()` is `@hide` / `@SystemApi` and absent from the API 35 and 36 public SDKs | PLA-28 F4 (`javap`; AOSP LinkProperties) | Our window can't learn the portal URL from Android. It starts from a plain HTTP URL instead |
| `reportNetworkConnectivity` is a hint to re-check, not a promise. It needs `INTERNET` and `ACCESS_NETWORK_STATE` | ConnectivityManager reference; PLA-18 Q3 saw it re-probe | The 5-minute deadline is needed even when validation never comes |
| Google blocks its OAuth sign-in in embedded WebViews (`disallowed_useragent`) | Google OAuth policies (PLA-28 F8) | Portals that need a Google account, a bank app or another app stay unsupported |
| On a ferry the user is often alone, with no mobile signal, so can't phone or text the trusted person | PLA-19 | PIN-only fails the case Beaux named |

## Options

| | Unhide Android's sign-in app | Plainphone's own sign-in window |
|---|---|---|
| **Trusted person's PIN each time (A)** | A1. Exposed to the unhide race at every start (needs mitigation B). Notification launch path (L2). Fails the ferry case | A2. Not exposed to the unhide race. Fails the ferry case unless a trip pass was set up |
| **User starts it, with limits (B)** | B1. As A1, more often, alone. **Also needs an explicit waiver of D2's PIN rule for an unhide** | **B2. Recommended** |
| **Keep unsupported** | | R0. Today's MVP. Ferries, hotels and hospitals don't work on Wi-Fi |

The two forks are independent. Who starts it (A or B) is Beaux's choice. How it opens is a technical choice, and
the own window is better for either A or B.

## The recommendation in detail

### 1. The sign-in window

**What it is.** A second, tiny app in the Plainphone build, "Plainphone Wi-Fi sign-in":
- A separate package, signed with Plainphone's key, with **no admin rights**. It runs under its own app ID.
- It is never hidden, from first setup on, and stays in the lock-task list permanently. Provisioning and updates
  never hide or unhide it.
- It is on Plainphone's self-exemption list (exact package and certificate). It declares no http(s) intent filters,
  so the D4 classifier doesn't count it as a browser.
- **Permissions:** `INTERNET` and `ACCESS_NETWORK_STATE` only.

**Entry points (F2).**
- Exactly **one** activity. No activity aliases, receivers, providers, pending intents or other services in its
  manifest.
- It is protected by a signature permission, but that is only the first gate. A signature permission admits any app
  signed with the same key, and permanent lock-task membership removes the lock-task barrier. So:
  - The activity takes **no URL, deadline or session data from its intent**. On every create, resume, new intent and
    restore, it asks Plainphone for the live session, over one Plainphone service protected by a signature
    permission. Plainphone checks that the caller's UID belongs to the companion package and certificate.
  - That service offers only two calls, "claim the session" and "report closed". It exposes no admin operation.
    The companion gains no Device Owner powers through it.
  - If Plainphone doesn't answer, or the session is missing, already claimed, expired or from a previous boot, the
    companion shows no web content and closes.
- `excludeFromRecents`, and `finishAndRemoveTask()` on every close.

**Session contract (F1).**
- Only one session at a time.
- Before anything opens, Plainphone durably writes the session record:
  - a session id;
  - a boot id;
  - a deadline on the elapsed-time clock, capped in code at 5 minutes;
  - the Wi-Fi `Network`;
  - a single-use claim token.
- The companion claims the session once. A second claim, a restored task or a recreated activity gets "already
  claimed" and closes. After a process death, a still-valid session can't be resumed: a new try needs a new tap.
- The companion validates the session **before** it creates a WebView. It keeps the deadline in memory once claimed
  and enforces it itself, with a timer on a thread separate from the UI thread. That way the deadline still holds
  while Plainphone is dead.
- On close, the companion reports to Plainphone. Plainphone writes "ended" before any cleanup, and an ended
  session can't be claimed again.
- Plainphone also enforces the deadline from its side while it runs. As the home app, it brings itself to the front
  at the deadline and asks the companion to close.
- On every Plainphone start, any session record left over is marked ended, and any companion task still on screen
  is closed.
- **What "fails closed" means now:**
  - if the companion crashes, the page is gone;
  - if Plainphone crashes, the page stays open **at most until its own deadline**;
  - after a reboot, an old session can never reopen.
  - The spike measures how late a close can be (the tolerance). The design states that measured number, not a hard
    bound.

**Network (F4, F6).**
- Bind the process to the session's Wi-Fi `Network` before any WebView starts, and check the return value. If the
  bind fails, show no page.
- Load one fixed plain-HTTP URL so the portal can intercept and redirect. Android's own default probe URL is a
  candidate; the spike picks the URL and records the exact cleartext rule.
- If the Wi-Fi is already validated or has dropped, close. Never fall back to mobile data: the bind makes sockets
  fail when that Wi-Fi goes.
- Portals that only use the newer standard (RFC 8908 API, with no HTTP intercept) are **unsupported**, and the
  spec says so.
- After page loads, call `reportNetworkConnectivity(network, true)`, at most once every few seconds.
- Close on `VALIDATED` for that exact network. Close on network loss. Always close at the deadline.

**WebView rules (F7).**
- **Schemes:** only `http:` and `https:` load. No `intent:` parsing or dispatch, and no `data:`, `blob:`, `file:` or
  `content:` top-level loads.
- **`tel:`:** opens the approved Dialer with `ACTION_DIAL`, and only from a main-frame user tap. It never dials
  automatically, and scripted or subframe requests are ignored.
- **One view:** `setSupportMultipleWindows(false)`. Pop-ups and `_blank` load in the same view. They are **not**
  blocked; that is the leak above.
- **Blocked or denied:**
  - downloads (no `DownloadListener`), the file chooser (default cancel kept);
  - JavaScript bridges, file and content access;
  - geolocation, camera, microphone and other permission requests;
  - full-screen video.
- **TLS:** TLS errors are cancelled, never bypassed.
- **Kept on:** Safe Browsing. WebView debugging is off in release builds.
- **Text and autofill:**
  - no text-selection actions (Share, Search, Process Text), no long-press menus;
  - autofill is off for the view.
  - The keyboard is whatever the protected-state rules allow; its settings must not open from here.
- **Clean profile each try:** cookies, web storage, cache and form data are cleared before every try. They are also
  cleared at Plainphone's next start after a crash, so a sign-in never carries over to make later browsing easier.
- **Close triggers:** screen off, Home, an incoming call, `onStop`, or the activity losing focus to another task.
- **Unsupported, and the spec says so:** portals that need Google sign-in, a bank or payment app, or another app.
  No user-agent spoofing. The failure path is the W5 screen: mobile data, another Wi-Fi, or forget the network.

**Boot loop (F9).** The window avoids the observed unhide-then-power-loss race, because no app is hidden or
unhidden at session time. That holds only if an audit shows **no device-policy change at session start, close or
recovery**. A hard power-off during the window is still in the spike. This doesn't prove there are no other
boot-loop causes.

### 2. Who starts it: the user, with limits (UX variant B)

- **Offered only** when the connected Wi-Fi reports `CAPTIVE_PORTAL`. Checked again just before opening.
- **5 minutes per try.** It ends as soon as the Wi-Fi works. No extending. Every retry is a new try.
- **3 tries in any 24 hours (F3).**
  - A try is debited durably **before** any web content loads. Starts are serialized, so a double tap can't get two.
  - A debit is never cleared by a crash, a reboot, a network change, a failed text or damaged storage. Damaged
    storage counts as "no tries left" until the trusted person resets it with the PIN.
  - Ageing across reboots is conservative. Each debit records the boot id and elapsed time. Plainphone saves its
    elapsed time every few minutes. A debit's age is the **lowest** elapsed time Plainphone can prove. After a
    reboot, a debit may count for longer than 24 hours, and the screens disclose that. Wall-clock and time-zone
    changes never shorten it.
  - That is at most 15 minutes a day for **user-started** tries. The trusted person can open extra tries with the
    PIN.
- **A text to the trusted person (F10).**
  - It is queued in the same write as the debit, and sent when there's signal. Retries run for 7 days.
  - It goes to the trusted person on record when the page opened.
  - Delivery status (waiting, sent, gave up) is shown on both people's lists. A text can arrive late, or not at
    all after 7 days. In a rare crash right after sending, it could arrive twice.
  - **The text is a notice, not a control.** It never stops the page.
  - Wording that stays true when late: "…This was 2 of 3 in the 24 hours before it was sent." (The UX Designer
    finalizes it.)
  - The text **doesn't send the Wi-Fi name or location**. Its arrival time still shows roughly when a page was used.
- **The trusted person is in charge.**
  - A switch in trusted-person mode, changed with a fresh PIN.
  - Asked once at setup, and **off unless the trusted person says yes**.
  - The trusted person can always open one more with the PIN.
- **Visible to both people.** A 30-day list of times, durations and text status, with no network names, URLs, page
  contents or tokens.
- **Copy:** PLA-19's W0, WB1–WB5 and W2–W5, with these changes:
  - "{n} of 3 today" becomes "in the last 24 hours";
  - W4's "use as is" note goes;
  - L2's pull-down instructions aren't needed;
  - the exception line in WB1 says that other websites can open for the whole 5 minutes.

**The PIN-only alternative (A2).** Safer, and it fails the ferry case. A dead end there may lead people to share the
PIN over the phone, and that PIN also adds apps (PLA-19 open question 3). The trip pass (WA5) covers planned trips
only.

### 3. What changes where (when approved)

| Where | Change |
|---|---|
| ADR 0001, Risk 3 | Replace "unsupported" with the sign-in window, its limits, the plain leak sentence, the unsupported portal kinds, and Beaux's exception decision |
| Design D2 | A named exception: "Wi-Fi sign-in window: user-started without a PIN when the trusted person has switched it on; enforced by the companion's session contract; not a D2 session." The D2 rules for Play and Settings are unchanged |
| Design D4 | Replace "Wi-Fi sign-in pages are unsupported (N5)" with the window rules. Add the companion to the self-exemption list. CaptivePortalLogin **stays hidden** |
| web-containment spec | Replace the "unsupported" requirement: trigger, session contract, network rules, WebView rules, unsupported portal kinds, new exact strings (W0) |
| trusted-person-controls spec | The switch, its setup question, the front-page row, extra tries with the PIN, the quota reset with the PIN, the text and its delivery status |
| UX README and flows | WB2 text row, the U8 sentence, the notice line, "What Plainphone can't stop" (PLA-19 change table), and the wording changes above |
| Decision log | Beaux's answers |
| tasks.md | Spike task, then build tasks in a follow-up OpenSpec change |

### 4. Work to get there (if Beaux approves the direction)

1. **Spike, emulator only (Android Platform Engineer), on its own isolated API 35 and API 36 emulators.** The
   acceptance tests are PLA-28's F1–F8 falsification steps, plus the following:
   - the companion window reaches `VALIDATED` after Accept, with CaptivePortalLogin still hidden. Test this with
     mobile data as the working default and with data off. Check that DNS, redirects and subresources go over
     Wi-Fi;
   - Accept via XHR, pages that never finish loading, blocked probe hosts, and the deadline when validation never
     comes;
   - stale-entry and caller tests, quota-across-reboot tests and text-queue failure tests, all with fault
     injection;
   - a hard power-off (`emu kill`) during the window boots normally;
   - an audit showing no device-policy write at session start, close or recovery;
   - a measured close tolerance;
   - a production-like HTTPS probe setup, as well as the HTTP-only fixture.
2. **Cross-model review** of the spike.
3. **Follow-up OpenSpec change** with the spec deltas above. Beaux approves it before build.
4. **Copy test** of W0 and WB1 on cold readers.
5. **Device check** on the spare Motorola with a real sign-in network, only with Beaux's go-ahead for that test.
   Venue compatibility is not claimed from the emulator's Accept button.

## Fallback if the spike fails

If our window can't reliably complete a portal sign-in, the fallback unhides Android's sign-in app. Its two variants
are **not** equally ready:
- **A1 (PIN each time)** needs all of these:
  - PLA-25 accepts mitigation B as a design rule;
  - B is re-tested on the device;
  - the launch path works without the pull-down gesture (UX L1), or the copy test passes with L2;
  - Beaux accepts the D2 custody limit again for this kind. Android's sign-in app, which is an open browser, stays
    reachable if Plainphone dies mid-session.
- **B1 (user-started)** needs all of A1's conditions **plus** Beaux's explicit waiver of D2's fresh-PIN rule for an
  unhide.

Otherwise, **keep sign-in Wi-Fi unsupported (R0).** A portal that fails in our window does not switch to Android's
app automatically. Any fallback comes back to Beaux before it is built.

## Risks left

1. **Other websites for up to 5 minutes**, 3 times in 24 hours for user-started tries (plus PIN tries). A hotspot
   can make this full web access. Bounded and disclosed, not prevented.
2. **Some portals won't work:** Google-account sign-in, bank-app payment, other-app hand-offs, and RFC 8908-only
   portals.
3. **Hostile page content runs in a Plainphone-signed app** with no admin rights, a separate app ID and WebView's
   sandboxed renderer. A WebView bug could still give an attacker that app's small rights. WebView updates come
   through Play (the D3 monthly check).
4. **`VALIDATED` doesn't prove sign-in.** It only ends the window early. W4 covers "closed but no internet".
5. **Texts can be late or never arrive.** The lists show delivery status.
6. **After a reboot, a used try can count for longer than 24 hours** (conservative ageing).
7. **Not yet tested:** our window at all, real portals and real phones.
8. **Calls without signal.** Without your carrier's signal or working Wi-Fi, ordinary calls and texts fail.
   - 9-1-1 can still go through another carrier's network if one is in range. Plainphone never blocks the Emergency
     button, whatever happens with a sign-in page.
   - This feature helps with Wi-Fi calling only after the sign-in is done.
   - Whether Beaux or the trusted person rely on Wi-Fi calling is still unanswered.

## Questions for Beaux

1. **Sign-in Wi-Fi: which?**
   - (a) the user can open the sign-in page alone, once the trusted person has switched that on (recommended);
   - (b) only with the trusted person's PIN each time, optionally with a trip pass for planned trips;
   - (c) keep it unsupported.
2. **The exception.** For (a) or (b): do you accept that a hotspot can show other websites, including search and
   social sites, for the whole 5 minutes? And that some sign-in pages (Google account, bank app) still won't work?
3. **Wi-Fi calling.** Do you or the trusted person rely on Wi-Fi calling where there's no cell signal (home, cabin, ferry,
   work)?
4. **The text to the trusted person.** Leave out the Wi-Fi's name, as recommended? It may arrive late, and it's a
   notice, not a control.
5. **Timing.** Build it after the MVP's core works (recommended), or hold the first handover until it's in?
