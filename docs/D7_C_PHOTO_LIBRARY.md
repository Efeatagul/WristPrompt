# D7-C imported photo library

Progress toward #8: implementation and focused automated acceptance; paired owner
physical transfer acceptance is deferred to #12. Keep #8 open. No #9 Responses
image serialization, #10 combined-composer persistence, or #11 generation is added.
PR #21 and its D6-D work are outside this change.

## Representation and ownership

Phone and watch use separate `noBackupFilesDir/image_library_v1` directories.
Normalized JPEGs and thumbnail JPEGs are immutable revision files. A bounded,
versioned canonical JSON manifest holds only opaque random UUIDs, revision,
dimensions, byte counts, internal integrity digests, source identity and (on the
watch) pinned phone node. No original URI, name, path or EXIF survives import.
Digests bind Assets to metadata; they are never identity, display or diagnostics.
No image bytes enter DataStore, SQLite text/JSON, preset DTOs or credential DTOs.
The existing D6 `ContentPart.ImageAttachment(attachmentId)` UUID representation
can reference these IDs in #9; this issue does not construct inference content.

Each application owns one store instance. Its mutex serializes metadata/file
transactions, including concurrent imports, deletion, publication and reception.
All file/decode work runs off Main. Existing secure-window and backup policies,
credentials, billing routes and model ownership remain unchanged. The library is
independent of an OpenAI account; changing accounts does not upload its contents.

## Limits and normalization

- One import/picker selection: at most 6 images; at most 24 live images.
- Entire live library, including thumbnails: at most 16 MiB on each device.
- At most 256 total identity records, including terminal tombstones. Deletion
  does not free this lifetime identity budget. No automatic eviction/reset/rebind.
- Input: at most 20 MiB compressed per image, 20,000 pixels per edge and 100 MP.
- Output: JPEG, at most 1280 pixels per edge and 768 KiB per image.
- Thumbnail: JPEG, at most 160 pixels per edge and 24 KiB.
- Manifest: 128 KiB; packet metadata: 2 KiB; at most 256 per-source DataItems.

`PickMultipleVisualMedia(6)` requests images only. Import validates returned count
again, since a platform fallback can ignore its selection limit. Streams are read
under the temporary Picker grant with a hard byte cap; the original URI is never
a durable source. Import does not continue across process death and needs no
persisted gallery grant or broad media/storage permission. If the Picker handler
is absent, the phone shows a recovery message without crashing.

JPEG/PNG/WebP are accepted according to decoder-detected type. Bounds are checked
before allocating pixels, then power-of-two sampling limits intermediate decode.
AndroidX EXIF applies all eight orientation values; aspect-preserving final scaling
bounds the image. Transparent pixels flatten onto white. JPEG recompression tries
85/70/55/40 quality, rejecting if none meets the cap. The thumbnail comes from the
same oriented normalized bitmap. Animated input, when supported by BitmapFactory,
is a still decoded frame; animation is not retained. Original metadata is stripped.
Malformed, unsupported, oversized or unreasonably dimensioned input fails the
whole batch. JPEGs without a terminal marker are rejected. This is defensive
platform decoding, not a guarantee that every malformed codec input is detected.

The official AndroidX EXIF 1.4.2 metadata reader is the sole new dependency. Lint
flags known security bugs in older `android.media.ExifInterface` implementations;
the phone supports API 26. This justifies using the maintained official metadata
reader rather than the old platform parser. No general image-loading library or
existing version upgrade is introduced. Its resolved POM is Apache-2.0; see
[third-party notices](../THIRD_PARTY_NOTICES.md). The Google exception is unchanged.

## Transaction, deletion and recovery

Phone imports are all-or-nothing: normalize and validate sequentially, write and
fsync files, then atomically replace the manifest as the sole commit point. A
capacity/codec/file failure preserves the old manifest and existing library.
Temporary files use `.part`; revision files are promoted with same-directory
`Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`. Failure to support atomic movement is
an error, never a non-atomic fallback. Scratch files temporarily require additional
space beyond the live-library budget. Storage-full errors preserve old metadata.

