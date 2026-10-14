# Build 60 spare exercise (PLA-585), 2026-10-14 23:12–23:27Z

PIN access screens are FLAG_SECURE, so screenshots are black; evidence is uiautomator text and bounds.

1. PIN settings › Add a hidden app › Calculator (Camera is already on Home, so it is not in the hidden list)
   › Next › Add anyway › Next (no permissions) › confirm title **"Add Calculator to this phone?"**, button "Add Calculator".
   At text size 2.0 the title is [60,265][1020,502] (two lines) and the Add button [60,2120][1020,2260], above the nav bar (2280).
   See add-confirm-2.0.txt. Tapped **Go back**: back on the hidden-app list, Calculator still hidden (not added). Font back to 1.0.
   Changing the font restarts PIN access at Enter PIN (activity recreated); re-entered the PIN.
2. PIN settings › New recovery code › PIN step: typed one digit, Back (keyboard hidden first) › **PIN settings**.
   Reopened New recovery code: PIN field empty. Back again › PIN settings.
3. More › PIN settings › Enter PIN › "Forgot PIN? Use recovery code" › Enter recovery code › Back › **More** (not PIN settings).
4. Code-shown page: New recovery code › PIN › "Here is your new recovery code". The new code was saved to the host
   secrets file without printing (old file kept). Back › **PIN settings**; reopening New recovery code shows the PIN step, no code.
   authority A/B hash changed accordingly (after-exercise.txt).
5. About: "Version 0.1.0-preview-60 (60)", What's new = the two Version 60 sentences.
6. Voice messages (Family): off, nobody added. The family store has read 0 people since build 49.

Gate rerun SKIP_INSTALL=1: exit 0, 241 PASS, 911 prefill via More › Emergency and Phone, zero calls.
