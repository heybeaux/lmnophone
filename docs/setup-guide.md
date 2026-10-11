# LMNOPhone setup guide (draft)

**For the helper.** For the first two phones that's Beaux. Write as if for anyone: you don't need to know Android,
but you do need a computer and about two hours the first time.

> **Draft status.** Written by the Product & UX Designer for [PLA-12](/PLA/issues/PLA-12) (task 5.3), with the
> mode choice, two trusted people, the account-number steps for each account holder and the privacy steps added
> for [PLA-42](/PLA/issues/PLA-42) (task 3.2 of `add-copy-test-product-decisions`). Paths marked **[to confirm in
> the dry run]** haven't been checked on a phone; the Android Platform Engineer confirms them during the MVP dry
> run ([PLA-15](/PLA/issues/PLA-15)). Every step
> that uses the setup script is marked **[PLACEHOLDER]** until the MVP issue provides the real script. The guide
> must be dry-run by someone who didn't write it (task 5.3) before anyone relies on it.
>
> **Gates.** Only set up a phone Beaux has approved for this. Never wipe a phone from its recovery menu unless
> Beaux has approved that for that phone; on the spare Motorola, never.

## What you need

- The phone, charged to at least 70%.
- A Mac or PC with Android's `adb` tool installed **[PLACEHOLDER: install steps come with the script]**.
- A USB cable that carries data (not a charge-only cable).
- The LMNOPhone files: the app and the setup script **[PLACEHOLDER: where to get them]**.
- The home Wi-Fi name and password.
- The person who'll use the phone (the **user**), for their Google password at step G4, the privacy steps, and the
  talk-through at the end.
- **Supervised phone:** each **trusted person** (one or two), for about 20 minutes each: their PIN, their recovery
  code, their Google account number (Part H), and the test text. They need their **own** phone or computer, not
  this one.
- **Self-managed phone:** the user, for the whole of Part G, with a computer or another phone of their own for
  Part H.
