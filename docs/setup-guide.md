# Plainphone setup guide (draft)

**For the helper.** For the first two phones that's Beaux. Write as if for anyone: you don't need to know Android,
but you do need a computer and about two hours the first time.

> **Draft status.** Written by the Product & UX Designer for [PLA-12](/PLA/issues/PLA-12) (task 5.3). Every step
> that uses the setup script is marked **[PLACEHOLDER]** until the MVP issue provides the real script. The guide
> must be dry-run by someone who didn't write it (task 5.3) before anyone relies on it.
>
> **Gates.** Only set up a phone Beaux has approved for this. Never wipe a phone from its recovery menu unless
> Beaux has approved that for that phone; on the spare Motorola, never.

## What you need

- The phone, charged to at least 70%.
- A Mac or PC with Android's `adb` tool installed **[PLACEHOLDER: install steps come with the script]**.
- A USB cable that carries data (not a charge-only cable).
- The Plainphone files: the app and the setup script **[PLACEHOLDER: where to get them]**.
- The home Wi-Fi name and password.
- The person who'll use the phone (the **user**), for their Google password at step G3 and the talk-through at the
  end.
- The **trusted person**, for about 20 minutes: their PIN, their recovery code, their Google account number
  (Part H), and the test text. They need their **own** phone or computer, not this one.
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
| G | Finish setup on the phone | from step G3 | 20 min |
| H | The trusted person gets their Google account number | (their device) | 5 min |
| I | Handover checks | yes | 30 min |

Time Parts F to G yourself: from the first script command to the green handover check should be **30 minutes or
less** (guided-setup). Write the times in the setup record.

---

## A. Check the phone is supported

1. On the phone, open **Settings → About phone**. Write down the **model** and the **Android version** in the
   setup record.
2. Compare with the supported list: **[PLACEHOLDER: supported models and builds, shipped with the script]**.
   The spare moto g power 5G (2024), XT2415-1, is for testing only.
3. If it isn't listed, **stop**. Plainphone needs a reviewed list of the phone's built-in parts for each model and
   software version. Without it, the script will refuse.

## B. Back up

Everything on the phone is erased in Part C. Back up first, and check the backup before you go on.

1. **Photos and videos.** Either turn on Google Photos backup and wait until it says "Backup complete", or copy the
   `DCIM`, `Pictures` and `Download` folders to the computer with the cable.
2. **Contacts.** Open **Contacts**; at the top, check they're saved to the Google account (not "Device"). If any
   are on the device only, move them to the Google account.
3. **Google backup.** **Settings → Google → Backup → Back up now.** Wait for it to finish.
4. **Text messages.** Plainphone's setup skips restoring from backup, so old texts probably won't come back onto
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
3. Turn the phone on. Keep the SIM card safe; it goes back in at step G3.

**eSIM (a SIM built into the phone)**
1. **Settings → Network & internet → SIMs.** Tap the eSIM.
2. Turn **Use SIM** off. Confirm.
3. Do **not** delete or erase the eSIM. You'll turn it back on at step G3.

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

The phone must not connect to anything until Plainphone has tidied it up (Part G). A fresh Motorola installed 7 apps
by itself within minutes of joining Wi-Fi (`spikes/device-moto/RESULT.md` § A0). Plainphone hides those before the
phone goes online.

1. **Language:** choose it, then **Start**.
2. **Wi-Fi screen:** don't pick a network. Tap **Skip** or **Set up offline**. If asked "Skip?", confirm.
3. **Copy apps & data:** **Don't copy**, if it appears.
4. **Google sign-in:** **Skip**. Don't sign in to any account.
5. **Google services and other choices** (location, diagnostics): choose what you prefer; they don't affect
   Plainphone. Write your choices in the setup record.
6. **Screen lock:** **Skip** for now. (Whether the user's phone gets a screen lock is a decision for later; it can
   be added with "Open full phone settings".)
