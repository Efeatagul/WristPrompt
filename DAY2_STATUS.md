# Day 2 status — 2026-10-28

## Current evidence

| Area | Result | Evidence label |
| --- | --- | --- |
| D2-1 authentication/account persistence | PASS: same account/plan after app restart, no reauthentication, successful inference | VERIFIED PHYSICALLY |
| D2-1 watch reboot persistence | PASS: authenticated state survives reboot; successful inference | VERIFIED PHYSICALLY |
| D2-1 phone-independent networking/discovery/inference | PASS: phone powered off; watch directly on Wi-Fi; catalog loads and inference completes | VERIFIED PHYSICALLY |
| Explicit model-selection restoration | PARTIAL: Terra → Sol after restart although Terra remains listed; low-priority Day 3 debt | VERIFIED PHYSICALLY |
| Persistence/startup unit coverage and debug builds/lint | Prior D2-1 run: 108 Wear tests pass; both debug builds/lint pass, zero lint errors | VERIFIED AUTOMATICALLY |
| Exact host-ID continuity at storage abstraction | DataStore close/reopen test passes; no physical hash comparison supplied | VERIFIED AUTOMATICALLY |
| Natural-expiry refresh without phone | Not established by A/B/C inference success | PENDING END-OF-DAY PHYSICAL TEST |

**D2-1 overall PASS** for authentication/account persistence, reboot persistence,
phone-independent networking, direct discovery and inference. User confirmation
is the physical evidence source; no new agent device action was performed.
Model restoration is separately PARTIAL, not a blocker or a claimed success.
Detailed evidence and repeatable procedures: [PERSISTENCE_TESTING.md](PERSISTENCE_TESTING.md).

## End-of-day physical acceptance checklist

Batch non-blocking requirements here as authorized implementation progresses.
Do not stop after every task for routine physical checks. Immediate safety
exceptions and exact result labels are defined in [AGENTS.md](AGENTS.md).

- [ ] PENDING END-OF-DAY PHYSICAL TEST: with the phone off and watch on working
  Wi-Fi, allow natural session ageing, reload models and complete one inference.
  Do not change clock/token files or force expiry. Record completion or the static
  failure state; do not infer actual refresh solely from elapsed time.

A/B/C are already VERIFIED PHYSICALLY; no immediate repeat is required. Model
restoration is deferred to Day 3 and should be retested after its authorized fix.
Do not clear app data, uninstall, revoke real sessions, delete credentials/keys or
exhaust usage to run this checklist. Destructive cases require explicit permission
for that exact action and must never be performed automatically.

## Deferred defect and scope

Low priority, Day 3 model-picker/chat: explicit GPT-6.1 Terra selection did not
survive restart; GPT-6.1 Sol was selected while Terra was still available. Root
cause is unresolved. Do not treat the unit-tested persistence implementation as
physical restoration success. No product fix is made in this task.

D2-2 has not been implemented. This status and testing-policy update does not
authorize starting another phase. Existing release/recovery requirements remain;
there is no new immediate physical-test blocker for D2-1.

## This documentation task

VERIFIED AUTOMATICALLY: git diff --check, changed-document whitespace checks and
product/test-source hash comparison. No product/test code changed; existing
uncommitted work was preserved. No builds, lint or unit suites were rerun for this
documentation-only task. Changed AGENTS.md, ROADMAP.md, PERSISTENCE_TESTING.md, DECISIONS.md and
this status document. No commit, push or device operation.

Suggested commit: `docs: record D2-1 physical acceptance and sprint testing policy`.
