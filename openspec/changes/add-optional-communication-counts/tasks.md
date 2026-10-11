## 1. Spike (throwaway, emulator only)

- [x] 1.1 Answer C1–C7 in `design.md` on API 35 and 36 emulators under `spikes/communication-counts/` (throwaway). Evidence: commands, `dumpsys` excerpts, observer logs with counts only.
- [x] 1.2 Lead folds the spike answers into `design.md` (revision 2). No blocking product limit; RCS is disclosed.
- [ ] 1.3 Cross-model review of revision 2 (Verifier).

## 2. Build

- [x] 2.1 UI (PLA-292, `mvp/home-counts-ui` @ `dfe553a`, Lead-reviewed): Home number on Phone and Messages rows, accessibility names with plurals, "99+", large-text layout at 1.3–2.0 font scale; Settings switch and coverage text. Tests first; Paparazzi goldens; before/after screenshots.
- [ ] 2.2 Host: count source with fixed projection/selection (unit-tested), observers while Home is visible, hide-on-error, no saved counts, switch persistence (default off, survives update), permission handling per C1/C5.
- [ ] 2.3 Cross-model review of 2.2 (Lead, Anthropic).
- [ ] 2.4 Carry the count source into the MVP host (`mvp/device`) when that lane integrates the Home UI.

## 3. Ship

- [ ] 3.1 Include in the next twice-daily preview after 2.3. Verify on the spare per design D8. Release notes with how-to-test and the coverage limits.
