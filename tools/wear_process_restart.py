#!/usr/bin/env python3
"""Synthetic process-death acceptance on the existing WristPrompt_Watch7 AVD only."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import uuid

from wear_scenarios import PACKAGE, ROOT, discover_watch

SCENARIOS = ('PROCESS_RESTART_IDLE', 'PROCESS_RESTART_AFTER_COMPLETED',
             'PROCESS_RESTART_DURING_ACTIVE', 'PROCESS_RESTART_AFTER_PARTIAL')
COMPONENT = PACKAGE + '/.openai.DebugScenarioActivity'


def fields(raw, token, scenario):
    prefix = f'{token} {scenario} READY '
    if not raw.startswith(prefix):
        return None
    return dict(item.split('=', 1) for item in raw[len(prefix):].split())


def verify_fresh(before, after):
    if before['pid'] == after['pid'] or before['owner'] == after['owner'] or before['processOwner'] == after['processOwner']:
        raise RuntimeError('No fresh process/owner evidence')
    expected = {'generations': '0', 'messages': '0', 'chars': '0', 'draftChars': '0',
                'busy': 'false', 'stage': 'EMPTY', 'proof': 'READY', 'surface': 'CHAT',
                'pendingInput': 'false', 'discoveries': '1', 'completions': '0',
                'model': 'debug-1', 'billing': 'CHATGPT_PLAN'}
    if any(after.get(key) != value for key, value in expected.items()):
        raise RuntimeError('Fresh process contains stale work or incompatible synthetic state')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--scenario', choices=SCENARIOS)
    selection.add_argument('--all', action='store_true')
    parser.add_argument('--method', choices=('am-kill', 'force-stop'), default='am-kill',
                        help='am-kill resumes the retained task; force-stop is a stronger separate cold-launch check')
    parser.add_argument('--install', action='store_true')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
    adb = str(sdk / 'platform-tools/adb')

    def call(*parts):
        return subprocess.check_output([adb, *parts], text=True, stderr=subprocess.STDOUT, timeout=30).strip()

    serial, name = discover_watch(call, required_name='WristPrompt_Watch7')
    print(f'{name}: {serial}; method={args.method}', flush=True)

    def device(*parts):
        return call('-s', serial, *parts)

    if args.install:
        device('install', '-r', str(ROOT / 'wear/build/outputs/apk/debug/wear-debug.apk'))
    if not device('shell', 'pm', 'path', PACKAGE).startswith('package:'):
        raise RuntimeError('WristPrompt package missing')
    device('shell', 'run-as', PACKAGE, 'pwd')  # Debug-only check before killing anything.

    def pid():
        try:
            value = device('shell', 'pidof', PACKAGE)
            if value and value.isdigit():
                return value
        except subprocess.CalledProcessError:
            pass
        return None

    def poll(predicate, label):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            result = predicate()
            if result:
                return result
            time.sleep(0.15)  # Event polling, never a stream-race timing assumption.
        raise RuntimeError('Timed out: ' + label)

    for scenario in SCENARIOS if args.all else [args.scenario]:
        token = uuid.uuid4().hex

        def launch():
            result = device('shell', 'am', 'start', '-W', '-n', COMPONENT, '-f', '0x10008000',
                            '--es', 'scenario', scenario, '--es', 'launch_id', token)
            if 'Error' in result or 'Exception' in result:
                raise RuntimeError('Scenario launch failed')

        def read():
            try:
                report = fields(device('shell', 'run-as', PACKAGE, 'cat', 'files/debug-scenario-ready.txt'), token, scenario)
                return report if report and report.get('pid') == pid() else None
            except subprocess.CalledProcessError:
                return None

        def state(**expected):
            def ready():
                report = read()
                return report if report and all(report.get(k) == str(v) for k, v in expected.items()) else None
            return poll(ready, 'scenario state')

        device('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        launch()
        state(generations=0, stage='EMPTY', lifecycle='RESUMED', proof='READY')
        device('shell', 'am', 'broadcast', '-a', PACKAGE + '.DEBUG_LIFECYCLE', '-p', PACKAGE,
               '--es', 'launch_id', token, '--es', 'command', 'SEED')
        expected = {
            'PROCESS_RESTART_IDLE': {'generations': 0, 'stage': 'EMPTY', 'model': 'debug-2'},
            'PROCESS_RESTART_AFTER_COMPLETED': {'generations': 1, 'stage': 'COMPLETED', 'messages': 2},
            'PROCESS_RESTART_DURING_ACTIVE': {'generations': 1, 'stage': 'STARTING', 'chars': 0, 'busy': 'true'},
            'PROCESS_RESTART_AFTER_PARTIAL': {'generations': 1, 'stage': 'RECEIVING', 'busy': 'true'},
        }[scenario]
        before = state(**expected)
        if scenario == 'PROCESS_RESTART_AFTER_PARTIAL' and int(before['chars']) <= 0:
            raise RuntimeError('Partial scenario produced no partial')
        device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        state(collecting='false', lifecycle='CREATED')
        device('shell', 'am', 'kill' if args.method == 'am-kill' else 'force-stop', PACKAGE)
        poll(lambda: not pid(), 'process termination; no force-stop fallback')
        if args.method == 'am-kill':
            device('shell', 'am', 'start', '-W', '-n', COMPONENT, '-f', '0x20000')
        else:
            token = uuid.uuid4().hex
            launch()  # Explicit launch after stronger package-stopped state; never seed again.
        after = state(generations=0, stage='EMPTY', proof='READY', lifecycle='RESUMED')
        verify_fresh(before, after)
        # Re-read once settled: neither startup nor another observation dispatches a request.
        verify_fresh(before, state(generations=0, stage='EMPTY', proof='READY'))
        print(json.dumps({'scenario': scenario, 'result': 'PASS — VERIFIED ON EMULATOR', 'method': args.method,
                          'previousPID': before['pid'], 'newPID': after['pid'], 'ownerChanged': True,
                          'before': {k: before[k] for k in ('stage', 'messages', 'chars', 'generations', 'model')},
                          'after': {k: after[k] for k in ('stage', 'messages', 'chars', 'generations', 'busy', 'model', 'billing', 'surface')}}), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError, KeyError, ValueError) as error:
        raise SystemExit(f'FAIL: {error}')
