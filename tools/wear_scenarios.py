#!/usr/bin/env python3
"""Launch offline debug scenarios on one dynamically discovered Wear emulator."""
import argparse
import os
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = 'io.github.grassified.wristprompt'
SOURCE = ROOT / 'wear/src/debug/kotlin/io/github/grassified/wristprompt/openai/DebugScenarios.kt'


def scenarios():
    body = SOURCE.read_text().split('internal enum class DebugScenario {', 1)[1].split(';', 1)[0]
    return [name.strip() for name in body.split(',')]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--list', action='store_true')
    parser.add_argument('--scenario', choices=scenarios())
    parser.add_argument('--smoke', action='store_true')
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--screenshot', type=Path)
    args = parser.parse_args()
    if args.list:
        print('\n'.join(scenarios()))
        return
    if not args.scenario and not args.smoke:
        parser.error('Choose --scenario or --smoke')
    if args.screenshot and args.smoke:
        parser.error('--screenshot requires a single --scenario')
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
    adb = str(sdk / 'platform-tools/adb')

    def call(*parts):
        return subprocess.check_output([adb, *parts], text=True, stderr=subprocess.STDOUT, timeout=30).strip()

    watches = []
    for line in call('devices').splitlines()[1:]:
        parts = line.split()
        if len(parts) < 2 or parts[1] != 'device' or not parts[0].startswith('emulator-'):
            continue
        serial = parts[0]
        if 'watch' in call('-s', serial, 'shell', 'getprop', 'ro.build.characteristics').split(','):
            watches.append((serial, call('-s', serial, 'emu', 'avd', 'name').splitlines()[0]))
    if len(watches) != 1:
        raise RuntimeError('Expected exactly one watch emulator; found ' + str(len(watches)))
    serial, name = watches[0]
    print(f'{name}: {serial}', flush=True)

    def device(*parts):
        return call('-s', serial, *parts)

    if args.install:
        print(device('install', '-r', str(ROOT / 'wear/build/outputs/apk/debug/wear-debug.apk')))
    if not device('shell', 'pm', 'path', PACKAGE).startswith('package:'):
        raise RuntimeError('WristPrompt package missing; build debug and use --install')
    # run-as proves installed application is debuggable; release fails closed.
    device('shell', 'run-as', PACKAGE, 'pwd')
    for scenario in scenarios() if args.smoke else [args.scenario]:
        token = uuid.uuid4().hex
        output = device('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.openai.DebugScenarioActivity',
                        '-f', '0x10008000', '--es', 'scenario', scenario, '--es', 'launch_id', token)
        if 'Error' in output or 'Exception' in output:
            raise RuntimeError('Debug scenario activity unavailable')
        deadline = time.monotonic() + 20
        while True:
            try:
                report = device('shell', 'run-as', PACKAGE, 'cat', 'files/debug-scenario-ready.txt')
                if report.startswith(f'{token} {scenario} READY ') and device('shell', 'pidof', PACKAGE):
                    break
            except subprocess.CalledProcessError:
                pass
            if time.monotonic() > deadline:
                raise RuntimeError(f'{scenario}: ready acknowledgement timed out')
            time.sleep(0.2)
        print(f'{scenario}: PASS (ready, process alive)', flush=True)
    if args.screenshot:
        args.screenshot.parent.mkdir(parents=True, exist_ok=True)
        with args.screenshot.open('wb') as target:
            subprocess.run([adb, '-s', serial, 'exec-out', 'screencap', '-p'], stdout=target, check=True, timeout=30)
        print(f'Screenshot: {args.screenshot}')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit(f'FAIL: {error}')
