# Day 2 final status — 2026-10-29

**PASS WITH KNOWN DEBT.** Day 2's authentication/recovery/billing-isolation stage
gate is accepted from the owner's physical results and current automated audit.
This is not v0.1 release readiness or authorization to start Day 3.

## Current evidence

| Area | Result | Evidence label |
| --- | --- | --- |
| D2-1 app restart/reboot authentication persistence and phone-off Wi-Fi discovery/inference | Prior PASS retained; no repeat | VERIFIED PHYSICALLY |
| Upgrade REFRESH_TIMING_UNDOCUMENTED regression | RESOLVED: same real registration, no destructive reauthentication, plan retained, models and exact completed inference restored | VERIFIED PHYSICALLY |
| ChatGPT-plan path | PASS with GPT-6.1 Sol; streamed WristPrompt works., response.completed, exact match | VERIFIED PHYSICALLY |
| Normal sign-out / reauthorization | PASS WITH TRANSIENT REAUTHORIZATION ANOMALY; first exchange failure and expired context, then successful intended-account recovery after reopen/retry | VERIFIED PHYSICALLY |
| Remote revocation result | Not reported; no physical confirmation inferred | UNOBSERVED |
| Exact issued-client reuse after sign-out | Implementation/tests verify; final physical report confirms intended account without a separate suffix comparison | VERIFIED AUTOMATICALLY |
| Fake-key setup and API 401 | PASS: explicit separate billing, API authentication rejection; fake key is not valid/funded | VERIFIED PHYSICALLY |
| API failure -> ChatGPT no-fallback | PASS in tested direction; explicit return to ChatGPT then succeeds | VERIFIED PHYSICALLY |
| Both-direction billing invariant and successful BYOK path | PASS using synthetic credentials/fake transport | VERIFIED AUTOMATICALLY |
| Successful real BYOK inference | BLOCKED — no funded OpenAI API credential available for physical verification | BLOCKED BY EXTERNAL TEST CONSTRAINT |
| Mode switching / ordinary restart | Exercised; no unreported exact per-mode outcomes inferred | VERIFIED PHYSICALLY — limited observation |
| Cancellation semantics and continuing ChatGPT usability | PASS: stopped partial is not completion; path remains usable | VERIFIED PHYSICALLY |
| Offline sign-out | SKIPPED / NOT REQUIRED FOR DAY 2 PHYSICAL GATE | SKIPPED |
| Exact natural-refresh telemetry/renewal time | Not supplied; specific upgrade regression is resolved without claiming more | UNOBSERVED |
| Terra -> Sol model restoration | Prior PARTIAL; not fixed or invalidated by Sol success | KNOWN DEFECT / TECHNICAL DEBT |
| Round-screen text overlap/clipping/density | Real observed issue; tested critical states remained recognizable | KNOWN DEFECT / TECHNICAL DEBT |

Final physical pair: Galaxy Watch 7 + Galaxy Note 10+, latest Day 2 debug apps
updated in place with unchanged signing and preserved data. Earlier device records
belong to their earlier sessions. No new device action was performed by the agent.

## Final review and files

[DAY2_PHYSICAL_ACCEPTANCE.md](DAY2_PHYSICAL_ACCEPTANCE.md) is now the single final
physical evidence record; do not execute the old sequence again.
[DAY2_REPORT.md](DAY2_REPORT.md) owns the final auth/concurrency/security/contract
audit, limits of the reauth explanation, release blockers and exact Day 3 order.
[REFRESH_REGRESSION_REVIEW.md](REFRESH_REGRESSION_REVIEW.md) records the initially
failing policy and owner-confirmed physical resolution.

No root cause is invented for the transient reauth failure. Current code fails
closed and supports bounded fresh-request recovery; the new synthetic recovery
test verifies fresh OAuth values and no code/old-envelope reuse. No product fix
was warranted by a concrete reproduced defect; no new physical sign-out cycle
is needed. Track anomaly triage for Day 3 and disposition before Day 5 freeze.

## Automated evidence and historical reports

VERIFIED AUTOMATICALLY: 178 Wear / 70 mobile tests, zero failures/errors/skips;
both debug builds/lint pass, zero lint errors and prior warning categories.
git diff --check passes; see DAY2_REPORT.md section K for executed commands.
Both unit tasks are explicitly rerun, both debug builds/lint checked, and
whitespace/security searches performed. Only one test and documentation changed
in the final review; production and DECISIONS.md hash comparison is unchanged.
No architecture choice changed. Prior uncommitted refresh-fix work is preserved.

Historical automated counts remain attributed to their phases:
- Baseline: DAY2_BASELINE.md — persistent toolchain/signing.
- D2-1: PERSISTENCE_TESTING.md — 108 Wear cases and accepted physical A/B/C.
- Phase C: REFRESH_REVIEW.md — 122 Wear cases; original blanket timing guard is
  superseded by the focused regression fix, not retroactively made correct.
- Phase D: SIGN_OUT_REVIEW.md — 141 Wear / 57 mobile cases.
- Phase E: BYOK_REVIEW.md — 161 Wear / 64 mobile cases.
- Phase F: prior UI/billing clarity evidence — 162 Wear / 64 mobile cases.
- Phase G: DAY2_FAILURE_MATRIX.md — 172 Wear / 69 mobile cases.
- Focused refresh fix: REFRESH_REGRESSION_REVIEW.md — 178 Wear / 69 mobile cases,
  subsequently physically resolved by the owner.

## Gate / remaining scope

No remaining demonstrated Day 2 critical/high security or billing-safety blocker.
External API funding constraint does not fail this Day 2 stage. Do not call paid
API inference physically verified or solicit a purchase. Release remains subject
to Day 3 chat/model/UI work and later Day 5/6 release-hardening gates. No additional
hardware tests are requested by this review.

Day 3 order (plan only): round-screen readability -> model restoration -> native
text chat/in-memory context -> streaming/Stop semantics -> account/model/billing
transitions and usage/errors -> full automated checks and one consolidated
meaningful physical session. Reauth anomaly stays tracked, not redesign scope.

No new feature, Day 3 work, publication, device action, paid request, commit or push.
Suggested commit: `test: record Day 2 physical acceptance and auth gate review`.
