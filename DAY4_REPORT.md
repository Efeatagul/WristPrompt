# Day 4 final acceptance — 2026-10-29

## 1. Executive result

**PASS WITH KNOWN DEBT — PHYSICAL PENDING.** D4-A through D4-F are complete
for their recorded automated/emulator scope. D4-G consolidation and final automated/
emulator validation pass. **PENDING PHYSICAL ACCEPTANCE:** no Day 4 physical result
has been supplied. This is not full Day 4 hardware or release acceptance.

Phase G changes documentation only. No features, production/debug/tooling code,
OAuth, billing, credential formats/storage, Data Layer routing, dependencies or
signing identity changed. No physical action, commit or push occurred. Day 5 is not started.

## 2. Scope

Read AGENTS, PROJECT_SPEC, ROADMAP, DECISIONS, DAY3_REPORT, DAY3_STATUS,
DAY4_STATUS, WEAR_SCENARIOS and both emulator Data Layer investigation reports.
Reviewed relevant lifecycle/session/conversation/router/refresh/transport/manifest
and emulator-selection code. Starting working tree was clean. Historical Day 3
physical evidence remains unchanged and does not establish updated Day 4 acceptance.

## 3. D4-A summary

PASS WITH KNOWN DEBT. Retained Activity recreation preserves ViewModel/chat/work.
Home pauses UI collection; return observes current state without replay. Screen-off
continuation is best effort. Final ViewModel clear synchronously invalidates active
work and cancels actual transport. Recreated platform-composer results are discarded,
never auto-sent. Rotating-refresh safety is unchanged. Detailed command-driven
lifecycle/composer emulator evidence in DAY4_STATUS remains historical; the final
catalog rerun verifies launch/readiness, not all those interaction sequences again.

## 4. D4-B summary

PASS WITH KNOWN DEBT. Fresh process reconstructs durable account, explicit billing
and model settings only. Chat, partial output, draft, callbacks, navigation, active
request and busy/Stop state are not durable. No interrupted inference replay.
Final am-kill and separate force-stop runs establish actual changed emulator PIDs
and fresh owners; force-stop is stronger than ordinary process death, neither is
full watch reboot. Synthetic process fixtures do not prove platform encrypted durability.

## 5. D4-C summary

PASS WITH KNOWN DEBT. Transport-driven network behavior; no observer or automatic
request/model retry. Interrupted partial text is retained as non-success and excluded
from future context. Only response.completed establishes success. Recovery needs
explicit user action. Refresh uncertainty remains durable/fail-closed. Synthetic
provider failures are not real radio-transition evidence.

## 6. D4-D summary

PASS WITH KNOWN DEBT. Watch directly owns model discovery, inference, chat, Stop,
New Chat and refresh after setup; normal runtime bridge accesses remain zero in
integrated tests/fixtures. Phone is needed for setup/handoff and reauthentication,
not as an inference proxy. No production fix was needed. Natural real-token refresh
with phone absent remains unobserved physically.

## 7. D4-E summary

PASS WITH KNOWN DEBT. Durability/reconstruction, encrypted-record fail-closed
behavior, isolated plan/API preferences, dormant model preference preservation,
refresh atomicity and interrupted sign-out are covered automatically. No storage
change was required. Injected faults do not prove destructive hardware Keystore or
AtomicFile interruption behavior; those tests were not performed.

## 8. D4-F summary

PASS WITH KNOWN DEBT. 43 canonical synthetic debug scenarios, one primary launcher,
specialized process/connectivity/phone-absence runners and shared safe ADB helpers.
Source/manifest/DEX isolation excludes scenario infrastructure from release. The
CapabilityClient investigation remains closed; no production fallback was added.

## 9. Final automated acceptance

**VERIFIED AUTOMATICALLY**, rerun from the current repository:

