#!/usr/bin/env python3
"""Probe only the two named WristPrompt AVDs. Never select physical devices."""
import argparse
from pathlib import Path
import subprocess
import time
from wear_adb import adb_call

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--install', action='store_true', help='Update debug APKs without clearing data')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
package = 'io.github.grassified.wristprompt'
activity = package + '/.bridge.DataLayerDiagnosticsActivity'

call = adb_call

found = {}
for line in call('devices').splitlines()[1:]:
    fields = line.split()
    if len(fields) < 2 or fields[1] != 'device' or not fields[0].startswith('emulator-'):
        continue
    serial = fields[0]
    name = call('-s', serial, 'emu', 'avd', 'name').splitlines()[0].strip()
    if name not in ('WristPrompt_Phone', 'WristPrompt_Watch7'):
        continue
    characteristics = call('-s', serial, 'shell', 'getprop', 'ro.build.characteristics')
    watch = 'watch' in characteristics.split(',')
    if watch != (name == 'WristPrompt_Watch7') or name in found:
        raise SystemExit('Ambiguous or unexpected AVD identity; stopping.')
    found[name] = (serial, 'wear' if watch else 'mobile')
if len(found) != 2:
    raise SystemExit('Both named WristPrompt AVDs must be booted and available.')

# Discover both identities before mutating either emulator.
for name, (serial, module) in found.items():
    print(f'{name}: {serial}', flush=True)
    if args.install:
        print(call('-s', serial, 'install', '-r', str(root / module / 'build/outputs/apk/debug' / f'{module}-debug.apk')), flush=True)
    # Stop the previous debug observation before starting a new window. No app data is cleared.
    call('-s', serial, 'shell', 'am', 'force-stop', package)
    call('-s', serial, 'shell', 'run-as', package, 'rm', '-f', 'files/data-layer-diagnostics.txt')
    # Finish the previous diagnostic screen so the next snapshot is fresh.
    call('-s', serial, 'shell', 'am', 'start', '-n', activity, '-f', '0x10008000')

for name, (serial, _) in found.items():
    deadline = time.monotonic() + 100
    while True:
        try:
            report = call('-s', serial, 'shell', 'run-as', package, 'cat', 'files/data-layer-diagnostics.txt')
            stamp = int(report.split('completedTimestamp=', 1)[1].splitlines()[0])
            # Emulator and host clocks may differ: compare with the emulator clock.
            now_ms = int(call('-s', serial, 'shell', 'date', '+%s')) * 1000
            if 0 <= now_ms - stamp < 15_000:
                break
        except (subprocess.CalledProcessError, ValueError, IndexError):
            pass
        if time.monotonic() >= deadline:
            raise SystemExit(f'{name}: fresh probe report timed out.')
        time.sleep(1)
    print(f'\n{name}\n{report}', flush=True)
