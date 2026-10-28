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
| D2-2 / Phase C refresh hardening | Automated matrix and contract audit in REFRESH_REVIEW.md; no physical refresh claim | VERIFIED AUTOMATICALLY |
| earliest_refresh_at encoding | Official reference names the field but does not define encoding/units; no timestamp guess | BLOCKED |
| Phase D sign-out/revocation | Implementation and synthetic race/recovery tests in SIGN_OUT_REVIEW.md; real results pending | VERIFIED AUTOMATICALLY |
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

- [ ] PENDING END-OF-DAY PHYSICAL TEST — NATURAL REFRESH: keep the current
  registration and record its saved account/client suffix. With the phone off and
  watch on working Wi-Fi, allow sufficient real token ageing, reload models and
  complete one inference. Observe the static "ChatGPT session renewed on this
  watch" indication (runtime-only; distinguish a previously displayed success).
  Require completed inference without phone authorization, no new registration
  and unchanged account/client mapping. Do not change clock/token files, force
  expiry or destructively manipulate credentials. Record static failures, including
  REFRESH_TIMING_UNDOCUMENTED, rather than looping sign-in. Elapsed time alone or
  fresh-token inference does not prove refresh.

- [ ] PENDING END-OF-DAY PHYSICAL TEST — NORMAL SIGN-OUT: after the natural-refresh
  check, explicitly tap Sign out of ChatGPT for the saved account. Verify inference
  is unavailable and the UI honestly reports confirmed remote revocation or REMOTE
  REVOCATION NOT CONFIRMED. Record the account/client suffix before signing out.
- [ ] PENDING END-OF-DAY PHYSICAL TEST — REAUTHORIZE SAME ACCOUNT: select that saved
  registration and Continue with ChatGPT. Verify the existing client/account mapping
  is reused, browser authorization succeeds and inference completes again.
- [ ] PENDING END-OF-DAY PHYSICAL TEST — OFFLINE SIGN-OUT: manually make watch
  networking unavailable, explicitly sign out, and verify bounded local completion
  plus REMOTE REVOCATION NOT CONFIRMED and ChatGPT disconnect guidance. No false
  remote-success claim. Restore networking manually; optionally reauthorize the
  same saved account afterward to leave the app usable.

A/B/C are already VERIFIED PHYSICALLY; no immediate repeat is required. Model
restoration is deferred to Day 3 and should be retested after its authorized fix.
The sign-out items above are explicit manual end-of-day actions; do not trigger
real sign-out/revocation now. Never clear app data, uninstall, delete Keystore
keys or exhaust usage to run this checklist. Destructive actions must never be
performed automatically and require explicit authorization for that exact action.

## Deferred defect and scope

Low priority, Day 3 model-picker/chat: explicit GPT-6.1 Terra selection did not
survive restart; GPT-6.1 Sol was selected while Terra was still available. Root
cause is unresolved. Do not treat the unit-tested persistence implementation as
physical restoration success. No product fix is made in this task.

D2-2 / Day 2 Phase C is implemented with automated evidence in REFRESH_REVIEW.md;
physical refresh remains deferred to end of day and the earliest-metadata contract
limitation is BLOCKED. Phase C added no sign-out, BYOK, model-restoration fix or chat UI.
Phase D subsequently adds explicit sign-out only; see SIGN_OUT_REVIEW.md.
This does not authorize another phase or change D2-1's accepted results.

## Earlier testing-policy documentation update

VERIFIED AUTOMATICALLY: git diff --check, changed-document whitespace checks and
product/test-source hash comparison. No product/test code changed; existing
uncommitted work was preserved. No builds, lint or unit suites were rerun for this
documentation-only task. Changed AGENTS.md, ROADMAP.md, PERSISTENCE_TESTING.md, DECISIONS.md and
this status document. No commit, push or device operation.

Suggested commit: `docs: record D2-1 physical acceptance and sprint testing policy`.


## Day 2 Phase C validation

VERIFIED AUTOMATICALLY: 122 Wear tests pass; both debug builds and both lint tasks
pass with zero lint errors and unchanged warnings. Unchanged mobile unit task was
up-to-date (55 prior cases). git diff --check passes; both APKs retain Day 1 signing.
Commands and complete audit/matrix are in [REFRESH_REVIEW.md](REFRESH_REVIEW.md).
No device operation, real-token manipulation, commit or push. Natural renewal is
PENDING END-OF-DAY PHYSICAL TEST; earliest-refresh metadata interpretation remains
BLOCKED by the missing official encoding contract. No model-restoration fix.


## Day 2 Phase D validation

VERIFIED AUTOMATICALLY: explicit sign-out/revocation and synthetic race/recovery
coverage pass with 141 Wear and 57 mobile tests, zero failures/errors/skips. Both
debug builds and both lint tasks pass; zero lint errors and existing warnings
unchanged. APKs retain Day 1 signing; git diff --check passes. Full state machine,
sources and limitations: [SIGN_OUT_REVIEW.md](SIGN_OUT_REVIEW.md).

Normal sign-out, same-registration reauthorization and offline local-only sign-out
remain PENDING END-OF-DAY PHYSICAL TEST in the checklist above. No real session
was signed out/revoked, and no data/key/network action was performed by the agent.
No BYOK, Day 3 chat, model-restoration fix, commit or push. Earlier Phase C pending
changes remain preserved.
