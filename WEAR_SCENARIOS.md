# Offline Wear scenarios — Day 3 Phase F / Day 4 Phases A–B

The explicitly launched `DebugScenarioActivity` replaces `StreamingPreviewActivity`.
All 31 fixtures live under `wear/src/debug`; the Activity is declared only in the
debug manifest overlay. There is one scenario entrypoint and one production UI.
`ScenarioOwner` retains a fixture and its `InferenceViewModel` across Activity
recreation, starts preparation once, and clears that VM through its own ViewModelStore when finally cleared.
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
contains safe launch/scenario/state metadata and the counters described below. The launcher
requires its matching acknowledgement plus a live app process within 20 seconds.
It does not read logs, credentials or chat payloads. Smoke runs all 31 states;
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
| ACTIVE_STREAM_RECREATION | One gated stream; automatic retained Activity recreation; production Stop and renderer |
| BACKGROUNDED_STREAM | One gated stream; Home allowed; latest output observed on foreground return |
| SCREEN_OFF_STYLE_RESUME | Gated stream with no debug keep-screen-on or turn-screen-on request; emulator sleep/resume |
| COMPOSER_RECREATION | Empty production Composer; open system text input and recreate the host; returned text deliberately discarded |
| PROCESS_RESTART_IDLE | Fresh fixture is empty; explicit SEED selects debug-2 in memory; restart returns to default debug-1 |
| PROCESS_RESTART_AFTER_COMPLETED | Explicit SEED creates one completed synthetic exchange; a new fixture never seeds itself |
| PROCESS_RESTART_DURING_ACTIVE | Explicit SEED starts one request before any delta; suspended until owner/process ends |
| PROCESS_RESTART_AFTER_PARTIAL | Explicit SEED starts one request and emits a partial; suspended until owner/process ends |

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


## Day 4 lifecycle controls

The three streaming lifecycle fixtures emit `Lifecycle chunk 1. `, then wait on
an in-memory channel, without timer-based races. Their visible round-screen
diagnostic shows `G` (generation count), `M` (model discoveries), stage and partial
character count. ADVANCE emits the next numbered chunk; COMPLETE emits one terminal
event. Use real Stop in the renderer to cancel. They perform no network requests.
Readiness still means ready, not completed or visually accepted.

An exported **debug-only**, Activity-lifetime receiver accepts fixed commands only
for these fixtures, composer recreation, and the process fixtures described below. It checks the launcher's fresh
`launch_id`; no production Activity registers it. Select only the emulator serial
that reports AVD `WristPrompt_Watch7` and watch characteristics. With the current
launch identifier from the acknowledgement:

```sh
adb -s EMULATOR_SERIAL shell am broadcast \
  -a io.github.grassified.wristprompt.DEBUG_LIFECYCLE \
  -p io.github.grassified.wristprompt \
  --es launch_id LAUNCH_ID --es command ADVANCE
```

Other accepted fixed commands are COMPLETE, RECREATE and STOP. Commands never
select credentials, dispatch real inference, access Data Layer or modify stores.
The acknowledgement additionally reports discoveries/completions, partial/draft
character counts, message count, retained owner UUID, Activity creation count,
STARTED-collection status, last observed partial length, lifecycle and window flag.
It contains no text content or credentials. Destroyed hosts cannot overwrite it.
The observation diagnostic uses repeatOnLifecycle(STARTED); the production
renderer continues using collectAsStateWithLifecycle. A paused observer can lag
behind the VM, then must match the current state on resume.

Use Home to background; return to the existing instance with Activity reorder-to-
front, not a fresh scenario launch (which intentionally starts a new owner).
For SCREEN_OFF_STYLE_RESUME, emulator KEYCODE_SLEEP/KEYCODE_WAKEUP can exercise
display transitions where supported. No production power-management guarantee
follows from this. Other fixtures retain the useful debug keep-screen-on behavior.

COMPOSER_RECREATION starts on Compose. Tap Write message, recreate the underlying
scenario Activity with the fixed command while platform input is open, then return
text. The restored surface says input was interrupted; draft remains empty and
there is no generation. Write again, review the returned draft and explicitly Send.
No token or draft text is saved across Activity recreation by the UI. A draft
already delivered before recreation remains in the retained ViewModel.

Current D4-A/D4-B dispositions and physical debt are in DAY4_STATUS.md. D4-C
network transitions remain separate, unstarted work.


## Day 4 process-restart controls

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/wear_process_restart.py --all --install
PYTHONDONTWRITEBYTECODE=1 python3 tools/wear_process_restart.py --scenario PROCESS_RESTART_AFTER_PARTIAL
PYTHONDONTWRITEBYTECODE=1 python3 tools/wear_process_restart.py \
  --scenario PROCESS_RESTART_DURING_ACTIVE --method force-stop
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_wear*.py'
```

The process runner shares watch discovery with wear_scenarios.py and additionally
requires the exact existing AVD name WristPrompt_Watch7. Physical serials,
ambiguous watches, another AVD and non-debuggable packages fail before process
mutation. No Android Studio, pairing, data clear, uninstall or credential access.

Each process scenario initially prepares only a fresh catalog and empty VM. One
explicit SEED broadcast (same fixed debug receiver and fresh launch identifier)
creates its pre-death condition. SEED is memory-only, idempotent within a fixture,
and **never** called during startup/relaunch. The old fixture is not serialized.
No "after restart" marker, request or previous PID is written into app storage.

Default --method am-kill backgrounds the Activity, observes CREATED/below STARTED,
uses `am kill`, requires the application PID to disappear, then reorders the
existing Android task to the foreground. Android restores the Activity's original
launch intent and saved framework state; a new process constructs new fixtures
and owners. The runner requires a different PID, process-owner UUID and
ScenarioOwner UUID, and checks EMPTY, zero messages/partial/draft/generations,
READY fresh catalog, no busy/pending input, and CHAT surface. It does not fall
back to force-stop if ActivityManager refuses to kill the background process.

Explicit --method force-stop is a **stronger, separately labelled** package-stop
check. It uses a new explicit cold launch after force-stop. It is not evidence for
every low-memory process-death condition. Neither method proves watch reboot or
hardware Keystore behavior. Process death does not establish server-side
completion/cancellation or usage; no previous generation is ever replayed.

The safe acknowledgement adds PID, process owner, chat busy, proof stage, selected
synthetic model slug, synthetic billing mode, current surface and pending-input
marker. No account IDs, text, credential records, authenticated bodies or headers
are reported. CLI output includes old/new PID and boolean owner-change evidence,
not account identity. Scenarios remain absent from release manifest/DEX.

Synthetic billing/model values are **not production persistence evidence**:
PROCESS_RESTART_IDLE intentionally changes debug-2 back to debug-1 in the fresh
fixture. Real durable account/billing/model/credential reconstruction is covered
separately by ProcessReconstructionTest and the D3-B encrypted-record integration
fixture. No debug scenario constructs a real repository or reads installed tokens.

D4-B moved shell navigation and the interruption marker from rememberSaveable to
an Activity-owned UI ViewModel. They survive retained recreation, but Android's
saved Bundle cannot restore them into a new process. Composer request tokens
still belong only to their composition, so retained recreation discards returned
text with guidance; a fresh process starts without the interruption marker/token.
