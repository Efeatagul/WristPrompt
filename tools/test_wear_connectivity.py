"""Host-only acceptance assertions; no ADB or networking."""
import unittest
import wear_connectivity as runner


class ConnectivityRunnerTest(unittest.TestCase):
    def failure(self):
        return dict(generations='1', discoveries='1', excluded='1', assistants='0', stage='FAILED',
                    busy='false', completions='0', billing='CHATGPT_PLAN', chars='7')

    def test_partial_failure_passes(self):
        runner.verify_failure(self.failure(), partial=True)

    def test_completed_or_busy_failure_rejected(self):
        for changes in ({'stage': 'COMPLETED'}, {'busy': 'true'}, {'assistants': '1'}, {'excluded': '0'}):
            with self.assertRaises(RuntimeError):
                runner.verify_failure(self.failure() | changes, partial=True)

    def test_lost_partial_rejected(self):
        with self.assertRaises(RuntimeError):
            runner.verify_failure(self.failure() | {'chars': '0'}, partial=True)

    def test_restoration_cannot_replay_or_reload(self):
        before = self.failure()
        runner.verify_no_replay(before, before | {'commandRevision': '1'})
        for key in ('generations', 'discoveries', 'stage', 'chars', 'billing'):
            with self.assertRaises(RuntimeError):
                runner.verify_no_replay(before, before | {key: 'changed'})

    def test_explicit_recovery_must_exclude_old_input(self):
        report = dict(generations='2', discoveries='1', stage='COMPLETED', completions='1', excluded='1',
                      assistants='1', lastInput='1', messages='3', busy='false', billing='CHATGPT_PLAN')
        runner.verify_recovery(report)
        with self.assertRaises(RuntimeError):
            runner.verify_recovery(report | {'lastInput': '2'})

    def test_discovery_failure_and_explicit_recovery(self):
        runner.verify_failure(dict(generations='0', discoveries='1', proof='FAILED', model='NONE',
                                  discoveryProblem='NETWORK', stage='EMPTY', messages='0', busy='false',
                                  completions='0', billing='CHATGPT_PLAN'), discovery=True)
        report = dict(generations='0', discoveries='2', proof='READY', model='debug-1', discoveryProblem='NONE',
                      messages='0', busy='false', billing='CHATGPT_PLAN')
        runner.verify_recovery(report, discovery=True)
        with self.assertRaises(RuntimeError):
            runner.verify_recovery(report | {'model': 'NONE'}, discovery=True)


if __name__ == '__main__':
    unittest.main()
