"""Host-only process-runner safety and no-replay assertions; never contacts ADB."""
import contextlib
import io
import subprocess
import sys
import unittest
from unittest.mock import patch
import wear_process_restart as runner


class ProcessRunnerTest(unittest.TestCase):
    def fresh(self):
        return dict(pid='200', owner='new', processOwner='new-process', generations='0', messages='0', chars='0',
                    draftChars='0', busy='false', stage='EMPTY', proof='READY', surface='CHAT', pendingInput='false',
                    discoveries='1', completions='0', model='debug-1', billing='CHATGPT_PLAN')

    def test_pid_and_both_owners_must_change(self):
        before = dict(pid='100', owner='old', processOwner='old-process')
        for field in before:
            after = self.fresh(); after[field] = before[field]
            with self.assertRaisesRegex(RuntimeError, 'fresh process'):
                runner.verify_fresh(before, after)

    def test_fresh_state_rejects_replay_history_busy_errors_and_saved_ui(self):
        before = dict(pid='100', owner='old', processOwner='old-process')
        for field, value in dict(generations='1', messages='2', chars='4', draftChars='2', busy='true', stage='RECEIVING',
                                 proof='FAILED', surface='COMPOSER', pendingInput='true', discoveries='0',
                                 completions='1', model='debug-2', billing='OPENAI_API_KEY').items():
            after = self.fresh(); after[field] = value
            with self.assertRaisesRegex(RuntimeError, 'stale work'):
                runner.verify_fresh(before, after)

    def test_matching_identifier_required(self):
        self.assertIsNone(runner.fields('old PROCESS_RESTART_IDLE READY pid=200', 'new', 'PROCESS_RESTART_IDLE'))
        self.assertEqual({'pid': '200'}, runner.fields('new PROCESS_RESTART_IDLE READY pid=200', 'new', 'PROCESS_RESTART_IDLE'))

    def run_cli(self, method='am-kill', name='WristPrompt_Watch7', debug=True, devices='emulator-5582\tdevice\n', scenario='PROCESS_RESTART_IDLE'):
        calls = []; self.calls = calls
        token = None; pid = '100'; seed = False; background = False; generation = 0

        def command(parts, **kwargs):
            nonlocal token, pid, seed, background, generation
            calls.append(parts)
            if parts[1:] == ['devices']:
                return 'List of devices attached\n' + devices
            if 'getprop' in parts:
                return 'watch,emulator'
            if 'avd' in parts:
                return name + '\nOK'
            if 'path' in parts:
                return 'package:debug.apk'
            if 'run-as' in parts and not debug:
                raise subprocess.CalledProcessError(1, parts)
            if 'start' in parts:
                if '--es' in parts:
                    token = parts[-1]
                pid = '200' if generation else '100'; seed = False; background = False
            if 'broadcast' in parts:
                seed = True
            if 'KEYCODE_HOME' in parts:
                background = True
            if 'kill' in parts or 'force-stop' in parts:
                generation += 1; pid = None
            if 'pidof' in parts:
                if pid is None:
                    raise subprocess.CalledProcessError(1, parts)
                return pid
            if 'cat' in parts:
                r = self.fresh()
                r.update(pid=pid, owner='old' if generation == 0 else 'new',
                         processOwner='old-process' if generation == 0 else 'new-process',
                         lifecycle='CREATED' if background else 'RESUMED', collecting='false' if background else 'true')
                if seed:
                    if scenario == 'PROCESS_RESTART_IDLE':
                        r['model'] = 'debug-2'
                    else:
                        r.update(generations='1', messages='2' if scenario.endswith('COMPLETED') else '1',
                                 stage='COMPLETED' if scenario.endswith('COMPLETED') else ('RECEIVING' if scenario.endswith('PARTIAL') else 'STARTING'),
                                 chars='10' if scenario.endswith('PARTIAL') else '0', busy='false' if scenario.endswith('COMPLETED') else 'true')
                return f'{token} {scenario} READY ' + ' '.join(f'{k}={v}' for k, v in r.items())
            return 'OK'

        with patch.object(sys, 'argv', ['tool', '--scenario', scenario, '--method', method]), \
             patch.object(runner.subprocess, 'check_output', side_effect=command), \
             patch.object(runner.time, 'sleep'), contextlib.redirect_stdout(io.StringIO()):
            runner.main()
        return calls

    def test_all_four_scenarios_kill_then_resume_without_reseeding(self):
        for scenario in runner.SCENARIOS:
            calls = self.run_cli(scenario=scenario)
            self.assertEqual(1, sum('kill' in c for c in calls))
            self.assertFalse(any('force-stop' in c for c in calls))
            self.assertEqual(1, sum('broadcast' in c for c in calls))
            self.assertEqual(2, sum('start' in c for c in calls))

    def test_force_stop_is_explicit_separate_cold_launch(self):
        calls = self.run_cli(method='force-stop')
        self.assertEqual(1, sum('force-stop' in c for c in calls))
        self.assertFalse(any('kill' in c for c in calls))
        self.assertEqual(2, sum('start' in c and 'launch_id' in c for c in calls))

    def test_wrong_avd_fails_before_process_mutation(self):
        with self.assertRaisesRegex(RuntimeError, 'Expected AVD'):
            self.run_cli(name='Unrelated_Watch')
        self.assertFalse(any(any(action in c for action in ('start', 'kill', 'force-stop', 'install')) for c in self.calls))

    def test_physical_only_is_never_selected(self):
        with self.assertRaisesRegex(RuntimeError, 'found 0'):
            self.run_cli(devices='physical-watch\tdevice\n')
        self.assertFalse(any('physical-watch' in c for c in self.calls))

    def test_release_fails_before_process_mutation(self):
        with self.assertRaisesRegex(RuntimeError, 'not accessible as debug'):
            self.run_cli(debug=False)
        self.assertFalse(any(any(action in c for action in ('start', 'kill', 'force-stop', 'install')) for c in self.calls))


if __name__ == '__main__':
    unittest.main()
