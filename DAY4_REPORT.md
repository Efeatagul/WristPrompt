# Day 4 final acceptance — 2026-10-29

## 1. Executive result

**PASS WITH KNOWN DEBT. Day 4 is complete.** Automated, emulator and targeted
physical acceptance are complete, including the owner's successful fixed-D4-H-APK
P9 retest on Galaxy Watch 7 with the physical phone. P9 is PASS — PHYSICAL AFTER
REMEDIATION. The pre-D4-H failure remains recorded as defect history, not current status.

This final closure changes documentation only. No production/test changes, new
automated or emulator runs, physical-device actions, commit or push occurred in
this closure. The physical results are owner-reported. Day 5 has not started.
D4-H changed account presentation only; no lifecycle/background architecture,
OAuth/handoff/storage architecture, routing or persistence change was required.

The final baseline combines D4-H Wear verification with applicable earlier D4-G
mobile/Python/emulator evidence. Mobile and Python were not rerun during D4-H;
no old automated result is represented as newly executed in this closure.

## 2. Historical D4-G scope

The following describes the earlier D4-G review, not the final closure worktree.
Final closure read DAY4_REPORT, DAY4_STATUS, ROADMAP, DECISIONS and DAY3_REPORT;
its starting tree contained the existing uncommitted D4-H changes, preserved intact.

D4-G read AGENTS, PROJECT_SPEC, ROADMAP, DECISIONS, DAY3_REPORT, DAY3_STATUS,
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

**VERIFIED AUTOMATICALLY — preserved baseline, not rerun during final closure.**
Wear counts/builds/lint/isolation below are the latest D4-H results; mobile,
Python and the focused 116-test subset are historical D4-G results. The D4-G
340-test Wear result is superseded by D4-H's 343-test suite.

| Check | Exact result |
| --- | --- |
| Full Wear JVM suite (D4-H) | 343 tests / 45 suites; 0 failures/errors/skips |
| Full mobile JVM suite (D4-G; not rerun in D4-H) | 70 tests / 11 suites; 0 failures/errors/skips |
| Focused Day 4 rerun (subset of full suite) | 116 tests / 13 suites; 0 failures/errors/skips |
| Python tool tests (D4-G; not rerun in D4-H) | 40 passed |
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
WatchRefresh 21, WatchSignOut 13. These 116 were a subset of the historical 340-test D4-G run, not extra tests.

Historical D4-G commands using DEVELOPMENT.md's preserved JDK/ANDROID_USER_HOME
(D4-H commands and suite details are recorded in section G below):

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

## 10. Final emulator baseline — preserved D4-G evidence

**VERIFIED ON EMULATOR.** Previously executed D4-G commands below ran sequentially;
none was rerun during D4-H or final documentation closure. Each runner dynamically
selected **WristPrompt_Watch7 (WATCH_SERIAL)**, verified watch characteristics and
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

## 11. Final physical acceptance — owner-reported, 2026-10-29

**VERIFIED PHYSICALLY / PASS WITH KNOWN DEBT.** The owner supplied P1–P10 results,
including successful P9 retesting of the fixed D4-H APK. The agent performed no
physical-device actions during documentation closure. This is targeted Day 4
acceptance, not full release-candidate or destructive platform acceptance.

| ID | Final physical result | Evidence / boundary |
| --- | --- | --- |
| P1 | PASS — PHYSICAL, WITH INTERMITTENT PRIOR INTERRUPTION NOT REPRODUCED | One initial real Home interruption retained partial output, marked the exchange unfinished/interrupted, excluded it from future context and caused no replay. Four later attempts succeeded, including two approximately 30-paragraph Linux scheduler requests, with more than 10 seconds on the watch face before returning to the same continuing stream. No duplicate request or replay. |
| P2 | PASS — PHYSICAL | Real stream interrupted after screen-off/wake; partial/non-success state retained with no replay. Accepted under the best-effort screen-off/Doze contract. |
| P3 | PASS — PHYSICAL | Process kill/restart restored account, billing mode and selected model; chat remained non-durable, no old active request replayed, and a new request returned exactly RESTART WORKS. Does not establish full reboot. |
| P4 | PASS — PHYSICAL | Phone Bluetooth disconnected; watch independently online over Wi-Fi. Model operations, real plan inference, follow-up, Stop and New Chat worked. Normal post-setup operation remained phone-independent; natural token refresh was not observed. |
| P5 | PASS — PHYSICAL | Removing watch Wi-Fi immediately stopped real streaming without false completion. Wi-Fi restoration caused no automatic replay; credentials remained usable. An explicit request returned exactly NETWORK RECOVERED. |
| P6 | PASS — PHYSICAL | Exact selected GPT-5.6 Luna restored. Terra was not physically retested; historical Terra -> Sol anomaly remains unreproduced and unresolved. |
| P7 | PASS — PHYSICAL | Rotary scrolling worked on available main-menu screens and long assistant output. |
| P8 | PASS — PHYSICAL | Maximum watch font size remained usable; controls remained reachable. |
| P9 | PASS — PHYSICAL AFTER REMEDIATION | Before D4-H, credentials were unusable, Continue returned and model loading stopped, but old name/ID remained visible. Fixed-APK retest clears previous account information and authenticated presentation, shows Continue with ChatGPT, completes physical-phone OAuth and watch credential handoff, restores correct account presentation, loads models and completes real inference. Historical transient reauthorization anomaly remains unresolved and was not reproduced during current physical acceptance. |
| P10 | PASS — PHYSICAL | Obviously invalid API key failed as expected without fallback to ChatGPT-plan credentials. |