- A pen and the [setup record](#setup-record) at the end of this guide.

## The order, at a glance

| Part | What | Phone online? | Time |
|---|---|---|---|
| A | Check the phone is supported | yes | 5 min |
| B | Back up | yes | 20–60 min |
| C | Remove accounts, take the phone offline, reset it through Settings | **going offline** | 15 min |
| D | First-boot setup, offline | **no** | 10 min |
| E | Turn on USB debugging | no | 5 min |
| F | Run the setup script **[PLACEHOLDER]** | no | 5 min |
| G | Finish setup on the phone, including the mode choice and the privacy steps | from step G4 | 30 min |
| H | Each account holder gets their Google account number | (their device) | 5 min each |
| I | Handover checks | yes | 30 min |

Time Parts F to G yourself: from the first script command to the green handover check should be **30 minutes or
less** (guided-setup). Write the times in the setup record.

---

## A. Check the phone is supported

1. On the phone, open **Settings → About phone**. Write down the **model** and the **Android version** in the
   setup record.
2. Compare with the supported list: **[PLACEHOLDER: supported models and builds, shipped with the script]**.
   The spare moto g power 5G (2024), XT2415-1, is for testing only.
3. If it isn't listed, **stop**. LMNOPhone needs a reviewed list of the phone's built-in parts for each model and
   software version. Without it, the script will refuse.

## B. Back up

Everything on the phone is erased in Part C. Back up first, and check the backup before you go on.

1. **Photos and videos.** Either turn on Google Photos backup and wait until it says "Backup complete", or copy the
   `DCIM`, `Pictures` and `Download` folders to the computer with the cable.
2. **Contacts.** Open **Contacts**; at the top, check they're saved to the Google account (not "Device"). If any
   are on the device only, move them to the Google account.
3. **Google backup.** **Settings → Google → Backup → Back up now.** Wait for it to finish.
4. **Text messages.** LMNOPhone's setup skips restoring from backup, so old texts probably won't come back onto
   the phone, though they stay in the Google backup. If any old texts matter, save them another way first. (Not
   yet tested; the dry run should check.)
5. **Chat apps** (WhatsApp, Signal, Telegram…): use each app's own backup.
6. **Sign-in codes.** If this phone holds an authenticator app or receives 2-step verification prompts, move them
   to another device **now**. After the reset they're gone.
7. **eSIM.** If the phone uses an eSIM, note the carrier and phone number. If anything goes wrong in Part C, the
   carrier may need to re-issue it.
8. **Check.** Open a few photos on the computer or in Google Photos on another device. Don't continue until the
   backup is confirmed.

## C. Remove accounts, take the phone offline, reset

### C1. Remove every account

**Settings → Passwords, passkeys & accounts.** Tap each account, then **Remove account**. Do every one, including
work and email accounts.

Why: removing accounts first means Google's reset protection won't lock the phone after the reset.

### C2. Take the phone off the mobile network

Do the branch that matches the phone.

**Physical SIM card**
1. Turn the phone off.
2. Push the SIM tool into the small hole in the SIM tray, pull the tray out, remove the card, and put the empty
   tray back.
3. Turn the phone on. Keep the SIM card safe; it goes back in at step G4.

**eSIM (a SIM built into the phone)**
1. **Settings → Network & internet → SIMs.** Tap the eSIM.
2. Turn **Use SIM** off. Confirm.
3. Do **not** delete or erase the eSIM. You'll turn it back on at step G4.

**Both** (some phones have a card and an eSIM): do both branches.

### C3. Turn off Wi-Fi

Swipe down from the top of the screen and turn Wi-Fi off. Also turn off Bluetooth.

### C4. Reset the phone through Settings

1. **Settings → System → Reset options → Erase all data (factory reset).**
2. If you see a tick box such as **Erase eSIMs** or **Erase downloaded SIMs**, make sure it is **not** ticked. You
   want to keep the eSIM.
3. Tap **Erase all data**. Enter the phone's screen lock if asked. Confirm.
4. The phone restarts and erases itself. This takes 5–10 minutes.

**Never use the recovery menu** (holding buttons at power-on) to wipe the phone. That triggers Google's reset
protection for the old accounts, and on a phone Beaux hasn't approved for it, it's not allowed.

## D. First-boot setup, offline

The phone must not connect to anything until LMNOPhone has tidied it up (Part G). A fresh Motorola installed 7 apps
by itself within minutes of joining Wi-Fi (`spikes/device-moto/RESULT.md` § A0). LMNOPhone hides those before the
phone goes online.

1. **Language:** choose it, then **Start**.
2. **Wi-Fi screen:** don't pick a network. Tap **Skip** or **Set up offline**. If asked "Skip?", confirm.
3. **Copy apps & data:** **Don't copy**, if it appears.
4. **Google sign-in:** **Skip**. Don't sign in to any account.
5. **Google services and other choices** (location, diagnostics): choose what you prefer; they don't affect
   LMNOPhone. Write your choices in the setup record.
6. **Screen lock:** **Skip** for now. (Whether the user's phone gets a screen lock is a decision for later; it can
   be added with "Open full phone settings".)
7. **App recommendations, "Moto apps", "Additional apps", offers:** untick everything, **Skip** or **No thanks**.
8. You reach the phone's normal home screen. Setup is finished.

> ### Stop here if the phone won't finish offline
>
> If any screen won't let you continue without Wi-Fi, a mobile connection or a Google account, **stop**.
> **This phone model can't be used with LMNOPhone.**
>
> Don't connect to Wi-Fi to get past the screen: the phone would start installing apps before LMNOPhone can
> control them. Turn the phone off and tell the LMNOPhone team the model and the screen you got stuck on.
>
> If you connected by mistake, go back to step C3 and reset again.

Note: on the spare Motorola, the first-boot wizard has so far only been run **with** Wi-Fi. Finishing it offline
on that model is not yet confirmed. The dry run checks this.

## E. Turn on USB debugging

USB debugging lets the computer install LMNOPhone. LMNOPhone turns it off again at the end of setup.

1. **Settings → About phone.** Scroll to the bottom. Tap **Build number** 7 times. The phone says "You are now a
   developer" (it may ask for the screen lock; there isn't one yet).
2. Go back. **Settings → System → Developer options.**
3. Turn on **USB debugging**. Confirm.
4. **Don't** turn on **Wireless debugging**.
5. Plug the phone into the computer.
6. On the phone, a box asks **Allow USB debugging?** Tick **Always allow from this computer**, then tap **Allow**.
   - **"Always allow" matters.** Once LMNOPhone locks the screen, this question can't be answered on the phone
     (PLA-5: with LMNOPhone's lock on, the prompt never authorised the computer). Without "Always allow", a later
     restart could leave the computer unable to finish setup.
7. On the computer, run `adb devices`. You should see one line ending in **device**. If it says
   **unauthorized**, unplug, plug in again, and answer the question on the phone.
8. If a USB menu appears ("Use USB for"), leave it on **No data transfer** or **Charging**.

## F. Run the setup script [PLACEHOLDER]

> **[PLACEHOLDER]** Everything in Part F is a placeholder until the MVP issue provides the real script. Commands,
> file names and messages will change.

1. **[PLACEHOLDER]** Put the LMNOPhone app and the script in one folder on the computer, for example
   `~/plainphone/`.
2. **[PLACEHOLDER]** Open Terminal in that folder. Note the time in the setup record ("script start").
3. **[PLACEHOLDER]** Run: `./plainphone-setup.sh`
4. **[PLACEHOLDER]** The script checks the phone. It stops, and installs nothing, unless **all** of these are true:

   | The script checks | If it stops, the message will say something like | What to do |
   |---|---|---|
   | The model and software version are on the approved list | "This phone model or software version isn't supported" | Stop. See Part A. |
   | The phone's software is the original, verified version | "Verified boot is not green" | Stop. This phone can't be used. |
   | The bootloader is locked | "Bootloader is unlocked" | Stop. This phone can't be used. |
   | There are no accounts on the phone | "Accounts found on the phone" | Go back to C1, then reset again (C4). |
   | No network of any kind is connected (Wi-Fi, mobile, Bluetooth tethering, USB tethering, VPN…) | "A network is active: …" | Turn it off (C2, C3). Run the script again. |
   | A reviewed list of the phone's built-in parts exists for this exact software version | "No inventory for this build" | Stop. Tell the LMNOPhone team the model and version. |

5. **[PLACEHOLDER]** When every check passes, the script installs LMNOPhone and makes it the phone's manager.
   (Android calls this "Device Owner"; you won't see that word again.)
6. **[PLACEHOLDER]** The script finishes with "Done – continue on the phone". The phone shows **Set up
   LMNOPhone**. Leave the cable plugged in.

## G. Finish setup on the phone

The screens are described in [docs/ux/flows-setup.md](ux/flows-setup.md). Before you start, decide the mode (G0).

### G0. Choose how the phone is looked after

The first screen asks this. Decide it with the user before setup.

| | **Someone looks after it** (supervised) | **The user looks after it** (self-managed) |
|---|---|---|
| Who holds a PIN | One or two trusted people. The user has none. | The user. |
| Adding an app | A trusted person, with the phone in their hands | The user, after a 10-minute wait; at most 2 times a day |
| Turning LMNOPhone off | A trusted person's PIN, then 24 hours; anyone can cancel. A trusted person's recovery code skips the wait. | The user's PIN, then 24 hours. Nothing skips it. |
| After a wipe from the phone's buttons | Google asks for a trusted person's (or the backup person's) account | Google asks for the user's own (or the backup person's) account, so the user can set the phone up without LMNOPhone |
| In one line | Support from someone you trust | **Friction, not a lock**: it slows the user down, but can't stop them |

Pick **self-managed** for someone who wants calm for themselves and has nobody to look after their phone, or doesn't
want anyone to. Say plainly to them that it's friction, not a lock. Either can be changed later; that takes 24
hours.

**Two trusted people.** They are equals: either can add apps or turn LMNOPhone off alone, and both get a text
whenever either PIN is used. Both need to be there for G3 and Part H, or come back to finish before "Ready".

### G1–G8. The steps

| Step | On the phone | Who |
|---|---|---|
| Tidy-up | LMNOPhone hides the apps that came with the phone. Offline. | Helper |
| G1. Mode | Supervised or self-managed (G0). Self-managed: read "Friction, not a lock" with the user, and optionally make the waits stricter. | Helper and user |
| G2. Who's who | Names and numbers: the user, each trusted person (one or two), the helper. Self-managed: no trusted people; the helper is optional. With two trusted people, both read "You are equals". | Helper |
| G3. PINs and recovery codes | **Hand the phone to each trusted person in turn** (self-managed: to the user). Each chooses a PIN and writes down their own recovery code. A trusted person's code must end up with that person, not the helper; if the helper *is* the trusted person, it must be kept away from the phone and the user. | Each trusted person, or the user |
| G4. Connect and privacy | **Now** put the SIM back (C2 in reverse) or turn the eSIM back on (Settings → Network & internet → SIMs → Use SIM, or LMNOPhone's own screen). Join Wi-Fi. The user signs in to **their own** Google account. Turn on automatic app updates in the Play Store. Send the test text to each trusted person; each confirms it arrived. Then the four [privacy steps](#g4b-privacy-steps), ticking each as done or skipped. | Helper, user, trusted people |
| G5. Reset protection | Enter each listed account number (Part H): each trusted person's, or in self-managed mode the user's own. Optionally a backup person's. | Helper and each account holder |
| G6. Apps | Install and add the user's apps. Read each app's note. Choose what each app can use. See the [starter catalog](ux/starter-catalog.md) for sign-in tips. | Trusted person, or the user |
| G7. Limits | Read "What LMNOPhone can't stop" (the version for this mode). | Trusted people, or the user |
| G8. Unplug | Unplug the cable. LMNOPhone turns off USB debugging and runs the handover check. | Helper |

### G4b. Privacy steps

These reduce what Google collects. LMNOPhone can't change or check them: it only stores the tick you give it
(done or skipped), and reads nothing from the Google account. Do them straight after the user signs in at G4, while
the phone's own settings can still be opened (the setup screen has **Open the phone's settings**). Each one is the
user's choice; "skip" is fine. Write the answers in the setup record.

**Turning a setting off may ask whether to delete what's already saved.** That's the user's choice too. Deleting
doesn't affect LMNOPhone.

The paths below are from Google's help pages (October 2026). **None has been checked on an LMNOPhone yet.**
Google account pages need a signed-in account, which agents never use, so they can't be checked on an emulator by the
team. **[to confirm in the dry run]** on every row: write the real path in the setup record if it differs, and the
Android Platform Engineer updates this table.

| Step | Where (stock Android 15 and 16 with Google Play; Motorola moto g power 5G 2024, Android 15) | What to choose |
|---|---|---|
| 1. Web & App Activity | Phone **Settings → Google → Manage your Google Account → Data & privacy**, then under "History settings", **Web & App Activity**. [Source](https://support.google.com/accounts/answer/54068?co=GENIE.Platform%3DAndroid) **[to confirm in the dry run]** | **Turn off** (older pages say **Pause**). Then **Turn off** or **Turn off and delete activity**. |
| 2. Timeline (location history) | Google's help says Timeline is turned off in the **Google Maps** app: profile picture → **Your Timeline** → **More** → **Location & privacy settings** → **Timeline is on** → **Turn off**. [Source](https://support.google.com/maps/answer/6258979?co=GENIE.Platform%3DAndroid) If Maps isn't on the phone or is hidden: phone **Settings → Google → Manage your Google Account → Data & privacy**, then under "History settings", **Timeline**, if it's listed there. **[to confirm in the dry run]**: whether Maps can be opened at this point in setup, and whether the account page offers the switch. On a new account, Timeline is often already off; then tick "done" once you've seen it's off. | **Turn off** |
| 3. Ad personalisation | Phone **Settings → Google → Manage your Google Account → Data & privacy → My Ad Center** (or **Personalized ads**). [Source](https://support.google.com/My-Ad-Center-Help/answer/12155656?co=GENIE.Platform%3DAndroid) **[to confirm in the dry run]** | Set **Personalized ads** to **Off**, and confirm. |
| 4. Advertising ID | Phone **Settings → Google → All services**, then under "Privacy & security", **Ads → Delete advertising ID**. Older builds: **Settings → Privacy → Ads**, or **Settings → Security & privacy → More privacy settings → Ads**. If none matches, search the phone's settings for "Ads". [Source](https://support.google.com/googleplay/android-developer/answer/6048248) **[to confirm in the dry run]** | **Delete advertising ID**, then confirm. Apps then get only zeros instead of an ID. |

**If a page asks for the user's password**, the user types it. Never write it down, and never type it for them.

**Outlook (G6).** Outlook has a known way onto the web, and Beaux has accepted it (ADR 0001 Risk 3; tested on
Outlook 5.2639.2 in PLA-5 B-Q4). On Outlook's sign-in screen, **Privacy and terms → Privacy & Cookies** opens
Microsoft's privacy page inside Outlook, and links on it lead to other websites. It works before sign-in and after
sign-out. When adding Outlook:
- read its note in the [starter catalog](ux/starter-catalog.md#outlook-microsoft) with the trusted person, so they
  know where the route is and what it reaches;
- write Outlook's version in the setup record;
- if Outlook asks to send diagnostic logs to Microsoft (Help → Contact support), choose **Cancel**;
- a Google account can't be added to Outlook once LMNOPhone is locked down. If the user reads Gmail in Outlook,
  use the Gmail app for that account instead; Outlook can't be set up for it yet.

When a later Outlook update shows "Reviewed for an older version", check the Privacy & Cookies route again on the
new version. If it has become easier (for example a browse button, an address bar or a search box), tell Beaux
before anything else changes.

**If the phone restarts during setup** after G3, it asks for a PIN set in G3 before carrying on (with two trusted
people, "Who are you?" first). That's expected.

**Texting notes for G4.** Chat features (RCS) work over Wi-Fi or mobile data. Picture messages (MMS) need mobile
data on most carriers. A plan with data is recommended, so group texts and photos work away from Wi-Fi.

**Wi-Fi with a sign-in page** (hotels, hospitals, ferries) doesn't work with LMNOPhone. Use home Wi-Fi or mobile
data.

When every handover check is green, the phone says **Ready**. Write the time in the setup record ("Ready").
"Ready" means setup is done. It does **not** mean the phone can be handed over yet: do Part I.

## H. Get each account holder's Google account number

LMNOPhone uses this number so that, if the phone is ever wiped, Google asks for a listed account before the phone
can be used again. Nobody gives LMNOPhone a password, email address or code: only this number.

**Who does it.** Every person whose account goes on the list, one after the other:

| Mode | Account holders |
|---|---|
| Supervised, one trusted person | the trusted person, and the backup person if any |
| Supervised, two trusted people | each trusted person, and the backup person if any (up to 3 numbers) |
| Self-managed | the user (their own account), and the backup person if any |
| Later: adding a trusted person, or handing over | the new person, on the day, before the 24-hour wait starts |

**Each person does this on their own phone or computer, not the LMNOPhone.** A computer is easier. In
self-managed mode the user uses a computer or another phone: browsers are hidden on the LMNOPhone. Each person
picks **their own** account at step 6; the helper never signs in for them.

1. Open this page: **https://developers.google.com/people/api/rest/v1/people/get**
2. Find the **Try this method** panel (on a computer it's on the right; on a phone, tap **Try it!**).
3. In **resourceName**, type: `people/me`
4. In **personFields**, type: `names`
5. Leave the other boxes as they are. Tap **Execute**.
6. **Google asks you to choose an account. This step matters most.** Each account shows a name and, under it, an
   email address. **Choose by the email address**, not the name or picture. If two accounts have the same name,
   the address is the only way to tell them apart. If yours isn't listed, tap **Use another account** and sign
   in.
7. Google asks to let "Google APIs Explorer" see your name. Tap **Continue** or **Allow**.
8. The page shows a result. Find the line that looks like:
   `"resourceName": "people/106237845912345678901"`
9. **Copy only the digits** after `people/` (here, `106237845912345678901`). Select them and copy them **as
   text**: paste them into a note or a text to the helper. Don't retype them from memory, and don't send a
   screenshot: one wrong digit and reset protection won't work.
10. Don't copy or share anything else from that page.
11. On the LMNOPhone (step G5), the helper types or pastes the digits. LMNOPhone shows them back in groups
    of three. **Read them out together** and check every group matches.
12. Afterwards, the account holder can remove the page's access: **myaccount.google.com → Security → Your
    connections to third-party apps & services**, find "Google APIs Explorer" if it's listed, and remove it.

**Two checks the helper records, for every account on the list** (G5 asks for them too), including the user's own
in self-managed mode:
- The account holder can sign in to this Google account on a device that isn't either LMNOPhone.
- Their 2-step verification (codes or prompts) isn't only on an LMNOPhone.

When a trusted person is added or handed over later, a current trusted person (or the helper) also confirms the new
account is **at least 3 days old**. LMNOPhone won't start the 24-hour wait without it.

**New accounts: the 72-hour note.** If the Google account is less than 72 hours (3 days) old, still enter it at
G5. Then, after 72 hours, repeat steps 1–9 and check the digits still match the number in the setup record.
Even a matching number doesn't prove reset protection works: only the reset-protection test on the target phone
model (Q10) does, and handover waits for it.

**Recommended: a backup person.** the two initial users look after each other's phones. Add a backup person's number
too (repeat this part with their account). Note from the approval packet: on Beaux's own phone, a backup account
that Beaux controls means Beaux could get past reset protection on his own phone. Beaux is to confirm he's fine
with that.

**Self-managed mode: say this plainly.** Because the list names the user's own account, wiping the phone from its
buttons erases it and then lets the user set it up again **without LMNOPhone**. Someone who can't sign in to a
listed account can't. That's part of this mode: friction, not a lock. It doesn't wait 24 hours.

## I. Handover checks

"Ready" covers setup. Before the user takes the phone for daily use, all of these must pass **on this phone**
(essential-calling-and-texting), and Beaux must give his OK for handing over this phone.

You need a second phone. **Never place a real 9-1-1 call** to test. That needs a separate procedure agreed with
E-Comm or the carrier and approved by Beaux.

### I1. 9-1-1 can be reached in every state, without calling

There's no Emergency button on the home screen, on purpose (Beaux: "I don't want users bumping it by accident").
9-1-1 is reached through **Phone**, through **More → Emergency 9-1-1**, and from the lock screen's **Emergency
call**. In each state below, use the route named, check that Android's dialer (or its emergency dialer) appears,
then back out **without pressing call**. Don't type 9-1-1 yourself. Tick each one in the setup record.

- [ ] Lock screen, straight after a restart, before the first unlock: tap **Emergency call**
- [ ] Lock screen: tap **Emergency call**
- [ ] Home screen: tap **Phone**
- [ ] Home screen: tap **More**, then **Emergency 9-1-1**. The dialer opens with 9-1-1 already typed in. Back out.
- [ ] Inside an app: press Home, then tap **Phone**
- [ ] During a PIN wait (enter a wrong PIN 5 times, then press Home and tap **Phone**)
- [ ] With the Play Store open for 10 minutes (trusted person starts it): press Home, then tap **Phone**
- [ ] During an LMNOPhone update (only if one is being installed): tap **Emergency call** on the lock screen, or
      **Phone** if the home screen shows
- [ ] After LMNOPhone restarts (restart the phone): unlock, then tap **Phone**
- [ ] From the power menu, if the phone has an emergency entry there
- [ ] Power button pressed 5 times (Emergency SOS): **skip this** unless the LMNOPhone team has confirmed that
      it works on this model and software version while LMNOPhone is running (feasibility question Q14,
      [PLA-40](/PLA/issues/PLA-40)). If they have, do this one **with the LMNOPhone team**, not alone: the SOS
      countdown calls 9-1-1 by itself if nobody cancels it in time.

### I2. Calls and texts

- [ ] Call the phone from the second phone. It rings, and you can answer it while an app is open.
- [ ] During that call, press **1** on the phone's keypad. The caller hears the tone. (After a hang-up, 9-1-1's
      callback can ask you to press a key.)
- [ ] Make a call from the phone.
- [ ] Send and receive a text.
- [ ] Send and receive a picture message (needs mobile data).
- [ ] Receive a group text that includes an iPhone user.
- [ ] Chat features (RCS) show as connected in Messages, if the carrier supports them (Rogers does).

### I3. Emergency alerts

- [ ] An emergency alert has been **seen** on this phone, on this exact software version: either an Alert Ready
      scheduled public test, or a test method Beaux approves. Seeing that the alert app is installed isn't enough.
- If no alert can be seen: the phone stays with the helper, **or** Beaux records that he accepts "alerts aren't
  blocked by LMNOPhone" for this software version only.

### I4. Talk it through with the user

Sit with the user for ten minutes. Show them:
- **how to call 9-1-1.** There's no Emergency button on the home screen, so a bump can't start it. Show them:
  tap **Phone**, type 9-1-1, and press call; or tap **More**, then **Emergency 9-1-1**, which opens the dialer
  with 9-1-1 typed in, ready to call; or, on the lock screen, tap **Emergency call**. Add "or press the power
  button 5 times quickly" **only if** the LMNOPhone team confirmed it works on this phone (Q14). The same list is
  on **More → About this phone**;
- **Phone**, **Messages** and their apps;
- **More → Ask for an app**, and that the trusted person gets a text;
- what happens when a web link doesn't open, and that "Send to {trusted person}" is there;
- the monthly check line, and that it doesn't mean anything is wrong;
- **More → About this phone**: the trusted person can't read their messages or see where they are;
- with two trusted people: that both get a text whenever either one's PIN is used;
- that big changes (turning LMNOPhone off, changing who looks after the phone) show a line on the home screen for
  24 hours, and **anyone holding the phone can tap it to cancel**, no PIN needed;
- **self-managed:** **More → Your settings**, how opening up works (PIN, a 10-minute wait, then Start; 2 a day),
  and **Waits and limits**;
- who to call: the helper's and trusted person's numbers.

### I5. Record and hand over

Fill in the setup record. Hand the phone over only with Beaux's OK for this phone.

## When something goes wrong

| What you see | What to do |
|---|---|
| The script stopped (Part F) | Read its message, then the table in F4. Most stops mean "go back a step"; a few mean "this phone can't be used". |
| "LMNOPhone can't tidy up this phone" | Stop. Don't connect the phone. Tell the LMNOPhone team; include the model and software version. |
| "Not ready to hand back – call the helper" | Keep the phone. Plug it into the computer and run **[PLACEHOLDER: the script's check command]**. Calls and Emergency still work meanwhile. |
| "LMNOPhone needs attention" | Look at the trusted-person front page ("Needs you"), which says what and what to do. |
| "Phone software changed – review pending" | Nothing to fix on the phone. The LMNOPhone team reviews the new Android version and sends an LMNOPhone update; install it from your computer (trusted-person menu, "Install an LMNOPhone update"). Then re-do the I1–I3 checks on the new version. |
| LMNOPhone won't start at all | Calls and 9-1-1 still work from the lock screen. Follow "Last resort" below. |
| The signing key is lost or may be stolen | Lost: no more LMNOPhone updates are possible; the phones need a full new setup with a new key. Stolen: turn LMNOPhone off on every phone, then set up again with a new key. |

### Last resort: when LMNOPhone can't run

This erases the phone. **It needs Beaux's OK for that phone. Never on the spare Motorola.**
1. The helper wipes the phone from its recovery menu (button combination for the model:
   **[PLACEHOLDER: per supported model]**).
2. When the phone restarts, Google's reset protection asks for a listed Google account: a trusted person's (or the
   backup person's), or in self-managed mode the user's own (or the backup person's). That person signs in on the
   phone.
3. The phone comes back **without LMNOPhone**. Do this whole guide again from Part D.
4. Anything not backed up is lost.

## Setup record

Copy this into a note for each phone. It contains no passwords, PINs or recovery codes, and must never contain
them.

```
Mode: supervised / self-managed
Phone user:                 Trusted person(s):         Helper:
Model:                      Android version / build:
Date:                       Supported list version:   [PLACEHOLDER]
Part D choices (Google services, location):
Script start (time):        Ready (time):             Minutes (F→Ready):
Total helper time incl. backup, reset, wizard:
Account numbers (digits), one line each (trusted person 1, trusted person 2, or the user; backup):
  ________  < 72 h old? Y/N  Re-check due:  Re-checked (date, matches?):  Signs in elsewhere Y/N  2-step not only on an LMNOPhone Y/N
Recovery code held by its owner (not the helper / away from the user), each person: Y/N
Test text received, each trusted person:  Y/N
Privacy steps (done / skipped): Web & App Activity __  Timeline __  Ad personalisation __  Advertising ID __
  Paths that differed from G4b (for the dry run):
Self-managed: waits and limits set at setup (wait ___ min, ___ a day):
Apps added (name, version, note read with trusted person Y/N):
  Outlook (if added): version ______   Privacy & Cookies route same as 5.2639.2?  Y/N
I1 emergency states:  all ticked? Y/N    I2 calls/texts: Y/N    I3 alert seen (date / method) or exception:
Beaux's OK to hand over (date, reference):
Problems and notes:
```
