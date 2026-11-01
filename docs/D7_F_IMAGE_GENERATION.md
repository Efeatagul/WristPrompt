# D7-F image generation and Wear viewer

Progress toward #11. The existing published implementation is continued, not replaced.
Physical/live API acceptance is intentionally deferred; issue #11 must stay OPEN.
PR #29, #1 and #12 are outside this task.

## Official contract and supported routes — reviewed 2026-11-01

- [Responses image tool](https://developers.openai.com/api/docs/guides/tools-image-generation)
  documents forced `image_generation`, separate tool/mainline models, `action: generate`,
  JPEG/compression and continuation with the image-generation call ID.
- [Image generation guide](https://developers.openai.com/api/docs/guides/image-generation)
  documents completed base64 output and account/organization access requirements.
- [SIWC preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
  explicitly exclude image generation. Image-input support is separate.
- [Flare model](https://developers.openai.com/api/docs/models/gpt-image-2.5-flare)
  documents the Responses image tool model. [API pricing](https://developers.openai.com/api/docs/pricing)
  establishes separate API usage billing; the UI does not promise subscription coverage
  or an inferred per-image price.
- [Cancel response](https://developers.openai.com/api/reference/resources/responses/methods/cancel)
  applies to background responses. Cancelling this foreground HTTP call is not proof
  of provider cancellation, free usage or a refund.

Only the explicit API-key route is enabled, intersected with the active catalog and
this conservative reviewed exact-ID set: `gpt-6.1-sol`, `gpt-6-astra`, `gpt-5.5`,
`gpt-5.4`. Unknown IDs, aliases, dated suffixes, case variants, mismatched provider/
model/route/source capability and all ChatGPT-plan entries fail closed. This is not
an exhaustive statement of provider support. No inferred model-name capability,
model switch, credential/billing fallback or automatic retry is introduced.

Explicit Generate image uses the existing ViewModel/session/provider/HTTP stack.
It preserves the selected supported top-level model and sends `POST /v1/responses`,
`store=false`, `stream=true`, explicit input and no `previous_response_id`:

```json
{
  "tools": [{
    "type": "image_generation",
    "model": "gpt-image-2.5-flare",
    "action": "generate",
    "size": "1024x1024",
    "quality": "low",
    "output_format": "jpeg",
    "output_compression": 80
  }],
  "tool_choice": {"type": "image_generation"}
}
```

Generation requires nonblank text up to 8,192 characters and rejects input-photo
attachments, including historical image-input replay. It is generation, not editing.
Thinking is omitted for this operation while its stored selection remains intact for
ordinary text. Drafting, navigation, restart, viewer gestures and file recovery do
not dispatch inference. The existing 262,144-byte text-request cap still applies.

## Shared completion / Stop ownership

RUNNING and image PREPARING are cancellable. A terminal provider event alone does
not own completion. Preparation validates/normalizes an unowned candidate; Stop can
still win. Session Job/lease checks reject late/duplicate callbacks. After preparation,
the existing session lease validator runs again, followed by current Job, account/
route and epoch checks. A deterministic test found and now covers invalidation
while normalization suspends; it cannot acquire ownership or promote a file.

On Main, with no suspension between final validation and acquisition,
`ChatConversation.complete` installs the canonical candidate and `completionOwner`
marks that run. This is the linearization point. A later Stop is a no-op: it cannot
advance the epoch, truncate replay or cancel the owned atomic save. Durable success
is published only after file promotion and SQLite commit succeed.

The shared text/image fix ignores Stop for the current completion owner. Previously
Stop could advance the epoch while a completion-owned save was blocked; a subsequent
failure could then suppress required in-memory rollback. On save failure the prior
replay/state is restored and CHAT_STORAGE is shown, with no false completed exchange
or automatic retry. Account/New Chat invalidation cannot revive old UI.

ConversationWriter and SQLite semantics are UNCHANGED: non-cancellable atomic
checkpoints, shorter-replay guard, revisions and tombstones remain authoritative.
There is no generation-only compensating deletion or rollback of committed data.

## Untrusted output, files and replay

- Bound generation SSE lines/frames to 3 MiB, cumulative bytes to 12 MiB and JSON
  depth to 32 before parsing. Ordinary text stream limits remain unchanged.
- Require exactly one completed image call in `response.completed`. Tool-completed
  signals alone, missing/duplicate results, unknown tools, editing/PNG metadata,
  malformed/canonical-invalid base64 and unsupported call IDs fail closed.
- Provider output is at most 2 MiB decoded JPEG / 2,796,204 base64 characters.
  Verify JPEG markers, decoder-detected MIME, dimensions up to 1280 per edge and
  1,638,400 pixels, then actual decode/normalization through D7-C AndroidImages.
- Canonical JPEG is at most 768 KiB/1280 edge; thumbnail at most 24 KiB/160 edge.
  Normalization strips original metadata. Native decoding is defensive validation,
  not a claim to detect every possible codec defect.
- Conversation-owned generated files live in `noBackupFilesDir/generated_images_v1`
  under opaque owner/chat/image IDs, separate from imported-photo ownership.
  Bound generated storage to 64 MiB. Fsync recognized temporary files, atomically
  promote immutable files and sync directories BEFORE SQLite can reference them.
  No fallback to non-atomic promotion; failures expose CHAT_STORAGE.
- Snapshot v4 adds a typed generated-image reference with dimensions, bounded byte
  counts and internal integrity digests. Versions 1–3 remain readable and cannot
  contain the new generated part. No schema reset or destructive migration.
- SQLite is the only ownership manifest. Failed pre-commit saves may leave unowned
  reservations; startup recovery serializes with promotion/save and consults canonical
  storage before removing recognized temp/orphan files. Unreadable/unknown storage
  preserves files. Unknown filenames are untouched. Canonical deletion precedes file
  cleanup; imported library deletion cannot reach generated files.
- Read verifies bounded bytes and digests. Missing/tampered content is unavailable,
  never an instruction to regenerate. No image bytes/base64 enter SQLite, DataStore,
  ordinary conversation JSON, logs or diagnostic string forms.
- Replay retains only validated `{type:"image_generation_call", id:"ig_..."}` metadata,
  not the base64 result. Generated display is a typed attachment, not fake textual
  tool output. Ordinary continuation does not re-upload the local generated image.
  The provider documents call-ID continuation, but indefinite ID availability with
  `store=false` is NOT guaranteed; provider rejection is surfaced without substitution.

## Viewer and acceptance diagnosis

The native fullscreen dialog shows the full aspect-fit image inside an inscribed
round-screen area. Pinch zoom is 1–5x. Pan clamps to the actual scaled aspect-fit
bounds, returns to zero on non-scrollable axes and cannot lose the image offscreen.
Close/system Back dismiss it; reopen starts at fit. Activity recreation keeps the
selected image but resets ephemeral viewport state. Missing images show Image
unavailable. Viewer math/gestures have no inference authority.

The reported prior pan failure did not reproduce in the initial fresh 450x450
Wear API-36 lab run: fit, real pinch and pan all passed before changing math.
Production pan mathematics therefore remain unchanged. The old assertion used a
fixed positive screen-centered drag and only compared a formatted string; it did
not establish available motion, actual viewport bounds or clipping.

Acceptance now measures scale, pan, viewport/image dimensions, actual pan bounds,
gesture endpoints and direction. It moves toward available space, verifies a real
delta, reaches/clamps at an edge, and moves back inward. The pure unit edge test
likewise verifies outward clamping and inward movement. A separately reproduced
real-touch failure was in the harness: lazy row semantics extended under the fixed
composer footer; scrolling could also remain in a fling. Taps now intersect
all ancestor clips and require stable bounds across rendered frames. Scroll gestures end
with zero release velocity. Native system taps wait for the dialog window event;
accessibility viewport state is refreshed after render-frame barriers. A reproduced
pinch failure was an old cached 1x value: the failure tree already showed 2.72x.
Gestures use actual viewer bounds. At font 2.0 the composer footer leaves only
197 pixels for the list: scroll injection now uses that actual scrollable area,
not display-centered coordinates in the footer. Navigation waits for its lazy
list to exist. Rotary acceptance compares all visible reader geometry, since the
image can be offscreen at both ends of a valid scroll. No production clamp is loosened.

## Evidence

VERIFIED AUTOMATICALLY — 2026-11-01:

- One broad affected run: **400 tests / 47 suites**, zero failures/errors/skips.
  Covers generation contracts/output, shared completion/Stop ownership, file/replay
  lifecycle, text cancellation, image input, composer, Thinking, account/billing and
  credential isolation, viewport/projection/error/model UI regressions.
- Wear debug, Android test and unsigned/unminified release assemblies pass.
- Final debug lint: **0 errors / 10 warnings**. The initial broad run found one
  RestrictedApi error in the WIP debug fixture's ViewModelStore.put. It now uses the
  existing project's public ViewModelProvider factory pattern; final focused
  debug/test builds and lint pass. No suppression/baseline or dependency change.
- Python host tools: **40 tests pass**; 36 changed-document local links checked with no broken targets. Source/manifest/debug/release DEX isolation
  passes, including absence of ImageGenerationInstrumentation from both app APKs
  and absence of generation debug fixtures from release. Whitespace checks pass.
- No full Wear/mobile test claim: shared/mobile production, dependencies, credential
  records and SQLite transaction/schema owners are unchanged by #11.

Broad command actually run (documented JDK 17 / SDK 37; PowerShell):

```powershell
.\gradlew.bat :wear:testDebugUnitTest --tests 'io.github.grassified.wristprompt.openai.*' --tests '*ImageViewportTest' --tests '*GeneratedImageProjectionTest' --tests '*ComposerInteractionTest' --tests '*ChatPresentationTest' --tests '*ErrorPresentationTest' --tests '*ModelItemsTest' :wear:assembleDebug :wear:assembleDebugAndroidTest :wear:assembleRelease :wear:lintDebug '-Pwristprompt.generationAcceptance=true' --max-workers=2 --console=plain --no-daemon
```

After the debug-only lint/native acceptance fixes:

```powershell
.\gradlew.bat :wear:assembleDebug :wear:assembleDebugAndroidTest :wear:lintDebug '-Pwristprompt.generationAcceptance=true' --max-workers=2 --console=plain --no-daemon
& $env:CONDA_PYTHON_EXE -X utf8 tools/check_scenario_isolation.py
git diff --check
```

Host unittest discovery ran with UTF-8, bytecode writes disabled and a writable
throwaway temp directory, as documented for Windows in TESTING.md. A new lease
invalidation test first deterministically failed (COMPLETED instead of FAILED),
then passed after the final session validation was added. The entire 400-test
production gate ran after that correction. Later fixes affect only debug/native
acceptance; the 400 tests were not repeatedly rerun for them.

VERIFIED ON EMULATOR — disposable WristPrompt_Watch7, round 450x450, API 36:

| Font setting | Seed / explicit generation + Stop barriers | Force-stop / new-PID history reopen |
| --- | --- | --- |
| 1.0 | PASS | PASS |
| 2.0 | PASS | PASS |

Both fonts exercised actual JPEG decode/normalization, immutable promotion, real
SQLite completion, generated conversation display, native system-touch viewer open,
real two-pointer pinch, horizontal pan, edge clamp/outward no-op/inward recovery,
Close/system Back, reopen-at-fit, Activity recreation retaining the owner while
resetting viewport, and rotary movement in the surrounding reader. Viewer captures
wait for the decoded bitmap to appear, not just the dialog's semantics. Initial
capture attempts caught loading frames; those are not treated as rendering evidence.

The normal measured viewport is 315x288 for the 256x192 synthetic image. Recorded
pinch is approximately 2.72x; pan moves about 92 pixels toward available space and
clamps at the computed edge (approximately 271 pixels). Unit math separately checks
the 1–5x zoom limit, vertical bounds, anchor and reset behavior. Screenshots were
visually inspected; emulator evidence does not establish Galaxy Watch ergonomics.

Force-stop was issued only after verifying the named disposable AVD. Verify checks
a different PID from seed, restored generated ownership/title/history, resolved local
files and unchanged persistent mock request count. It opens history without sending.
This is a force-stop/cold-restart check; unplanned OS process death was not separately
induced here. Failed/stopped/interrupted replay exclusion, exact typed persistence,
no durable base64/re-upload, corrupt/missing file reads, temporary/orphan cleanup,
unreadable-store preservation, unknown-file preservation, quotas and imported-library
isolation have the focused automated coverage above. No unrelated destructive D6
instrumentation was rerun.

Executed native commands (each font 1.0 and 2.0):

```powershell
& $env:CONDA_PYTHON_EXE -X utf8 tools/wear_generation_acceptance.py --checkpoint seed --font-scale 1.0 --install --artifacts ../d7f-acceptance
# Verify AVD name WristPrompt_Watch7 before this test-only force-stop:
adb -s emulator-5564 shell am force-stop io.github.grassified.wristprompt
& $env:CONDA_PYTHON_EXE -X utf8 tools/wear_generation_acceptance.py --checkpoint verify --font-scale 1.0 --artifacts ../d7f-acceptance
```

Repeat with 2.0, without reinstalling. The runner restores the prior font setting in
finally. Every case requires explicit native PASS, not merely an instrumentation
exit code. Mock generation is explicit; viewer/navigation/recreation/restart/rotary
add **zero** inference requests. The seed's two extra generation attempts deliberately
exercise Stop before and after commit ownership. No live OpenAI request occurred.

Windows JVM tests inject only directory-sync behavior because Windows JDK directory
FileChannel open throws AccessDeniedException; production Android fsync remains the
default and is exercised natively. The fake provider explicitly supports the retained
Thinking value, and disk-backed waits use a real-time dispatcher. Cleanup assertions
check recognized owned files while preserving unknown files.

VERIFIED PHYSICALLY: NOT TESTED / intentionally deferred. Remaining consolidated
owner/live checks: actual supported API-key generation and billing/model/account,
Galaxy Watch rendering, pinch/pan/round ergonomics/large text/rotary, Stop before and
after completion ownership, restart/history reopen, and zero automatic generation.
No owner credentials, paid calls, owner installations, app data, Keystore or real
sessions were accessed or cleared. Keep #11 OPEN for that later evidence.