Background continuation remains best effort. The one prior Home interruption
could not be reproduced; subsequent repeated physical continuation and the safe
interrupted/excluded path satisfy Day 4 acceptance. No foreground service,
Ongoing Activity or background architecture change was justified. D4-H made no
lifecycle production change. No additional physical-device actions are required
for Day 4 closure.

## 12. Architecture/security invariants

**Preserved Day 4 automated/source-review conclusions, supplemented by owner-reported P9:**

- No automatic inference replay or model-discovery retry. Initial usable-account
  discovery and required bearer-change catalog reconciliation are not retry loops.
  HTTP connection retry remains disabled.
- No active-request or conversation persistence; ChatConversation and operation
  ownership remain process memory. Fresh navigation ownership is also memory-only.
- CredentialRouter routes solely by explicit selected mode; no credential fallback.
- Watch OpenAiProvider/OpenAiHttp perform direct inference; phone is not a proxy.
- WearConnectivity still requires reachable capability discovery; no Data Layer
  bypass or production NodeClient/connectedNodes fallback. Debug diagnostic use is separate.
- No ConnectivityManager observer, foreground service, Ongoing Activity, WorkManager, wake lock or
  production keep-screen-on behavior.
- WatchProfiles preserves durable refreshPending, bounded NonCancellable rotation
  and atomic replacement; uncertain consumed grants remain fail-closed.
- ResponseEvents/StreamResult require response.completed for success;
  ChatConversation commits only completed exchanges. Failure/cancel/interruption
  excludes pending exchanges from future context; cancellation reaches HTTP transport.
- Scenario Activity/classes/names/ACK/action are absent from release manifest/DEX;
  production source references and fixture storage/network dependencies are rejected.
- Signed-out authenticated account name/ID is no longer presented as current,
  verified by the fixed-APK physical P9 retest. Durable installation/registration
  identity remains intact internally for normal reauthentication.

Best-effort power management remains intentional; destructive platform interruption
durability and full RC acceptance are not inferred from targeted physical success.

## 13. Known debt

- Historical Terra -> Sol physical restoration anomaly: unreproduced and unresolved;
  current P6 physically verified GPT-5.6 Luna only.
- Historical transient reauthorization anomaly remains unresolved and was not
  reproduced during current physical acceptance.
- Natural real-token refresh while phone is absent: not physically observed;
  automated watch-only refresh coverage passes. Do not force token expiry.
- Funded BYOK success: unverified because no funded API credits are available;
  external constraint accepted, no purchase required.
- Paired-emulator CapabilityClient synchronization: existing GMS/emulator limitation;
  production physical Data Layer handoff works. No investigation reopened.
- Destructive Android Keystore / AtomicFile interruption behavior: intentionally
  not physically tested; synthetic tests do not prove platform interruption durability.
- Terra-specific restoration physical retest: deferred unless naturally useful later.

These are retained known debt, not blockers to closing Day 4.

## 14. Deferred RC items

Retain release-build hardware smoke/signing compatibility, full reboot/platform
storage durability, actual cancelled-exchange server-context exclusion and model-change
context boundary checks, remote revocation outcome, and funded BYOK success when an
external credential is available. Distinguish these from this debug hardware matrix.
No forced expiry, destructive storage test or funded-credit purchase is required.
Reauthorization debt needs explicit disposition before freeze; compilation cannot close it.

## 15. Final closure and Day 5 handoff

Day 4 is complete with PASS WITH KNOWN DEBT. Recommended next focus:
**DAY 5 — release / RC hardening**. Defer the exact D5 phase plan to a fresh
Day 5 read-only audit; no Day 5 implementation or optional voice work has started.
The deferred RC evidence above is distinct from the completed Day 4 gate.

