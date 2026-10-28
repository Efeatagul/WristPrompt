# Day 2 physical acceptance — final evidence record

Recorded 2026-10-29 from the project owner's real-hardware report.
Overall Day 2: **PASS WITH KNOWN DEBT**. This is an evidence record, not a request
to rerun the former acceptance procedure. No additional sign-out is requested.

Devices: Samsung Galaxy Watch 7 + Samsung Galaxy Note 10+. Latest Day 2 debug
builds updated in place with the same signing identity; app data preserved.
The agent did not operate the devices. OS versions/build hashes were not supplied
in this report. Previous physical sessions may reference a different phone.

## VERIFIED PHYSICALLY

| Check | Result |
| --- | --- |
| D2-1 authenticated app restart, watch reboot and phone-off Wi-Fi discovery/inference | Prior PASS retained; not repeated |
| Initial upgraded build | FAIL: REFRESH_TIMING_UNDOCUMENTED blocked model loading/inference while identity/plan remained |
| Focused refresh-regression fix retest | PASS / PHYSICALLY RESOLVED: same real registration/account, no destructive reauthentication, plan mode unchanged, models loaded, streamed WristPrompt works., response.completed and exact match |
| ChatGPT-plan proof | PASS: account connected, explicit plan mode, account-specific model selection, GPT-6.1 Sol, streaming/completed/exact output |
| Normal sign-out and reauthorization | PASS WITH TRANSIENT REAUTHORIZATION ANOMALY: exchange failure and another expired setup context, eventual recovery after force-restart/reopen and retry, intended account/plan/catalog/completed inference restored |
| Fake API-key transfer and invalid-key rejection | PASS: fake value configured, explicit API mode/separate billing, HTTP 401, API authentication rejected, ChatGPT was not used |
| API failure -> ChatGPT no-silent-fallback | PASS for tested direction: no ChatGPT response or automatic mode switch |
| Explicit switch back to ChatGPT | PASS: known-good ChatGPT inference worked again |
| Ordinary mode switching / app restart | Exercised; no unreported per-mode persistence matrix or successful API request inferred |
| Cancellation | PASS: running inference stopped; “Stopped. Partial output is not a completed proof.”; ChatGPT auth/inference remained usable afterward |
| Watch layout | KNOWN DEFECT: overlap/clipping/dense diagnostics, without a reported block of the tested auth/inference/billing operations |

The normal sign-out report does not specify remote revocation's CONFIRMED versus
UNCONFIRMED outcome. Eventual reauthorization is not proof of remote revocation.
It confirms the same intended account; exact post-sign-out client-ID suffix reuse
was not separately reported. The focused upgrade retest did confirm retention of
the existing registration. Do not conflate those two observations.

## BLOCKED BY EXTERNAL TEST CONSTRAINT

**BLOCKED — no funded OpenAI API credential available for physical verification**

This is the exact status of successful real BYOK inference. No purchase, API
credits or real API key is requested. A stored fake key is not a valid credential.
Successful BYOK behavior remains VERIFIED AUTOMATICALLY with fake transport and
synthetic credentials. It is not physically PASS and is not a product-code failure.

## SKIPPED / unobserved evidence

- Offline sign-out: **SKIPPED / NOT REQUIRED FOR DAY 2 PHYSICAL GATE**.
- No additional destructive Keystore, credential, backup/restore or quota tests.
- No detailed refresh telemetry, exact timing, renewal-banner observation or token
  tuple was supplied. The refresh regression's usability is physically resolved;
  additional renewal details are not inferred.
- Manage usage browser navigation, exact per-mode restart outcomes, independent
  API-key removal and controlled lost-ACK/disconnect timing were not explicitly
  reported in this final result; they are not silently marked physically PASS.

## Review disposition

See [DAY2_REPORT.md](DAY2_REPORT.md) for the complete security/concurrency/contract
audit, reauthorization uncertainty, current automated results and exact Day 3
order. No concrete unsafe reauthorization transition was identified; recovery
coverage was strengthened with one synthetic test, without product-code changes.

Carry to Day 3: Terra -> Sol restoration, real round-screen layout/text density,
and transient reauthorization anomaly triage. Day 5 must disposition remaining
reliability/release-hardening evidence before v0.1 freeze. Day 3 has not started.

These records supersede prior STOPPED/pending instructions after the owner-supplied
successful retest. They do not schedule or authorize further physical operations.
