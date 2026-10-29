#!/usr/bin/env python3
"""Launch offline debug scenarios on one dynamically discovered Wear emulator."""
import argparse
from pathlib import Path
import subprocess
import time
import uuid

from wear_adb import ROOT, PACKAGE, adb_path, adb_call, discover_watch, require_debug, install_debug, fields, screenshot
from wear_catalog import CATEGORIES, SMOKE, category

SOURCE = ROOT / 'wear/src/debug/kotlin/io/github/grassified/wristprompt/openai/DebugScenarios.kt'


def scenarios():
    body = SOURCE.read_text().split('internal enum class DebugScenario {', 1)[1].split(';', 1)[0]
    return [name.strip() for name in body.split(',')]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--list', action='store_true')
    selection.add_argument('--scenario', choices=scenarios())
    selection.add_argument('--smoke', action='store_true')
    selection.add_argument('--all', action='store_true', help='Launch every non-destructive fixture; no process termination or radio changes')
    parser.add_argument('--category', choices=CATEGORIES, help='Filter --list or --all')
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--screenshot', type=Path)
    args = parser.parse_args()
    if args.category and not (args.list or args.all):
        parser.error('--category requires --list or --all')
    selected = [name for name in scenarios() if not args.category or category(name) == args.category]
    if args.list:
        print('\n'.join(f'{name} [{category(name)}]' for name in selected))
        return
    if args.screenshot and not args.scenario:
        parser.error('--screenshot requires a single --scenario')
    adb = adb_path()
    call = adb_call

    serial, name = discover_watch(call, required_name='WristPrompt_Watch7')
    print(f'{name}: {serial}', flush=True)

    def device(*parts):
        return call('-s', serial, *parts)

    if args.install:
        print(install_debug(device))
    require_debug(device)
    device('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    for scenario in SMOKE if args.smoke else selected if args.all else [args.scenario]:
        token = uuid.uuid4().hex
        output = device('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.openai.DebugScenarioActivity',
                        '-f', '0x10008000', '--es', 'scenario', scenario, '--es', 'launch_id', token)
        if 'Error' in output or 'Exception' in output:
            raise RuntimeError('Debug scenario activity unavailable')
        deadline = time.monotonic() + 20
        while True:
            try:
                report = device('shell', 'run-as', PACKAGE, 'cat', 'files/debug-scenario-ready.txt')
                metadata = fields(report, token, scenario)
                if metadata and metadata.get('lifecycle') == 'RESUMED' and metadata.get('pid') == device('shell', 'pidof', PACKAGE):
                    break
            except subprocess.CalledProcessError:
                pass
            if time.monotonic() > deadline:
                raise RuntimeError(f'{scenario}: ready acknowledgement timed out')
            time.sleep(0.2)
        if scenario in ('WATCH_ONLY_AUTHENTICATED', 'WATCH_ONLY_CHAT', 'WATCH_ONLY_REFRESHED', 'PHONE_ABSENT', 'API_KEY_WATCH_ONLY', 'REAUTH_REQUIRED_PHONE_ABSENT'):
            from wear_phone_absence import verify
            verify(metadata, scenario)
        print(f'{scenario}: PASS — VERIFIED ON EMULATOR (ready, matching PID, resumed)', flush=True)
    if args.screenshot:
        screenshot(adb, serial, args.screenshot)
        print(f'Screenshot: {args.screenshot}')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit(f'FAIL: {error}')