Final closure changed DAY4_REPORT.md, DAY4_STATUS.md and ROADMAP.md only.
DECISIONS.md remains consistent: D12 retains durable registration identity, while
D22/D23 preserve best-effort lifecycle and memory-only request ownership.
DAY3_REPORT.md remains a historical record and was not changed. No new architecture
decision, code/test edit, automated/emulator rerun, device action, commit or push.

Documentation consistency: P1–P10 accepted within their stated scope; P6 is Luna
only; P9 passes after remediation without claiming the historical reauthorization
anomaly fixed; historical counts retain phase attribution. Closure git diff and
changed/new-file whitespace checks PASS.

Suggested commit: `docs: close Day 4 acceptance with known debt`.

# D4-H — narrow physical sign-out remediation — 2026-10-29

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED PHYSICALLY.**
D4-H's stale account presentation fix passed the owner's fixed-APK Galaxy Watch 7
retest. P9 is PASS — PHYSICAL AFTER REMEDIATION; its earlier failure remains defect
history. Current acceptance is DAY4_REPORT.md section 11. The historical transient
reauthorization anomaly remains unresolved and was not reproduced during current
physical acceptance. Day 5 remains unstarted. No device action, commit or push
was performed by the agent during final documentation closure.

## B–C. P1 classification and architecture

**PASS — PHYSICAL, WITH INTERMITTENT PRIOR INTERRUPTION NOT REPRODUCED.**
Background continuation is best effort but repeatedly worked on physical Galaxy
Watch 7: four successful later attempts, including two approximately 30-paragraph
Linux scheduler responses, after more than 10 seconds on the watch face. One
prior interruption safely retained partial text, marked interrupted, excluded the
exchange from future context and never replayed. No confirmed lifecycle defect.
No lifecycle production change, foreground service, Ongoing Activity, WorkManager,
wake lock, screen-off policy or background ownership change. None is justified by
current evidence. P1 requires no further stress testing in this pass.

## D–F. Root cause, fix and contract

Exact source: WatchProfiles.signOut retains encrypted profile metadata by design;
WatchProfiles.publish previously mapped every retained profile to name/email plus
client-ID suffix, including signOutPending/signOutResults registrations.
WatchAuthViewModel.accounts directly exposes that state; ConnectionItems rendered
all labels, while ShellPresentation independently resolved the selected label
without checking authentication. This was repository publication plus presentation
eligibility, not cached Compose state or CredentialRouter credential availability.

Before production edits, SignOutPresentationTest deterministically failed with
`expected null, but was:<Synthetic · nt_one>` after real repository sign-out and
router/ViewModel transition to REAUTHORIZE. Logs: /tmp/d4h-red.log. The original
single integration test then passed after the production fix (/tmp/d4h-green.log).

Small production fix:
- Publish “Signed-out ChatGPT account” for pending/completed/blocked sign-out
  registrations, without name/email/client-ID suffix. This applies to every
  registration, including non-selected signed-out entries. Keep its internal
  selection key so the normal same-registration reauthorization path remains.
- Present current account summary only when connected, not signing/signed out,
  not requiring reauthorization, and without protected-storage error.
- Show selected model only when the selected billing route is available, so a
  retained catalog snapshot cannot suggest a signed-out usable model.

After successful local sign-out: old name/ID absent from current presentation,
Continue with ChatGPT available, plan models/catalog/inference unavailable, and
explicit billing unchanged even with a configured API key. Repeated sign-out is
idempotent. Reconstruction keeps presentation signed out. Normal phone OAuth and
handoff restore current presentation, catalog and explicit inference. No OAuth,
protocol, handoff, router, credential storage or lifecycle implementation changed.

## G. Automated verification — preserved D4-H run

| Check | Exact result |
| --- | --- |
| Full Wear JVM suite, explicitly rerun | 343 tests / 45 suites, 0 failures/errors/skips |
| New SignOutPresentationTest | 2 passed (included in 343) |
| ShellPresentationTest | 12 passed, including 1 new transition guard (included) |
| WatchSignOutTest / OpenAiRevocationTest | 13 / 5 passed (included) |
| TransitionTest / ErrorTransitionTest | 12 / 2 passed (included) |
| WatchProfilesTest / CredentialRouterTest | 15 / 4 passed (included) |
| D4-A LifecycleOwnershipTest | 8 passed (included) |
| D4-B ProcessReconstructionTest | 11 passed (included) |
| D4-D PhoneIndependenceTest | 6 passed (included) |
| D4-E PersistenceRecoveryTest | 13 passed (included) |
| ModelRestorationIntegrationTest / WatchRefreshTest | 15 / 21 passed (included) |
| Wear assembleDebug / assembleRelease | Both PASS |
| Wear lintDebug | 0 errors/fatals, 6 existing warnings |
| Release source/manifest/DEX scenario isolation | PASS |
| git diff --check / changed and new file whitespace | PASS |
| Mobile JVM suite | Not rerun: no shared/mobile changes |
| Python unit tests / syntax | Not rerun: no tooling changes |

