# On-phone setup and the handover check

Words, rules and placeholders are in [README.md](README.md). This is what the phone shows **after** the setup
script has made LMNOPhone the phone's manager. The steps before that (backup, reset, the offline wizard, the
script) are in [../setup-guide.md](../setup-guide.md).

**Spec:** guided-setup ("Setup asks which mode the phone uses"; "Privacy steps after Google sign-in"; "On-phone setup
is a helper-custody phase with authenticated resume"; "Reset-protection account IDs…"; "Handover check gates
'Ready'"), all as changed by `add-copy-test-product-decisions`; trusted-person-controls (two trusted people; recovery
code; self-managed mode); essential-calling-and-texting (test text; RCS/MMS notes).

**Two kinds of phone** ([PLA-42](/PLA/issues/PLA-42)). Step 1 picks one:
- **supervised:** one or two trusted people hold PINs. Screens say `{tp}` for one person; with two, the PIN and code
  steps run once for each person, and `{everyone}` is both names ("Sam and Jo");
- **self-managed:** the user holds the PIN. Steps that name `{tp}` are done by `{user}`, and S2's trusted-person
  fields are skipped.

The new screens and every new string are indexed in [flows-people-modes-and-waits.md](flows-people-modes-and-waits.md#m0-exact-strings-and-where-they-come-from).

**Who holds the phone.** The helper, throughout. The screens say when to hand it to the trusted person (or, in
self-managed mode, the user) and when to take it back. On a supervised phone the user ideally isn't present until
"Ready", because some steps (PIN, recovery code) are for the trusted people only. On a self-managed phone the user
is there throughout. Adding trusted people during setup doesn't wait 24 hours: setup is in the helper's hands
(guided-setup).

**Resume.** Once a PIN exists (step 3), any restart sends setup back to a PIN screen: "Setup isn't finished.
Enter a PIN from step 3 to carry on." With two trusted people it asks "Who are you?" first
([M1](flows-people-modes-and-waits.md#m1-who-are-you)). Lock task does not start until steps 1–7 are done.

A step bar is shown on every screen, as text, not dots: "Step 3 of 8".

---

## S0. Welcome

- **Title:** Set up LMNOPhone
- **Body:**
  This part is for the helper. It takes about 30 minutes, plus a few minutes with each trusted person.
  Keep the phone with you until the last step says **Ready**.
  First, LMNOPhone tidies up the phone. It hides apps that came with it. This happens before the phone goes
  online.
- **Primary:** Start
- **Then:** "Tidying up the phone…" (the preinstall sweep, guided-setup; offline), then "Done. {n} apps were
  hidden." with **Next**. If the sweep can't run: "LMNOPhone can't tidy up this phone. Stop here and see the
  setup guide, 'The script or sweep stopped'." No way forward.

## S1. How is this phone looked after? (step 1 of 8)

**Spec:** guided-setup "Setup asks which mode the phone uses" (before any PIN; plain words for each; self-managed
says "friction, not a lock" before the user sets their PIN).

### S1a. Choose

- **Title:** How is this phone looked after?
- **Two large choices** (outline buttons; the chosen one is filled, with ✓, as in design-tokens "Choices"):

  | Choice | Second line |
  |---|---|
  | **Someone looks after it** | One or two trusted people add apps with {user}. Turning LMNOPhone off waits 24 hours. |
  | **{user} looks after it** | {user} holds the PIN. LMNOPhone adds waits and limits so that distraction takes effort. |

- **Body (under the choices, small):** You can change this later. Changing it takes 24 hours.
- **Primary:** Next (enabled once one is chosen)
- **Notes:** `{user}` isn't known yet on this screen, so it reads "the person using it" here; S2 asks the name.
  Supervised → S2. Self-managed → S1b.

### S1b. Self-managed: friction, not a lock

Shown before the user sets a PIN (spec scenario "Self-managed setup").

- **Title:** Friction, not a lock
- **Body:**
  In self-managed mode, you hold the PIN. LMNOPhone adds waits and limits so that distraction takes effort. It
  can't stop you, because you are in charge. You can always remove it by waiting at least 24 hours of the phone
  being on (longer if the change is cancelled or interrupted), or by wiping the phone.
  ([Flag 3](flows-people-modes-and-waits.md#flags-strings-i-think-should-change): the words in brackets are the
  spec's, added to the design's sentence.)
- **Box** (grey), heading **"How opening up works"**:
  - To open the Play Store or full phone settings, you enter your PIN, then wait **10 minutes**, then tap Start.
  - You can do this **2 times a day**. It closes by itself after 5 or 10 minutes.
  - Turning LMNOPhone off, or making the waits shorter, takes 24 hours.
- **Primary:** I understand
- **Secondary:** Go back

### S1c. Self-managed: make it stricter? (optional)

- **Title:** Waits and limits
- **Body:** These are the usual settings. You can make them stricter now. Later, stricter is instant and looser takes
  24 hours.
- **Wait before opening up:** 10 minutes (− / +, up to 24 hours; − stops at 10 minutes)
- **Openings a day:** 2 (− / +, 0 to 3; + stops at 2 here, because looser than the default isn't offered at setup)
- **Primary:** Next
- **Notes:** GS: "let the user make them stricter". Looser than the defaults is a waiting change after handover
  ([M7g](flows-people-modes-and-waits.md#m7g-waits-and-limits)).

## S2. Who's who (step 2 of 8)

### S2a. Names and numbers

- **Title:** Who's who
- **Fields:**
  - "Whose phone is this?" — first name ({user})
  - **Supervised:** "Who looks after this phone?" — first name ({tp}), then "{tp}'s mobile number"
  - **Supervised:** text link **Add a second trusted person** → the same two fields again, titled "Second trusted
    person". A **Remove** link takes them off again.
  - "Who is the helper?" — first name ({helper}), then "{helper}'s mobile number". A tick box above:
    "{tp} is also the helper" (fills both). In self-managed mode the helper is optional
    ([Open question 3](flows-people-modes-and-waits.md#open-questions-for-the-lead)): "Skip if there's no helper".
- **Body (small, under the fields):**
  - Supervised: {everyone} get a text when {user} asks for an app, when something changes on the phone, and once a
    month for the phone check. {helper} gets a text if the phone needs fixing.
  - Self-managed: {helper} gets a text only if the phone needs fixing.
- **Primary:** Next
- **Notes:** numbers are checked for format only. The real test text is in step 4, once the phone is connected.
  Self-managed mode skips the trusted-person fields (GS step 2).

### S2b. Two trusted people: you are equals

Only when a second trusted person was added. Both should be there; if not, the helper reads it to them later.

- **Title:** {tp} and {other} are equals
- **Body:**
  You are equals. Either of you can approve apps, or turn LMNOPhone off.
  ([Flag 1](flows-people-modes-and-waits.md#flags-strings-i-think-should-change) proposes "add apps".)
  Each of you has your own PIN. Neither of you can see or change the other's. You both get a text whenever either
  PIN is used, so you'll know if someone else used yours.
  Big changes, like turning LMNOPhone off, wait 24 hours so either of you can cancel. That protects against
  someone who saw a PIN. It doesn't stop the other trusted person: a recovery code skips the wait.
- **Primary:** We understand
- **Notes:** spec (TPC): "Setup SHALL tell both people, in plain words, that they are equals and that either can
  approve apps or turn LMNOPhone off." The last sentence is the disclosure item about two people, said here first.

## S3. PINs and recovery codes (step 3 of 8)

With two trusted people, S3a–S3c run once for each, in turn: "Hand the phone to {tp}", then "Hand the phone to
{other}". In self-managed mode, they run once for {user}.

### S3a. Choose a PIN

- **Title:** Hand the phone to {tp}
- **Body:** {tp}, choose a PIN of 6 or more numbers. You'll use it every time you add an app or change something.
  Don't use a birthday or a number {user} would guess.
- **Self-managed version:** **Title:** Choose your PIN. **Body:** {user}, choose a PIN of 6 or more numbers. You'll
  use it to open things up and to change your settings. Pick one you won't use without thinking, not your phone's
  unlock code.
- **Keypad**, then "Type it again".
- **Primary:** Next
- **Notes:** two trusted people may pick the same PIN. LMNOPhone never says so (TPC).

### S3b. Your recovery code

- **Title:** Write down your recovery code
- **Body:**
  If you ever forget your PIN, this code lets you choose a new one. It's shown once only.
  Write it on paper and keep it somewhere safe, away from this phone and away from {user}.
- **Self-managed version of the body:** If you ever forget your PIN, this code lets you choose a new one. It's
  shown once only. Write it on paper and keep it away from this phone. It only resets your PIN: it doesn't skip any
  wait.
- **The code**, in groups of four, large monospace: `7K3Q-M9XD-2FRW-…`
- **Note under it:** "This code can't get past Google's reset protection if the phone is ever wiped. Only your
  Google account can do that."
- **Primary:** I've written it down
- **Then S3c.** `FLAG_SECURE` (no screenshots).

### S3c. Check the code is safe

Three versions ([Open question 2](README.md#open-questions-for-the-lead)):

| Helper is a different person | Helper is also the trusted person | Self-managed |
|---|---|---|
| **Title:** Hand the phone back to {helper} | **Title:** Keep the code safe | **Title:** Keep the code safe |
| **Body:** {helper}, check with {tp}: | **Body:** Check each one: | **Body:** Check each one: |
| ☐ {tp} has the code written down. | ☐ I have the code written down. | ☐ I have the code written down. |
| ☐ I, the helper, don't have a copy. | ☐ It's not stored on this phone, and {user} can't get to it. | ☐ It's not stored on this phone. |
| **Primary:** Next (enabled when both are ticked) | **Primary:** Next (enabled when both are ticked) | **Primary:** Next (enabled when both are ticked) |

## S4. Connect and privacy (step 4 of 8)

The guide makes sure the SIM is out (or the eSIM is off) until here.

### S4a. SIM and Wi-Fi

- **Title:** Now connect the phone
- **Body:**
  The tidy-up is done, so it's safe to go online.
  1. Put the SIM card back in, or turn the eSIM back on (the setup guide shows how).
  2. Join Wi-Fi.
- **Secondary:** Turn the eSIM on → an LMNOPhone screen listing the phone's eSIMs, each with an on/off switch.
  (If this can't be done without Android Settings, the AE makes it a short Settings session; the guide covers
  both.)
- **Primary:** Join Wi-Fi → the U7 Wi-Fi screen, then back here.

### S4b. {user}'s Google account

- **Title:** Sign in to {user}'s Google account
- **Body:** Use {user}'s own account, not {tp}'s. The Play Store needs it to keep apps up to date. {user} may need
  to type the password, or approve the sign-in on another device.
  (Self-managed: "Use your own account.")
- **Primary:** Sign in (opens the Play Store's sign-in)

### S4c. Automatic app updates

- **Title:** Turn on automatic app updates
- **Body:** In the Play Store: tap your Google picture or initial at the top right, then **Settings**, **Network preferences**,
  **Auto-update apps**, then **Over any network** or **Over Wi-Fi only**.
  (Play's labels as of October 2026; the AE checks them on the target build.)
- **Primary:** Open the Play Store
- **Then:** "Is auto-update on?" → **Yes, it's on** / **No, I couldn't find it** (the second shows the guide
  section to use). Handover check item "Play auto-update confirmed" uses this answer.

### S4d. Test text to each trusted person

- **Title:** Send a test text to {tp}
- **Body:** LMNOPhone uses texts to tell {tp} about requests, changes and the monthly check. This checks they arrive.
  If the phone has two SIMs: "Send from: {SIM 1 name} / {SIM 2 name}".
- **Primary:** Send test text
- **With two trusted people:** one text to each, and one "Did {tp} get it?" question for each. Each answer is its own
  handover-check row.
- **Self-managed:** skipped (no trusted people). If a helper number was entered, the same screen sends a test text to
  {helper}.
- **Then:** "Did {tp} get it? Ask {tp} to check their messages." → **Yes, it arrived** / **No** (No: "Check
  {tp}'s number, and that this phone has signal." with **Try again** and **Change the number**).
- **Note on the same screen, small:** "Picture messages and group texts need mobile data on most plans. Chat
  features (RCS) work over Wi-Fi or mobile data."

### S4e. Make Google collect less

**Spec:** guided-setup "Privacy steps after Google sign-in" (straight after sign-in, before lock task; four steps;
each recorded as done or skipped by the helper; LMNOPhone doesn't claim to check them or read account data);
design C9.

- **Title:** Make Google collect less
- **Body:**
  This phone runs Google's Android, and Google collects some data about how it's used. These four settings reduce
  that. They're in {user}'s Google account and in the phone's settings, so LMNOPhone can't change or check them:
  you do them together, and tick each one.
  The setup guide, "Privacy steps", shows where each one is.
- **Four rows**, each with a short line and two choice buttons, **Done** and **Skip** (nothing pre-selected):

  | Row | Short line |
  |---|---|
  | **Web & App Activity** | Turn it off, so Google stops saving searches and app use to {user}'s account |
  | **Timeline** | Turn it off, so Google Maps stops keeping a diary of places |
  | **Ad personalisation** | Turn it off, so Google's ads aren't based on {user}'s activity |
  | **Advertising ID** | Delete it, so apps can't use it to follow {user} for ads |

- **Secondary:** Open the phone's settings → Android Settings (before lock task, so it opens; GS "This happens
  before lock task starts"). Back returns here.
- **Primary:** Next (enabled when every row has Done or Skip)
- **Notes:**
  - "Skip" is a fine answer (spec scenario "User wants to keep Timeline"). No warning when skipping.
  - Never say "private", "protected" or "Google can't see". Say "collect less".
  - LMNOPhone stores only the four answers. It reads nothing from the Google account.
  - Self-managed: "{user}'s" becomes "your".

## S5. Google's reset protection (step 5 of 8)

**Spec:** guided-setup ("Reset-protection account IDs are collected without secrets, from the right account").

### S5a. What this is

- **Title:** If the phone is ever wiped
- **Body:**
  If someone erases this phone using its buttons, Google will ask for {tp}'s Google account before the phone can
  be used again. That stops someone simply wiping LMNOPhone away.
  For that, LMNOPhone needs an account number from Google. {tp} gets it on their own phone or computer, not
  this one. The setup guide, "Get the account number", shows how; it takes about 5 minutes.
- **Two trusted people:** "…Google will ask for {tp}'s or {other}'s Google account…". S5b–S5d run once for each, then
  S5e.
- **Self-managed version:**
  **Title:** If the phone is ever wiped
  **Body:** If someone erases this phone using its buttons, Google will ask for your Google account before the phone
  can be used again. A stranger can't get past that.
  You can, though: after a wipe, you can set the phone up without LMNOPhone. That's part of self-managed mode:
  friction, not a lock.
  For this, LMNOPhone needs an account number from Google. Get it on a computer or another phone, not this one.
  The setup guide, "Get the account number", shows how.
- **Primary:** I have the number

### S5b. Enter the number

- **Title:** {tp}'s account number from Google (self-managed: "Your account number from Google")
- **Body:** Type or paste only the numbers that came after `people/`.
- **Number field** (digits only; anything else is removed, and a line says "Only numbers are kept").
- **Primary:** Next

### S5c. Read it back

- **Title:** Check the number
- **Body:** Read this out to {tp}. It must match exactly.
- The number in groups of three, very large: `106 237 845 912 345 678 901`
- **Primary:** It matches
- **Secondary:** Change it

### S5d. Two questions for {tp}

- **Title:** Two checks with {tp}
- ☐ {tp} can sign in to this Google account on a phone or computer that isn't {user}'s phone or {tp}'s own
  LMNOPhone.
- ☐ {tp}'s 2-step verification (codes or prompts) isn't only on an LMNOPhone.
- **Question:** "Is this Google account less than 3 days old?" **Yes** / **No**.
  Yes → "Carry on. After {date+72h}, repeat 'Get the account number' and check it still matches. The setup guide
  has a reminder." The handover check then shows reset protection as "set, waiting for the 3-day re-check".
- **Primary:** Next (enabled when both boxes are ticked)
- **Self-managed:** the same two checks, about {user}'s own account: "…on a phone or computer that isn't this phone",
  and "2-step verification isn't only on this phone." GS requires them for every listed account.

### S5e. A backup person (optional, recommended)

- **Title:** Add a backup person?
- **Body:** If {tp} ever can't sign in, a backup person's Google account can also unlock the phone after a wipe.
  This matters most when two people look after each other's phones.
- **Primary:** Add a backup person → S5b–S5d again, for the backup person
- **Secondary:** Not now
- **Notes:** when `{tp}` is also the user of the other LMNOPhone (the two initial users), the body adds:
  "Recommended: you two look after each other's phones." In supervised mode, no trusted person's or backup person's
  account may be signed in on this phone; in self-managed mode, {user}'s own account is signed in as normal, and the
  backup person's must not be (device-lockdown).
- **Then, with two trusted people and a backup:** "Reset protection will list 3 accounts: {tp}, {other} and
  {backup}." (spec scenario "Two trusted people").

## S6. Choose apps (step 6 of 8)

- **Title:** Choose {user}'s apps
- **Body:** Phone and Messages are always there, and 9-1-1 always works. Add the other apps {user} needs. Each one shows a note
  about web pages inside it. You can add more later.
- **Primary:** Open the Play Store (installs happen here; each installed app then goes through
  [T4](flows-trusted-person.md#t4-adding-an-app-after-it-is-installed-in-a-play-session) with permissions)
- **Secondary:** Skip for now
- **Then:** a list of apps added, with **Add another** and **Next**. Order them on the next screen with Move
  up / Move down.
- **Notes:** during setup the PIN entered in step 3 counts as the fresh entry for each app only if no more than 10
  minutes have passed; otherwise T4d asks again.

## S7. Read the limits (step 7 of 8)

- **Title:** Before you finish: what LMNOPhone can't stop
- **Body:** {tp}, please read this. It takes about 3 minutes. (Two trusted people: "{tp} and {other}, please
  read this together." Self-managed: "{user}, please read this.")
- Then [L1](flows-limits-and-off.md#l1-what-lmnophone-cant-stop) in full, in the version for this mode.
- **Primary:** I've read this

## S8. Last step: unplug and check (step 8 of 8)

- **Title:** Unplug the computer
- **Body:** LMNOPhone will now switch off the computer connection, lock everything down, and check the phone.
  This takes about a minute.
- **Primary:** Lock down and check
- **Then:** the handover check (S9). From here, setup can't be opened again.

## S9. Handover check

**Spec:** guided-setup ("Handover check gates 'Ready'").

A list, one row per check. A passed check shows **✓** and its text in `ink`. A failed check shows **✗** and the
failure text in `ink`, with the fix on a second line in `ink-2`, inside a grey box so failures stand out by shape,
not colour ([design-tokens.md](design-tokens.md)). LMNOPhone says **Ready** only when every check has passed; the
only exception is reset protection with Beaux's recorded acceptance.

| Check (text when passed) | Failure text | Fix shown |
|---|---|---|
| LMNOPhone is in charge of this phone | "LMNOPhone isn't in charge of this phone." | "Run the setup script again (setup guide, Part F)." |
| Protections are on | "{n} protections didn't turn on." | "Restart the phone and run the check again. If it still fails, see the guide." |
| Computer connection is off | "The computer connection is still on." | "Unplug the cable and tap Check again." |
| Phone software is genuine | "This phone's software isn't the locked, original version." | "Stop. This phone can't be used with LMNOPhone. See the guide." |
| Reset protection is set for {everyone} (and {backup}); self-managed: "for {user}" (and {backup}) | "Reset protection doesn't match the numbers entered." | "Re-enter the numbers in step 5." |
| Web links come to LMNOPhone | "Another app is handling web links." | "Run the check again. If it fails, see the guide." |
| Emergency and phone parts are all there | "Part of the phone's emergency system is missing." | "Stop. Don't hand the phone over. See the guide." |
| Emergency self-test passed | "The emergency self-test failed." | "Stop. Don't hand the phone over. See the guide." |
| App updates are on | "Automatic app updates weren't confirmed." | "Go back to step 4." |
| Tidy-up is complete | "Some apps that came with the phone aren't hidden." | "Tap Check again. If it fails, see the guide." |
| {tp} got the test text (one row per trusted person; not shown in self-managed mode) | "{tp} hasn't confirmed the test text." | "Go back to step 4." |

Under the list, a grey box that **doesn't** gate Ready (GS):
**Privacy steps (recorded by the helper):** Web & App Activity: done · Timeline: skipped · Ad personalisation: done ·
Advertising ID: done. No ✓ or ✗ here: LMNOPhone can't check them, so it only repeats what was ticked.

**Primary:** Check again (while anything fails) · **Done** (when every check has passed, after S10)

## S10. Ready

- **Title:** Ready
- **Body:**
  LMNOPhone is set up on {user}'s phone.
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
- **Self-managed:** the box heading is "How to call 9-1-1" and the list is for {user} to read.
- **Notes:** "Ready" means setup only (guided-setup). Handing the phone over for daily use also needs the
  real-device checks and Beaux's OK for that phone.