| Check | Exact result |
| --- | --- |
| Full Wear JVM suite | 340 tests / 44 suites; 0 failures/errors/skips |
| Full mobile JVM suite | 70 tests / 11 suites; 0 failures/errors/skips |
| Focused Day 4 rerun (subset of full suite) | 116 tests / 13 suites; 0 failures/errors/skips |
| Python tool tests | 40 passed |
| Wear debug / release builds | Both PASS |
| Mobile debug / release builds | Both PASS |
| Wear lintDebug | 0 errors / 6 existing warnings |
| Mobile lintDebug | 0 errors / 3 existing warnings |
| Source/manifest/DEX scenario isolation | PASS |
| Python AST syntax | 13 files PASS |
| git diff --check and changed/new text whitespace | PASS |
| Both debug APK signing certificates | Match preserved SHA-256 ending 79d37061 |

Focused suites: LifecycleOwnership 8, ComposerInteraction 6,
StreamingHttpCancellation 8, ProcessReconstruction 11, ShellNavigationViewModel 2,
ConnectivityTransitions 7, ConnectivityCredential 2, PhoneIndependence 6,
PersistenceRecovery 13, ModelRestorationIntegration 15, ProtectedFailureMatrix 4,
WatchRefresh 21, WatchSignOut 13. These 116 are not additional to the 340.

Commands actually run using DEVELOPMENT.md's preserved JDK/ANDROID_USER_HOME:

```sh
./gradlew :wear:testDebugUnitTest --rerun :mobile:testDebugUnitTest --rerun \
  :wear:assembleDebug :wear:assembleRelease :mobile:assembleDebug \
  :mobile:assembleRelease :wear:lintDebug :mobile:lintDebug --console=plain
./gradlew :wear:testDebugUnitTest --rerun \
  --tests '*LifecycleOwnershipTest' --tests '*ComposerInteractionTest' \
  --tests '*StreamingHttpCancellationTest' --tests '*ProcessReconstructionTest' \
  --tests '*ShellNavigationViewModelTest' --tests '*ConnectivityTransitionsTest' \
  --tests '*ConnectivityCredentialTest' --tests '*PhoneIndependenceTest' \
  --tests '*PersistenceRecoveryTest' --tests '*ModelRestorationIntegrationTest' \
  --tests '*ProtectedFailureMatrixTest' --tests '*WatchRefreshTest' \
  --tests '*WatchSignOutTest' --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_scenario_isolation.py
git diff --check
```

Validation logs for this run are locally available under /tmp/d4g-*.log
(Gradle, focused, smoke, catalog, process, force-stop, connectivity and phone);
/tmp/d4g-full-counts.json preserves full-suite totals before focused filtering.
These temporary files are not durable release artifacts; the counts and boundaries
above are the consolidated record.

