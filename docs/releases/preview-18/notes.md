# Build 18 — About this phone

Open **More → About this phone** to read what LMNOPhone is designed to do,
what it sends, and how to reach 9-1-1. The first line shows the installed version
and says that some described features are not in this test build yet. **OK** or
Back returns to More. Scroll to read all the text, including **To call 9-1-1**.

The screen uses the existing preview names Sam and Jo. Sam fills the sample
helper name in the product description; this is not helper enrollment. There
is no helper phone number or “Who to call for help” action. Never place an
emergency call while testing: opening the dialer with 911 typed in is enough.

The optional power-button SOS sentence is omitted. The committed Q14 spike
(`spikes/device-moto/RESULT.md`, “9-1-1 routes (PLA-40, Q14)”) found the gesture
blocked with the planned list; it worked only after adding Personal Safety to
the throwaway list. That is not confirmation on this preview. No SOS gesture
was tested for build 18.

The pending-change route is still unavailable because it needs a recovery
profile. Update remains disabled. The product-description text about messages,
history and the helper's computer is the approved U8 copy, not a claim that
these features now work. PIN setup/entry remains as in build 17. This increment
also explicitly guards dormant cancel/needs-attention screens behind READY.

This is a test preview with the existing starter plan, not a complete MVP or a
public release. Author checks and the emulator install are recorded separately
from the Lead's review and physical-spare installation.

[Release case](/PLA/cases/PLA-C70); [emulator evidence](/PLA/cases/PLA-C71).
