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
| ★6 | Erasing the phone with its buttons | Anyone holding the phone can erase it using its buttons (a factory reset). After that, Google asks for your Google account, or the backup person's you chose at setup, before the phone can be used. Plainphone doesn't come back by itself: it has to be set up again, and anything not backed up is lost. |
| 7 | Wi-Fi with a sign-in page | Wi-Fi that asks you to sign in or accept terms on a page first, like in hotels, hospitals or on ferries, won't work. Mobile data and home Wi-Fi are fine. |
| 8 | Phone software updates | Android updates can arrive before the Plainphone team has checked them; some security updates install anyway. Until we check, a new update could add a way around Plainphone that we haven't seen. |
| 9 | A bad Plainphone update | There's no undo for a Plainphone update. If one goes badly wrong, the fix is to wipe the phone and set it up again. |
| 10 | Emergency alerts | Plainphone doesn't block emergency alerts. {build_alert_line} |
| 11 | What Google and apps collect | Plainphone doesn't change what Google or each app's maker collects. The phone still uses a Google account for the Play Store, and each app follows its own privacy rules. Plainphone itself sends nothing anywhere. |

`{build_alert_line}` is "We checked that alerts appear on this phone ({date})." when the real-device check
was observed, or "We couldn't test that an alert appears on this phone's software. This was agreed on {date}." when Beaux recorded a build-scoped exception. It is never left blank.

- **Primary:** I've read this (during setup); Done (afterwards)
- **Notes:** item 6 must also say, per device-lockdown, that reset protection doesn't put Plainphone back. The
  sentence "Plainphone doesn't come back by itself" covers this; the engineer must not shorten it away. Items 7–10 are not
  required by the spec's list, but are part of what Beaux accepted, so the trusted person should see them too.
  Item 11 was added after the copy-test pilot, where the reader's biggest worry was "Google (due to tracking
  etc)". It is true today: the design has no servers and no analytics.

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
- **Copy-test change (pilot):** the earlier last line ("a new part of the phone lets an app get around
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
| Both saved settings copies broken (safe fallback in use) | "Plainphone's saved settings were damaged, so it's using a basic safe setup: Phone, Messages and Emergency." | "{helper} needs to set Plainphone up again, or install an update." |
| Post-update self-check failed | "After the last Plainphone update, a check didn't pass: {item}." | "Call {helper}." |
| Unknown phone software (recommended, [Open question 3](README.md#open-questions-for-the-lead)) | "The phone has new software that hasn't been reviewed." | "See 'Phone software changed – review pending'." |

- **Primary:** OK
- **Secondary:** Details for the helper

## L4. Turning Plainphone off

**Spec:** trusted-person-controls ("Turning Plainphone off restores everything it changed": fresh PIN, two plain
confirmations, journal and resume, report "Off" only when Device Owner is gone, never fall back to a wipe).

### L4a. What happens

- **Title:** Turn Plainphone off?
- **Body:**
  This makes {user}'s phone an ordinary Android phone again. Every app will open, including web browsers and the
  Play Store, and the home screen will look different.
  Photos, messages and contacts are kept. Nothing is erased.
  To use Plainphone again later, {helper} has to set it up again with a computer.
  Plainphone only turns off this way, with your PIN. A flat battery, a restart or a phone update doesn't turn it
  off.
- **Primary:** Continue
- **Secondary:** Keep Plainphone on

### L4b. First confirmation

- **Title:** Are you sure?
- **Body:** Plainphone will be turned off on {user}'s phone.
- **Primary:** Yes, turn it off (the only red button outside Emergency)
- **Secondary:** No, keep it on
- **Then:** PIN or recovery code (fresh).

### L4c. Second confirmation

- **Title:** Last check
- **Body:** Once it's off, only {helper} can turn it back on, using a computer. Keep the phone on and plugged in if
  you can. It takes a minute or two.
- **Primary:** Turn off now
- **Secondary:** Cancel

### L4d. Turning off

- **Title:** Turning Plainphone off…
- **Body:** Step {n} of {total}. Keep the phone on. Calls and Emergency still work.
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
| reset-protection policy | "removing {tp}'s account from reset protection" |
| Play app restrictions | "restoring the Play Store" |
| check home app and dialer | "checking the phone and home screen work" |
| remove Device Owner | "handing the phone back to Android" |
