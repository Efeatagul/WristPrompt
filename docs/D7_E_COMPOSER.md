# D7-E — combined watch composer

Progress toward #10 — implementation/automated acceptance complete; owner physical
composer acceptance deferred to #12. Keep #10 open. No #11 image generation,
navigation redesign, dependency changes or new inference/persistence owner.

## Ownership and review

`InferenceViewModel` continues to own ordinary draft text and `ImageSelection`'s
ordered UUID list. Composer UI observes these owners; it contains no image files,
base64 payloads or hidden prompt. D7-C owns library files/thumbnails and staged
picker edits. D7-D `ConversationImages`, `ImageInput`, session and conversation
checkpoint owners remain the only inference/serialization/replay path.

Empty-draft preset insertion uses exact preset text. Nonempty Insert appends two
LFs and exact preset text; Replace is explicit; Cancel does nothing. Inserted text
is ordinary editable USER text, detached from later preset sync. The native field
remains present when all text is removed, allowing another edit without changing
image selection. Platform replacement/keyboard Done never send.

Photos opens the existing staged library picker. Use selection commits only after
its existing token/text/previous-selection/file checks. Cancel/back preserves the
committed draft and IDs. Selection order is tap/commit order; removing one image
preserves relative order. Composer shows each available thumbnail and a separate
Remove photo action, including for unavailable IDs. Removal invalidates stale
picker/platform callbacks without deleting files. Missing selections are never
silently pruned by the composer. Discard draft and photos clears both together,
invalidates callbacks and leaves completed messages/replay untouched.

## Draft lifecycle policy

Unsent drafts remain memory-only; no autosave, SavedStateHandle, image copying or
new schema is introduced. The composer and existing Settings explain this policy.

| Boundary | Behavior |
| --- | --- |
| Navigate away/back, model/thinking picker visit | Same retained text and ordered image IDs; no Send. Reader position stays with existing navigation owner. |
| Activity recreation | Existing Activity ViewModelStore retains draft, selection, pending preset/photo edits and navigation. Platform RemoteInput callback is deliberately discarded under D22; interrupted input can be reopened. |
| Process death/final owner destruction | Unsent text, selected IDs, picker edits and navigation are discarded. Existing canonical storage restores completed chat; pending attempts become excluded, never resumed/sent. |
| New Chat or successful history reopen | Clears unsent draft/selection at existing canonical chat boundary. Opening a picker or visiting history alone does not clear them. |
| Explicit different model | Existing new-replay behavior remains; unsent text/images survive for review under the new model. Old input/picker tokens are invalidated. Saved chats remain in canonical history. |
| Thinking change | Existing configuration checkpoint and provider-native validation; draft/images remain. Default omits the control; never maps to native `none`. |
| Account/billing change | Existing owner invalidation clears conversation and unsent review; no cross-owner carry or route fallback. |

## Validation and Send

The fixed Send control remains explicit and Stop takes its place on every surface
while busy. Blank/oversized text disables Send with visible guidance; selected
images plus blank text additionally show Text required. Direct images-only Send
also fails in D7-D with zero requests and exact text/IDs retained.

D7-C limits selection to six distinct valid UUIDs. Existing D7-D preflight checks
model/route image capability, thinking selection, selected/replay file integrity,
count, text/context and encoded request bounds before consuming the draft. Errors
preserve exact text/selection and issue no inference request; there is no image
removal, truncation, model switch or billing fallback. Explicit Send snapshots exact
text and committed image order and enters the existing D7-D owner exactly once.

Existing accepted-Send semantics apply: draft/selection clear after successful
preflight/start, rather than waiting for provider completion. Failed/stopped/
interrupted accepted attempts remain excluded; partial display and actual HTTP
cancellation are unchanged. Only response.completed plus durable completion commit
admits replay. Canonical transcript/attachment restoration is unchanged. No retry,
background Send, Markdown/code redesign, forced autoscroll or Latest changes.

## Evidence

VERIFIED AUTOMATICALLY:

- 443 tests / 47 suites; zero failures, errors or skips. Includes all Wear openai,
  preset/image, composer/navigation, Markdown/presentation/error suites. New tests
  cover ordinary/preset-derived edited text with 0/1/N images, individual removal,
  exact provider text/image order, duplicate Send, stale picker after removal,
  combined discard, retained Activity owner versus fresh-process canonical restore,
  and explicit model change retaining review with zero automatic requests.
  A later 15-test ImageChatIntegrationTest pass added a combined navigation/picker
  visit and reader-position regression (444 distinct tests covered across runs;
  no full 444-test rerun claimed).
  Existing affected tests cover Insert/Replace/Cancel/sync detachment, selection
  bounds, missing/corrupt files, stale thinking, payload limits, both billing routes,
  Stop/excluded replay, completion/reopen, Markdown/code and reader/Latest behavior.
- Wear debug/release assemblies and debug lint passed; zero errors / eight warnings.
  A later layout-only adjustment moved lifecycle guidance below review actions;
  debug/release/lint were rerun for that adjustment.
- Android test APK build with `-Pwristprompt.imageInferenceAcceptance=true` passed.
  Scenario source/manifest/DEX isolation and `git diff --check` passed. No shared or
  mobile production changes; mobile tasks and Python tool tests were not needed.

Final affected gate:

```sh
./gradlew :wear:testDebugUnitTest --tests 'io.github.grassified.wristprompt.openai.*' --tests '*images.*' --tests '*presets.*' --tests '*ComposerInteractionTest' --tests '*ShellInteractionTest' --tests '*ShellNavigation*Test' --tests '*ChatPresentation*Test' --tests '*ErrorPresentationTest' --tests '*Markdown*Test' :wear:assembleDebug :wear:assembleRelease :wear:lintDebug :wear:assembleDebugAndroidTest -Pwristprompt.imageInferenceAcceptance=true
python3 tools/check_scenario_isolation.py
git diff --check
```

VERIFIED ON EMULATOR: `WristPrompt_Watch7`, 454×454 round. The isolated native
D7-D/E seed/verify instrumentation returned explicit PASS in separate processes.
Real normalized JPEGs and isolated SQLite exercised preset insertion/edit plus
additional text, three-photo selection, picker Cancel, individual removal,
two-photo explicit Send, unsupported/missing/corrupt validation, Stop exclusion,
canonical attachments/replay and inert process restart discarding unsent review.
Synthetic provider only; no live OpenAI requests, credentials or production DB.
This native gate does not establish visible thumbnail/IME interaction.

Visible synthetic PROMPT_PRESETS review passed at font scales 1.0 and 1.24.
Touch insertion returned to the composer with 69 draft characters and zero requests;
font-triggered Activity recreation retained the same owner/draft with zero requests.
Rotary scrolling reached the native draft field while Send stayed reachable; round
screenshots were inspected. Keyboard/key injection did not establish an edit and is
not counted as keyboard evidence. Returning via Ask then explicit Send produced
one request, one completed assistant exchange and an empty draft. Font scale was
restored to 1.0. Visible multi-image thumbnail interaction and
full maximum-font/IME ergonomics are not established by the native gate above.

No physical tests were performed or awaited. Paired transfer, real-device composer,
maximum-font/rotary/keyboard ergonomics and live-route acceptance remain part of
one consolidated #12 pass. No physical PASS is claimed.
