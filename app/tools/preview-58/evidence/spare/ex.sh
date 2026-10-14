# usage: bash ex.sh '<commands using spare_lib functions>'
: "${S:?spare serial from the environment}"; export S PLAINPHONE_SPARE_DRIVER=1
source "$(dirname "$0")/../../spare_lib.sh"
PIN=$(cat ~/.plainphone/secrets/spare-test-pin)
L="$(dirname "$0")/layouts"
save(){ dump > "$L/$1.xml"; }
# Tap every unchosen button with this exact text that is on screen (one per group).
tapall(){ local c; for c in $(dump | python3 -I -c "
import sys,re,xml.etree.ElementTree as E
for n in E.fromstring(sys.stdin.read()).iter('node'):
    if n.get('text')==sys.argv[1]:
        b=list(map(int,re.findall(r'\\d+',n.get('bounds')))); print('%d,%d'%((b[0]+b[2])//2,(b[1]+b[3])//2))" "$1"); do a shell input tap ${c/,/ }; sleep 1; done; }
eval "$1"