The 3 new tests cover authenticated name/ID before sign-out; clearing both after
sign-out; Continue; model/catalog gating including an intentionally retained old
snapshot; blocked plan lease and inference; no API fallback despite configured key;
idempotence; encrypted-record reconstruction; retained registration and absent
ID-token hint; reauthentication presentation/catalog/inference; stale generation
rejection; pending sign-out plus an independent selectable authenticated account.
Existing account/mode revision, late callback, sign-out/revocation and lifecycle
safeguards passed in the full suite. These are synthetic JVM results, not physical
or emulator acceptance, and no new emulator/device run is claimed.

Commands actually run (existing JDK/cache/signing configuration):

```sh
export JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9"
export ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android"
./gradlew :wear:testDebugUnitTest --tests '*SignOutPresentationTest' --console=plain
# Above executed before and after production fix; expected initial regression failure.
./gradlew :wear:testDebugUnitTest --rerun :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
git diff --check
```

XML parsing counted tests and lint severities; an explicit changed/new-file scan
checked trailing whitespace, conflict markers and final newline. Logs and full
per-suite counts: /tmp/d4h-validation.log and /tmp/d4h-counts.txt (temporary).
The first restricted Gradle attempt could not write its existing cache lock;
authorized host execution succeeded. No cache migration or signing change.

## H. Security and identity review

Current authenticated account presentation is conditional UI state. After sign-out,
it must not expose the old name or ID as authenticated. The neutral registration
row explicitly says signed out. Internal client keys remain for selection/routing;
they are not rendered in that row. Retained encrypted display metadata is unchanged.

Durable installation/registration identity is host ID plus issued client and
verified issuer/subject. It must survive sign-out for normal protocol continuity.
No host/client/key deletion, migration, new storage format or token handling change.
Tests verify preserved client registration/no ID-token hint and old lease rejection.
No API fallback, sensitive logs, production diagnostic changes, Data Layer bypass,
conversation/request persistence, inference replay or remote service change.
DECISIONS.md unchanged: D12's durable identity retention remains correct; this is a
presentation bug fix within that existing architecture.

## I. Historical D4-H implementation files changed

- wear/src/main/kotlin/io/github/grassified/wristprompt/auth/WatchProfiles.kt
- wear/src/main/kotlin/io/github/grassified/wristprompt/ui/ShellPresentation.kt
- wear/src/test/kotlin/io/github/grassified/wristprompt/openai/SignOutPresentationTest.kt (new)
- wear/src/test/kotlin/io/github/grassified/wristprompt/ui/ShellPresentationTest.kt
- DAY4_STATUS.md
- DAY4_REPORT.md
- ROADMAP.md (acceptance updates only)

## J. Fixed-APK physical retest — complete

**P9 — PASS — PHYSICAL AFTER REMEDIATION.** Owner-reported retest confirms previous
account information disappears, authenticated presentation clears, and Continue
with ChatGPT appears. Physical-phone OAuth completes; credentials return to watch,
current account presentation returns, models load and real inference succeeds.
Historical transient reauthorization anomaly remains unresolved and was not
reproduced during current physical acceptance. No additional Day 4 physical retest
or P1 stress testing is required.

## K. Remaining known debt

Historical Terra → Sol anomaly remains unreproduced/unresolved; P6 proves exact
GPT-5.6 Luna restoration only, not Terra. Historical transient reauthorization
anomaly remains unresolved and was not reproduced during current physical acceptance. Natural
watch-only real-token refresh is unobserved physically. Funded BYOK success remains
externally blocked. Emulator/GMS CapabilityClient limitation is unchanged and was
not reopened. Destructive Keystore/AtomicFile interruption tests were not performed.
Full reboot, release-on-hardware, remote revocation and actual server-context
boundary checks remain deferred RC scope, not extra required D4-H physical retests.

## L. Historical D4-H suggested commit message

`fix(wear): hide retained account identity after ChatGPT sign-out`

No commit or push performed. Day 5 remains unstarted.
