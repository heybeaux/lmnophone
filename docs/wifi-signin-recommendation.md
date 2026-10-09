# Wi-Fi sign-in pages: Lead's recommendation (PLA-17)

Status: **draft for cross-model review. Not approved.** Until Beaux approves a recommendation, Wi-Fi that needs a
sign-in page stays unsupported (design D4, web-containment spec), and the MVP goes ahead on that basis.

Inputs:
- the emulator probe, `spikes/wifi-signin/RESULT.md` ([PLA-18](/PLA/issues/PLA-18));
- the boot-loop repro, `spikes/bootloop-repro/RESULT.md` ([PLA-22](/PLA/issues/PLA-22)), with mitigation B under
  review in [PLA-25](/PLA/issues/PLA-25);
- the UX flows, `docs/ux/wifi-signin-override.md` ([PLA-19](/PLA/issues/PLA-19));
- Beaux's rule for leaks (Telegram 4463): *"The overall goal is to cut down on distractions, not make the phone
  unusable."*

## Short answer

1. **Yes, allow sign-in pages, for up to 5 minutes at a time.** But don't do it the way the starting frame assumed
   (by unhiding Android's sign-in app). Show the page in **a small sign-in window that Plainphone ships**, bound to
   that Wi-Fi network. This changes no device policy during use. So there is no unhide, no boot-loop window, no
   dependence on Android's notification, and nothing is left open if Plainphone crashes.
2. **The user can start it alone, with limits** (UX variant B): only on a Wi-Fi that needs sign-in, 3 times in any
   24 hours, a text to the trusted person each time. The trusted person turns the option on or off with a fresh
   PIN, and can always open one with the PIN. The ferry case (alone, no signal) is the reason: a PIN-only design
   fails exactly where it's needed.
3. **Do it as a follow-up change after the MVP's core works**, not in the first MVP build. First, a short emulator
   spike proves the sign-in window. If the spike fails, see [Fallback](#fallback-if-the-sign-in-window-spike-fails).

## What we learned

| Finding | Source | What it means |
|---|---|---|
| Plainphone can tell when the connected Wi-Fi needs sign-in (`NET_CAPABILITY_CAPTIVE_PORTAL` on a Wi-Fi `NetworkCallback`), with mobile data on or off | PLA-18 Q7 (emulator) | We can offer it only then |
| Android's sign-in app is a general-purpose browser while open: outside links, JS redirects, `target=_blank`, `window.open` and Google Search all load. Navigation can't be limited from outside the app | PLA-18 Q4 (emulator) | The time limit is the only limit on browsing, if we use Android's app |
| `VALIDATED` arrives about 1 s after the portal accepts; Plainphone closed the session 73 ms later. But "Use this network as is" and "Continue anyway via browser" also set `VALIDATED` | PLA-18 Q5 (emulator) | Close-on-validated works. It doesn't prove sign-in |
| A Device Owner can't open Android's sign-in app with the system's sign-in state: `startCaptivePortalApp` is a hidden API and `CaptivePortal` has no public constructor. A dismissed notification only comes back after a Wi-Fi reconnect | PLA-18 Q3 (emulator, `javap` on android-36 `android.jar`) | Using Android's app means relying on its notification and a pull-down gesture (UX path L2), which is hard for a stranger |
| If Plainphone's process dies mid-session, Android's sign-in app stays open and reachable until Home is pressed | PLA-18 Q6a (emulator) | The custody limit is real for this kind |
| **Unhiding any app and then losing power within about 10 s bricks the phone** (system_server crashes on every boot; only a recovery wipe helps, which triggers FRP). Hiding is safe. Mitigation B (a synchronous PackageManager flush after every unhide) booted 3 of 3 | PLA-22 (emulator, API 35 and 36; AOSP `WRITE_SETTINGS_DELAY = 10*1000`) | Any session that unhides an app carries this risk. A user-started kind would carry it more often, with nobody technical nearby |
| Android's own sign-in app works by binding its process to the Wi-Fi network and loading the portal in a WebView: `initializeWebView()` calls `mCm.bindProcessToNetwork(mNetwork)` before `setContentView` | AOSP `packages/modules/CaptivePortalLogin`, `CaptivePortalLoginActivity.java` (main), around L548–551 (code-confirmed) | Plainphone can do the same with public APIs. Untested by us, hence the spike |
| `reportNetworkConnectivity(wifi, …)` makes Android probe the network again | PLA-18 Q3 (emulator) | Our window can ask Android to re-check after each page load, so `VALIDATED` arrives without Android's app |
| On a ferry the user is often alone, with no mobile signal, so can't phone or text the trusted person | PLA-19 | A PIN-only design fails the main case |

## Options

| | Unhide Android's sign-in app | Plainphone's own sign-in window |
|---|---|---|
| **Trusted person's PIN each time (A)** | A1. Boot-loop window at every start (needs mitigation B). Notification launch path (L2). Fails the ferry case | A2. No boot-loop exposure. Fails the ferry case |
| **User starts it, with limits (B)** | B1. As A1, more often, and alone | **B2. Recommended** |
| **Keep unsupported** | | R0. Today's MVP. Ferries, hotels and hospitals don't work on Wi-Fi |

A2 versus B2 is the "who starts it" fork. Own window versus Android's app is the "how it opens" fork. The two forks
are independent: whichever Beaux picks for who starts it, the own window is the better way to open it.

## The recommendation in detail

### 1. How the page opens: a sign-in window Plainphone ships

**What it is.** A second, tiny app in the Plainphone build, "Plainphone Wi-Fi sign-in":
- A separate package, signed with Plainphone's key, with **no admin rights**. It runs under its own app ID, so
  hostile portal content never runs in the Device Owner's process.
- One activity, with no launcher entry. It is protected by a signature permission, so only Plainphone can start
  it.
- It is never hidden, and it stays in the lock-task list permanently. It can't be reached any other way, so
  allowlisting it adds no reach. **Nothing about device policy changes when the page opens or closes.**
- It is on Plainphone's self-exemption list (exact package and certificate), so the browser classifier never
  hides it. It declares no http(s) intent filters, so it is not a "general browser" under D4.

**What it does.**
- Plainphone starts it with the Wi-Fi `Network` from its callback, the deadline, and a session id.
- It binds its own process to that network (`bindProcessToNetwork`, as AOSP does), then loads a plain http URL so
  the portal can redirect it. If `LinkProperties.getCaptivePortalData()` gives a portal URL, it uses that.
- After each page load it calls `reportNetworkConnectivity(network, true)` so Android checks again.
- It **closes itself** on any of these:
  - the network becomes `VALIDATED`;
  - the deadline (its own timer, plus an `AlarmManager` alarm in Plainphone);
  - the screen turns off;
  - it leaves the screen (`onStop`: Home, a call, the Dialer);
  - the phone leaves that Wi-Fi.
- When it closes, it finishes and kills its own process.

**What it does not do.**
- No address bar, search box, menu, or "open in browser".
- No "Use this network as is" and no "Continue anyway via browser". Those were the two false-`VALIDATED` paths in
  PLA-18 Q5.
- No Custom Tabs hand-off. Android's app queries `CustomTabsService` (PLA-18 Q2).
- No multiple windows (`setSupportMultipleWindows(false)`), no downloads (no `DownloadListener`), no file chooser,
  no JavaScript bridge, no file or content access.
- Only `http:` and `https:` load. `tel:` opens the Dialer, which is already allowed. Every other scheme is
  ignored.

**What still leaks.** Links on the portal page still load. Before sign-in, a real portal's walled garden usually
blocks most of the web. After sign-in, the window closes within about a second. So the realistic exposure is small.
The worst case (a portal with the open web behind it) is the PLA-18 Q4 matrix, limited to 5 minutes. We don't try
to limit sites further: portals send people to third-party sign-in providers, and a site list would break real
sign-ins. Under Beaux's rule this is a small, deliberate-effort leak that makes the phone usable on ferries, in
hotels and in hospitals.

**Custody.** This kind relaxes no device policy, so the D2 limit ("if Plainphone can't run, the relaxation stays
until a helper fixes it") **does not apply**:
- if the sign-in window crashes, the page is gone (fails closed);
- if Plainphone crashes, the window still enforces its own deadline and close conditions, and nothing else can
  open it;
- on every Plainphone start, Plainphone ends any sign-in window still open.

### 2. Who starts it: the user, with limits (UX variant B)

- **Offered only** when the connected Wi-Fi reports `CAPTIVE_PORTAL`. Checked again just before opening.
- **5 minutes per try.** It ends as soon as the Wi-Fi works. No extending, and no back-to-back tries without a new
  tap.
- **3 tries in any 24 hours**, counted on a monotonic clock plus a stored log, so changing the phone's clock doesn't
  reset them. At most 15 minutes a day.
- **A text to the trusted person each time.** It is queued when the page opens and sent when there's signal, with
  retries for 7 days. The page doesn't wait for it. The text has **no network name, place or time** (PLA-19 WB2).
- **The trusted person is in charge.** A switch in trusted-person mode ("Let {user} open sign-in pages"), changed
  with a fresh PIN. It is asked once at setup and **off unless the trusted person says yes**. The trusted person can
  always open one more with the PIN when they're with the phone.
- **Visible to both people.** A 30-day list of times and durations on the trusted person's front page and in the
  user's "About this phone". No network names.
- **Copy:** use PLA-19's W0, WB1–WB5 and W2–W5. Drop W4's "use as is" note, because the window has no such option.
  L3 is the launch path, so L2's pull-down instructions are not needed.

**Why not PIN-only (A2).** It is safer on paper and fails the case Beaux named. On a ferry the user is often alone
with no signal. A dead end there teaches people to read the PIN over the phone, and that PIN also adds apps
(PLA-19 open question 3). The trip pass (WA5) only covers planned trips.

**Is it still custody?** Partly. D2 rule 1 (a fresh PIN to start) governs relaxations of device policy. This kind
relaxes none. So instead of a D2 exception, it becomes a **web-containment exception**: a time-boxed web view that
the trusted person has switched on. It still needs an ADR change, cross-model review and Beaux's approval, because
it puts web pages in front of the user without a PIN.

### 3. What changes where (when approved)

| Where | Change |
|---|---|
| ADR 0001, Risk 3 | Replace "Wi-Fi networks that need a sign-in page are unsupported in the MVP" with the sign-in window, its limits and its leak. Record Beaux's decision |
| Design D4 | Replace "Wi-Fi sign-in pages are unsupported (N5)" with the sign-in window rules above. Add the companion package to the self-exemption list |
| Design D2 | One line: the sign-in window is **not** a supervised session, because it relaxes no policy. D2's rules are unchanged |
| web-containment spec | Replace the "unsupported" requirement with: offered only on `CAPTIVE_PORTAL`; 5 minutes; close conditions; schemes; limits; text; new exact strings (W0). CaptivePortalLogin **stays hidden** |
| trusted-person-controls spec | The switch, its setup question, the front-page row, and the extra try with the PIN |
| UX README and flows | WB2 text row, the U8 sentence, the notice line, and the "What Plainphone can't stop" line (PLA-19 change table) |
| Decision log | Beaux's answer |
| tasks.md | A new spike task, then build tasks in a follow-up OpenSpec change |

### 4. Work to get there (if Beaux approves)

1. **Spike, emulator only (Android Platform Engineer).** On the PLA-18 fake-portal fixture, API 35 and API 36:
   - The companion window binds to the Wi-Fi, loads the portal and reaches `VALIDATED` after Accept, with
     CaptivePortalLogin still hidden.
   - It closes within 2 s of `VALIDATED`, at the deadline, on screen off and on Home. It ends when Plainphone
     restarts.
   - Reach matrix as in PLA-18 Q4, to show it is no worse.
   - It can't be started by another app (`am start` from shell as a non-Plainphone caller), and it isn't classed as
     a browser.
   - It works under lock task, both with mobile data on and with it off.
   - A hard power-off (`emu kill`) during the window boots normally. Nothing is unhidden, so this is expected, but
     check it.
2. **Cross-model review** of the spike (Verifier & Escape Artist).
3. **Follow-up OpenSpec change** with the spec deltas above. Beaux approves it before build.
4. **Copy test** of W0 and WB1 on cold readers, in the existing copy-test packet.
5. **Device check** on the spare Motorola with a real sign-in network. That needs Beaux's go-ahead for that test.

## Fallback if the sign-in window spike fails

If a third-party app can't reliably complete a portal sign-in (for example, binding or re-probing doesn't work), the
fallback is A1/B1, which unhides Android's sign-in app. It is acceptable only if:
- PLA-25 accepts mitigation B as a design rule, and B is re-tested on the device;
- the launch path works without the pull-down gesture (UX L1), or the copy test passes with L2;
- the D2 custody limit is restated for this kind, and Beaux accepts it again: Android's sign-in app (an open
  browser) stays reachable if Plainphone dies mid-session.

If those don't hold, **keep sign-in Wi-Fi unsupported (R0).** The Lead brings the fallback back to Beaux before
building it. It is not pre-approved by this recommendation.

## Risks left

1. **Sign-in pages can show other websites for up to 5 minutes, 3 times a day.** Bounded and disclosed, not
   prevented.
2. **A hostile hotspot's page runs in a Plainphone-signed app.** It has no admin rights and a separate app ID, and
   WebView's renderer is sandboxed. A WebView bug could still give an attacker that app's (small) rights. WebView
   is updated through Play (D3 monthly check).
3. **`VALIDATED` doesn't prove sign-in.** It only ends the window early, which is the safe direction. W4 covers
   "closed but no internet".
4. **The text to the trusted person can arrive hours late** (no signal on a ferry). The 30-day list is the record.
5. **Not tested on a real portal or a real phone yet.** Real portals vary (cookies, pop-ups, payment pages,
   third-party sign-in providers).
6. **Wi-Fi calling.** A phone with no cell signal and no working Wi-Fi can't call anyone, including 9-1-1. This
   feature helps only once the sign-in is done. Whether Beaux or the trusted person rely on Wi-Fi calling is still unanswered.

## Questions for Beaux

1. **Sign-in Wi-Fi.** Approve the recommendation (own sign-in window; the user can start it, 5 minutes, 3 times a
   day, a text to the trusted person; the trusted person switches it on)? Or only with the trusted person's PIN
   each time? Or keep it unsupported?
2. **Wi-Fi calling.** Do you or the trusted person rely on Wi-Fi calling where there's no cell signal (home, cabin, ferry,
   work)?
3. **The text to the trusted person.** Leave out the network's name, as recommended, so the text doesn't say where
   the user is? Or include it?
4. **Timing.** Build it after the MVP's core works (recommended), or hold the first handover until it's in?
