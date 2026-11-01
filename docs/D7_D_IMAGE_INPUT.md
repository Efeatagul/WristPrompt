# D7-D image input and durable replay

Progress toward #9: implementation and automated acceptance; live-route/owner
physical acceptance is intentionally deferred to the consolidated end-of-D7 pass
before/as part of #12. Keep #9 open. This does not implement #10 or #11.

## Contracts reviewed 2026-11-01

Both explicit billing routes use the existing public `POST /v1/responses`, Bearer
authorization, `store=false`, `stream=true`, explicit history, and no
`previous_response_id`. There is one session/provider/HTTP/SSE stack.

- [Responses schema](https://developers.openai.com/api/reference/typescript/resources/responses)
  and [vision guide](https://developers.openai.com/api/docs/guides/images-vision?api-mode=responses):
  user content accepts ordered `input_text` and multiple `input_image` parts;
  `image_url` accepts a base64 data URL. JPEG is supported; omitted `detail` uses
  the model default. The current guide gives 512 MB/1,500 images as request ceilings;
  model/context limits still apply. WristPrompt's limits below are much smaller.
- [SIWC inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
  requires current account catalog membership and the public Responses endpoint.
  [SIWC preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
  explicitly permits image inputs when the model accepts them. Files upload is
  unavailable on this route. No Files API, remote URL, upload endpoint, Codex
  internals, private ChatGPT endpoint, or phone inference proxy is used.
- Exact model contracts checked for image input and Responses:
  [gpt-6.1-sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol),
  [gpt-6-astra](https://developers.openai.com/api/docs/models/gpt-6-astra),
  [gpt-5.5](https://developers.openai.com/api/docs/models/gpt-5.5),
  [gpt-5.4](https://developers.openai.com/api/docs/models/gpt-5.4).

`OpenAiImageCapabilities` is a dated, exact-ID application allowlist of those
contracts, intersected with the selected credential's current catalog. For plan
usage, support additionally relies on the SIWC image-input permission above.
Catalogs do not document per-entry image capability flags; unknown IDs, aliases,
snapshots, or future models fail closed, even if their names resemble known models.
This is contract-based eligibility, not a guarantee of account quota/access or live
provider acceptance. New IDs require source review. Neither route, billing mode,
credential nor model changes automatically; images are never silently stripped.
Text-only requests on unknown image-capability models continue through the existing
text path. Thinking capability policy remains separate and unchanged.

**Product decision: text is required.** Images-only messages are disabled. The
schema can represent image content, but #9 does not treat that as approval for an
images-only product interaction. Explicit images-only Send reports Text required;
no text is fabricated and no request is made.

## Canonical versus HTTP representation

Zero images preserves the ordinary `{role:"user", content:<exact text>}` form.
One/N images use one user message with `input_text` first (exact ordinary text),
then `input_image` parts in committed selection order. Each HTTP image is only
`{type:"input_image", image_url:"data:image/jpeg;base64,..."}`. There is no API
identity derived from a filename, path, digest, gallery URI, or local UUID.

Canonical snapshot version 3 keeps typed Text/ImageAttachment UUID parts and
separate replay context. Its local replay uses `image_attachment/attachment_id`
references, never provider data URLs. Each explicit Send resolves the complete
replay plus new turn into ephemeral HTTP input. Versions 1/2 text-only records
remain readable without changing their ordinary replay. Opaque validated output
items, including encrypted reasoning, remain ordered and unflattened.

## Request and storage bounds

- Existing selection limit: 6 distinct IDs per new turn. Inference also allows at
  most 6 image occurrences across the entire request, including historical replay.
  Repeated references in different turns count again. This is a conservative
  watch-memory/request budget; adding a seventh requires an explicit new chat or
  an explicitly changed selection. No history/image truncation or token estimate.
- Each normalized JPEG: 1–786,432 bytes, maximum 1280 pixels per edge. The D7-C
  store validates metadata, existence, bounded read, length and SHA-256 integrity;
  Android JPEG header/bounds/actual decode and thumbnail checks run at Send too.
- Encoded data URLs: at most 6,291,594 bytes total (6 times 1,048,576 base64 bytes
  plus each 23-byte prefix). Whole serialized request: at most 6,553,738 UTF-8
  bytes. Text-only retains its 262,144-byte request cap. Existing 8,192 prompt
  characters, 196,608 canonical context bytes and 12 attempts remain in force.
  Image token costs/context acceptance are provider-dependent, not claimed exact.
- Replay storage reuses the D7-C store: 24 distinct retained images/16 MiB per
  chat, including thumbnails, plus bounded metadata. All conversation image
  directories together have a conservative 64 MiB admission cap and at most
  100 directories. Metadata headroom is reserved before copying. Capacity failure
  is explicit; no eviction of another conversation's files.

## Durable ownership and cleanup

The watch owns `noBackupFilesDir/conversation_images_v1/<chat UUID>` separately
from `image_library_v1`. The existing D7-C atomic store retains immutable copies
under the same opaque attachment IDs with its own source/node binding and metadata.
All selected library records are resolved and validated before retention. Every
required completed-turn file is resolved again from this conversation-owned store;
there is no fallback to a library entry with the same ID.

Files/metadata commit before a pending conversation checkpoint can reference them.
A crash can leave an extra retained reservation, never a committed reference to a
not-yet-copied file. Failed/stopped attempts may leave bounded conservative
reservations until explicit chat deletion. A valid archive manifest owns those
reservations too: cleanup never guesses that an unobserved conversation is gone.
There is no global garbage collector or cross-account scan. Before-first-checkpoint
storage failure can conservatively retain a directory; the aggregate admission
cap prevents unbounded growth. Automatic removal of these ambiguous directories
is deliberately avoided because SQLite/file commits are separate.

Existing store cleanup removes only recognized temporary/orphan files outside the
valid manifest's retained set. Library tombstones/replacements and its orphan
cleanup cannot touch conversation directories. After an explicit, owner-checked
chat deletion durably tombstones the SQLite record, only recognized files in that
chat's directory may be removed. Failed filesystem deletion may leave safe extra
files; it never deletes another chat/library. A missing/corrupt required file or
archive manifest blocks continuation with Photo unavailable while preserving the
draft, selection and saved transcript. Missing replay metadata never initializes a
new archive over recoverable files. No binary blobs/base64 copies enter SQLite,
conversation JSON, settings or credential storage.

## Send, failures and privacy

Explicit Send snapshots text/IDs/model/configuration; validates capability, all
selected and required replay files, count and serialized byte limits before
consuming the draft. Async completion rechecks epoch, account/billing ownership,
draft token/text and committed selection. Validation errors issue zero inference
requests and preserve draft/selection. Unavailable selected photos are retained
until explicitly removed; picker Clear selection is available. Only an accepted
Send clears composer selection and opens chat. Double Send while work is owned
cannot duplicate inference.

The existing session revalidates current credential/catalog/model/controls and
image capability before provider dispatch. Real OkHttp cancellation remains Stop.
Only `response.completed` followed by durable completion commit admits the whole
user/assistant exchange into replay. HTTP failures, malformed/unfinished streams,
Stop, process interruption and completion-save failure do not admit the image
turn or partial assistant output. No retries, model fallback, route fallback,
background inference or per-token writes are introduced.

No logging of prompts, image bytes/data URLs, original URIs, private paths,
authenticated bodies or credentials. Attachment exceptions lose original causes;
UI errors use static categories. Tests compare encoded payloads using boolean
assertions with static messages, never body-dumping equality assertions.

## Verification

VERIFIED AUTOMATICALLY, final affected gate:

- 380 Wear tests / 43 suites, zero failures/errors/skips. Includes all `openai.*`,
  shared `images.*`, ShellInteraction/ShellNavigation/ShellNavigationViewModel,
  ErrorPresentation and ChatPresentation tests. Covers 0/1/N serialization,
  exact text/order, both billing routes, unknown capability, missing/deleted/corrupt/
  oversized files, total request bounds, v1/v2 migration, completed replay/restart,
  opaque reasoning/thinking, owner/credential isolation, real HTTP Stop, preflight
  Stop/account change, no retry/duplicate send, and completion-save failure.
- `:wear:assembleDebug :wear:assembleRelease :wear:lintDebug
  :wear:assembleDebugAndroidTest -Pwristprompt.imageInferenceAcceptance=true`
  passed in the combined affected gate. Lint: zero errors / eight warnings.
- `python3 tools/check_scenario_isolation.py`, release DEX exclusion of the new
  androidTest instrumentation, affected Markdown local links, focused logging/
  privacy/scope inspection and `git diff --check` passed.
- No production phone/shared files or dependencies changed; mobile tasks and the
  complete 500+ Wear suite were not run. An initial broad gate caught an async
  text-start/immediate-Stop regression and stale snapshot-version assertions;
  focused correction and the final 380-test gate passed afterward.

VERIFIED ON EMULATOR: disposable `WristPrompt_Watch7`, final build, explicit PASS
from both `seed` and `verify` checkpoints separated by process force-stop. Real
native normalized JPEGs, one/two-image explicit Send with counting provider,
unsupported model/route capability, missing/corrupt file errors with draft/selection
retention, image Stop exclusion, chat-owned files after library tombstones, inert
restart/reopen, and explicit continuation with ordered historical images passed.
This does not establish live-provider image acceptance or visible composer UX.

The native gate is opt-in and lives only in androidTest:

```sh
./gradlew :wear:assembleDebug :wear:assembleDebugAndroidTest -Pwristprompt.imageInferenceAcceptance=true
adb -s VERIFIED_DISPOSABLE_WATCH install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s VERIFIED_DISPOSABLE_WATCH install -r wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk
adb -s VERIFIED_DISPOSABLE_WATCH shell am instrument -w -e checkpoint seed io.github.grassified.wristprompt.test/io.github.grassified.wristprompt.storage.ImageInferenceInstrumentation
adb -s VERIFIED_DISPOSABLE_WATCH shell am force-stop io.github.grassified.wristprompt
adb -s VERIFIED_DISPOSABLE_WATCH shell am instrument -w -e checkpoint verify io.github.grassified.wristprompt.test/io.github.grassified.wristprompt.storage.ImageInferenceInstrumentation
```

Require each explicit PASS stream. The fixture uses a dedicated device-protected
`d7d-image-inference.db` and private fixture directory, synthetic credentials and a
counting provider. It never opens the production conversation DB/library. Seed
resets only that synthetic namespace. Default D6 instrumentation is unchanged.
This gate exercises VM/session/serialization/native storage, not system-picker UI,
paired transfer, visible composer interaction, or live OpenAI acceptance.

No owner physical tests were performed or awaited. Live images on both selected
billing routes, paired Galaxy Watch behavior and remaining D7 acceptance are
intentionally deferred to #12. No physical PASS is claimed.
