# Day 4 status — 2026-10-29

**Current D4-B result: PASS WITH KNOWN DEBT.** See the D4-B section below for
303 Wear / 16 Python tests and actual emulator PID-change acceptance. D4-C is
unstarted. The Phase A sections retain the 962452c handoff evidence and counts.

# D4-A handoff — historical phase record

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY; emulator matrix below.**
At the D4-A handoff, only lifecycle work was implemented. D4-0 was supplied as
complete; D4-B and D4-C were unstarted at that handoff. No physical test,
credential operation, commit, push, publication or signing change was performed.
The starting working tree was clean.

## B. Final lifecycle contract

| Event | Contract |
| --- | --- |
| Recomposition | Only renders state; no operation ownership change, resend or duplicated delta. |
| Retained Activity recreation | New Activity/composition, same ViewModelStore/InferenceViewModel/conversation/Job. Active generation continues once; Stop remains available. Completed/cancelled state is stable. |
| Home / brief background | Collection below STARTED pauses; normal inference is not automatically cancelled. No lifecycle-triggered model reload, credential selection or generation. |
| Foreground return | Observe the latest immutable StateFlow value. No automatic replay; cached message rows are keyed to the current message list. Partial text is rendered from current ChatState. |
| Screen-off-style transition | Same retained-process policy; best effort, without uninterrupted-execution guarantees or background infrastructure. Resume presents current/completed/cancelled/failed state without replay. |
| Final ViewModel clear | Scope cancellation reaches the HTTP Call, unblocks SSE and closes the body. onCleared synchronously invalidates publication/input ownership and excludes an unfinished exchange. No late completion/history publication. |

No model/account/credential switch is caused by these lifecycle events. Existing
account/generation/mode-revision changes still invalidate incompatible work.

## C. Baseline defects and gaps

- **Reproduced automatically before production edits:** the new final-store-clear
  test failed: immediately after actual ViewModelStore.clear(), chat still showed
  RECEIVING/PENDING while coroutine cancellation was queued. Seven other initial
  lifecycle tests passed; eight ran. Final clear now synchronously marks CANCELLED
  and EXCLUDED and invalidates the epoch, with no later state publication.
- **Composer gap confirmed in source:** pending inputToken was ordinary remember
  state, lost on recreation, while the platform Activity Result registration can
  deliver to a new Activity. Original UI also reset to chat, with no interruption
  explanation. This was a result-handling gap, not demonstrated duplicate sending.
  The chosen explicit-discard contract and platform exercise are recorded below.
- No reproduced background cancellation, resend, duplicate chunk, model reload or
  billing switch defect required changing generation/transport/session ownership.
- During emulator validation, a prior debug host's delayed lifecycle callback could
  overwrite the next launch's fixed acknowledgement. A debug Activity report epoch
  and destroyed-host guard now reject that write; this was diagnostic-only.

## D. Ownership map

Production chain: MainActivity -> Activity ViewModelStore -> InferenceViewModel ->
viewModelScope operation Job -> shared InferenceSession -> OpenAiProvider ->
OpenAiHttp -> per-request OkHttp Call -> Response/body/SSE.
The application shares the session/provider/HTTP objects; it does **not** own the
inference operation Job. Sharing these dependencies is pre-existing behavior.

Legend: S = survives, R = recreated, P = UI observation pauses, C = cancelled or
closed, F = final ownership ends. All survival assumes the process remains alive.

| Owner/resource | Recompose | Retained recreation | Home | Background | Foreground | Screen-off-style | Activity finish | Final VMStore clear |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Activity | S | R | S/stopped | S/stopped | S/resumed | S/stopped where platform does so | F | Not itself owned by store |
| ViewModelStore | S | S | S | S | S | S | Cleared on final destruction | F |
| InferenceViewModel | S | S | S | S | S | S | Cleared with store | F |
| ChatConversation / partial / completed-only history | S | S | S | S | S | S | Active exchange excluded; memory becomes unreachable | Same |
| Active operation Job | S | S | S | S | S | S/best effort | C at final store clear | C |
| UI epoch | S | S | S | S | S | S | Incremented at final clear | Incremented; late callbacks invalid |
| Input revision | S | S | S | S | S | S | Incremented at final clear | Incremented; input rejected |
| UI collectors / render cache | S/re-render current snapshot | R from current StateFlow | P below STARTED | P below STARTED | Resume latest snapshot | P when below STARTED | C/disposed | Activity collection ends with lifecycle |
| InferenceSession | S | S | S | S | S | S | Shared instance survives | Shared instance survives |
| Session generation mutex | Same instance | S | S | S | S | S | Cancelled generation releases lock when unwound | Same; no overlapping replacement |
| Session active-job map | S | S | S | S | S | S | Cancelled operation removed in finally | Same |
| Provider / OpenAiHttp | S | S | S | S | S | S | Shared objects survive | Shared objects survive |
| Per-request OkHttp Call | S | S | S | S/best effort | Same request, no replay | S/best effort | C via operation cancellation | C |
| Response/body/SSE reader | S | S | S | S/best effort | Same resources if still active | S/best effort | Unblocked/closed through cancelled Call and use | Same |
| Prepared catalog / credential lease | S | S | S, subject to revisions | Same | Reuse when compatible | Same | VM catalog becomes unreachable; active lease released on unwind | Same; secure records untouched |
| CredentialRouter / protected profile and refresh owners | S | S | S | S | S | S | Existing application/repository lifetime | Unchanged; no deletion |

