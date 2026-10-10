# Public evidence handling

The original device evidence is retained privately by the maintainers, with source paths, Git blob IDs and SHA-256 checksums. Public copies label redactions in their first line. Identifying values are not needed to reproduce the reported policy behavior.

The following seven screenshots are intentionally omitted from the public tree:

| Original relative path | Why omitted | Technical observation retained |
| --- | --- | --- |
| `phase-b/bq4/gmail/05_Learn_more_link_.png` | Account address visible | The Gmail navigation step reached an app-unavailable dialog for Google Play services. The adjacent navigation log and RESULT remain available. |
| `a3/control-no-locktask/panel-internet-newtask.png` | Connected and nearby network names visible | The Internet panel opened without lock task; the matching matrix row records `OPENED_WINDOW(SystemUIDialog)`. |
| `a3/control-no-locktask/panel-internet-sametask.png` | Byte-identical copy of the omitted Internet-panel screenshot, with network names visible | The matching matrix row records `OPENED_WINDOW(SystemUIDialog)` with lock task `NONE`. |
| `a3/control-no-locktask/panel-volume-sametask.png` | Byte-identical copy of the omitted Internet-panel screenshot, despite the volume-panel filename | The matching matrix row records `OPENED` for `com.android.settings/com.motorola.settings.panel.MotoVolumePanelActivity` with lock task `NONE`. The image does not establish the volume panel's appearance. |
| `a4/qs-control.png` | Connected network name visible in the Wi-Fi tile | Control run with lock task off and the status bar enabled: `cmd statusbar expand-settings` focused `NotificationShade` with 5 Quick Settings tile/gear descriptions. `a4/quick-settings.txt` and the RESULT A4 table retain the observation; the `setStatusBarDisabled(true)` screenshots remain public. |
| `a3/locktask-block-off/shade-notifications.png` | Voicemail dial number visible | Notification shade showed system, setup, voicemail and app-install notifications. The UI text dump retains the observations with the number removed. |
| `a3/locktask-block-off/shade-quicksettings.png` | Same voicemail identifier visible | The captured notification shade included an app-install notification; see the redacted UI text dump. |

Other publication edits replace the physical device serial in command transcripts with an explicit `SERIAL` variable or a device role, remove local workstation paths/host identity, and redact account-derived identifiers from the Play job log. No passwords or private signing keys were found in the audited evidence. Historical copies require separate cleanup before publication; removing files from the current tree alone does not erase history.

Do not commit new account screens, network lists, personal call/contact data, or raw logs without review. Preserve raw evidence privately and publish the smallest sanitized observation needed to support a result. Do not regenerate forensic screenshots with an image model.
