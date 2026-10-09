# Limits, warnings and turning off

Words, rules and placeholders are in [README.md](README.md).

---

## L1. What Plainphone can't stop

**Spec:** trusted-person-controls ("Plain disclosure of leftover risk": each item in one or two plain sentences);
approval-packet "Limits we need you to accept"; web-containment; device-lockdown (system updates disclosure).

Reached from the trusted-person front page, and read in full during setup step 6. A scrolling page of short
sections. One heading per item, then one or two sentences. The required items are marked ★.

- **Title:** What Plainphone can't stop
- **Intro:** Plainphone makes this phone much simpler, but it isn't perfect. Here's what it can't stop, so you can
  decide with {user} what's OK.

| # | Heading | Text |
|---|---|---|
| ★1 | Web pages inside apps | Some apps show web pages inside themselves, such as help pages, articles or sign-in screens. Plainphone can't control those pages; each app's note tells you what we found. |
| ★2 | Google help and account pages | Some Google screens, like account and help pages, can show web pages. Testing found these on this phone: {list from Q4/Q13, or "none so far"}. |
| ★3 | The Play Store and full settings | When you open the Play Store or full phone settings, the phone is open for those minutes, and their help pages can show the web. That's why you stay with the phone until you tap Done. |
| ★4 | If Plainphone stops working while they're open | If Plainphone breaks while the Play Store or settings are open, they stay open until {helper} fixes the phone. Don't hand the phone back until Plainphone says it's ready. |
| ★5 | Apps installed without you | If an app gets installed without you, it can't be opened. But if Plainphone can't hide it, it might still run unseen in the background, for any length of time. |
| ★6 | Erasing the phone with its buttons | Anyone holding the phone can erase it using its buttons (a factory reset). After that, Google asks for your Google account (with two trusted people: either one's), or the backup person's you chose at setup, before the phone can be used. Plainphone doesn't come back by itself: it has to be set up again, and anything not backed up is lost. |
| 7 | Wi-Fi with a sign-in page | Wi-Fi that asks you to sign in or accept terms on a page first, like in hotels, hospitals or on ferries, won't work. Mobile data and home Wi-Fi are fine. |
| 8 | Phone software updates | Android updates can arrive before the Plainphone team has checked them; some security updates install anyway. Until we check, a new update could add a way around Plainphone that we haven't seen. |
| 9 | A bad Plainphone update | There's no undo for a Plainphone update. If one goes badly wrong, the fix is to wipe the phone and set it up again. |
| 10 | Emergency alerts | Plainphone doesn't block emergency alerts. {build_alert_line} |
| ★11 | What Google collects | Plainphone has no server of its own, no analytics and no account. It sends texts to your trusted people through your carrier, connects to your helper's computer over home Wi-Fi only to update itself, and keeps its history on the phone. But this phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop. The privacy steps from setup reduce it. |
| ★12 | Two trusted people *(only with two)* | {tp} and {other} are equals. Either of you can add apps, open the Play Store or settings, or turn Plainphone off. The 24-hour wait protects against someone who saw a PIN, not against the other trusted person: a recovery code skips it. |
| ★13 | Changes can be held up | Someone who knows a PIN can keep starting changes, and anyone holding the phone can keep cancelling them. Nobody gains anything by it, but it can hold up a change you want. A trusted person's recovery code skips the wait. |
| ★14 | Losing power at the wrong moment | If the phone loses power at the very moment Plainphone shows an app again, the phone may not start up. Then it has to be erased with its buttons and set up again. Plainphone keeps that moment as short as it can, and doesn't start these changes when the battery is below 5% unless the phone is charging. |

`{build_alert_line}` is "We checked that alerts appear on this phone ({date})." when the real-device check
was observed, or "We couldn't test that an alert appears on this phone's software. This was agreed on {date}." when Beaux recorded a build-scoped exception. It is never left blank.

- **Primary:** I've read this (during setup); Done (afterwards)
- **Notes on the new items ([PLA-42](/PLA/issues/PLA-42)):** item 11 is design C9's sentence, word for word (TPC
  disclosure: no server, analytics or account; texts; the update connection; Google). It replaces the earlier
  "Plainphone itself sends nothing anywhere", which wasn't accurate (PLA-31 F9). Items 12–14 are TPC's disclosure
  items on two trusted people, denial of progress, and the unhide-to-save gap (device-lockdown, "Every unhide is
  saved at once"). Item 14 doesn't promise the battery rule covers everything: repairs that keep 9-1-1 working are
  never delayed, so "these changes" means adding apps, opening up and turning off.
- **Notes:** item 6 must also say, per device-lockdown, that reset protection doesn't put Plainphone back. The
  sentence "Plainphone doesn't come back by itself" covers this; the engineer must not shorten it away. Items 7–10 are not
  required by the spec's list, but are part of what Beaux accepted, so the trusted person should see them too.
  Item 11 was added after Beaux's copy test, where his biggest worry was "Google (due to tracking etc)". The
  setup privacy steps now exist ([S4e](flows-setup.md#s4e-make-google-collect-less)), so the item points to them.

### L1, self-managed version

**Spec:** TPC "Plain disclosure of leftover risk" (self-managed item; scenario "Self-managed user reads the limits":
"it says plainly that Plainphone is friction, not a lock, in this mode"); C4.

- **Title:** What Plainphone can't stop
- **Intro:** In this mode Plainphone is friction, not a lock. Here's what that means, and what it can't stop.
- **First item, heading "Friction, not a lock":** In self-managed mode, you hold the PIN. Plainphone adds waits and
  limits so that distraction takes effort. It can't stop you, because you are in charge. You can always remove it
  by waiting at least 24 hours of the phone being on (longer if the change is cancelled or interrupted), or by
  wiping the phone. ([Flag 3](flows-people-modes-and-waits.md#flags-strings-i-think-should-change))
- Then items 1, 2, 5, 7–11 and 14 as above, with "you" for {user}, and these in place of 3, 4, 6 and 13:

| # | Heading | Text |
|---|---|---|
| 3 | The Play Store and full settings | When you open the Play Store or full phone settings, the phone is open for those minutes, and their help pages can show the web. Plainphone closes them at the time it shows. |
| 4 | If Plainphone stops working while something is open | If Plainphone breaks while the Play Store or settings are open, they stay open until {helper} fixes the phone, and nobody is watching. |
| 6 | Erasing the phone with its buttons | Anyone can erase this phone using its buttons. After that, Google asks for your own Google account (or the backup person's). So you can set the phone up again without Plainphone: a way out that doesn't wait 24 hours. Plainphone doesn't come back by itself, and anything not backed up is lost. |
| 13 | Changes can be held up | Anyone holding your phone can cancel a change you started, and nothing in Plainphone can skip the wait. If that happens, keep the phone with you and start it again. |

- Item 11 in this mode: [Flag 4](flows-people-modes-and-waits.md#flags-strings-i-think-should-change) (no trusted
  people to text).

## L2. Phone software changed – review pending

**Spec:** device-lockdown ("System updates follow a maintained lifecycle, and unknown builds fail safe").

### L2a. The notice ("Needs you" on T2)

Exact text: **Phone software changed – review pending**

### L2b. The page

- **Title:** Phone software changed – review pending
- **Body:**
  Android, the phone's own software, updated itself on {date}. The Plainphone team checks each new version for
  gaps, and hasn't checked this one yet.
  Nothing has changed for {user}: the same apps, the same limits, and Emergency still works. There's nothing you
  need to do. {helper} has been sent a text and will install a Plainphone update.
  Until then, there's a small chance the update added a gap that lets an app get around Plainphone.
- **Copy-test change (round 3, Beaux):** the earlier last line ("a new part of the phone lets an app get around
  Plainphone") was read as "{user} will have elevated permissions". The body now says nothing changed for
  {user}, and calls the risk "a gap".
- **Primary:** OK
- **Secondary:** What changed → L2c

### L2c. What changed

- **Title:** What changed
- **Body:** Parts of the phone that are new or different since the last version Plainphone knows. You don't need to
  do anything with this list; it's for {helper} and the Plainphone team.
- A list: friendly app name where Android has one, with the technical name underneath in small grey text, and
  "New" or "Changed" beside each.
- **Primary:** OK

### L2d. Before the update (pending)

When Android reports an update is waiting (`onSystemUpdatePending`), before it installs: the spec's exact text is
sent to `{tp}` and `{helper}`: "A phone software update is coming. Plainphone needs a review." The front page shows
"Phone software update waiting – the Plainphone team will review it" in "Needs you", with this page:
"Android has an update ready. Plainphone has asked the phone to wait, but some updates install anyway. Nothing
for you to do."

## L3. Plainphone needs attention (trusted-person view)

**Spec:** calm-home-screen; device-lockdown (baseline); approved-apps (hide failed); trusted-person-controls
(post-update self-check).

- **Title:** Plainphone needs attention
- **Body:** Calls, texts and Emergency still work. Here's what Plainphone found:
  one line per cause, from this table, then "What to do" under each.

| Cause | Line | What to do |
|---|---|---|
| Hiding an app failed | "{app} couldn't be hidden. It still can't be opened." | "Ask {helper} to remove it." |
| Both saved settings copies broken (safe fallback in use) | "Plainphone's saved settings were damaged, so it's using a basic safe setup: Phone and Messages. 9-1-1 still works." | "{helper} needs to set Plainphone up again, or install an update." |
| Post-update self-check failed | "After the last Plainphone update, a check didn't pass: {item}." | "Call {helper}." |
| Unknown phone software (recommended, [Open question 3](README.md#open-questions-for-the-lead)) | "The phone has new software that hasn't been reviewed." | "See 'Phone software changed – review pending'." |

- **Primary:** OK
- **Secondary:** Details for the helper

## L4. Turning Plainphone off

**Spec:** trusted-person-controls ("Turning Plainphone off restores everything it changed": a waiting change; fresh
PIN, two plain confirmations, journal and resume, report "Off" only when Device Owner is gone, never fall back to a
wipe); design C6. The wait itself, its texts and the skip are in
[flows-people-modes-and-waits.md](flows-people-modes-and-waits.md#m3-changes-that-wait-24-hours).

### L4a. What happens

- **Title:** Turn Plainphone off?
- **Body:**
  This makes {user}'s phone an ordinary Android phone again. Every app will open, including web browsers and the
  Play Store, and the home screen will look different.
  Photos, messages and contacts are kept. Nothing is erased.
  It happens after at least 24 hours of the phone being on. Until then, anyone holding the phone can cancel, and
  {everyone} will get a text now.
  To use Plainphone again later, {helper} has to set it up again with a computer.
  Plainphone only turns off this way, with a PIN. A flat battery doesn't turn it off: when the phone starts
  again, Plainphone is back.
- **Primary:** Continue
- **Secondary:** Keep Plainphone on

- **Self-managed version:** "This makes your phone an ordinary Android phone again…", and the wait line reads
  "It happens after at least 24 hours of the phone being on. Until then, you can cancel it. Nothing can make it
  shorter."
- **Notes:** the flat-battery line is from the Lead, based on PLA-5 A6 (the lockdown comes back after a
  restart). The 24-hour wait is Beaux's choice (card `c60170d6`, approved in PLA-30).

### L4b. First confirmation

- **Title:** Are you sure?
- **Body:** Plainphone will be turned off on {user}'s phone.
- **Primary:** Yes, turn it off (black like every primary button; it stands out by its words and the extra confirmations, not by colour)
- **Secondary:** No, keep it on
- **Then:** PIN or recovery code (fresh; with two trusted people, "Who are you?" first).

### L4c. Second confirmation

- **Title:** Last check
- **Body:** Once it's off, only {helper} can turn it back on, using a computer. Plainphone turns off after at least
  24 hours of the phone being on. Time while the phone is off doesn't count.
- **Primary:** Start the 24-hour wait
- **Secondary:** Cancel
- **Then:** [M3c](flows-people-modes-and-waits.md#m3c-started) ("Waiting 24 hours"). The home line becomes
  "Plainphone will turn off after {time}. Tap to cancel."

### L4c2. When the wait ends

Plainphone turns off by itself (C2: "It doesn't wait for anyone to come back and confirm"), after checking that the
PIN that started it is unchanged. If the battery is below 5% and not charging, it waits for the charger (C6). In
supervised mode, a trusted person can skip the wait with their recovery code
([M3e](flows-people-modes-and-waits.md#m3e-skip-the-wait-with-a-recovery-code)): "Skip the wait and turn
Plainphone off now?"

### L4d. Turning off

- **Title:** Turning Plainphone off…
- **Body:** Step {n} of {total}. Keep the phone on. Calls and Emergency still work.
- Shown if someone is looking at the phone when the wait ends; otherwise it happens in the background, and the user
  sees L4e when they next unlock.
- No buttons.
- **After a restart in the middle:** "Still turning off. Carrying on from where it stopped." (resumes at the next
  step not yet read back).

### L4e. Off

- **Title:** Plainphone is off
- **Body:** This is now an ordinary phone. Android will ask which home screen to use; pick the one that isn't
  Plainphone.
- **Primary:** OK
- **Notes:** shown only when the phone is no longer managed by Plainphone. If Plainphone finds it already isn't,
  it goes straight here.

### L4f. Couldn't finish

- **Title:** Plainphone couldn't finish turning off
- **Body:** Nothing has been erased, and calls and Emergency still work. Plainphone stopped at: {step in plain
  words}. Call {helper} and show this screen.
- **Primary:** Try again
- **Secondary:** Details for the helper
- **Notes:** never offer "Erase phone" or any wipe here (spec: "SHALL NOT fall back to a wipe on its own").

Plain names for the steps shown in `{step}` (from the spec's restore list):

| Restore item | `{step}` text |
|---|---|
| user restrictions | "turning normal settings back on" |
| hidden apps | "showing hidden apps again" |
| lock-task packages and features | "unlocking the home screen" |
| persistent preferred activities | "letting you choose a home screen" |
| input methods, accessibility services | "allowing other keyboards and accessibility apps" |
| keyguard disabled features | "restoring the lock screen" |
| browser role | "letting a web browser open links" |
| default SMS and dialer | "restoring the texting and phone apps" |
| reset-protection policy | "removing {everyone}'s accounts from reset protection" (self-managed: "removing your account from reset protection") |
| Play app restrictions | "restoring the Play Store" |
| check home app and dialer | "checking the phone and home screen work" |
| remove Device Owner | "handing the phone back to Android" |
