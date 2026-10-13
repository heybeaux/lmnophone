# THROWAWAY: Companion Device Manager association under lock task (PLA-494)

Question (Lead option A on PLA-495): can LMNOPhone, while LOCKED, get a CDM
association so that Settings confirms a Just Works pairing without its dialog
(`canBondWithoutDialog`)? Public APIs only; no lock-task list change.

Result: **no**. `associate()` needs `uses-feature android.software.companion_device_setup`
in the app manifest. With that line added (throwaway target only), the chooser
`com.android.companiondevicemanager/.CompanionAssociationActivity` is started
single-instance by system_server and refused as a lock-task violation (result 101).
No association, no bond. Output: `probe.txt`.

Build: `REVIEW_DEBUG_KEYSTORE=~/.android/debug.keystore PAPERCLIP_SCRATCH_DIR=... bash build.sh`;
the target must be the app plus only the uses-feature line, same debug signer.
