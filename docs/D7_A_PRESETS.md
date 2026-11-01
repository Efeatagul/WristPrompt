# D7-A — phone prompt presets

Issue #6. Phone-managed ordinary USER text: list, create, edit/rename and confirmed
delete. Nothing applies presets to a conversation or invokes inference. D7-B
transport and watch insertion are absent.

## Storage and representation

A separate Preferences DataStore (`prompt_presets`, key `catalog_v1`) commits one
bounded versioned JSON catalog atomically. Manual Application injection and a
retained ViewModel follow existing conventions. Unlike the D6 conversation SQLite
store, this small phone catalog needs no indexed history/replay queries or owner
bindings; the existing DataStore dependency suffices. Serialization and storage run
off Main. Credentials, auth DTOs, chat replay and inference have no dependency on it.

The shared pure codec defines version 1: `version`, `records`, with each record
containing `id`, `revision`, `name`, `text`, `deleted`. IDs are canonical lowercase
UUIDv4; creation starts at revision 1. Every successful edit, rename or deletion
increments that preset's revision with compare-and-set on the expected revision.
Concurrent stale writes fail without partial commit. Overflow fails explicitly.
There are no timestamps or wall-clock ordering assumptions. Identical names are
allowed; IDs distinguish records. Edits preserve exact text and never trim it.

Limits (UTF-16 code units for name/text; UTF-8 bytes for serialized storage):

- Name: 1–80, nonblank, no surrounding whitespace or control characters.
- Prompt: 1–8,192, nonblank, multiline, tabs/CR/LF allowed; other controls rejected.
- Valid Unicode only, including paired supplementary characters.
- 50 live presets; 256 total lifetime IDs including tombstones.
- 512 KiB for the complete canonical encoded catalog (JSON escaping included).

Deletion replaces name/text with empty strings and keeps ID/revision forever in
this schema. It does not promise forensic erasure of filesystem remnants. IDs are
never reused. A new preset after deletion receives a fresh ID. No automatic
compaction, eviction or destructive recovery. At the lifetime-ID limit creation
fails clearly; editing/deleting existing records remains available. Future
compaction needs a separately designed acknowledged sync/reset protocol.

The JSON codec is also the future sync representation. It validates versions,
IDs, positive revisions, duplicates, content, record count and byte limits before
use. Only its canonical encoding is accepted, rejecting extra/duplicate fields,
omitted defaults and ambiguous forms. Unsupported/corrupt saved data is preserved
and access fails safely; it is never replaced with an empty catalog.

A pure reference merge defines partial create/update/delete semantics: missing IDs
are additions (including tombstones); lower revisions are ignored; equal revisions
are idempotent only for identical records, otherwise conflict; higher revisions
replace live records. Tombstones cannot become live again, even at higher revision.
Merge validates the resulting catalog too and fails atomically on conflicts or
limits. Missing records never imply deletion. The phone remains the authority.
Future D7-B must bind sender/installation and choose bounded batching/transport;
this issue adds no Data Layer path, receiver, publisher or watch composer UI.
A catalog is not a promise that its entire size fits one future transport message.

## UI and lifecycle

Native Material 3 controls appear below existing companion setup. A single editor
handles name and multiline text; Save commits both together. Delete and discarding
changed drafts require confirmation. No inference or automatic application action
exists. The editor and in-flight save survive Activity recreation in the ViewModel.
Successful Save closes only after persistence returns. Failed Save keeps the draft
and displays a fixed sanitized message. Process restart reloads committed data;
unsaved drafts are deliberately memory-only and may be lost on process death.

Names, text, IDs and catalog metadata are not logged. Domain, catalog, editor and
UI-state `toString()` methods are redacted. Storage/parser errors are replaced with
fixed enum-based exceptions without raw causes. Presets are app-private content,
not credential-grade encrypted storage. Existing backup exclusions and FLAG_SECURE
remain in force; no clipboard, analytics or diagnostics integration is added.

## Evidence and remaining checks

Focused JVM tests use synthetic content and real temporary DataStore files,
including cancellation/join and recreation of the store after create/edit/rename/
delete. They cover ordering/conflicts, limits, malformed/future serialization,
exact sync round trips, corruption preservation, capacity rollback, sanitized
storage failures, redaction and local-only ViewModel operations.

VERIFIED AUTOMATICALLY (2026-11-01): focused preset tests passed during iteration.
The final gate passed with 82 mobile tests and 6 shared preset tests compiled in
Wear, zero failures/errors; mobile debug/release assembly and debug lint passed.
Lint reported three existing warnings (OldTargetApi, ExportedService, VectorPath),
none in preset code. Release output remains unsigned/unminified per project setup.

Final command (existing JDK 17 / Android environment from DEVELOPMENT.md):

```sh
./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:assembleRelease :mobile:lintDebug :wear:testDebugUnitTest --tests '*presets*'
git diff --check
```

The filter applies to the Wear task; the mobile task ran all 82 tests. Static
review found no log, bridge transport, auth, replay or network imports/calls in the
preset implementation. No dependencies were added. No emulator or physical
result is claimed. Pending manual phone checks: multiline keyboard and
scrolling, edit/rename/delete confirmation, rotate while editing/saving, committed
content after force-stop/relaunch, large font and TalkBack. Use synthetic prompts;
no real app data clearing or credential actions are required. These checks do not
authorize D7-B or issue #7.
