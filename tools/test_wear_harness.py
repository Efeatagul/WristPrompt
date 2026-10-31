"""Catalog, acknowledgement, isolation and common helper regression tests; host only."""
import contextlib
import io
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import wear_adb as adb
import wear_catalog as catalog
import wear_scenarios as runner
import check_scenario_isolation as isolation
import test_wear_scenarios


class HarnessTest(unittest.TestCase):
    def test_catalog_exactly_covers_public_enum(self):
        names = [n for group in catalog.CATEGORIES.values() for n in group]
        self.assertEqual(set(runner.scenarios()), set(names))
        self.assertEqual(len(names), len(set(names)))
        for key, group in catalog.CATEGORIES.items():
            for name in group:
                self.assertEqual(key, catalog.category(name))

    def test_smoke_is_valid_unique_and_bounded(self):
        self.assertEqual(len(catalog.SMOKE), len(set(catalog.SMOKE)))
        self.assertLess(len(catalog.SMOKE), len(runner.scenarios()))
        self.assertTrue(set(catalog.SMOKE) <= set(runner.scenarios()))
        self.assertTrue(set(('SIGNED_OUT', 'MANY_MODELS', 'ACTIVE_STREAM_RECREATION',
                             'PROCESS_RESTART_IDLE', 'WATCH_ONLY_AUTHENTICATED')) <= set(catalog.SMOKE))

    def test_list_category_has_no_adb_side_effect(self):
        output = io.StringIO()
        with patch.object(sys, 'argv', ['tool', '--list', '--category', 'lifecycle']), \
             patch.object(runner, 'adb_call') as call, contextlib.redirect_stdout(output):
            runner.main()
        call.assert_not_called()
        self.assertEqual(set(catalog.CATEGORIES['lifecycle']), {line.split()[0] for line in output.getvalue().splitlines()})

    def test_ack_source_fields_equal_safe_allowlist(self):
        source = (runner.SOURCE.parent / 'DebugScenarioActivity.kt').read_text()
        report = source.split('private fun report(', 1)[1].split('override fun onDestroy', 1)[0]
        keys = set(re.findall(r' ([A-Za-z]+)=', report))
        self.assertEqual(adb.ACK_FIELDS, keys)
        self.assertFalse(keys & {'token', 'credentials', 'apiKey', 'accountId', 'prompt', 'response', 'Authorization'})
        isolation.check_dependencies()

    def test_ack_rejects_unknown_duplicate_and_malformed_fields_without_echo(self):
        for body in ('token=SECRET', 'messages=0 messages=1', 'bad', 'busy='):
            with self.assertRaisesRegex(RuntimeError, '^Invalid debug acknowledgement metadata$'):
                adb.fields('id SIGNED_OUT READY ' + body, 'id', 'SIGNED_OUT')

    def test_ack_rejects_stale_launch_and_wrong_scenario(self):
        self.assertIsNone(adb.fields('old SIGNED_OUT READY pid=1', 'new', 'SIGNED_OUT'))
        self.assertIsNone(adb.fields('id EMPTY_CHAT READY pid=1', 'id', 'SIGNED_OUT'))

    def test_adb_timeout_capture_and_sdk_override(self):
        with patch.dict('os.environ', {'ANDROID_HOME': '/tmp/test-sdk'}), \
             patch.object(adb.subprocess, 'check_output', return_value=' OK\n') as call:
            self.assertEqual('OK', adb.adb_call('devices'))
        call.assert_called_once_with([str(Path('/tmp/test-sdk') / 'platform-tools' / 'adb'), 'devices'], text=True,
                                    stderr=subprocess.STDOUT, timeout=30)

    def test_screenshot_uses_selected_watch_only(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(adb.subprocess, 'run') as call:
            adb.screenshot('/sdk/adb', 'emulator-5792', Path(folder) / 'nested/frame.png')
        self.assertEqual(['/sdk/adb', '-s', 'emulator-5792', 'exec-out', 'screencap', '-p'], call.call_args.args[0])
        self.assertEqual(30, call.call_args.kwargs['timeout'])

    def test_release_and_missing_package_fail_clearly(self):
        with self.assertRaisesRegex(RuntimeError, 'package missing'):
            adb.require_debug(lambda *parts: '')
        def release(*parts):
            if 'path' in parts:
                return 'package:release.apk'
            raise subprocess.CalledProcessError(1, parts)
        with self.assertRaisesRegex(RuntimeError, 'not accessible as debug'):
            adb.require_debug(release)

    def test_wrong_avd_rejected_before_launch(self):
        def call(*parts):
            if parts == ('devices',):
                return 'List of devices attached\nemulator-5792\tdevice\n'
            return 'watch' if 'getprop' in parts else 'Other_Watch'
        with self.assertRaisesRegex(RuntimeError, 'Expected AVD'):
            adb.discover_watch(call, 'WristPrompt_Watch7')

    def test_selected_watch_used_for_cli_screenshot(self):
        with patch.object(adb.subprocess, 'run') as capture, tempfile.TemporaryDirectory() as folder:
            test_wear_scenarios.LauncherTest().run_cli('emulator-5792\tdevice\n', ['--scenario', 'EMPTY_CHAT', '--screenshot', folder + '/frame.png'])
        self.assertEqual('emulator-5792', capture.call_args.args[0][2])

    def test_specialized_runner_lists_match_catalog(self):
        import wear_process_restart
        import wear_connectivity
        import wear_phone_absence
        self.assertEqual(catalog.CATEGORIES['process'], wear_process_restart.SCENARIOS)
        self.assertEqual(catalog.CATEGORIES['connectivity'], wear_connectivity.SCENARIOS)
        self.assertEqual(set(catalog.CATEGORIES['phone']), set(wear_phone_absence.SCENARIOS))

    def test_missing_debug_build_fails_before_install(self):
        with patch.object(Path, 'is_file', return_value=False), patch('wear_adb.adb_call') as device:
            with self.assertRaisesRegex(RuntimeError, 'Debug APK missing.*assembleDebug'):
                adb.install_debug(device)
        device.assert_not_called()

    def test_install_preserves_existing_package_data(self):
        with patch.object(Path, 'is_file', return_value=True), patch('wear_adb.adb_call', return_value='Success') as device:
            self.assertEqual('Success', adb.install_debug(device))
        device.assert_called_once_with('install', '-r', str(adb.ROOT / 'wear/build/outputs/apk/debug/wear-debug.apk'))
