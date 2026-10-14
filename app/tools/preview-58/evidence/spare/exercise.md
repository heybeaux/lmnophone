# Build 58 exercise on the spare (PLA-573)

Installed in place 2026-10-14 19:17:45Z (gate exit 0, 243 PASS). Exercised 19:21–19:40Z with `ex.sh` and `../../spare_lib.sh`.
PIN screens are FLAG_SECURE, so evidence is layout dumps in `layouts/`, not screenshots.

1. **PIN settings menu** (`02-menu-top`, `03-menu-bottom`): exactly 8 rows, in order: Voice messages, Add a hidden app,
   Apps on this phone, Requests from the person using this phone, Who gets requests, Change PIN, New recovery code,
   Turn LMNOPhone off, then Done. No Needs you row and no Requests count, as on build 56/57 with 0 open requests
   (store: 0 open, 1 removed, unchanged).
2. **Each row opened once; none showed "Not in this test build yet"**:
   - Voice messages → its intro (`10`). Apps on this phone → the 4 home apps (`11`). Requests → "No requests saved on
     this phone." (`12`). Who gets requests → fresh-PIN step in RequestRecipientActivity (`15`).
     Change PIN → PIN step (`16`, `row-Change-PIN`). New recovery code → PIN step (`17`, `row-New-recovery-code-direct`).
     Turn LMNOPhone off → its intro (`row-Turn-off`). Nothing was completed: PIN, recovery code and turn-off untouched.
   - Back from Requests and Who gets requests asks for the PIN again (they are separate activities). Same as build 56.
   - Back on the New recovery code PIN step leaves PIN settings to More (`row-New-recovery-code-back`). Change PIN's
     Back returns to the menu. Unchanged code: the BackHandler finishes on every page except CHANGE_*.
3. **Add a hidden app** → the hidden-app list (`21`, redacted to a count). Back → PIN settings (`22`). Then Clock (an
   unreviewed app): review → "Add anyway" → choices (all Don't allow) → confirm → **fresh-PIN step** (`23`–`27`). Clock
   stayed hidden=true at the PIN step. Stopped there; Back walked PIN → confirm → list → PIN settings (`28`).
   Clock is still hidden. Copy seen on confirm: "Add Clock to the person using this phone's phone?" (pre-existing).
4. **About** (`30-about`, `30-about.png`): "Version 0.1.0-preview-58 (58)", both build 58 notes, and the new limitation
   sentence "This is a test version. Parts that are not ready yet are not shown. …". Older versions (`31`) starts with
   Version 57 and its two notes.
5. **Text size 2.0** (`40-menu-2.0-bottom`): Turn LMNOPhone off row [60,1777][1020,1946], Done [60,2026][1020,2260],
   both above the nav bar at y 2280. Font back at 1.0.
6. **After exercise** (`after-exercise.txt`): only the authority A/B hash changed (PIN entries). Gate rerun with no
   install: exit 0, 240 PASS; More › Emergency prefilled 911, Phone typed 911, zero calls.