Finish means final Activity destruction, not every onDestroy callback. A
configuration-change onDestroy retains the store. Refresh can deliberately delay
job unwinding after durable intent; its separate bounded safety contract remains.

## E–F. Production changes and composer rule

1. `InferenceViewModel.kt`: final onCleared invalidates epoch/input revision,
   cancels the operation and synchronously marks active chat/proof cancelled.
   Cleared owners reject input. Existing session serialization and transport code
   are unchanged.
2. `ComposerInteraction.kt`: composition-owned, single-use input token, disposed
   when its composition is destroyed. This makes discard and late-result rejection
   directly testable. It never calls Send.
3. `WearShell.kt`: save only surface and pending-input UI marker. A recreated
   composition has no token: **returned platform text is deliberately discarded**,
   Compose is restored, and the user sees “Text input was interrupted. Write your
   message again.” Existing delivered draft remains ViewModel-owned.
4. `InferenceViewModel.inputResult`: consume a normal returned result once, validate
   current account/generation/billing snapshot directly even before the account-flow
   collector runs, and update only the draft. Signed-out local drafting remains
   available; Send still requires a usable prepared catalog. New Chat/model changes
   invalidate it. Saving a bare
   revision token would not identify a fresh ViewModel owner after reconstruction;
   explicit discard avoids extending this phase into restored interaction ownership.

No durable draft/history persistence, inference protocol change, OAuth redesign,
billing redesign or credential-format change. No Activity lifecycle callback starts
or retries a generation. No production background cancel hook was added.

## G. Debug scenarios

Added ACTIVE_STREAM_RECREATION, BACKGROUNDED_STREAM, SCREEN_OFF_STYLE_RESUME and
COMPOSER_RECREATION; total **27**. Lifecycle streams use channel gates with numbered
chunks and one generation. Visible G/M/stage/length diagnostics and fixed safe
acknowledgements establish readiness and state. Existing completed/cancelled
recreation fixtures are retained. ScenarioOwner now clears its nested inference
ViewModelStore rather than merely cancelling an arbitrary scope.

Only SCREEN_OFF_STYLE_RESUME omits debug FLAG_KEEP_SCREEN_ON and setTurnScreenOn.
Other fixtures keep that convenience. Fixed debug broadcast commands are guarded
by the fresh launch identifier and scenario allowlist. The report uses a debug
Activity epoch so old hosts cannot overwrite a new launch's acknowledgement.
WEAR_SCENARIOS.md documents controls and isolation. None exists in release.

## H. Automated validation — VERIFIED AUTOMATICALLY

