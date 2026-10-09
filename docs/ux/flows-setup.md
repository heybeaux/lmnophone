# On-phone setup and the handover check

Words, rules and placeholders are in [README.md](README.md). This is what the phone shows **after** the setup
script has made Plainphone the phone's manager. The steps before that (backup, reset, the offline wizard, the
script) are in [../setup-guide.md](../setup-guide.md).

**Spec:** guided-setup ("On-phone setup is a helper-custody phase with authenticated resume"; "Reset-protection
account IDs…"; "Handover check gates 'Ready'"); trusted-person-controls (recovery code); essential-calling-and-
texting (test text; RCS/MMS notes).

**Who holds the phone.** The helper, throughout. The screens say when to hand it to the trusted person and when
to take it back. The user ideally isn't present until "Ready", because some steps (PIN, recovery code) are for the
trusted person only.

**Resume.** Once the PIN exists (step 2), any restart sends setup back to a PIN screen: "Setup isn't finished.
Enter {tp}'s PIN to carry on." Lock task does not start until steps 1–6 are done.

A step bar is shown on every screen, as text, not dots: "Step 3 of 7".

---

## S0. Welcome

- **Title:** Set up Plainphone
- **Body:**
  This part is for the helper. It takes about 20 minutes, plus a few minutes with the trusted person.
  Keep the phone with you until the last step says **Ready**.
  First, Plainphone tidies up the phone. It hides apps that came with it. This happens before the phone goes
  online.
- **Primary:** Start
- **Then:** "Tidying up the phone…" (the preinstall sweep, guided-setup; offline), then "Done. {n} apps were
  hidden." with **Next**. If the sweep can't run: "Plainphone can't tidy up this phone. Stop here and see the
  setup guide, 'The script or sweep stopped'." No way forward.

## S1. Who's who (step 1 of 7)

- **Title:** Who's who
- **Fields:**
  - "Whose phone is this?" — first name ({user})
  - "Who looks after this phone?" — first name ({tp}), then "{tp}'s mobile number"
  - "Who is the helper?" — first name ({helper}), then "{helper}'s mobile number". A tick box above:
    "{tp} is also the helper" (fills both).
- **Body (small, under the fields):** {tp} gets a text when {user} asks for an app, and once a month for the
  phone check. {helper} gets a text if the phone needs fixing.
- **Primary:** Next
- **Notes:** numbers are checked for format only. The real test text is in step 3, once the phone is connected.

## S2. PIN and recovery code (step 2 of 7)

### S2a. Choose a PIN

- **Title:** Hand the phone to {tp}
- **Body:** {tp}, choose a PIN of 6 or more numbers. You'll use it every time you add an app or change something.
  Don't use a birthday or a number {user} would guess.
- **Keypad**, then "Type it again".
- **Primary:** Next

### S2b. Your recovery code

- **Title:** Write down your recovery code
- **Body:**
  If you ever forget your PIN, this code lets you choose a new one. It's shown once only.
  Write it on paper and keep it somewhere safe, away from this phone and away from {user}.
- **The code**, in groups of four, large monospace: `7K3Q-M9XD-2FRW-…`
- **Note under it:** "This code can't get past Google's reset protection if the phone is ever wiped. Only your
  Google account can do that."
- **Primary:** I've written it down
- **Then S2c.** `FLAG_SECURE` (no screenshots).

### S2c. Check the code is safe

Two versions, depending on S1's tick box ([Open question 2](README.md#open-questions-for-the-lead)):

| Helper is a different person | Helper is also the trusted person |
|---|---|
| **Title:** Hand the phone back to {helper} | **Title:** Keep the code safe |
| **Body:** {helper}, check with {tp}: | **Body:** Check each one: |
| ☐ {tp} has the code written down. | ☐ I have the code written down. |
| ☐ I, the helper, don't have a copy. | ☐ It's not stored on this phone, and {user} can't get to it. |
| **Primary:** Next (enabled when both are ticked) | **Primary:** Next (enabled when both are ticked) |

## S3. Connect the phone (step 3 of 7)

The guide makes sure the SIM is out (or the eSIM is off) until here.

### S3a. SIM and Wi-Fi

- **Title:** Now connect the phone
- **Body:**
  The tidy-up is done, so it's safe to go online.
  1. Put the SIM card back in, or turn the eSIM back on (the setup guide shows how).
  2. Join Wi-Fi.
- **Secondary:** Turn the eSIM on → a Plainphone screen listing the phone's eSIMs, each with an on/off switch.
  (If this can't be done without Android Settings, the AE makes it a short Settings session; the guide covers
  both.)
- **Primary:** Join Wi-Fi → the U7 Wi-Fi screen, then back here.

### S3b. {user}'s Google account

- **Title:** Sign in to {user}'s Google account
- **Body:** Use {user}'s own account, not {tp}'s. The Play Store needs it to keep apps up to date. {user} may need
  to type the password, or approve the sign-in on another device.
- **Primary:** Sign in (opens the Play Store's sign-in)

### S3c. Automatic updates

- **Title:** Turn on automatic app updates
- **Body:** In the Play Store: tap your Google picture or initial at the top right, then **Settings**, **Network preferences**,
  **Auto-update apps**, then **Over any network** or **Over Wi-Fi only**.
  (Play's labels as of October 2026; the AE checks them on the target build.)
- **Primary:** Open the Play Store
- **Then:** "Is auto-update on?" → **Yes, it's on** / **No, I couldn't find it** (the second shows the guide
  section to use). Handover check item "Play auto-update confirmed" uses this answer.

### S3d. Test text to {tp}

- **Title:** Send a test text to {tp}
- **Body:** Plainphone uses texts to tell {tp} about requests and the monthly check. This checks they arrive.
  If the phone has two SIMs: "Send from: {SIM 1 name} / {SIM 2 name}".
- **Primary:** Send test text
- **Then:** "Did {tp} get it? Ask {tp} to check their messages." → **Yes, it arrived** / **No** (No: "Check
  {tp}'s number, and that this phone has signal." with **Try again** and **Change the number**).
- **Note on the same screen, small:** "Picture messages and group texts need mobile data on most plans. Chat
  features (RCS) work over Wi-Fi or mobile data."

## S4. Google's reset protection (step 4 of 7)

**Spec:** guided-setup ("Reset-protection account IDs are collected without secrets, from the right account").

### S4a. What this is

- **Title:** If the phone is ever wiped
- **Body:**
  If someone erases this phone using its buttons, Google will ask for {tp}'s Google account before the phone can
  be used again. That stops someone simply wiping Plainphone away.
  For that, Plainphone needs an account number from Google. {tp} gets it on their own phone or computer, not
  this one. The setup guide, "Get the account number", shows how; it takes about 5 minutes.
- **Primary:** I have the number

### S4b. Enter the number

- **Title:** {tp}'s account number from Google
- **Body:** Type or paste only the numbers that came after `people/`.
- **Number field** (digits only; anything else is removed, and a line says "Only numbers are kept").
- **Primary:** Next

### S4c. Read it back

- **Title:** Check the number
- **Body:** Read this out to {tp}. It must match exactly.
- The number in groups of three, very large: `106 237 845 912 345 678 901`
- **Primary:** It matches
- **Secondary:** Change it

### S4d. Two questions for {tp}

- **Title:** Two checks with {tp}
- ☐ {tp} can sign in to this Google account on a phone or computer that isn't {user}'s phone or {tp}'s own
  Plainphone phone.
- ☐ {tp}'s 2-step verification (codes or prompts) isn't only on a Plainphone phone.
- **Question:** "Is this Google account less than 3 days old?" **Yes** / **No**.
  Yes → "Carry on. After {date+72h}, repeat 'Get the account number' and check it still matches. The setup guide
  has a reminder." The handover check then shows reset protection as "set, waiting for the 3-day re-check".
- **Primary:** Next (enabled when both boxes are ticked)

### S4e. A backup person (optional, recommended)

- **Title:** Add a backup person?
- **Body:** If {tp} ever can't sign in, a backup person's Google account can also unlock the phone after a wipe.
  This matters most when two people look after each other's phones.
- **Primary:** Add a backup person → S4b–S4d again, for the backup person
- **Secondary:** Not now
- **Notes:** when `{tp}` is also the user of the other Plainphone phone (Beaux and the trusted person), the body adds:
  "Recommended: you two look after each other's phones." Neither account may be signed in on this phone.

## S5. Choose apps (step 5 of 7)

- **Title:** Choose {user}'s apps
- **Body:** Phone and Messages are always there, and 9-1-1 always works. Add the other apps {user} needs. Each one shows a note
  about web pages inside it. You can add more later.
- **Primary:** Open the Play Store (installs happen here; each installed app then goes through
  [T4](flows-trusted-person.md#t4-adding-an-app-after-it-is-installed-in-a-play-session) with permissions)
- **Secondary:** Skip for now
- **Then:** a list of apps added, with **Add another** and **Next**. Order them on the next screen with Move
  up / Move down.
- **Notes:** during setup the PIN entered in step 2 counts as the fresh entry for each app only if no more than 10
  minutes have passed; otherwise T4d asks again.

## S6. Read the limits (step 6 of 7)

- **Title:** Before you finish: what Plainphone can't stop
- **Body:** {tp}, please read this. It takes about 3 minutes.
- Then [L1](flows-limits-and-off.md#l1-what-plainphone-cant-stop) in full.
- **Primary:** I've read this

## S7. Last step: unplug and check (step 7 of 7)

- **Title:** Unplug the computer
- **Body:** Plainphone will now switch off the computer connection, lock everything down, and check the phone.
  This takes about a minute.
- **Primary:** Lock down and check
- **Then:** the handover check (S8). From here, setup can't be opened again.

## S8. Handover check

**Spec:** guided-setup ("Handover check gates 'Ready'").

A list, one row per check. A passed check shows **✓** and its text in `ink`. A failed check shows **✗** and the
failure text in `ink`, with the fix on a second line in `ink-2`, inside a grey box so failures stand out by shape,
not colour ([design-tokens.md](design-tokens.md)). Plainphone says **Ready** only when every check has passed; the
only exception is reset protection with Beaux's recorded acceptance.

| Check (text when passed) | Failure text | Fix shown |
|---|---|---|
| Plainphone is in charge of this phone | "Plainphone isn't in charge of this phone." | "Run the setup script again (setup guide, Part F)." |
| Protections are on | "{n} protections didn't turn on." | "Restart the phone and run the check again. If it still fails, see the guide." |
| Computer connection is off | "The computer connection is still on." | "Unplug the cable and tap Check again." |
| Phone software is genuine | "This phone's software isn't the locked, original version." | "Stop. This phone can't be used with Plainphone. See the guide." |
| Reset protection is set for {tp} (and {backup}) | "Reset protection doesn't match the number entered." | "Re-enter the number in step 4." |
| Web links come to Plainphone | "Another app is handling web links." | "Run the check again. If it fails, see the guide." |
| Emergency and phone parts are all there | "Part of the phone's emergency system is missing." | "Stop. Don't hand the phone over. See the guide." |
| Emergency self-test passed | "The emergency self-test failed." | "Stop. Don't hand the phone over. See the guide." |
| App updates are on | "Automatic app updates weren't confirmed." | "Go back to step 3." |
| Tidy-up is complete | "Some apps that came with the phone aren't hidden." | "Tap Check again. If it fails, see the guide." |
| {tp} got the test text | "{tp} hasn't confirmed the test text." | "Go back to step 3." |

**Primary:** Check again (while anything fails) · **Done** (when every check has passed, after S9)

## S9. Ready

- **Title:** Ready
- **Body:**
  Plainphone is set up on {user}'s phone.
  Before {user} uses it every day, do the emergency and calling checks in the setup guide, Part I. They need a
  second phone and about 15 minutes.
- **Box** (grey), heading **"Show {user} how to call 9-1-1"**. There's no Emergency button on the home screen,
  so {user} needs to know these:
  - Tap **Phone**, type 9-1-1, and press call.
  - Or tap **More**, then **Emergency 9-1-1**.
  - From the lock screen, tap **Emergency call**.
  - {sos_line} (as in [U8](flows-user.md#u8-about-this-phone): only if Q14 confirms it works on this phone)
  The same list is always in About this phone.
- **Primary:** Go to the home screen
- **Notes:** "Ready" means setup only (guided-setup). Handing the phone over for daily use also needs the
  real-device checks and Beaux's OK for that phone.
