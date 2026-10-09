#!/bin/bash
# THROWAWAY SPIKE (PLA-18). Q4 reach: tap one portal element, record where it lands, go back.
# Usage: bash tools/reach.sh <serial> "<link text>" <tag>
cd "$(dirname "$0")/.."; source tools/pp.sh "${1:?serial}" || exit 1
L="$2"; T="$3"; PLOG="${PORTAL_LOG:?}"
n0=$(wc -l < "$PLOG")
echo "## $T: tap [$L]"; echo "before top: $(toppkg)"
tapp "$L"; sleep 5
echo "after top: $(toppkg)"; echo "screen: $(texts 300)"
shot "evidence/q4-$T.png"
echo "portal requests:"; tail -n +$((n0+1)) "$PLOG" | sed 's/^/  /'
A shell dumpsys activity activities | grep -E "mLockTaskModeState" | head -1
