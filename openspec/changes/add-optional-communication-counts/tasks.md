## 1. Spike (throwaway, emulator only)

- [ ] 1.1 Answer C1–C7 in `design.md` on API 35 and 36 emulators under `spikes/communication-counts/` (throwaway). Evidence: commands, `dumpsys` excerpts, observer logs with counts only.
- [ ] 1.2 Cross-model review of the spike result and this delta. Lead folds the answers into `design.md` (revision 2) and reports any product limit to Beaux.

## 2. Build

- [ ] 2.1 UI: Home number on Phone and Messages rows, accessibility names with plurals, "99+", large-text layout at 1.3–2.0 font scale; Settings switch and coverage text. Tests first; Paparazzi goldens; before/after screenshots.
- [ ] 2.2 Host: count source with fixed projection/selection (unit-tested), observers while Home is visible, hide-on-error, no saved counts, switch persistence (default off, survives update), permission handling per C1/C5.
- [ ] 2.3 Cross-model review of 2.1 and 2.2.

## 3. Ship

- [ ] 3.1 Include in the next twice-daily preview after 2.3. Verify on the spare: missed vs acknowledged call, read/unread text, dismissed notification, permission loss, restart, switch off. Release notes with how-to-test and the coverage limits.
