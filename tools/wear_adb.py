"""Small shared emulator-only ADB and safe acknowledgement helpers."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = 'io.github.grassified.wristprompt'


def adb_path():
    return str(Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))) / 'platform-tools/adb')


def adb_call(*parts):
    return subprocess.check_output([adb_path(), *parts], text=True, stderr=subprocess.STDOUT, timeout=30).strip()


def discover_watch(call, required_name=None):
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
    if required_name is not None and name != required_name:
        raise RuntimeError('Expected AVD ' + required_name + '; found ' + name)
    return serial, name


def install_debug(device):
    apk = ROOT / 'wear/build/outputs/apk/debug/wear-debug.apk'
    if not apk.is_file():
        raise RuntimeError('Debug APK missing; run ./gradlew :wear:assembleDebug before --install')
    return device('install', '-r', str(apk))


def require_debug(device):
    if not device('shell', 'pm', 'path', PACKAGE).startswith('package:'):
        raise RuntimeError('WristPrompt package missing; build :wear:assembleDebug and use --install')
    try:
        device('shell', 'run-as', PACKAGE, 'pwd')
    except subprocess.CalledProcessError:
        raise RuntimeError('Installed package is not accessible as debug; install wear-debug.apk') from None


# Explicit metadata allowlist: never echo unknown fields or raw acknowledgement content.
ACK_FIELDS = frozenset("generations stage bridgeLookups bridgeRequests refreshes commandRevision discoveries completions chars draftChars messages owner creations collecting observedChars lifecycle pid processOwner busy proof model billing surface pendingInput excluded assistants lastInput problem discoveryProblem keepScreenOn".split())


def fields(raw, token, scenario):
    prefix = f'{token} {scenario} READY '
    if not raw.startswith(prefix):
        return None
    report = {}
    for item in raw[len(prefix):].split():
        key, separator, value = item.partition('=')
        if not separator or key not in ACK_FIELDS or key in report or not value:
            raise RuntimeError('Invalid debug acknowledgement metadata')
        report[key] = value
    return report


def screenshot(adb, serial, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('wb') as target:
        subprocess.run([adb, '-s', serial, 'exec-out', 'screencap', '-p'], stdout=target, check=True, timeout=30)
