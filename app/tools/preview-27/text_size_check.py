# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned-emulator-only text-size permission, readback and restore checks."""
import datetime
import sys
import time
from pathlib import Path
from upgrade_check import Gate, previous

require = previous.require
nodes = previous.nodes


def main():
    require(len(sys.argv) == 2, 'output directory required')
    gate = Gate('emulator-5880', sys.argv[1])
    def scale(): return gate.sh('read font scale', 'settings', 'get', 'system', 'font_scale')
    def text(xml, value): return any(n.get('text') == value for n in nodes(xml))
    def button(xml, label):
        matches = [n for n in nodes(xml) if n.get('content-desc') == label]
        require(len(matches) == 1, 'one '+label+' control')
        return matches[0]
    def open_page():
        gate.home(); gate.tap('^More$'); gate.tap('^Phone settings$'); gate.tap('^Text size$')
        time.sleep(1)
    def tap_size(label):
        for _ in range(4):
            xml = gate.dump()
            if any(n.get('content-desc') == label for n in nodes(xml)):
                xy = previous.tap_bounds(xml, '^'+label+'$')
                gate.sh('tap '+label, 'input', 'tap', *xy)
                time.sleep(2)
                return
            gate.sh('scroll text-size page', 'input', 'swipe', '540', '1650', '540', '800', '400')
        raise previous.GateFailure('text-size control reachable')
    print('Text size check '+datetime.datetime.now(datetime.timezone.utc).isoformat(), flush=True)
    require(scale() == '1.0', 'start at scale 1.0')
    try:
        gate.sh('deny settings app-op', 'appops', 'set', 'org.plainphone', 'WRITE_SETTINGS', 'deny')
        open_page()
        xml = gate.dump()
        require(text(xml, "Text size couldn't be changed."), 'missing permission reports failure')
        for label in ['Smaller text', 'Bigger text']:
            require(button(xml, label).get('enabled') == 'false', label+' disabled without permission')
        gate.shot('text-size-denied.png')
        tap_size('Bigger text')
        require(scale() == '1.0', 'disabled tap leaves scale unchanged')
        gate.sh('grant settings app-op', 'appops', 'set', 'org.plainphone', 'WRITE_SETTINGS', 'allow')
        time.sleep(2)
        require(button(gate.dump(), 'Bigger text').get('enabled') == 'true', 'permission refresh enables bigger text')
        for expected in ['1.15', '1.3', '1.5', '1.8', '2.0']:
            tap_size('Bigger text')
            require(scale() == expected, 'app write/readback scale '+expected)
            xml = gate.dump()
            require(text(xml, str(round(float(expected)*100))+'%'), 'confirmed percentage '+expected)
            require(not text(xml, "Text size couldn't be changed."), 'successful change has no failure line')
        gate.shot('text-size-200.png')
        require(button(gate.dump(), 'Bigger text').get('enabled') == 'false', '200 percent disables bigger text')
        # Outside changes are observed without rewriting them; either direction uses the fresh value.
        gate.sh('set unlisted test value', 'settings', 'put', 'system', 'font_scale', '1.42')
        time.sleep(2)
        require(text(gate.dump(), '142%'), 'unlisted value shown as read')
        tap_size('Smaller text'); require(scale() == '1.3', 'unlisted value moves down to 130')
        gate.sh('set unlisted test value', 'settings', 'put', 'system', 'font_scale', '1.42')
        time.sleep(2)
        tap_size('Bigger text'); require(scale() == '1.5', 'unlisted value moves up to 150')
        gate.sh('revoke settings app-op', 'appops', 'set', 'org.plainphone', 'WRITE_SETTINGS', 'deny')
        time.sleep(2)
        xml = gate.dump()
        require(button(xml, 'Bigger text').get('enabled') == 'false', 'permission loss disables control')
        require(text(xml, "Text size couldn't be changed."), 'permission loss reports failure')
    finally:
        gate.sh('restore text scale', 'settings', 'put', 'system', 'font_scale', '1.0')
        gate.sh('reset settings app-op', 'appops', 'set', 'org.plainphone', 'WRITE_SETTINGS', 'default')
        time.sleep(2)
        require(scale() == '1.0', 'restored scale 1.0')
        print('WRITE_SETTINGS left at default (not granted).', flush=True)
        gate.home()
        gate.readback()
    print('exit=0', flush=True)

if __name__ == '__main__': main()