Python ast.parse checked all tools/*.py; an explicit text scan checked changed/new
files for trailing whitespace, conflict markers and missing final newlines. Both
APKs were checked with SDK apksigner verify --print-certs. Full counts were captured
before the focused rerun replaced Wear's test XML. Build/lint tasks may reuse current
Gradle outputs; unit tasks explicitly reran. Initial sandbox cache/ADB access failed;
authorized host execution passed without cache migration or signing changes.

## 10. Final emulator acceptance

**VERIFIED ON EMULATOR.** All commands below ran sequentially. Each runner dynamically
selected **WristPrompt_Watch7 (emulator-5554)**, verified watch characteristics and
excluded physical serials. No physical device was selected or modified.

| Command | Result |
| --- | --- |
| python3 tools/wear_scenarios.py --smoke | 11/11 PASS |
| python3 tools/wear_scenarios.py --all | 43/43 PASS |
| python3 tools/wear_process_restart.py --all | 4/4 PASS; am-kill, changed PID/owners |
| python3 tools/wear_process_restart.py --all --method force-stop | 4/4 PASS; separate cold-launch check |
| python3 tools/wear_connectivity.py | 6/6 PASS; synthetic failure/recovery |
| python3 tools/wear_phone_absence.py | 6/6 PASS; synthetic absence, zero bridge access |
| python3 tools/check_scenario_isolation.py | PASS |

Am-kill PID transitions: 8414→9321, 9321→9416, 9416→9505, 9505→9594.
Force-stop PID transitions: 9594→9696, 9696→9792, 9792→9882, 9882→9974.
Both fixture/process owners changed in every restart, with messages=0, generations=0,
partial chars=0 and busy=false afterward.

The catalog establishes fresh ACK/readiness, matching live PID and resumed host.
Connectivity is synthetic and does not toggle radios. Phone absence uses synthetic
local access, not production OAuth/handoff. No real OpenAI, hardware lifecycle,
real radio-transition or release-on-device result is implied. Capability-dependent
production paired-emulator OAuth/handoff remains NOT VERIFIABLE ON CURRENT EMULATOR LAB.

## 11. Physical acceptance status

**PENDING PHYSICAL ACCEPTANCE.** Use physical Galaxy Watch 7 and Android phone,
current Day 4 debug APKs and the existing signing identity. Update with install -r;
no uninstall, clear-data, key deletion, token tampering or destructive corruption.
Phone/watch ADB must be ready; select actual physical serials explicitly. These
commands are for the user, not executed in Phase G:

```sh
adb devices -l
adb -s PHONE_SERIAL install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s WATCH_SERIAL install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Replace serial placeholders after identifying each physical device. Both built
APKs match the preserved signing fingerprint in DEVELOPMENT.md. If installation
reports a signing mismatch, stop; do not uninstall to bypass it.

This is the single final Day 4 matrix; earlier phase checklists are historical.
Record each row using exactly one of: **PASS — PHYSICAL**, **FAIL — PHYSICAL**,
**NOT EXERCISED**, **BLOCKED — EXTERNAL**. All rows are currently untested.

| ID / check | User action and expected acceptance | Status |
| --- | --- | --- |
| P1 Retained background stream | Start real long response, Home, wait briefly, reopen. Latest partial/completed state coherent; no duplicate/replay; credentials/model unchanged. If active, Stop works, partial stays excluded, Compose returns. | NOT EXERCISED |
| P2 Screen-off / wake | Start another long response, let/force screen off without app keep-screen-on; wake/return. Continued coherent state OR coherent interrupted non-success is acceptable. No duplicate output/request, replay, impossible busy state or account/model corruption. | NOT EXERCISED |
| P3 App/process restart | From normal authenticated state, safe process-level termination/relaunch or natural close/reopen, without clearing data. Durable account/billing/model restores; empty chat, no old partial/active request or replay; new Send works. Not equivalent to watch reboot. | NOT EXERCISED |
| P4 Watch-only | Verify normal operation with phone, give watch its own Internet (prefer Wi-Fi), then disable phone Bluetooth, power phone off or move out of range. Model reload, plan inference, follow-up, Stop and New Chat work; no normal-operation phone-required state. Do not force reauthentication. | NOT EXERCISED |
| P5 Real network loss | Start real long stream, interrupt watch's usable Internet, restore. No false completion, credentials retained, coherent failure/partial, no automatic resend. Explicitly send “Reply with exactly: NETWORK RECOVERED”; expect “NETWORK RECOVERED”. Phone state irrelevant. | NOT EXERCISED |
| P6 Model restoration | Select non-default model (Terra if available), leave/relaunch; same model restores if available. If Terra becomes Sol, STOP and preserve state/logs/exact steps; no speculative repair. | NOT EXERCISED |
| P7 Rotary | Check long response, model picker and error/recovery details if practical. Crown scrolling usable; controls neither skipped nor trapped. | NOT EXERCISED |
| P8 Larger font | Increase watch font one practical setting; check chat, composer/recovery, picker and long error. No severe overlap; controls reachable; wrapping/scrolling appropriate. Restore setting if desired. | NOT EXERCISED |
| P9 Final sign-out / reauth | Only after P1–P8: sign out; local authenticated state disappears. Existing Continue with ChatGPT sends setup to phone; reauth/handoff completes; models and one real inference work. Record transient anomaly state/timing if seen; do not claim fixed. | NOT EXERCISED |
| P10 Optional API separation | Optional fake/known-invalid API key, explicitly select API-key mode, request: API auth failure and NO plan fallback. Explicitly return to plan mode. No funded credit needed; optional due to D2/D3 coverage. | NOT EXERCISED |

Physical PASS requires P1–P9 without a new critical/high defect; P10 is optional.
P9 is the only planned sign-out/reauth operation and is performed by the user last.
No destructive physical credential/key test is required or authorized here.

If P6 passes without reproducing the historical issue, retain this wording:
“Historical physical model-restoration anomaly remains unreproduced and unresolved;
current physical and automated acceptance passes.” Do not assert its physical
clause until results are supplied.

Natural refresh: do not force expiry or tamper with tokens. Record it if observed;
otherwise retain **NOT PHYSICALLY OBSERVED — AUTOMATED WATCH-ONLY REFRESH COVERAGE PASSES.**

## 12. Architecture/security invariants

**VERIFIED AUTOMATICALLY / source review:**

- No automatic inference replay or model-discovery retry. Initial usable-account
  discovery and required bearer-change catalog reconciliation are not retry loops.
  HTTP connection retry remains disabled.
- No active-request or conversation persistence; ChatConversation and operation
  ownership remain process memory. Fresh navigation ownership is also memory-only.
- CredentialRouter routes solely by explicit selected mode; no credential fallback.
- Watch OpenAiProvider/OpenAiHttp perform direct inference; phone is not a proxy.
- WearConnectivity still requires reachable capability discovery; no Data Layer
  bypass or production NodeClient/connectedNodes fallback. Debug diagnostic use is separate.
- No ConnectivityManager observer, foreground service, WorkManager, wake lock or
  production keep-screen-on behavior.
- WatchProfiles preserves durable refreshPending, bounded NonCancellable rotation
  and atomic replacement; uncertain consumed grants remain fail-closed.
- ResponseEvents/StreamResult require response.completed for success;
  ChatConversation commits only completed exchanges. Failure/cancel/interruption
  excludes pending exchanges from future context; cancellation reaches HTTP transport.
- Scenario Activity/classes/names/ACK/action are absent from release manifest/DEX;
  production source references and fixture storage/network dependencies are rejected.

These checks do not establish physical power management or platform interruption durability.

## 13. Known debt

- Historical Terra -> Sol physical restoration anomaly: unreproduced/unresolved.
- Transient reauthorization anomaly: unresolved; record recurrence, do not mark fixed.
- Funded BYOK success: unverified, BLOCKED — EXTERNAL (no funded API credits).
- Paired-emulator CapabilityClient synchronization: emulator/GMS limitation; closed investigation.
- Physical Keystore / AtomicFile interruption behavior: not destructively tested.
- Natural real-token refresh without phone: not physically observed; automated coverage passes.
- Final hardware lifecycle/network/watch-only acceptance: pending P1–P9.

## 14. Deferred RC items

Retain release-build hardware smoke/signing compatibility, full reboot/platform
storage durability, actual cancelled-exchange server-context exclusion and model-change
context boundary checks, remote revocation outcome, and funded BYOK success when an
external credential is available. Distinguish these from this debug hardware matrix.
No forced expiry, destructive storage test or funded-credit purchase is required.
Reauthorization debt needs explicit disposition before freeze; compilation cannot close it.

## 15. Day 5 handoff

Recommendation only: after recording the Day 4 hardware result, follow ROADMAP's
release-hardening focus: protocol/security regression, manifest/exported-component/
diagnostic/backup/crypto/signing/dependency and release-shrinker review, then accurate
release documentation/privacy/setup materials and current Play/Wear requirements.
Resolve or explicitly disposition historical anomalies before freeze. Optional voice
is not introduced by this handoff. **Day 5 has not started.**

Changed files: DAY4_REPORT.md, DAY4_STATUS.md, ROADMAP.md only.
Suggested commit: `docs: consolidate Day 4 acceptance and physical test matrix`.
No commit or push performed.
