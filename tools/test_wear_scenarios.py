"""Host-only discovery/closed-failure tests; never contacts ADB."""
import contextlib
import io
import sys
import unittest
from unittest.mock import patch
import wear_scenarios as runner


class LauncherTest(unittest.TestCase):
    def run_cli(self, devices, argv=None, installed=True, debug=True):
        calls = []

        def command(parts, **kwargs):
            calls.append(parts)
            tail = parts[1:]
            if tail == ['devices']:
                return 'List of devices attached\n' + devices
            if 'getprop' in parts:
                return 'watch,emulator'
            if 'avd' in parts:
                return 'WristPrompt_Watch7\nOK'
            if 'path' in parts:
                return 'package:debug.apk' if installed else ''
            if 'run-as' in parts and not debug:
                import subprocess
                raise subprocess.CalledProcessError(1, parts)
            if 'pidof' in parts:
                return '100'
            if 'cat' in parts:
                return 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa EMPTY_CHAT READY generations=0 stage=EMPTY lifecycle=RESUMED pid=100'
            return '/data/user/0/debug'
        with patch.object(sys, 'argv', ['wear_scenarios.py', *(argv or ['--scenario', 'EMPTY_CHAT'])]), \
             patch.object(runner.subprocess, 'check_output', side_effect=command), \
             patch.object(runner.uuid, 'uuid4', return_value=type('Nonce', (), {'hex': 'a' * 32})()), \
             contextlib.redirect_stdout(io.StringIO()):
            runner.main()
        return calls

    def test_list_has_all_names(self):
        self.assertEqual(84, len(runner.scenarios()))
        self.assertEqual(84, len(set(runner.scenarios())))

    def test_unknown_rejected_before_adb(self):
        with patch.object(sys, 'argv', ['tool', '--scenario', 'UNKNOWN']), \
             patch.object(runner.subprocess, 'check_output') as adb, \
             contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            runner.main()
        adb.assert_not_called()

    def test_physical_devices_ignored(self):
        with self.assertRaisesRegex(RuntimeError, 'found 0'):
            self.run_cli('real-watch\tdevice\n')

    def test_no_watch(self):
        with self.assertRaisesRegex(RuntimeError, 'found 0'):
            self.run_cli('')

    def test_ambiguous_watches(self):
        with self.assertRaisesRegex(RuntimeError, 'found 2'):
            self.run_cli('emulator-5582\tdevice\nemulator-5590\tdevice\n')

    def test_missing_package(self):
        with self.assertRaisesRegex(RuntimeError, 'package missing'):
            self.run_cli('emulator-5582\tdevice\n', installed=False)

    def test_dynamic_serial_used_for_launch(self):
        calls = self.run_cli('real-watch\tdevice\nemulator-5582\tdevice\n')
        launches = [p for p in calls if 'start' in p]
        self.assertEqual(1, len(launches))
        self.assertEqual('emulator-5582', launches[0][2])
        self.assertFalse(any('real-watch' in p for p in calls))

    def test_release_fails_run_as(self):
        import subprocess
        with self.assertRaisesRegex(RuntimeError, 'not accessible as debug'):
            self.run_cli('emulator-5582\tdevice\n', debug=False)


if __name__ == '__main__':
    unittest.main()