Deletion commits a text-free, byte-free terminal tombstone before removing files.
A replacement revision commits before old revision files are removed. Restart/load
removes recognized temp/orphan/deleted/replaced files only within this dedicated
directory, never recursively. Cleanup examines at most 1024 entries per pass; the
bounded writer normally creates fewer. Failed deletion is retried on later loads.
Unknown files are untouched. Unreadable/unsupported metadata fails closed and
preserves files; it is never replaced with an empty library.

Missing/size-mismatched files stay out of watch browsing; metadata is retained to
prevent state regression and permit same-revision repair. Content reads verify
integrity digests and fail safely. Phone metadata remains manageable/deletable
when a file is missing. No partial staged record is published as a live item.
These are process-interruption guarantees, not a forensic-erasure or hardware
power-loss durability claim.

## Data Layer contract and ordering

Images use `/wristprompt/images/v1/<opaque-id>` DataItems. Scalar data is canonical
bounded `ImagePacket` metadata; `image` and `thumbnail` are separate Data Layer
Assets. Tombstones have no Assets. Ordinary MessageClient payloads and preset/auth
protocols never carry image bytes. Receiver metadata is bounded/parsed before any
Asset resolution. Asset reads have independent byte caps, matching digests and
actual JPEG decode/dimension validation before local promotion.

Discovery reuses D7-B `WearConnectivity` capability validation (`FILTER_REACHABLE`).
First binding requires exactly one eligible phone; node/source commit with the
first accepted record. Later source/node changes fail closed. Query URIs bind the
DataItems to that phone; callback payloads are not trusted as import authority.
A live Asset acquisition rechecks reachable phone identity before commit.

Per-ID ordering is durable: identical duplicates are idempotent; lower revisions
are ignored; equal conflicting revisions fail; terminal tombstones reject all
later live revisions. Replayed old items cannot resurrect deletion. Items are
processed individually, with tombstones first; phone import is atomic locally but
cross-device batch visibility is intentionally incremental. A bad or interrupted
transfer never exposes that record, while already accepted records remain valid.
DataItem removal alone never means image deletion.

No application storage ACK is asserted. Phone `QUEUED` means only DataClient
accepted publication; the UI explicitly says this is not watch-storage confirmation.
Persistent DataItems/Assets remain available for finite re-pulls on startup,
capability/reconnect events, changed data, and explicit refresh/retry. Each operation
has a 60-second deadline; no polling/backoff/unlimited application retry loop is
added. Missing Assets, disconnect, storage-full, timeout or process interruption
leave old state intact; a later refresh can repair a missing same-revision file.
A disconnected watch browses already stored thumbnails without phone access.
Retries have no inference dependency and cannot dispatch generation.

## Watch selection and #9 boundary

Menu → Photos and the composer’s Photos action open a retained picker edit.
It supports zero/one/multiple selections (up to 6), per-photo removal, explicit Use
selection, and Cancel/back. The pending edit is separate from the composer-owned
UUID list. Apply checks the exact text draft, input ownership token, previous
selection and current local files; stale edits fail without overwriting the draft.
Cancel changes neither text nor committed selections. Deleted/unavailable records
are pruned from committed selection. Activity recreation/navigation use existing
retained ViewModels. New Chat/opening another chat clear selected IDs; process-death
unsent draft/selection persistence belongs to #10, not this change.

Selected photos block Send in the UI and ViewModel until #9 supplies image
serialization, preventing accidental text-only submission. Opening/selecting,
deselecting, syncing, restarting, import and deletion never call inference.

## Evidence — 2026-11-01

VERIFIED AUTOMATICALLY:

- Initial complete mobile/shared affected pass: 98 tests / 17 suites; focused Wear
  pass: 36 tests / 4 suites; no failures. Includes shared image tests and composer
  ownership/zero-provider-call tests, not the full 530+ Wear suite or #18 rerun.
- Storage/protocol tests cover multi-import stable IDs/reload/thumbnail association,
  per-image/count/total-byte limits, rollback, orphan/temp cleanup, first transfer,
  peer/source rejection, duplicate/stale/conflicting revisions, terminal deletion,
  resurrection rejection, missing/corrupt/interrupted Assets, interrupted promotion,
  restart, missing-file repair, asset-key ownership, bounded/versioned metadata and
  redacted printable objects. Selection tests cover zero/one/multiple, deselection,
  stale selection and deletion; real ViewModel tests cover Cancel/text preservation
  and zero provider calls, including blocked Send with selected photos.
