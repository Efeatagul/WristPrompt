#!/usr/bin/env python3
"""Deterministic provider-failure acceptance on WristPrompt_Watch7; no real radio toggles."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import uuid

from wear_adb import PACKAGE, discover_watch, adb_path, adb_call, require_debug, install_debug, fields

SCENARIOS = ('NETWORK_LOST_BEFORE_SEND', 'NETWORK_LOST_DURING_STREAM', 'NETWORK_RECOVERED',
             'MODEL_DISCOVERY_OFFLINE', 'MODEL_DISCOVERY_RECOVERED', 'STOP_NETWORK_RACE')


def verify_failure(report, discovery=False, partial=False):
    expected = ({'generations': '0', 'discoveries': '1', 'proof': 'FAILED', 'model': 'NONE',
                 'discoveryProblem': 'NETWORK', 'stage': 'EMPTY', 'messages': '0'} if discovery else
                {'generations': '1', 'discoveries': '1', 'excluded': '1', 'assistants': '0'})
    expected.update(busy='false', completions='0', billing='CHATGPT_PLAN')
    if any(report.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Inconsistent failure state')
    if not discovery:
        if report.get('stage') not in ('FAILED', 'CANCELLED'):
            raise RuntimeError('Failure became success or stayed active')
        if (int(report['chars']) > 0) != partial:
            raise RuntimeError('Partial output not retained correctly')


def verify_no_replay(before, after):
    for key in ('generations', 'discoveries', 'stage', 'proof', 'messages', 'chars', 'completions',
                'excluded', 'assistants', 'model', 'problem', 'discoveryProblem', 'billing'):
        if before.get(key) != after.get(key):
            raise RuntimeError('Restoration changed work/state without explicit recovery: ' + key)


def verify_recovery(report, discovery=False):
    expected = ({'generations': '0', 'discoveries': '2', 'proof': 'READY', 'model': 'debug-1',
                 'discoveryProblem': 'NONE', 'messages': '0'} if discovery else
                {'generations': '2', 'discoveries': '1', 'stage': 'COMPLETED', 'completions': '1',
                 'excluded': '1', 'assistants': '1', 'lastInput': '1', 'messages': '3'})
    expected.update(busy='false', billing='CHATGPT_PLAN')
    if any(report.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Inconsistent explicit recovery or old exchange replayed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--screenshots', type=Path)
    args = parser.parse_args()
    adb = adb_path()
    call = adb_call

    serial, name = discover_watch(call, required_name='WristPrompt_Watch7')
    print(f'{name}: {serial}; synthetic provider transitions only', flush=True)

    def device(*parts):
        return call('-s', serial, *parts)

    if args.install:
        install_debug(device)
    require_debug(device)

    for scenario in SCENARIOS:
        token = uuid.uuid4().hex
        discovery = scenario.startswith('MODEL_DISCOVERY_')
        partial = scenario in ('NETWORK_LOST_DURING_STREAM', 'STOP_NETWORK_RACE')
        result = device('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.openai.DebugScenarioActivity',
                        '-f', '0x10008000', '--es', 'scenario', scenario, '--es', 'launch_id', token)
        if 'Error' in result or 'Exception' in result:
            raise RuntimeError('Debug scenario launch failed')

        def poll(predicate):
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                try:
                    report = fields(device('shell', 'run-as', PACKAGE, 'cat', 'files/debug-scenario-ready.txt'), token, scenario)
                    if report and predicate(report):
                        return report
                except subprocess.CalledProcessError:
                    pass
                time.sleep(0.15)  # Readiness polling only; no stream race timing assumptions.
            raise RuntimeError('Scenario acknowledgement timed out: ' + scenario)

        report = poll(lambda r: r.get('lifecycle') == 'RESUMED')

        def command(action):
            revision = int(report['commandRevision'])
            device('shell', 'am', 'broadcast', '-a', PACKAGE + '.DEBUG_LIFECYCLE', '-p', PACKAGE,
                   '--es', 'launch_id', token, '--es', 'command', action)
            return poll(lambda r: int(r['commandRevision']) > revision)

        def screenshot(suffix):
            if args.screenshots:
                args.screenshots.mkdir(parents=True, exist_ok=True)
                with (args.screenshots / f'{scenario}-{suffix}.png').open('wb') as target:
                    subprocess.run([adb, '-s', serial, 'exec-out', 'screencap', '-p'], stdout=target, check=True, timeout=30)

        if scenario == 'STOP_NETWORK_RACE':
            report = command('FAIL_STOP')
            report = poll(lambda r: r.get('busy') == 'false')
        verify_failure(report, discovery, partial)
        screenshot('failed')
        before = report
        report = command('RESTORE_NETWORK')
        verify_no_replay(before, report)
        report = command('RELOAD_MODELS' if discovery else 'NEXT_REQUEST')
        report = poll(lambda r: r.get('proof') == 'READY' if discovery else r.get('stage') == 'COMPLETED')
        verify_recovery(report, discovery)
        screenshot('recovered')
        print(json.dumps({'scenario': scenario, 'result': 'PASS — VERIFIED ON EMULATOR',
                          'evidence': 'synthetic provider, production VM/session', 'terminal': report}), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit(f'FAIL: {error}')
