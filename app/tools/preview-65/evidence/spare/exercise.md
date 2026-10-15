# Build 65 exercise on the spare (PLA-622)

Sole spare driver, 2026-10-15 08:52–09:26 UTC. Slot 8a: timed Play Store opening. Play is not
signed in; nothing was signed in, typed into Google screens or installed. No sends, no emergency calls.
PinAccess and Play screens are FLAG_SECURE, so their evidence is UI-dump text, not screenshots.

| Check | Result |
|---|---|
| PIN settings | PIN accepted after the upgrade. Rows: Voice messages, **Add an app**, Add a hidden app, Apps on this phone, … (no detail line under Add an app, review finding 6) |
| Add an app | "Open the Play Store?" — 10-minute wait, then 10 minutes open, "This uses 1 of your 2 openings left today", the ★3 disclosure, Start the wait / Not now. Start the wait asks for the PIN again |
| Countdown 1 (08:54:20) | "Opening up in 10 minutes"; Home shows the same line. Home notice → Stop the countdown → Home, notice gone, no PIN asked. Afterwards still "2 openings left" (cancel used nothing) |
| Countdown 2 (08:55:35) | Home line counts down 10 → 1 minute; "Ready – tap to start" at 09:05:43. Add an app during the countdown shows the same countdown (no second start) |
| Ready screen | "Ready to open the Play Store. Tap Start within 5 minutes. This uses 1 of your 2 openings left today." Start / Not now |
| Start (09:06:13) | Play opens at once (no further confirm): `UnauthenticatedMainActivity`, "Sign in to find the latest Android apps, games, movies, music, & more", Sign in. LOCKED; lock-task list = before + `com.android.vending` only; Play `hidden=false`; `market://` resolves to Play; zero calls |
| Play › Sign in | Google's sign-in **opens inside Play's locked task** (`com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity`, same task, LOCKED): "Sign in with your work account", Work email field, Forgot email?, Next. Nothing entered |
| Back, Back | Back to Play's welcome, then back to LMNOPhone: "Everything is back in place" / Done (09:08:51). Lock-task list = before, Play `hidden=true`, no `market://` resolver |
| Done | Home. Recents (APP_SWITCH) stays on Home; Recents lists only LMNOPhone (Play task in mHiddenTasks). `am start` of Play's activity: does not exist; `market://`: unable to resolve |
| Countdown 3 (09:10:07) | "This uses 1 of your **1 openings** left today" (plural nit). Ready at 09:20:28 |
| Screen off during a session | Start 09:20:47, SLEEP 09:20:49.8. LMNOPhone got SCREEN_OFF +28 ms, but its receiver took **6.8 s** to finish (broadcast history), ending ~1.3 s after the wake key. Readback 6 s after screen-off: vending still in lock task, Play `hidden=false`. After wake: "Everything is back in place", lock-task list = before, Play hidden. Screen-off alone, with the screen left off, was not shown to close the session |
| Restart right after (gate, SKIP_INSTALL) | exit 0: restart, LOCKED, HOME `.HomeEntry`, More › Emergency and Phone prefill 911, zero calls. Play hidden, lock-task list = before |
| Limit after restart | "This uses 1 of your **0 openings** left today" is still offered; Start the wait + PIN → "No openings left for now. You've used your 2 openings. The next one is available after about 2:06 a.m., if the phone stays on. Restarting the phone doesn't change this." |
| 2.0 text | Home notice, countdown, Ready, Restored, Add an app, No openings and PIN settings: all text on one screen, lowest button bottom 2260 or less (nav bar 2280). Play and Google screens are Play's own |
| About | "Version 0.1.0-preview-65 (65)", the four Version 65 lines; no "since" section (previous 64) |

Release-note lines: 1–3 held on the spare. Line 4 (reinstall) could not be tried without a Play
account; it states the emulator result from the 8a evidence.

Authority record: its hash changed at install, after the sessions and after the restart (session
record and opening count live there now). The PIN was accepted and the used openings survived the
restart, so the record kept its content.

Spare now: Home, LOCKED, Device Owner kept, text size 1.0 (as before), TalkBack off.
