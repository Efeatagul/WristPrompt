#!/usr/bin/env python3
"""Synthetic watch independence presentation acceptance; only WristPrompt_Watch7 is eligible."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

from wear_adb import PACKAGE, discover_watch, adb_path, adb_call, require_debug, install_debug, fields

SCENARIOS = ('WATCH_ONLY_AUTHENTICATED', 'PHONE_ABSENT', 'WATCH_ONLY_CHAT',
             'WATCH_ONLY_REFRESHED', 'REAUTH_REQUIRED_PHONE_ABSENT', 'API_KEY_WATCH_ONLY')


def verify(report, scenario):
    reauth = scenario == 'REAUTH_REQUIRED_PHONE_ABSENT'
    turns = 2 if scenario == 'WATCH_ONLY_CHAT' else 1 if scenario == 'API_KEY_WATCH_ONLY' else 0
    expected = dict(bridgeLookups='0', bridgeRequests='0', busy='false',
                    discoveries='0' if reauth else '1', generations=str(turns), completions=str(turns),
                    proof='IDLE' if reauth else 'READY', discoveryProblem='REAUTHORIZE' if reauth else 'NONE',
                    model='NONE' if reauth else 'debug-1', messages=str(turns * 2),
                    billing='OPENAI_API_KEY' if scenario == 'API_KEY_WATCH_ONLY' else 'CHATGPT_PLAN',
                    refreshes='1' if scenario == 'WATCH_ONLY_REFRESHED' else '0')
    for key, value in expected.items():
        if report.get(key) != value:
            raise RuntimeError(f'{scenario}: invalid {key}; expected {value}')
    if turns and report.get('lastInput') != str(turns * 2 - 1):
        raise RuntimeError('Completed context missing')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--screenshots', type=Path)
    args = parser.parse_args()
    adb = adb_path()
    call = adb_call

    serial, name = discover_watch(call, required_name='WristPrompt_Watch7')
    print(f'{name}: {serial}; synthetic non-persisted state only', flush=True)

    def device(*parts):
        return call('-s', serial, *parts)

    if args.install:
        install_debug(device)
    require_debug(device)
    for scenario in SCENARIOS:
        # Reconstruct every fixture in a fresh emulator-only process; preserve app data.
        device('shell', 'am', 'force-stop', PACKAGE)
        token = uuid.uuid4().hex
        output = device('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.openai.DebugScenarioActivity',
                        '-f', '0x10008000', '--es', 'scenario', scenario, '--es', 'launch_id', token)
        if 'Error' in output or 'Exception' in output:
            raise RuntimeError('Debug scenario launch failed')
        deadline = time.monotonic() + 30
        while True:
            report = fields(device('shell', 'run-as', PACKAGE, 'cat', 'files/debug-scenario-ready.txt'), token, scenario)
            if report and report.get('lifecycle') == 'RESUMED':
                break
            if time.monotonic() > deadline:
                raise RuntimeError('Readiness acknowledgement timed out')
            time.sleep(0.15)  # Readiness polling, never a stream/test race assumption.
        verify(report, scenario)
        # RESUMED/VM readiness can precede the end of the Activity's visual transition.
        # The platform hierarchy dump waits for idle; verify rendered text before capturing.
        device('shell', 'uiautomator', 'dump', '/sdcard/d4d-window.xml')
        xml = device('shell', 'cat', '/sdcard/d4d-window.xml')
        texts = {n.get('text', '') for n in ET.fromstring(xml).iter('node')}
        expected_text = ('Authentication required' if scenario == 'REAUTH_REQUIRED_PHONE_ABSENT' else
                         'OpenAI API · API billing' if scenario == 'API_KEY_WATCH_ONLY' else 'Using ChatGPT plan')
        if expected_text not in texts:
            raise RuntimeError('Expected rendered state missing: ' + expected_text)
        if scenario != 'REAUTH_REQUIRED_PHONE_ABSENT' and 'Phone unavailable' in texts:
            raise RuntimeError('Phone absence blocked ordinary watch UI')
        if args.screenshots:
            args.screenshots.mkdir(parents=True, exist_ok=True)
            previous = None
            stable = 0
            deadline = time.monotonic() + 20
            while stable < 3:
                frame = subprocess.check_output([adb, '-s', serial, 'exec-out', 'screencap', '-p'], timeout=30)
                stable = stable + 1 if frame == previous else 0
                previous = frame
                if time.monotonic() > deadline:
                    raise RuntimeError('Rendered frame did not settle')
                time.sleep(0.2)  # Compositor readiness, independent of VM/transport assertions.
            (args.screenshots / f'{scenario}.png').write_bytes(frame)
        print(json.dumps(dict(scenario=scenario, result='PASS — VERIFIED ON EMULATOR', terminal=report)), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit(f'FAIL: {error}')