7. **App recommendations, "Moto apps", "Additional apps", offers:** untick everything, **Skip** or **No thanks**.
8. You reach the phone's normal home screen. Setup is finished.

> ### Stop here if the phone won't finish offline
>
> If any screen won't let you continue without Wi-Fi, a mobile connection or a Google account, **stop**.
> **This phone model can't be used with Plainphone.**
>
> Don't connect to Wi-Fi to get past the screen: the phone would start installing apps before Plainphone can
> control them. Turn the phone off and tell the Plainphone team the model and the screen you got stuck on.
>
> If you connected by mistake, go back to step C3 and reset again.

Note: on the spare Motorola, the first-boot wizard has so far only been run **with** Wi-Fi. Finishing it offline
on that model is not yet confirmed. The dry run checks this.

## E. Turn on USB debugging

USB debugging lets the computer install Plainphone. Plainphone turns it off again at the end of setup.

1. **Settings → About phone.** Scroll to the bottom. Tap **Build number** 7 times. The phone says "You are now a
   developer" (it may ask for the screen lock; there isn't one yet).
2. Go back. **Settings → System → Developer options.**
3. Turn on **USB debugging**. Confirm.
4. **Don't** turn on **Wireless debugging**.
5. Plug the phone into the computer.
6. On the phone, a box asks **Allow USB debugging?** Tick **Always allow from this computer**, then tap **Allow**.
   - **"Always allow" matters.** Once Plainphone locks the screen, this question can't be answered on the phone
     (PLA-5: with Plainphone's lock on, the prompt never authorised the computer). Without "Always allow", a later
     restart could leave the computer unable to finish setup.
7. On the computer, run `adb devices`. You should see one line ending in **device**. If it says
   **unauthorized**, unplug, plug in again, and answer the question on the phone.
8. If a USB menu appears ("Use USB for"), leave it on **No data transfer** or **Charging**.

## F. Run the setup script [PLACEHOLDER]

> **[PLACEHOLDER]** Everything in Part F is a placeholder until the MVP issue provides the real script. Commands,
> file names and messages will change.

1. **[PLACEHOLDER]** Put the Plainphone app and the script in one folder on the computer, for example
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
   | A reviewed list of the phone's built-in parts exists for this exact software version | "No inventory for this build" | Stop. Tell the Plainphone team the model and version. |

5. **[PLACEHOLDER]** When every check passes, the script installs Plainphone and makes it the phone's manager.
   (Android calls this "Device Owner"; you won't see that word again.)
6. **[PLACEHOLDER]** The script finishes with "Done – continue on the phone". The phone shows **Set up
   Plainphone**. Leave the cable plugged in.

## G. Finish setup on the phone

The screens are described in [docs/ux/flows-setup.md](ux/flows-setup.md). In short:

| Step | On the phone | Who |
|---|---|---|
| Tidy-up | Plainphone hides the apps that came with the phone. Offline. | Helper |
| G1. Who's who | Names and numbers: the user, the trusted person, the helper | Helper |
| G2. PIN and recovery code | **Hand the phone to the trusted person.** They choose a PIN and write down the recovery code. The recovery code must end up with the trusted person, not the helper; if the helper *is* the trusted person, it must be kept away from the phone and the user. | Trusted person |
| G3. Connect | **Now** put the SIM back (C2 in reverse) or turn the eSIM back on (Settings → Network & internet → SIMs → Use SIM, or Plainphone's own screen). Join Wi-Fi. The user signs in to **their own** Google account. Turn on automatic app updates in the Play Store. Send the test text; the trusted person confirms it arrived. | Helper, user, trusted person |
| G4. Reset protection | Enter the trusted person's Google account number (Part H). Optionally a backup person's. | Helper and trusted person |
| G5. Apps | Install and add the user's apps. Read each app's note. Choose what each app can use. See the [starter catalog](ux/starter-catalog.md) for sign-in tips. | Trusted person |
| G6. Limits | The trusted person reads "What Plainphone can't stop". | Trusted person |
| G7. Unplug | Unplug the cable. Plainphone turns off USB debugging and runs the handover check. | Helper |

**Outlook (G5).** Outlook has a known way onto the web, and Beaux has accepted it (ADR 0001 Risk 3; tested on
Outlook 5.2639.2 in PLA-5 B-Q4). On Outlook's sign-in screen, **Privacy and terms → Privacy & Cookies** opens
Microsoft's privacy page inside Outlook, and links on it lead to other websites. It works before sign-in and after
sign-out. When adding Outlook:
- read its note in the [starter catalog](ux/starter-catalog.md#outlook-microsoft) with the trusted person, so they
  know where the route is and what it reaches;
- write Outlook's version in the setup record;
- if Outlook asks to send diagnostic logs to Microsoft (Help → Contact support), choose **Cancel**;
- a Google account can't be added to Outlook once Plainphone is locked down. If the user reads Gmail in Outlook,
  use the Gmail app for that account instead; Outlook can't be set up for it yet.

When a later Outlook update shows "Reviewed for an older version", check the Privacy & Cookies route again on the
new version. If it has become easier (for example a browse button, an address bar or a search box), tell Beaux
before anything else changes.

**If the phone restarts during setup** after G2, it asks for the trusted person's PIN before carrying on. That's
expected.

**Texting notes for G3.** Chat features (RCS) work over Wi-Fi or mobile data. Picture messages (MMS) need mobile
data on most carriers. A plan with data is recommended, so group texts and photos work away from Wi-Fi.

**Wi-Fi with a sign-in page** (hotels, hospitals, ferries) doesn't work with Plainphone. Use home Wi-Fi or mobile
data.

When every handover check is green, the phone says **Ready**. Write the time in the setup record ("Ready").
"Ready" means setup is done. It does **not** mean the phone can be handed over yet: do Part I.

## H. Get the trusted person's Google account number

Plainphone uses this number so that, if the phone is ever wiped, Google asks for the trusted person's account
before the phone can be used again. Nobody gives Plainphone a password, email address or code: only this number.

**The trusted person does this on their own phone or computer, not the Plainphone phone.** A computer is easier.

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
11. On the Plainphone phone (step G4), the helper types or pastes the digits. Plainphone shows them back in groups
    of three. **Read them out together** and check every group matches.
12. Afterwards, the trusted person can remove the page's access: **myaccount.google.com → Security → Your
    connections to third-party apps & services**, find "Google APIs Explorer" if it's listed, and remove it.

**Two checks the helper records** (G4 asks for them too):
- The trusted person can sign in to this Google account on a device that isn't either Plainphone phone.
- Their 2-step verification (codes or prompts) isn't only on a Plainphone phone.

**New accounts: the 72-hour note.** If the Google account is less than 72 hours (3 days) old, still enter it at
G4. Then, after 72 hours, repeat steps 1–9 and check the digits still match the number in the setup record.
Even a matching number doesn't prove reset protection works: only the reset-protection test on the target phone
model (Q10) does, and handover waits for it.

**Recommended: a backup person.** Beaux and the trusted person look after each other's phones. Add a backup person's number
too (repeat this part with their account). Note from the approval packet: on Beaux's own phone, a backup account
that Beaux controls means Beaux could get past reset protection on his own phone. Beaux is to confirm he's fine
with that.

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
- [ ] During a Plainphone update (only if one is being installed): tap **Emergency call** on the lock screen, or
      **Phone** if the home screen shows
- [ ] After Plainphone restarts (restart the phone): unlock, then tap **Phone**
- [ ] From the power menu, if the phone has an emergency entry there
- [ ] Power button pressed 5 times (Emergency SOS): **skip this** unless the Plainphone team has confirmed that
      it works on this model and software version while Plainphone is running (feasibility question Q14,
      [PLA-40](/PLA/issues/PLA-40)). If they have, do this one **with the Plainphone team**, not alone: the SOS
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
  blocked by Plainphone" for this software version only.

### I4. Talk it through with the user

Sit with the user for ten minutes. Show them:
- **how to call 9-1-1.** There's no Emergency button on the home screen, so a bump can't start it. Show them:
  tap **Phone**, type 9-1-1, and press call; or tap **More**, then **Emergency 9-1-1**, which opens the dialer
  with 9-1-1 typed in, ready to call; or, on the lock screen, tap **Emergency call**. Add "or press the power
  button 5 times quickly" **only if** the Plainphone team confirmed it works on this phone (Q14). The same list is
  on **More → About this phone**;
- **Phone**, **Messages** and their apps;
- **More → Ask for an app**, and that the trusted person gets a text;
- what happens when a web link doesn't open, and that "Send to {trusted person}" is there;
- the monthly check line, and that it doesn't mean anything is wrong;
- **More → About this phone**: the trusted person can't read their messages or see where they are;
- who to call: the helper's and trusted person's numbers.

### I5. Record and hand over

Fill in the setup record. Hand the phone over only with Beaux's OK for this phone.

## When something goes wrong

| What you see | What to do |
|---|---|
| The script stopped (Part F) | Read its message, then the table in F4. Most stops mean "go back a step"; a few mean "this phone can't be used". |
| "Plainphone can't tidy up this phone" | Stop. Don't connect the phone. Tell the Plainphone team; include the model and software version. |
| "Not ready to hand back – call the helper" | Keep the phone. Plug it into the computer and run **[PLACEHOLDER: the script's check command]**. Calls and Emergency still work meanwhile. |
| "Plainphone needs attention" | Look at the trusted-person front page ("Needs you"), which says what and what to do. |
| "Phone software changed – review pending" | Nothing to fix on the phone. The Plainphone team reviews the new Android version and sends a Plainphone update; install it from your computer (trusted-person menu, "Install a Plainphone update"). Then re-do the I1–I3 checks on the new version. |
| Plainphone won't start at all | Calls and 9-1-1 still work from the lock screen. Follow "Last resort" below. |
| The signing key is lost or may be stolen | Lost: no more Plainphone updates are possible; the phones need a full new setup with a new key. Stolen: turn Plainphone off on every phone, then set up again with a new key. |

### Last resort: when Plainphone can't run

This erases the phone. **It needs Beaux's OK for that phone. Never on the spare Motorola.**
1. The helper wipes the phone from its recovery menu (button combination for the model:
   **[PLACEHOLDER: per supported model]**).
2. When the phone restarts, Google's reset protection asks for the trusted person's (or backup person's) Google
   account. They sign in on the phone.
3. The phone comes back **without Plainphone**. Do this whole guide again from Part D.
4. Anything not backed up is lost.

## Setup record

Copy this into a note for each phone. It contains no passwords, PINs or recovery codes, and must never contain
them.

```
Phone user:                 Trusted person:            Helper:
Model:                      Android version / build:
Date:                       Supported list version:   [PLACEHOLDER]
Part D choices (Google services, location):
Script start (time):        Ready (time):             Minutes (F→Ready):
Total helper time incl. backup, reset, wizard:
Trusted person's account number (digits):
  Account < 72 h old?  Y/N   Re-check due:            Re-checked (date, matches?):
Backup person's account number (digits):
Trusted person can sign in elsewhere:  Y/N   2-step not only on a Plainphone phone: Y/N
Recovery code held by trusted person (not helper / away from user): Y/N
Test text received:  Y/N
Apps added (name, version, note read with trusted person Y/N):
  Outlook (if added): version ______   Privacy & Cookies route same as 5.2639.2?  Y/N
I1 emergency states:  all ticked? Y/N    I2 calls/texts: Y/N    I3 alert seen (date / method) or exception:
Beaux's OK to hand over (date, reference):
Problems and notes:
```
