"""Acceptance assertions fail closed; no ADB/network calls."""
import unittest
from wear_phone_absence import verify


class PhoneAbsenceTest(unittest.TestCase):
    def report(self):
        return dict(bridgeLookups='0', bridgeRequests='0', busy='false', discoveries='1',
                    generations='2', completions='2', proof='READY', discoveryProblem='NONE',
                    model='debug-1', messages='4', billing='CHATGPT_PLAN', refreshes='0', lastInput='3')

    def test_multi_turn_valid(self):
        verify(self.report(), 'WATCH_ONLY_CHAT')

    def test_bridge_use_rejected(self):
        for key in ('bridgeLookups', 'bridgeRequests'):
            report = self.report(); report[key] = '1'
            with self.assertRaises(RuntimeError):
                verify(report, 'WATCH_ONLY_CHAT')

    def test_missing_context_or_billing_fallback_rejected(self):
        for key, value in [('lastInput', '1'), ('billing', 'OPENAI_API_KEY'), ('completions', '0')]:
            report = self.report(); report[key] = value
            with self.assertRaises(RuntimeError):
                verify(report, 'WATCH_ONLY_CHAT')

    def test_reauth_requires_blocked_state(self):
        report = self.report()
        with self.assertRaises(RuntimeError):
            verify(report, 'REAUTH_REQUIRED_PHONE_ABSENT')
        report.update(discoveries='0', generations='0', completions='0', proof='IDLE',
                      discoveryProblem='REAUTHORIZE', model='NONE', messages='0')
        verify(report, 'REAUTH_REQUIRED_PHONE_ABSENT')
