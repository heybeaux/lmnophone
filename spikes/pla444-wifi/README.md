# THROWAWAY: connected Wi-Fi removal feasibility

Owned API-35 emulator `pla363-policy-a35` / `emulator-5880` only. Compile
`ForgetProbe.java` temporarily in `:device` debug sources; register its receiver
in the test app debug manifest with the DUMP permission, as in the build-33 probe.
Add one synthetic open configuration with the shell's `cmd wifi add-network`;
pass its numeric ID as `--ei external ID` to the receiver. Start Home under LOCKED
first. Never pass the original saved AP's ID. Read only the WifiForgetProbe tag.
The receiver adds/removes a different synthetic configuration itself, selects
the original connection unchanged, then removes the shell-created configuration.
It restores prior Location in finally. Saved-list SHA-256 before/after must match.
No fixture receiver is included in the candidate APK.

The recorded API-35 result is in `docs/previews/preview-34/probe.txt`. Own and
shell-created removals returned true. Original connection and saved-list hash
survived. Raw configured-network counts were 4 → 6 → 4 → 2: Android returned two
rows per network ID. Distinct IDs were 2 → 3 → 2 → 1. Product readback counts IDs.
