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

## End-of-day physical acceptance — single procedure

Use only [DAY2_PHYSICAL_ACCEPTANCE.md](DAY2_PHYSICAL_ACCEPTANCE.md) for the
remaining Galaxy Watch 7 + paired-phone session. It consolidates the earlier
Phase C–G checklists; do not execute those historical lists as additional tests.
D2-1 remains PASS — VERIFIED PHYSICALLY and is not repeated.

The procedure uses no watch reboot or phone power cycle, two targeted app-process
restarts for the new billing-mode setting, fake-key testing before one real-key
entry, and one planned same-account reauthorization. Natural refresh is
conditional on real token state; offline sign-out is optional because it adds
another recovery login. Exact prerequisites/actions/expected results/evidence
and PASS/FAIL criteria, plus the paste-back template, are in that one document.
No physical result is added by preparing this procedure.

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
remain PENDING END-OF-DAY PHYSICAL TEST in DAY2_PHYSICAL_ACCEPTANCE.md. No real session
was signed out/revoked, and no data/key/network action was performed by the agent.
No BYOK, Day 3 chat, model-restoration fix, commit or push. Earlier Phase C pending
changes remain preserved.

## Day 2 Phase E — BYOK

Implementation scope and security/test matrix are in [BYOK_REVIEW.md](BYOK_REVIEW.md).
Two independent modes are now explicit: CHATGPT_PLAN and OPENAI_API_KEY. Protected
receipt of an API key does not activate API billing. Only a watch mode button
changes the persisted active mode; errors and credential removal never fall back.
OAuth registration/token/host storage is unchanged. No real API key or credential
was used by automated tests, and no physical operation was performed by the agent.

### Combined end-of-day checks

Superseded for execution by [DAY2_PHYSICAL_ACCEPTANCE.md](DAY2_PHYSICAL_ACCEPTANCE.md).
BYOK, billing-mode visibility/persistence, failure recovery, cancellation,
sign-out and registration reuse remain pending physical results. Precise replay,
Keystore-loss and storage-fault tests retain their synthetic evidence; never
provoke destructive key loss on the real watch.

### Phase E final validation

VERIFIED AUTOMATICALLY: 161 Wear tests and 64 mobile tests pass, with zero
failures/errors/skips. Both assembleDebug builds and both lintDebug tasks pass;
zero lint errors, three existing warning categories per app. Both APKs verify
with the unchanged Day 1 certificate SHA-256
`fd8491b6238368a04d58bd0653c35ff54a3a17fc9b31e53a6af7110579d37061`.
Git diff whitespace validation passes; no private signing/encrypted/local.properties
artifact is tracked or stageable. Full commands and changed-file manifest are
in BYOK_REVIEW.md. No real-key requests/device tests, commit or push were run.

Gradle still emits the existing missing SDK Platform-Tools/license warning;
unit tests, compilation, lint and APK assembly do not require that package and
succeeded. No SDK/license/signing configuration was modified. Physical Data Layer,
protected storage and billed BYOK acceptance remain pending as listed in DAY2_PHYSICAL_ACCEPTANCE.md.

## Day 2 Phase F — connection and billing clarity

VERIFIED AUTOMATICALLY: Wear connection/settings groups safe ChatGPT account
metadata, plan availability/usage, Manage usage and sign-out independently from
API-key configured state, change/remove actions and separate API billing. The
persisted active mode is fixed above the scrolling list; credentials do not
choose it. The selector remains available even when a selected mode has no
credential, so missing-key/sign-out states remain explicit without fallback.
Proof controls are hidden if their catalog mode does not match settings.

The companion remains setup-only, with an explicit billing explanation, short
BYOK notice, secure transfer/retry/result states and a direct ChatGPT usage URL.
The watch retains its instructional usage hook; automatic browser launch from
watch to phone is deferred. The phone text field is process-memory only and is
now cleared once the bounded transfer envelope is prepared, then remains cleared
on ACK; retry uses only the existing expiring envelope. No SavedStateHandle,
plaintext persistence, OAuth/storage/signing/protocol redesign or migration.

Model preferences remain per ChatGPT registration and separately per API key.
A new mode-switch regression rejects a stale preference absent from the current
mode's catalog and restores independent preferences on subsequent switches.
The Terra → Sol physical restart defect is still deferred to Day 3.

Final command actually run (existing JDK/SDK environment from BYOK_REVIEW.md):

```sh
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:lintDebug :mobile:lintDebug :wear:assembleDebug :mobile:assembleDebug
git diff --check
```

Result: BUILD SUCCESSFUL; 162 Wear and 64 mobile tests, zero failures/errors/skips.
Both debug APKs assemble; both lint tasks pass with zero errors and the same three
prior warning categories per app. The initial sandboxed attempt could not write
an external Gradle cache lock; the authorized validation rerun succeeded. No SDK,
dependency, signing or external-service changes. No device/emulator test, real
credential action, commit or push. Physical acceptance remains PENDING END-OF-DAY
PHYSICAL TEST in DAY2_PHYSICAL_ACCEPTANCE.md; Day 2's physical gate is open.

Phase F changed files (in addition to preserved earlier uncommitted work):
- wear MainActivity.kt, openai/InferenceProofUi.kt, res/values/strings.xml,
  openai/InferenceViewModelTest.kt.
- mobile MainActivity.kt, auth/ApiKeySetupUi.kt, auth/PhoneApiKeyViewModel.kt,
  auth/PhoneApiKeyTest.kt.
- DAY2_STATUS.md, DECISIONS.md and ROADMAP.md.

Suggested commit: `feat: clarify connection settings and explicit billing modes`.

## Day 2 Phase G — targeted failure matrix

Audit, source references, all eleven case results, changed-file manifest and
security findings: [DAY2_FAILURE_MATRIX.md](DAY2_FAILURE_MATRIX.md).
The fixes close late phone result publication, expired retry state, stale proof
retargeting across account/billing contexts, and current plan-error diagnostic
classification. No authentication redesign or new product feature.

VERIFIED AUTOMATICALLY: final required Gradle validation passed with 172 Wear
and 69 mobile tests, zero failures/errors/skips. Both debug builds and both lint
tasks pass, with zero lint errors and the same three prior warning categories
per app. git diff --check passes. Fifteen new test methods expand deterministic
fake-transport/store/clock failure coverage. The source security search found no
secret logger or plaintext secret persistence; only allowlisted BridgeLog output.

No physical/emulator action, real OpenAI request, paid usage, quota manipulation,
credential deletion/revocation, commit or push. Existing end-of-day checks remain
pending, with disconnect/cancellation/stale-result checks consolidated in DAY2_PHYSICAL_ACCEPTANCE.md.
Existing earliest_refresh_at interpretation remains BLOCKED; no new Phase G
hardware blocker. Terra → Sol, Day 3 chat and voice remain deferred.

Suggested commit: `fix: close Day 2 auth and inference failure matrix`.

## Consolidated physical procedure preparation

VERIFIED AUTOMATICALLY: git diff --check and product/test/build-source hash
comparison. Only this status document and DAY2_PHYSICAL_ACCEPTANCE.md changed in
this task. No source edits, builds/tests/lint rerun, device actions, real requests,
commit or push. Earlier automated and physical results retain their attribution.