| Check | Exact result |
| --- | --- |
| D4 lifecycle/composer/transport/scenario suites | 29 tests; 0 failures/errors/skips |
| Combined focused D4 + D3 streaming/race/session/transition/chat-context suites | 86 tests; 0 failures/errors/skips (includes the 29 above) |
| Full Wear JVM suite | 288 tests; 0 failures/errors/skips; 16 additional tests over 272 baseline |
| Refresh suites included in full Wear run | 35 tests; 0 failures/errors/skips; unchanged production refresh |
| Full mobile JVM suite | Not rerun: no shared/mobile changes; historical 70 is not a current-run result |
| Wear assembleDebug / assembleRelease | Both PASS; release APK unsigned, no publication |
| Wear lintDebug | PASS; 0 errors, 6 existing warnings, same as Day 3 |
| Scenario manifest/DEX/source/dependency release-isolation checker | PASS |
| Python scenario-launcher suite | 8 tests; PASS |
| All tools/*.py AST syntax | 4 files; PASS |
| git diff --check and changed/new-text whitespace | PASS; 14 changed/new text files |

Commands actually run with DEVELOPMENT.md's preserved JDK/signing environment:

```sh
export JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9"
export ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android"
./gradlew :wear:testDebugUnitTest --tests '*LifecycleOwnershipTest'
./gradlew :wear:testDebugUnitTest \
  --tests '*LifecycleOwnershipTest' --tests '*ComposerInteractionTest' \
  --tests '*StreamingHttpCancellationTest' --tests '*DebugScenariosTest' \
  --tests '*StreamingRaceTest' --tests '*ChatViewModelTest' \
  --tests '*InferenceSessionTest' --tests '*ErrorTransitionTest' \
  --tests '*TransitionTest' --tests '*ChatConversationTest' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug \
  :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_wear_scenarios.py'
python3 tools/check_scenario_isolation.py
git diff --check
```

Initial command failed on the sandbox's read-only Gradle cache; authorized runs
used the existing environment outside that restriction. A nullable debug-Intent
compilation issue and a restricted ViewModelStore.put debug use were fixed before
final passing validation; debug now uses public ViewModelProvider. A new receiver
API warning was removed with ContextCompat's compatible registration. No dependency
pins changed. Existing lint warnings are Gradle/AGP version notices (2), exported
production service (1), vector complexity (1), debug WearRecents (2).

`LifecycleOwnershipTest` uses actual ViewModelStore/Provider and unsafe test-only
LifecycleRegistry + repeatOnLifecycle(STARTED), controllable channels/deferreds,
not simulated sleeps. Host replacement in JVM tests is retained-store evidence;
actual Activity recreation is covered on the emulator. `StreamingHttpCancellationTest`
clears a real store while the real VM/session/OpenAiProvider/OpenAiHttp/OkHttp path
is blocked in synthetic SSE; EventListener cancellation and body-close gates prove
transport cleanup. Existing D3 serialization/race tests remain passing.

Final review also preserved D3's signed-out local drafting: input-result guards
compare the current access snapshot with the observed access revision rather than
requiring a catalog just to draft. A new negative test proves drafting is allowed
while signed out and Send still creates zero generations. All final counts above
include that regression test.

The production collector remains collectAsStateWithLifecycle, not a generation
owner. It launches no inference effects. Immutable current chat state rebuilds
message caches on recreation; partial text has no independent lifecycle cache.

## I. Emulator acceptance

**PASS — VERIFIED ON EMULATOR** for each scenario below, with the stated screen-
off evidence limit. Existing AVD WristPrompt_Watch7 was launched with
`-no-snapshot-save`, dynamically verified as watch/emulator, and updated using
`adb -s emulator-5554 install -r wear/build/outputs/apk/debug/wear-debug.apk`.
Only that verified emulator serial was targeted. A connected physical phone was
ignored. No Android Studio, data clear, uninstall, pairing change, capability
probe, real authentication or real network request was involved.

| Scenario | Classification | Observed evidence |
| --- | --- | --- |
| ACTIVE_STREAM_RECREATION | PASS — VERIFIED ON EMULATOR | Active first chunk; automatic Activity creation count 1 -> 2 with same owner UUID; explicit next chunk takes 19 -> 38 chars; G=1, M=1. One completion, two messages; a third Activity creation preserves completed state. Separate run tapped production Stop after recreation: CANCELLED, one excluded exchange/19-char partial, G=1, zero completions. |
| BACKGROUNDED_STREAM | PASS — VERIFIED ON EMULATOR | Home: lifecycle CREATED, collecting=false, observedChars=19. ADVANCE while stopped: VM chars=38, observer still 19. Reorder existing Activity to front: observedChars=38, same owner, G=1, M=1. COMPLETE yields one terminal completion/two messages. |
| SCREEN_OFF_STYLE_RESUME | PASS — VERIFIED ON EMULATOR, display-transition scope | keepScreenOn=false; KEYCODE_SLEEP produced emulator mWakefulness=Dozing. During the measured interval Activity remained RESUMED/collecting=true. Gate advanced 19 -> 38 chars; wake/return kept same owner, G=1, M=1, then one completion. This does not prove below-STARTED collection pause under display sleep; Home covers that independently. Uninterrupted hardware execution remains unverified. |
| COMPOSER_RECREATION | PASS — VERIFIED ON EMULATOR | Actual system RemoteInput/Gboard with on-screen keys: Hi returned as draftChars=2, G=0. Entered Old as replacement, recreated underlying Activity while keyboard remained open, then returned Old. Same owner UUID, creation count 2 -> 3, original two-character draft retained; replacement discarded, interruption notice shown, G=0/M=1. Explicit Send then repeated action left G=1, COMPLETED/two messages, empty draft. |
| RECREATED_COMPLETED_STATE | PASS — VERIFIED ON EMULATOR | Two Activity creations, one retained owner/generation, COMPLETED/two messages, no replay. |
| RECREATED_CANCELLED_STATE | PASS — VERIFIED ON EMULATOR | Two Activity creations, one retained owner/generation, CANCELLED/63-char partial, one excluded user message, no replay. |

Final-build composer recheck also started empty, entered Hi using on-screen keys,
recreated with that nonempty result pending, and returned it: same owner, two
Activity creations, draftChars=0, generations=0, interruption notice. A fresh Hi
then returned as draftChars=2/generations=0; explicit Send completed exactly once
(completions=1, messages=2). The temporary
`python3 /tmp/d4-composer-acceptance.py` runner performed this state/real-key result
exercise and passed. The separate prior-reviewed-draft preservation case is in
the matrix above.

The earlier injected-text attempt also showed empty draft, disabled Send and
the interruption notice, but injected keyboard text was not accepted as delivery
evidence. The real nonempty on-screen-key replacement exercise above is the
accepted result. Hardware-key text injection appears in Gboard but can fail to
return through RemoteInput, matching the limitation already recorded in D3-C.
No voice input was used or added.

Emulator commands actually used include the existing launcher:

```sh
python3 tools/wear_scenarios.py --scenario COMPOSER_RECREATION
adb -s emulator-5554 shell input keyevent KEYCODE_HOME
adb -s emulator-5554 shell input keyevent KEYCODE_SLEEP
adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP
adb -s emulator-5554 shell am start -W \
  -n io.github.grassified.wristprompt/.openai.DebugScenarioActivity -f 0x20000
adb -s emulator-5554 shell am broadcast \
  -a io.github.grassified.wristprompt.DEBUG_LIFECYCLE \
  -p io.github.grassified.wristprompt --es launch_id LAUNCH_ID --es command ADVANCE
```

Temporary host acceptance runner `/tmp/d4-emulator-acceptance.py` used the same
launch interface, fresh identifiers, bounded acknowledgement polling, explicit
ADVANCE/COMPLETE/RECREATE gates, safe reports, power-state reads and synthetic
screenshots. `python3 /tmp/d4-emulator-acceptance.py` exercised the first three and
two terminal recreation fixtures. Stop and platform composer were then exercised
with on-screen controls. The screen check initially assumed sleep must put the
Activity below STARTED; that assertion was inappropriate for this Wear emulator.
The accepted result records the actual Dozing + retained RESUMED behavior instead.
No fixed-delay timing is used to decide a streaming race.

Screenshots/visible controls were inspected for generation/readiness diagnostic,
production Stop/Compose, composer review/notice and terminal state; safe numeric
acknowledgements additionally establish ownership, counters and paused observation.
No result above is physical Galaxy Watch evidence.

## J. Refresh safety review

WatchProfiles.credential still checks cancellation before entering the shielded
transaction, persists refreshPending under the vault lock, performs a bounded
35-second NonCancellable grant, and atomically commits against the current record.
Uncertain post-dispatch failure retains durable intent; explicit trustworthy
rejection/not-sent handling is unchanged. InferenceSession still rechecks Job and
lease currency before publication. None of WatchProfiles, CredentialRouter,
OpenAiRefresh, OpenAiHttp, refresh protocol/storage or their formats changed.

Final UI-owner cancellation may cancel normal HTTP inference. It cannot cancel
away the refresh commit/uncertainty boundary, enable reuse of an uncertain consumed
refresh token, clear protected uncertainty or delete valid credentials. Full Wear
regression includes 35 refresh tests; no new physical refresh claim is made.
Official [accounts/session refresh](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
and [errors/recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
were rechecked during final review. The existing current-bearer catalog, explicit
billing, completed-only success and serialized rotating replacement contracts remain.

## K. Release and security invariants

Confirmed by diff/source review and release isolation: no foreground service,
WorkManager, AlarmManager, wake lock, production keep-screen-on, connectivity
observer, app-scoped inference Job, credential fallback, Data Layer bypass or
sensitive logging added. No authentication, billing route, signing, credential
storage or format change. No real OpenAI call or credential used by scenarios/tests.
Debug scenarios/commands absent from release manifest and DEX. No real app-data
clear, uninstall, revocation, key invalidation or usage-exhaustion test performed.

## L–M. Remaining debt and deferred boundaries

**PENDING END-OF-DAY PHYSICAL TEST:** batch retained active recreation, Stop after
recreation, Home/background/return, normal real-network completion, and Galaxy
Watch 7 screen-off/resume under device power management. Observe current/error/
interrupted/completed state and verify no automatic replay. No physical test was
performed during D4-A implementation; emulator Dozing is not hardware acceptance.

- D4-B process death remains pending: separate owner/process recreation and current
  intentional memory-only conversation loss; no persistence/recovery implemented.
- D4-C network transitions remain pending; no connectivity observers added.
- Historical Terra -> Sol restoration anomaly remains unreproduced/unresolved.
- Historical transient reauthorization anomaly remains unresolved; Day 5 disposition
  still required before freeze. Normal current behavior does not close that debt.
- Funded BYOK physical inference remains externally constrained; no funded key
  available/required and no purchase requested.
- Emulator CapabilityClient limitation remains: production capability-dependent
  authentication is NOT VERIFIABLE ON CURRENT EMULATOR LAB. Investigation was not
  reopened and production routing was not changed.
- Existing final RC debts from DAY3_REPORT.md, including physical context-boundary,
  rotary/larger-font, signing and release checks, remain where not covered here.

## N. Suggested commit

`test: harden retained lifecycle and final inference teardown`

No commit or push performed. Changed files: DAY4_STATUS.md, DECISIONS.md,
ROADMAP.md, WEAR_SCENARIOS.md, InferenceViewModel.kt, WearShell.kt,
ComposerInteraction.kt, DebugScenarioActivity.kt, DebugScenarios.kt,
LifecycleOwnershipTest.kt, ComposerInteractionTest.kt,
StreamingHttpCancellationTest.kt, DebugScenariosTest.kt and test_wear_scenarios.py.
DAY3_REPORT.md remains unchanged.

# D4-B — process death and fresh reconstruction

## A. Executive result — PASS WITH KNOWN DEBT

**VERIFIED AUTOMATICALLY:** 303 Wear tests, 16 Python tests, Wear debug/release
assembly, lint, release isolation and whitespace/syntax checks pass. **PASS —
VERIFIED ON EMULATOR:** all four actual am-kill/task-resume scenarios, a separate
force-stop/cold launch, the reproduced page-reset regression, and retained D4-A
stream/Stop/composer-marker checks. Hardware process/reboot/Keystore and release
acceptance remain separate. Only D4-B was implemented; D4-C is unstarted.

Starting tree was clean at `962452c` (the completed D4-A commit). No commit, push,
publication, real credential operation or physical-device testing performed.

## B. Final process-death contract

Process death ends the in-memory conversation, including completed replay and
opaque reasoning items, draft, partial output and interrupted user exchange.
Request Jobs, callbacks, busy/Stop state, prepared catalogs and leases are not
durable. A fresh graph restores protected configuration and discovers a fresh
catalog before reconciling the exact saved slug. Chat then starts EMPTY/idle.
Initial credential/model loading is legitimate fresh startup work; it is not
restored inference. A live account observer also is not an active generation Job.

No generation is resumed, replayed, retried or inferred completed. The client
process dying cannot establish whether OpenAI finished generation or incurred
usage. A new inference requires an explicit user Send. No recovery feature or
persistent interrupted-request/conversation marker was added. Existing empty-chat
copy already says history stays in memory for this session; no confusion evidence
justified new UX copy.

| Lifecycle | Meaning |
| --- | --- |
| Recomposition | Same owners/work; rendering only, covered by D4-A. |
| Retained Activity recreation | Same ViewModelStore and conversation/operation; D4-A behavior retained. |
| Finish/final ViewModel destruction | Graceful local cancellation/invalidation and transport cleanup; not process-death evidence. |
| Background `am kill` | Actual app process termination with Android task/saved state retained; used for primary D4-B acceptance. Not induced low-memory pressure. |
| Force-stop | Stronger package-stopped state; checked separately with explicit cold launch. Not equivalent to all natural kills. |
| Relaunch after process death | New Application/process dependencies, stores and ViewModels; existing durable records reconstruct, ephemeral work does not. |
| Device reboot | Separate hardware/RC boundary; not exercised. |

## C. Fresh startup and ownership sequence

1. Android creates WristPromptApplication. Its clock and application scope are
   eager; repositories/router/session/HTTP/bridge dependencies remain lazy.
2. MainActivity creates WatchAuthViewModel. Constructor dependencies instantiate
   the auth bridge, profiles, revocation/HTTP and inference dependencies as needed.
   InferenceSession is a fresh shared dependency, not an application-owned request.
   CredentialRouter starts combining fresh repository flows with usable=false.
3. WatchAuthViewModel starts `profiles.load()` in viewModelScope. MainActivity's
   ApiKeySettingsViewModel starts `keys.load()` independently. EncryptedRecord
   construction alone does not decrypt; repository reads validate/decrypt the
   existing vaults. Startup ordering is concurrent, not an assumed fixed read order.
4. WatchProfiles loads selected registration/generation/model preferences and
   refresh/sign-out metadata. Abandoned sign-out intent completes local clearing
   with UNCONFIRMED remote outcome. Uncertain refresh remains blocked, not retried.
5. ApiKeyRepository restores loaded mode/modeRevision, key/keyRevision and API
   model. Until loaded, the router is unusable even if a plan profile arrives first.
   API mode may legitimately become usable before plan profiles load; its route is
   independent. Direct `router.current()` also reads the durable billing snapshot.
6. Fresh InferenceViewModel constructs empty ChatConversation, no prepared catalog
   or operation, and new epoch/input ownership. Only a usable current access state
   triggers model discovery. It never calls Send from startup or restoration.
7. InferenceSession obtains the current lease from the selected route, discovers
   with that bearer, checks lease currency and cancellation, then the ViewModel
   reads the account/mode-specific saved model and revalidates epoch/lease again.
   Exact slug wins regardless of service order/labels; otherwise first service
   entry is a display fallback with no dormant-preference writeback.
8. Activity-owned ShellNavigationViewModel starts at CHAT (debug fixtures may
   explicitly choose another initial surface). No old shell page or pending-input
   marker is restored from Android's saved Bundle. Composer tokens remain local
   to their composition and cannot return text into a fresh owner.

Installation DataStore is instantiated lazily via HostIdRepository for bridge
identity use. Normal inference startup does not manufacture/read a new host ID.
Independent DataStore reconstruction verifies the existing UUID remains stable.
Absent first-install identity follows existing getOrCreate behavior; invalid or
unreadable persisted identity fails without calling a replacement generator.
Existing encrypted records with a missing key fail closed; no reset/fallback path
was introduced. These are code/JVM boundaries, not hardware Keystore acceptance.

The delayed-profile, delayed-billing and delayed-model-preference D3-B tests pass;
new API reconstruction tests explicitly try Send before billing load and observe
zero discovery/generation. No startup wrong-mode race was reproduced. A corrupt
API vault may remain loaded=false/storageError=true rather than publishing a
router STORAGE problem immediately; it stays unusable and UI has storageError.
The safety assertion is fail-closed/no dispatch, not a guessed intermediate enum.

## D. Baseline defect reproduced

D4-A's `rememberSaveable` surface and pending-input marker were eligible for
Android process saved-state restoration. Baseline EMPTY_CHAT was navigated to
Compose, backgrounded, killed with am kill, and its original task resumed:
PID **3746 -> 3877**, fresh ScenarioOwner, yet Compose/disabled Send/Write message
returned. The conversation was correctly empty, but a stale shell page survived.

Final code recheck: PID **4497 -> 4910**, fresh owner, **COMPOSER -> CHAT** after
am kill/task resume. This is an actual process transition, not Activity.recreate().
No credential, refresh, model restoration, chat persistence or inference replay
production defect was reproduced. The initial corrupted-record test was corrected
to test fail-closed routing rather than require an intermediate error enum.

## E. Production changes

- Added `ui/ShellNavigationViewModel.kt` for two transient UI values: current
  surface and pending-input marker. Activity ViewModel ownership retains them
  across configuration changes; a fresh process starts with fresh defaults.
- `ui/WearShell.kt` now uses that owner rather than rememberSaveable for those
  two values. No SavedStateHandle or persisted marker is involved. Existing
  composition-owned ComposerInteraction discards old returned text as in D4-A.

No change to MainActivity/Application, InferenceViewModel/session/conversation,
CredentialRouter, credential/model vaults, DataStore identity, encryption/signing,
refresh, sign-out, OAuth, billing or production Data Layer routing. D23 records
the new shell-state boundary; D18's memory-only chat decision is unchanged.

## F. Persistence matrix after process death

| State | Disposition |
| --- | --- |
| Installation host identity | RESTORED from existing DataStore; missing first installation has existing explicit getOrCreate semantics; invalid/unreadable storage fails closed. |
| Issued client/registration identity, verified identity, scopes, tokens | RESTORED from validated encrypted profiles, if usable; no new registration inferred. |
| Selected account and generation | RESTORED from WatchVault, including a selected second registration in the integrated test. |
| Billing mode and mode revision | RESTORED from ApiVault before routing can become usable. |
| API key/key revision/configured state | RESTORED from ApiVault; missing key in API mode never falls back to plan. |
| Plan model per registration / API model | RESTORED preferences, reconciled against a fresh current catalog; no fallback writeback. |
| Refresh intent/uncertainty / reauth-required state | RESTORED; uncertain/terminal grants remain blocked. |
| Sign-out intent/result and retained registration | RESTORED/recovered; abandoned intent clears local secrets, reports UNCONFIRMED, never revives credentials. |
| Repository/router/session/provider objects, mutexes and active-job map | RECONSTRUCTED; no old instances or active request ownership. |
| Catalog and bearer lease | RECONSTRUCTED through discovery/current routing; never restored from prior process memory. |
| Credential/model loading | RECONSTRUCTED current startup work, not stale request busy state. |
| Conversation, completed replay, reasoning items, partial output, draft | CLEARED / NOT DURABLE. |
| Active generation, SSE, operation Job, old callback ownership | CLEARED / NOT DURABLE; remote outcome remains unknown. |
| UI epoch and input revision | RECONSTRUCTED for the fresh ViewModel; old callbacks retain no reference to it. |
| Stop/receiving state, old transient errors, shell page, pending composer token/marker | CLEARED / NOT DURABLE. |
| In-memory refresh cooldown and renewed-session display hint | CLEARED / NOT DURABLE; durable uncertainty is independent and remains authoritative. |

## G. Refresh and sign-out recovery

**VERIFIED AUTOMATICALLY**, with unchanged production code:

- Normal fresh credentials restore and discover without refresh or generation.
- Definitely-unsent refresh failure clears durable intent; a fresh graph may safely
  renew for catalog access. This is not replay of inference or an uncertain grant.
- Synthetic uncertain post-dispatch NETWORK outcome persists refreshPending.
  A fresh router/VM requires reauth, performs zero catalog/generation calls, and
  direct credential acquisition cannot reuse the old grant.
- Terminal invalid_grant persists unusable/reauth state; fresh acquisition does
  not attempt another refresh or normal inference.
- A snapshot taken after durable sign-out intent but before revocation completion
  is reconstructed as abandoned work. Local clearing completes, registration/host
  remain for reauthorization, no ID-token hint returns, and remote result is
  UNCONFIRMED. No remote revoke replay or credential resurrection occurs.
- Existing full regression includes **35 refresh** tests and **13 sign-out** tests.
  Bounded NonCancellable rotation/commit semantics were not weakened.

## H. Debug scenarios and tooling

Added PROCESS_RESTART_IDLE, PROCESS_RESTART_AFTER_COMPLETED,
PROCESS_RESTART_DURING_ACTIVE and PROCESS_RESTART_AFTER_PARTIAL: **31 total**
fixtures. Each new fixture starts empty with fresh discovery; only the runner's
explicit once-only SEED creates its pre-death state. No fixture, stage, prompt or
"already seeded" marker is written for the next process. Fresh app ownership never
calls SEED. The active-before-delta and active-after-partial cases suspend on
cancellation, without timer races. Completed case uses normal ViewModel/session/
conversation paths. Debug lease remains an empty bearer, never a real credential.

`tools/wear_process_restart.py` shares watch discovery with the existing launcher,
requires AVD WristPrompt_Watch7, verifies debug before mutation, and reports safe
PID/enum/count/synthetic-model/billing metadata. Default am-kill waits for below-
STARTED observation and actual PID disappearance, then resumes the original task.
Explicit force-stop is a distinct method and performs a new explicit launch.
Fresh PID/process-owner/fixture-owner and empty/no-replay assertions are mandatory.
No hidden force-stop fallback, data clear, uninstall, credential access or network.
WEAR_SCENARIOS.md records the public commands and evidence boundaries.

## I. Automated results — VERIFIED AUTOMATICALLY

| Check | Exact result |
| --- | --- |
| Full Wear unit suite | **303 tests**, 0 failures/errors/skips; 15 additional tests over D4-A's 288. |
| Combined focused D4-B/D3-B/D4-A/recovery/chat suites | **123 tests**, 0 failures/errors/skips; included in full suite. |
| New process reconstruction class | **11 tests**, including gated/mode/state loops; passing. |
| D3-B ModelRestorationIntegrationTest | **15 tests**, passing, all existing cases retained. |
| D4-A LifecycleOwnershipTest | **8 tests**, passing. ComposerInteractionTest also passes. |
| New shell owner tests | **2 tests**, passing. |
| HostIdRepositoryTest | **5 tests**, passing; includes new corrupt persisted-DataStore reconstruction rejection. |
| Python launcher + process-runner suites | **16 tests**, 0 failures/errors/skips. |
| Wear assembleDebug / assembleRelease | Both PASS; unsigned release inspection only. |
| Wear lintDebug | PASS; 0 errors, same 6 warning instances as D4-A. |
| Scenario manifest/DEX/source/dependency release-isolation checker | PASS. |
| Python syntax | All 6 tools/*.py files PASS. |
| git diff --check and changed/new-file whitespace | PASS. |
| Mobile unit/build/lint | Not rerun: no shared/mobile changes; historical results not claimed as current. |

Reused/extracted the existing D3-B encrypted-disk fixture in
`ReconstructionFixture.kt`; no second repository/router fixture framework. Each
runtime constructs new SecretRecord wrappers, WatchProfiles, ApiKeyRepository,
router, session, ViewModel and actual ViewModelStore. Catalog parsing uses the
production provider/HTTP adapter with synthetic interceptor responses. Chat inputs
and gated responses pass through real VM/session/conversation paths. JCE AES-GCM
file encryption/serialization and real disk DataStore are exercised; test keys
live at the simulated OS boundary. Android Keystore/AtomicFile/device reboot are
not established by JVM tests.

All race tests use deferred/channel/state gates, not arbitrary sleeps. The account
collector is expected to remain live after startup; tests distinguish it from an
active inference Job. A deliberately late NonCancellable old-owner completion
cannot publish into the old cleared or fresh graph. A new explicit request after
fresh reconstruction contains exactly its one new user item, no old reasoning or
completed/partial history.

Final commands, with DEVELOPMENT.md's existing JDK/signing environment:

```sh
export JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9"
export ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android"
./gradlew :wear:testDebugUnitTest \
  --tests '*ProcessReconstructionTest' --tests '*ModelRestorationIntegrationTest' \
  --tests '*LifecycleOwnershipTest' --tests '*ShellNavigationViewModelTest' \
  --tests '*ComposerInteractionTest' --tests '*DebugScenariosTest' \
  --tests '*WatchRefreshTest' --tests '*RefreshUpgradeTest' \
  --tests '*WatchSignOutTest' --tests '*HostIdRepositoryTest' \
  --tests '*ChatConversationTest' --tests '*ChatViewModelTest' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_wear*.py'
python3 tools/check_scenario_isolation.py
git diff --check
```

## J. Emulator process-restart acceptance

Existing WristPrompt_Watch7 was started with `-no-snapshot-save`, verified by AVD
name/watch characteristics, and updated with install -r using the existing key.
No Android Studio, physical device, reboot, pairing or CapabilityClient probe.

| Scenario | Method | PID before -> after | Result |
| --- | --- | --- | --- |
| PROCESS_RESTART_IDLE | Home / am kill / original-task resume | **4004 -> 4103** | PASS — VERIFIED ON EMULATOR |
| PROCESS_RESTART_AFTER_COMPLETED | Same | **4103 -> 4197** | PASS — VERIFIED ON EMULATOR |
| PROCESS_RESTART_DURING_ACTIVE | Same | **4197 -> 4287** | PASS — VERIFIED ON EMULATOR |
| PROCESS_RESTART_AFTER_PARTIAL | Same | **4287 -> 4381** | PASS — VERIFIED ON EMULATOR |
| PROCESS_RESTART_DURING_ACTIVE | Separate force-stop + explicit cold launch | **4381 -> 4497** | PASS — VERIFIED ON EMULATOR; stronger semantics |

Every row changed both process-owner and fixture-owner UUIDs. Every new process:
EMPTY, zero generations/messages/partial/draft, busy=false, fresh READY catalog,
no pending input, CHAT surface. Before death: completed had two messages; active
had one pending user/zero partial; partial had one user/25 partial characters.
The idle fixture's debug-2 selection resets to debug-1 intentionally: fixtures
are not persisted and **do not prove production preference restoration**.
Synthetic billing remains CHATGPT_PLAN. Real durable production account/billing/
model/credential reconstruction is covered separately by integrated JVM tests;
installed real credentials were never read for emulator assertions.

Additional actual renderer/owner checks:

- Saved-page defect recheck: **4497 -> 4910**, COMPOSER -> CHAT with fresh owner.
- ACTIVE_STREAM_RECREATION still retained one owner/generation through two Activity
  creations; explicit gate yielded 38 chars and production Stop yielded CANCELLED
  with one excluded user exchange. No process restart was confused with recreation.
- COMPOSER_RECREATION retained its marker/surface through Activity recreation,
  then native swipe-back dismissal returned RESUMED/COMPOSER, pending marker=true,
  zero draft/generations and coherent interruption guidance. Generic Back did not
  dismiss this Wear picker; that tooling assumption was corrected to swipe-back.

Commands actually executed:

```sh
python3 tools/wear_process_restart.py --all --install
python3 tools/wear_process_restart.py --scenario PROCESS_RESTART_DURING_ACTIVE --method force-stop
PYTHONDONTWRITEBYTECODE=1 python3 /tmp/d4b-ui-acceptance.py
```

The temporary UI runner's first immediate tap was not accepted as a UI transition;
rendered-control synchronization confirmed the page-reset/stream checks. Its
Back-key composer dismissal timed out; manual native swipe-back completed that
check with the safe acknowledgement above. These are not claimed as passing
unmodified host-script runs. Process runner assertions themselves passed all rows.

This is actual process termination, not induced low-memory pressure or hardware
reboot. am kill preserves the Android task; force-stop has stronger stopped-package
semantics. PID change does not prove remote inference ended cleanly or usage was
avoided. No generation was automatically resumed/replayed in any fresh fixture.

## K. Security and architecture invariants

No conversation/request persistence, automatic inference replay, unsafe uncertain
refresh reuse, credential fallback, Data Layer bypass or sensitive logging added.
Debug metadata includes no account IDs, token/key/header/payload, prompt, response
or raw authenticated body. No credential schema/storage migration, OAuth/billing
redesign, signing/dependency change, foreground service, WorkManager, wake lock,
connectivity observer or production keep-screen-on. Scenario classes/commands are
absent from release manifest/DEX; production has no references to them. No real
app-data clearing, uninstall, session revocation or key invalidation performed.

## L–M. Remaining debt / deferred to D4-C

**PENDING END-OF-DAY PHYSICAL TEST / RC:** actual Galaxy Watch process kill/relaunch,
watch reboot, hardware Keystore restoration, phone-off/watch-only reconstruction,
and final release-build lifecycle acceptance. No physical testing performed here.

D4-C connectivity/network transitions remain unstarted; no observer/retry or
Wi-Fi/Bluetooth transition implementation was added. Historical Terra -> Sol
physical anomaly remains unreproduced/unresolved; integrated regression passes
are not an anomaly fix. Reauthorization anomaly remains unresolved for Day 5
freeze disposition. Funded BYOK physical success remains externally constrained;
no funded credential/purchase required. Emulator CapabilityClient limitation is
unchanged and was not reinvestigated. Existing D3/D4-A physical/RC debt remains.

## N. Suggested commit

`test: verify fresh-process reconstruction without inference replay`

No commit or push performed. Changed files: WearShell.kt,
ShellNavigationViewModel.kt, ModelRestorationIntegrationTest.kt,
ReconstructionFixture.kt, ProcessReconstructionTest.kt,
ShellNavigationViewModelTest.kt, HostIdRepositoryTest.kt,
DebugScenarios.kt, DebugScenarioActivity.kt, DebugScenariosTest.kt,
wear_scenarios.py, wear_process_restart.py, test_wear_scenarios.py,
test_wear_process_restart.py, DAY4_STATUS.md, WEAR_SCENARIOS.md,
DECISIONS.md and ROADMAP.md. DAY3_REPORT.md remains unchanged.
