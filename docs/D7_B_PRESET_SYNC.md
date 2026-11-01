# D7-B — watch preset sync and explicit insertion

Issue #7; based on merged D7-A / PR #22 (`6c5b8f4`). No image work, credential
protocol change, inference transport change or automatic Send is included.

## Wire and authority

The existing WearConnectivity owns the new DataClient calls alongside its unchanged
MessageClient/auth interface. Android documents a 100 KB DataItem payload limit:
[Data items](https://developer.android.com/training/wearables/data/data-items) and
[event delivery](https://developer.android.com/training/wearables/data/events).
One persistent DataItem per preset uses `/wristprompt/presets/v1/<UUIDv4>` with raw
canonical JSON, at most 64 KiB including its envelope. No Asset or image transport.
The envelope contains version 1, a separate random phone preset-source UUID, and a
D7-A version-1 catalog containing exactly one record. Path and record ID must match.
All D7-A Unicode/content/revision/tombstone limits and redaction remain enforced.

Data Layer enforces matching package/signature. The publisher uses the existing
reachable Wear capability before queuing data. The receiver independently queries
the existing reachable MOBILE capability, reads items for that node's URI authority,
and requires exactly one eligible phone for first binding. It atomically pins that
node ID plus the preset-source UUID with the first validated catalog. Later nodes
or source UUIDs cannot replace that binding. This uses no credential consent/DTO,
auth host ID, token or account identity. Source identity is persisted in the phone's
preset DataStore, independently of credentials and conversations.

Before parsing, reads cap packet size, total batch bytes (576 KiB including envelope
overhead), and count (256). A complete batch must validate before one store edit.
Logical merged catalogs retain D7-A's 512 KiB / 50 live / 256 lifetime-ID bounds.
Duplicate IDs, malformed UTF-8, path mismatch, noncanonical serialization, invalid
IDs/content, mixed sources and unsupported versions fail closed. Nothing logs a
packet, name, text, ID, node, source or raw parser/storage/transport exception.

The D7-A merge is unchanged: lower revisions are ignored; equal identical revisions
are idempotent; conflicting equal revisions reject the batch; higher revisions
replace live entries. Terminal tombstones cannot become live again. Missing records
and DataItem deletion events never mean preset deletion. A phone delete publishes
a higher-revision, text-free tombstone at the same path. Tombstones are retained on
both devices; no automatic eviction or source reset is introduced.

## Delivery and restart

Phone successful commits queue publication in an application IO scope. Publication
is serialized and reloads current durable state inside its mutex. It runs on phone
startup, reachable-capability events, and explicit retry too. A failed publication
does not undo the local save or claim watch delivery. UI status distinguishes
waiting, failed, and queued in GMS; queued is not a watch-storage ACK.

DataItems retain the latest per-record state for reconnect. The watch re-reads the
bounded batch on data-change/capability events, startup and opening/refreshing
Prompts. The watch sync mutex serializes refreshes. Reading the batch together avoids
an out-of-order create exceeding the live limit before its corresponding tombstone
arrives; a later event/refresh retries the retained final state. No polling, generic
connectivity observer, foreground service, WorkManager or inference retry exists.
A process interrupted before publication/acceptance recovers from durable phone
records/DataItems on the next startup/reconnect/explicit refresh.

A separate Preferences DataStore (`synced_prompt_presets`) commits the validated
catalog and source binding atomically off Main. It survives process restart and
permits offline browsing without a currently reachable phone. Corruption/future
versions fail safely without resetting data. No credential/replay/history storage
is reused. App-private preset content is not credential-grade encrypted storage;
existing backup exclusions remain.

## Composer ownership

Composer → Prompts → select opens a review/choice screen. Selection captures the
record revision, current draft and existing input-owner token. Apply re-reads the
store and rejects changed/deleted selections. Cancellation/dismissal also invalidates
an in-flight selection before its storage read can apply anything.

- Insert: if draft is empty, exact preset text; otherwise exact existing draft +
  two LF characters (`\n\n`) + exact preset text. Whitespace-only drafts are nonempty.
- Replace: exact preset text, only by explicit action when a draft already exists.
- Cancel/Back: no draft write. Original text remains byte-for-byte unchanged.

InferenceViewModel retains draft ownership. It verifies the captured token and
exact draft again, computes the result, and rejects anything over the real
ChatConversation 8,192 UTF-16-unit limit. It never trims or truncates. Changed/busy
contexts reject rather than overwriting user edits. Successful insertion invalidates
late platform-input results and returns to review; it does not call Send or alter
model/account/billing/replay. Later sync never changes inserted ordinary draft text.
A native multiline draft field with an explicit keyboard Done action permits
editing/removing/adding ordinary text; the
existing platform replacement action remains available. Send remains explicit and
existing actual HTTP Stop is unchanged.

The picker uses the existing round-screen ShellList, native Wear buttons and rotary
scrolling. Detail actions start at the top independently of the list position;
Insert/Replace/Cancel precede long text previews. No selectable metadata besides
preset names is invented. Unsaved drafts keep the existing process-memory lifetime.

## Evidence and remaining checks

VERIFIED AUTOMATICALLY: 88 mobile tests, 95 affected Wear/shared tests, zero
failures/errors. Includes real temporary DataStore phone CRUD → wire → watch
acceptance/restart, binding rejection, malformed/version/bounds, duplicate/stale/
conflicting/tombstone replay, exact draft preservation, edits, sync-after-insertion,
explicit Send and actual HTTP Stop regression. Shared auth protocol/peer and shell
ownership tests also pass. No provider/network is wired into sync owners.

```sh
./gradlew :mobile:testDebugUnitTest :wear:testDebugUnitTest --tests '*presets*' --tests 'io.github.grassified.wristprompt.openai.ChatViewModelTest' --tests '*InferenceSessionTest' --tests '*StreamingRaceTest' --tests '*StreamingHttpCancellationTest' --tests '*ComposerInteractionTest' --tests '*ShellInteractionTest' --tests '*ShellNavigationViewModelTest' --tests '*WatchAuthBridgeTest' --tests '*BridgeProtocolTest' :mobile:assembleDebug :wear:assembleDebug :wear:assembleRelease :mobile:lintDebug :wear:lintDebug
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_scenario_isolation.py
git diff --check
```

Both debug builds, Wear release and both lint tasks passed. Lint has no errors
(2 mobile / 6 Wear warnings outside preset code). Forty tool tests and manifest/DEX
release isolation passed. A Wear-only build/release/lint follow-up passed after the
max-font detail-scroll fix. A final focused preset/composer test and both-app build/
lint follow-up passed after making the accept API return Unit (never raw Preferences).
Final Wear debug/release assembly and lint also passed after adding keyboard Done. No mobile release build was required or claimed.

The debug-only `PROMPT_PRESETS` scenario uses a synthetic in-memory store and
provider, no real transport/persistence/credentials. Its result does not establish
paired Data Layer delivery or owner hardware behavior. VERIFIED ON EMULATOR: the synthetic round 454×454 watch fixture passed normal
font insertion, nonempty Cancel/back with exact draft length preserved, and zero
inference generations. At font scale 1.24 it passed rotary scrolling, Insert,
Replace, oversized Insert rejection, Cancel/back and zero generations. Screenshots
were visually reviewed for round-screen layout and action reachability; native Wear
buttons provide large targets. The detail-scroll fix was discovered and rechecked
here. This is synthetic UI evidence, not actual phone-to-watch sync or hardware.
Native touch-keyboard editing and its Done action also passed on the final build:
inserted draft length changed from 33 to 35 after two touch-key entries, with zero
generations. Removal/editing semantics are additionally covered by unit tests.
Early hardware-key injection/charging-overlay automation attempts were inconclusive;
normal touch-keyboard entry is the successful emulator evidence.

Pending paired/physical checks: phone create/edit/rename/delete through actual GMS,
watch process restart/offline browsing and reconnect delivery on the paired devices;
physical rotary/IME/TalkBack. The existing lab's reciprocal capability limitation is
documented in TESTING.md; production gating was preserved. No physical PASS is
claimed, and no owner app data/credentials were cleared.

Known limits: source/node replacement after phone reinstall is deliberately rejected;
no automatic rebind/reset exists. Supporting that safely needs an explicit future
user-mediated operation, not a credential change or silently erased tombstones.
D7-A's 256 lifetime-ID limit remains; queued status is not a delivery ACK. These are
explicit bounds, not automatic fallbacks. D7-C and D7-E remain unimplemented.
