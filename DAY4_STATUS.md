# Day 4 Phase A — 2026-10-29

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY; emulator matrix below.**
Only D4-A lifecycle work is implemented. D4-0 was supplied as complete. D4-B
process death and D4-C network transitions are unstarted. No physical test,
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
