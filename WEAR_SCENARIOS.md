# Offline Wear scenarios — Day 3 Phase F

The explicitly launched `DebugScenarioActivity` replaces `StreamingPreviewActivity`.
All 23 fixtures live under `wear/src/debug`; the Activity is declared only in the
debug manifest overlay. There is one scenario entrypoint and one production UI.
`ScenarioOwner` retains a fixture and its `InferenceViewModel` across Activity
recreation, starts preparation once, and cancels that VM when finally cleared.
This simulates no process persistence: a new task/owner starts fresh memory.

`DebugScenarios` owns synthetic account/billing display metadata, a synthetic
`AiProvider`, an in-memory access flow and the production `InferenceSession` /
`InferenceViewModel`. Chat is built by real draft/send/delta/completed/stop calls,
so completed-only replay is the production `ChatConversation`, not copied UI data.
No HTTP client, store, credential repository, bridge or Application dependency is
constructed. The required transient `CredentialLease` has an **empty bearer**;
it is not a persisted `CredentialProfile`, `ApiKeyCredential` or OAuth token set.
Model reads/writes use the VM's default memory-only/no-op callbacks. Changing a
synthetic model cannot modify the installed account's model preferences.

The production `WearShell` wrapper still passes its original state and actions.
Its extracted `WearShellContent` and connection callbacks let debug supply display
state and inert account/billing actions. Chat/model/Stop actions still call the
real VM. No alternate fake screen, production scenario switch, routing fallback,
authentication change, storage change or network request is involved.

## Commands

Run from the repository root with the SDK/JDK environment in DEVELOPMENT.md:

```sh
./gradlew :wear:assembleDebug
python3 tools/wear_scenarios.py --list
python3 tools/wear_scenarios.py --scenario STREAMING_LONG
python3 tools/wear_scenarios.py --scenario ERROR_429 --screenshot /tmp/error_429.png
python3 tools/wear_scenarios.py --install --scenario EMPTY_CHAT
python3 tools/wear_scenarios.py --smoke
```

`--install` updates the existing debug APK using `install -r`; never uninstalls or
clears data. Without it, the package must already be installed and debuggable.
ADB is resolved through ANDROID_HOME (default ~/Android/Sdk). Discovery considers
only online `emulator-*` serials and checks `ro.build.characteristics` for `watch`;
exactly one watch must exist. Its AVD name is printed. Physical devices and phone
emulators are never selected; boot order and serial numbers do not matter.
Unknown names, no/ambiguous watches, missing/release packages, launch errors and
missing fresh ready acknowledgements fail closed. No pairing/association is needed.

Every launch clears the previous Activity task, not app data, and supplies a fresh
32-character random launch identifier. The Activity accepts only a known enum and
a valid identifier. A fixed app-private `debug-scenario-ready.txt` acknowledgement
contains only that identifier, enum, generation count and chat stage. The launcher
requires its matching acknowledgement plus a live app process within 20 seconds.
It does not read logs, credentials or chat payloads. Smoke runs all 23 states;
it proves readiness/liveness, **not visual acceptance**. Optional screenshots use
ADB screencap; there is no OCR, image comparison or accessibility framework.

## Fixtures

| Public name | Production path / initial surface |
| --- | --- |
| SIGNED_OUT | Inaccessible inference; Connection/settings, inert sign-in action |
| CHATGPT_AUTHENTICATED | Synthetic connected account, explicit plan billing, ready chat |
| API_KEY_CONFIGURED | Synthetic configured display state, explicit API billing, ready chat |
| MODELS_LOADING | Provider catalog suspended until cancellation; real LOADING/Stop |
| MODELS_LOADED | Three models, production picker |
| MANY_MODELS | Forty models, production picker |
| MODEL_UNAVAILABLE | NO_MODELS failure, selection cleared, Send blocked; Reload succeeds synthetically |
| EMPTY_CHAT | Real empty conversation/composer entry |
| LONG_USER_PROMPT | 8,192-character draft, at the inclusive product input bound; Composer |
| ONE_TURN_COMPLETED | One production completed exchange |
| MULTI_TURN_CHAT | Four completed exchanges, replay input sizes 1/3/5/7 |
| LONG_ASSISTANT_OUTPUT | Eighty completed sections |
| STREAMING_SHORT | Three sections emitted in bounded chunks at 400 ms intervals |
| STREAMING_LONG | Eighty sections emitted through the same real streaming/session path |
| STOPPED_PARTIAL | Real send/delta/Stop; partial retained, user excluded, next send available |
| ERROR_401_REAUTH | Production bare plan 401 mapping: PERMISSION; account/plan permission guidance |
| ERROR_401_API_KEY | Production API-mode 401 mapping: INVALID_API_KEY, no fallback |
| ERROR_429 | Production plan USAGE_LIMIT/Manage usage guidance; no reset timestamp |
| ERROR_503 | Production TEMPORARY guidance; account state retained |
| ERROR_OFFLINE | Production NETWORK category; explicit compose/send retry path |
| LONG_ERROR_MESSAGE | TEMPORARY failure plus repeated static production recovery copy, chunked by shared renderer |
| RECREATED_COMPLETED_STATE | Completes once, automatically recreates Activity with same retained owner |
| RECREATED_CANCELLED_STATE | Stops once, automatically recreates; cancellation/partial remain, no new generation |

ERROR_401_REAUTH is the requested public name, **not** a new claim that a bare
inference 401 proves expired OAuth. D3-E deliberately keeps that case PERMISSION.
Terminal renewal/reauthorization remains a different production category. Synthetic
connection/account/key controls intentionally do nothing; they cannot launch real
OAuth, change modes, sign out, delete secrets or send Data Layer requests.
LONG_ERROR_MESSAGE repeats safe existing guidance, never an arbitrary server body.

## Isolation and regression checks

```sh
./gradlew :wear:testDebugUnitTest --tests '*DebugScenariosTest'
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_wear_scenarios.py'
python3 tools/check_scenario_isolation.py
```

The isolation checker verifies the debug/release merged manifests and APK DEX:
scenario Activity/classes must exist in debug and be absent from release. It
rejects production-source references to either scenario entrypoint and harness
references to secure records/repositories, serializable real credentials, HTTP
clients and durable preferences. The Application's production services are lazy;
the scenario host never accesses them. The only scenario disk write is its fixed
non-secret acknowledgement. Production capabilities/listeners/routing are untouched.
Release assembly produces an unsigned APK for inspection, not a signed release.

Final consolidated evidence/counts and the per-scenario acceptance matrix are in
DAY3_REPORT.md; phase-local history remains in DAY3_STATUS.md, Phase F. No physical verification, real OAuth, OpenAI request or
funded BYOK success is established. Touch scrolling was checked at default emulator
font/display settings; rotary, larger fonts and real-network behavior remain separate.

Production capability-dependent authentication remains:
**NOT VERIFIABLE ON CURRENT EMULATOR LAB**.