- Both debug builds and lints passed. The focused follow-up for missing-Picker
  recovery also passed. After the AndroidX EXIF replacement: 10 phone/shared and
  27 focused watch tests passed, both debug/release builds and both lints passed,
  and the native phone instrumentation was rerun with its explicit PASS stream.
  No final full Wear suite is claimed. Lint has no errors; platform EXIF warnings
  are removed. Remaining warnings concern existing SDK/tooling/UI conventions.
- `git diff --check` and focused privacy/scope checks apply to the final diff.

VERIFIED ON EMULATOR:

- Disposable `WristPrompt_Phone`, standalone `ImageImportInstrumentation`, explicit
  PASS stream: portrait/landscape; all 8 EXIF orientations (90° includes pixel-side
  check); JPEG/PNG/WebP; large-image sampling/final bounds; thumbnail size/association;
  invalid/truncated/unsupported input; zero/negative/absurd dimensions; oversize
  compressed input; corrupt/mismatched canonical Assets; real private-file
  multi-import/reload/failed-batch rollback and cleanup.
- The phone AVD has no activity handling Photo Picker's request. Actual multi-select
  system UI automation is NOT VERIFIED on this lab. The native import gate is
  separate from that missing platform UI; no production permission/capability
  check is weakened to bypass it. After the fix, tapping Import photos showed
  the explicit Picker-unavailable message in the still-running companion UI.
- Round-watch offline UI uses three explicitly seeded synthetic JPEG/thumbnail
  records in a previously empty library, not claimed Data Layer delivery.
  Two photos were selected and explicitly committed, then reopening showed
  `2/6 selected`. At font scale 2.0, Remove photo 1 stayed within its rounded
  button; tapping it produced Select photo 1. A synthetic rotary scroll moved the
  list. Normal/max-font screenshots were visually inspected; touch scrolling
  exposes the other rows/actions. Cancelling the pending one-photo deselection
  and reopening restored `2/6 selected`. A process restart still exposed the
  seeded offline thumbnails. The original empty emulator catalog was restored
  afterward; no owner app data was cleared.
  These are UI fixture observations, not real paired Asset transfer or a hardware
  performance/accessibility claim. Zero provider calls were measured in the
  ViewModel tests; no live emulator HTTP counter is claimed.

Paired capability-valid GMS Asset delivery is NOT VERIFIED by this synthetic lab.
All owner phone/Galaxy Watch acceptance, including actual Picker multi-selection,
thumbnail fidelity, disconnect/reconnect/interrupt/delete/restart/storage-full,
maximum font/rotary/touch and zero automatic inference, remains deferred to the
consolidated end-of-D7 pass before/as part of #12. No physical PASS is claimed.

## Official contracts actually relied upon

Checked 2026-11-01, primary sources:

- [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker):
  multiple visual selection, returned-count fallback limit, temporary grants and
  optional persistent grants. This implementation copies immediately into private
  storage rather than depending on continued gallery access.
- [BitmapFactory.Options](https://developer.android.com/reference/android/graphics/BitmapFactory.Options):
  bounds-only decode avoids pixel allocation; sampling bounds intermediate memory.
- [EXIF reader](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface)
  and [stable release](https://developer.android.com/jetpack/androidx/releases/exifinterface):
  orientation metadata and the maintained compatible reader.
- [Wear sync/Assets](https://developer.android.com/training/wearables/data/sync):
  Assets carry binary blobs; Data Layer caching/replication is not the app’s durable
  storage. `getFdForAsset` supplies a stream that must be closed/released.
- [DataItem limits](https://developer.android.com/training/wearables/data/data-items)
  and [client choice](https://developer.android.com/training/wearables/data/client-types):
  ordinary small payloads are bounded around 100 KB; larger binaries belong in
  Assets. This design uses at most 2 KiB scalar metadata and no image messages.
- [Atomic move](https://developer.android.com/reference/java/nio/file/StandardCopyOption):
  API-26 atomic same-filesystem promotion; no fallback to non-atomic copying.
