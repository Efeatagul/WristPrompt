#!/usr/bin/env python3
"""D7-F synthetic production-path acceptance on exactly one Wear emulator.
Build with -Pwristprompt.generationAcceptance=true; never clears app/credential data.
Only the dedicated synthetic debug namespace is used. Physical devices are excluded.
"""
import argparse
from pathlib import Path
import subprocess
from wear_adb import ROOT, PACKAGE, adb_call, adb_path, discover_watch, install_debug, require_debug


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', choices=('seed', 'verify'), required=True)
    parser.add_argument('--font-scale', choices=('1.0', '2.0'), default='1.0')
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--artifacts', type=Path)
    args = parser.parse_args()
    serial, name = discover_watch(adb_call, required_name='WristPrompt_Watch7')
    def device(*parts):
        return adb_call('-s', serial, *parts)
    if args.install:
        print(install_debug(device))
        print(device('install', '-r', str(ROOT / 'wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk')))
    require_debug(device)
    previous = device('shell', 'settings', 'get', 'system', 'font_scale')
    try:
        device('shell', 'settings', 'put', 'system', 'font_scale', args.font_scale)
        device('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        output = subprocess.check_output([adb_path(), '-s', serial, 'shell', 'am', 'instrument', '-w', '-e', 'checkpoint', args.checkpoint,
                        PACKAGE + '.test/' + PACKAGE + '.storage.ImageGenerationInstrumentation'], text=True, stderr=subprocess.STDOUT, timeout=120)
        print(output)
        if 'PASS: D7-F' not in output or 'FAIL:' in output:
            raise RuntimeError('Generation instrumentation did not pass')
        if args.artifacts:
            args.artifacts.mkdir(parents=True, exist_ok=True)
            for state in ('viewer-fit', 'viewer-zoom', 'viewer-pan', 'reader-rotary'):
                source = f'/data/user_de/0/{PACKAGE}/files/d7f-{args.checkpoint}-{state}.png'
                target = args.artifacts / f'{args.checkpoint}-{args.font_scale}-{state}.png'
                with target.open('wb') as destination:
                    subprocess.run([adb_path(), '-s', serial, 'exec-out', 'run-as', PACKAGE, 'cat', source],
                                   stdout=destination, check=True, timeout=30)
            metrics = device('shell', 'run-as', PACKAGE, 'cat', f'/data/user_de/0/{PACKAGE}/files/d7f-{args.checkpoint}-viewer-metrics.txt')
            (args.artifacts / f'{args.checkpoint}-{args.font_scale}-metrics.txt').write_text(metrics, encoding='utf-8')
            print(metrics)
        print(f'VERIFIED ON EMULATOR: {name}, {args.checkpoint}, font {args.font_scale}; synthetic provider only')
    finally:
        if previous in ('', 'null'):
            device('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            device('shell', 'settings', 'put', 'system', 'font_scale', previous)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f'FAIL: {error}')
