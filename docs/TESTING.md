# Testing and evidence

Use the pinned JDK/SDK and wrapper described in [../README.md](../README.md).
Focused tests come first, followed by one appropriate complete validation pass at
stabilization. Commands below are available workflows, not newly executed evidence.

## Automated checks

```sh
./gradlew :wear:testDebugUnitTest
./gradlew :mobile:testDebugUnitTest
./gradlew :wear:testDebugUnitTest --tests 'io.github.grassified.wristprompt.openai.ChatConversationTest'
./gradlew :wear:lintDebug :mobile:lintDebug
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew :wear:assembleRelease :mobile:assembleRelease
python3 -m unittest discover -s tools -p 'test_*.py'
git diff --check
```

Shared tests compile into both apps. Use `--tests` for the affected actual suite.
Release output is currently unsigned/unminified; compilation is not release readiness.
No stable binaries or signing keys should be uploaded by this workflow.

## Synthetic emulator scenarios

Use a disposable test lab with the named AVDs `WristPrompt_Watch7` and, for paired
Data Layer investigation, `WristPrompt_Phone`. Runners discover and validate the
watch; do not hardcode owner device serials. Install matched-signing debug APKs.

```sh
python3 tools/wear_scenarios.py --list
python3 tools/wear_scenarios.py --list --category markdown
python3 tools/wear_scenarios.py --install --smoke
python3 tools/wear_scenarios.py --scenario MULTI_TURN_CHAT
python3 tools/wear_scenarios.py --all
python3 tools/check_scenario_isolation.py
python3 tools/wear_process_restart.py --all --method am-kill
python3 tools/wear_process_restart.py --all --method force-stop
python3 tools/wear_connectivity.py
python3 tools/wear_phone_absence.py
python3 tools/emulator_data_layer.py
```

`wear_catalog.py` is metadata imported by the runner, not a standalone catalog CLI.
Isolation checks require both Wear debug and release APKs already built. It verifies
source/manifest/DEX separation. Process-restart commands kill/force-stop the verified
emulator app; connectivity/phone-absence runners also reconstruct synthetic fixtures
in fresh emulator processes. They do not prove radio, live credentials or hardware
behavior. Do not run these against physical hardware. See
[../WEAR_SCENARIOS.md](../WEAR_SCENARIOS.md) for readiness ACK/PID/lifecycle semantics.
77/77 ready fixtures does not mean 77 visual reviews.

Paired-emulator nodes/RPC work, while reciprocal CapabilityClient discovery remains
unresolved: **NOT VERIFIABLE ON CURRENT EMULATOR LAB** for capability-dependent auth.
Keep production gating. See [../EMULATOR_CAPABILITY_INVESTIGATION.md](../EMULATOR_CAPABILITY_INVESTIGATION.md).

## Evidence and physical acceptance

- VERIFIED AUTOMATICALLY: unit tests, static checks, lint and builds.
- VERIFIED ON EMULATOR: synthetic fixture/lifecycle/interaction/performance results.
- VERIFIED PHYSICALLY: explicit owner/device results, with scope and limitations.
- PENDING END-OF-DAY PHYSICAL TEST / BLOCKED: preserve the reason and dependency.

D5-F's recorded baseline is in [../DAY5_F_REPORT.md](../DAY5_F_REPORT.md),
[../DAY5_F_METRICS.json](../DAY5_F_METRICS.json) and [../DAY5_STATUS.md](../DAY5_STATUS.md).
Do not substitute old runs for newly executed checks. Physical D5-G remains pending;
the owner will supply P1–P13. Current repository has grouped physical checklist areas,
not an already recorded numbered P1–P13 result set. Preserve the owner's numbering
when received. Update DAY5_STATUS.md, create DAY5_G_REPORT.md and update ROADMAP.md
only with supplied evidence. Recommend remediation for reproducible material defects.

Non-blocking physical checks are batched. Never clear real app data, uninstall owner
apps, revoke sessions, corrupt credentials/Keystore or intentionally exhaust usage
without explicit authorization for that exact action. A new Codex session must not
fabricate owner results or start D6 while waiting.

For documentation/license/ignore-only edits, run repository hygiene, secret/privacy,
license, links and whitespace checks. Do not rerun 459 tests and 77 emulator fixtures
merely to validate prose. Broaden validation if production/build behavior changes.
