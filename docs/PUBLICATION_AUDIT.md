# Day 5 source-publication gate — 2026-10-30

## Gate result

**PASS — GPLv3 WITH NARROW LINKING EXCEPTION.**

All source-publication gates pass. The owner explicitly authorized the narrow
GPLv3 section 7 permission, a focused handoff commit, normal initial push of
sanitized main and public visibility for `grassified/WristPrompt`. This document
records the gate immediately before publication; external push/visibility results
must be verified separately. This is a development snapshot, not a stable release.

D5-F — PASS WITH KNOWN DEBT.
D5-G — PHYSICAL ACCEPTANCE PENDING.
D6 — NOT STARTED.

## Ownership and licensing

All 28 pre-publication main commits use the owner's public handle and GitHub
noreply identity as author and committer. No co-author trailers, separate copyright
attributions or copied third-party GPL source were identified in WristPrompt's
source/tools. This supports proceeding with the owner's express permission; Git
metadata is evidence of repository provenance, not an independent title guarantee.
Dependencies and the upstream Gradle wrapper remain separately licensed.

The initial license blocker was Google's SDK-licensed bundled Wearable client.
The owner authorized resolving it with [../LICENSE_EXCEPTION.md](../LICENSE_EXCEPTION.md),
following [GNU's conventional section 7 approach](https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs).
The exception covers only these unmodified Google artifact inputs at exact versions:

| Artifact in `com.google.android.gms` | Version |
| --- | --- |
| play-services-wearable | 20.0.1 |
| play-services-base | 18.5.0 |
| play-services-basement | 18.9.0 |
| play-services-tasks | 18.2.0 |

Both apps' offline releaseRuntimeClasspath reports and existing debug/release
resolved models agree on this set. The Wearable artifact provides Data Layer;
Base/Basement/Tasks are its dependencies; the coroutines adapter also uses Tasks.
All four POMs declare Android SDK License. No future version, other Google product
or arbitrary proprietary library is implicitly covered. No System Libraries
classification is assumed. Normal Android compilation/packaging is permitted.

The unchanged canonical [../LICENSE](../LICENSE) is GPLv3; the base SPDX identifier
stays GPL-3.0-only. The project-specific exception is documented separately and
has no SPDX-listed exception identifier. WristPrompt and its modifications remain
strong copyleft; its Corresponding Source and other applicable GPLv3 obligations
are preserved. The permission grants no rights on Google's behalf and does not
relicense dependencies. Source-only publication includes no SDK AARs or app binaries.

The bounded resolved dependency review covers 97 distinct production artifacts:
90 Apache-2.0, two BSD-2-Clause, one BSD-3-Clause and the four SDK-licensed artifacts
covered by the permission. ListenableFuture's Apache license is inherited from its
parent POM; both CommonMark 0.31.0 embedded JAR notices match the retained notice.
No other license incompatibility was identified in that set. See
[../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Complete binary transitive
notice packaging remains later RC work. Future contributors are expected to submit
WristPrompt-owned source under GPL-3.0-only plus the exception; no CLA, copyright
assignment or retroactive contributor consent is asserted.

## History and privacy remediation

Initial audit inspected 207 tracked files, 28 commits and 475 reachable file blobs
including app-managed local checkpoint refs. Eighteen historical Markdown blobs
across eight report/setup paths contained personal home paths. The owner authorized
sanitizing them. All 28 main commits retain sequence, author/committer metadata,
timestamps, messages and source/build content; only Markdown paths changed.
Original evidence commit IDs map to sanitized IDs in [HISTORY_REWRITE.md](HISTORY_REWRITE.md).
Measurements and DAY5_F_METRICS.json are unchanged. No engineering history was squashed.

Main's sanitized pre-handoff history has 448 reachable file blobs. App-managed
checkpoint refs/reflogs and the external recovery bundle may retain original
objects locally. They are outside the publication graph. Push only main; never
--mirror or all local refs. No force-push is needed for the verified empty remote.

No real credentials/private key, unnecessary private email/name, account ID or
physical-device serial/private IP was identified in the publication graph. All
commit author/committer metadata is public-handle/noreply. Synthetic fixture values,
public copyright notices and required loopback 127.0.0.1 protocol examples are
retained. Generic home/project paths and WATCH_SERIAL replace machine-specific
report examples without changing numerical evidence.

## Security and hygiene method

Screened current publication candidates and every reachable main file blob for
OpenAI credential shapes, JWTs, PEM private-key markers, quoted credential/password
assignments, home paths, private-network addresses and email/account/device
candidates; manually classified source/test/debug examples. Inspected historical
file paths, current sizes/types, manifests, dependency metadata and report metrics.
No external scanner was installed; gitleaks was unavailable. This is a bounded
pattern/manual audit, not proof that arbitrary unrecognized/encoded secrets cannot
exist. Unreachable objects/reflogs and provider-side storage are not certified.

No unsafe tracked/history env/local config, keystore, logs, network/credential dumps,
APK/AAB, IDE/cache/build state, media, heap or profiler dump was found. Source XML
assets and the Gradle wrapper JAR are intentional. Local ignored local.properties
contains only an SDK setting and is not published; ignored build outputs remain
local. No owner credentials/app data were read, changed, deleted or revoked.
Safe synthetic aggregate DAY5_F_METRICS.json remains unchanged, without chat text,
private account details, credentials, personal paths or device identifiers.

Ignore rules cover Android/build/IDE/native/capture, credentials/signing,
logs/temp/profiling and OS/editor artifacts. Twenty-nine representative ignore
cases pass. Required source, fixtures, documentation and wrapper remain included.
Canonical GPL text matches the downloaded GNU text byte-for-byte. Changed/new text
whitespace and all Markdown local-file links pass; git diff --check passes.

## Public handoff and validation

README, LICENSE, LICENSE_EXCEPTION, AGENTS, CONTRIBUTING, SECURITY, PRIVACY,
architecture/testing/Codex guides, CHANGELOG, PR template and notices are ready.
A new Codex account can build/test with its own matched debug app pair, understand
ownership/invariants, distinguish recorded evidence and identify next actions
without prior chats, owner credentials or owner signing keys. README/AGENTS/SPEC
and handoff documentation identify the base license plus narrow exception.

This pass changes license/documentation/ignore files only. Production, test,
build configuration and tooling behavior remain unchanged. Actual validation:

```sh
./gradlew --offline :wear:dependencies --configuration releaseRuntimeClasspath \
  :mobile:dependencies --configuration releaseRuntimeClasspath --console=plain
git diff --check
```

Both dependency tasks PASS. Additional bounded repository checks cover current and
main-history secrets/privacy/artifacts, author metadata, dependency licenses,
internal links, whitespace, ignore cases, GPL/notice equality, and unchanged
source/build/tool/metrics content. The previous D5 baseline is recorded evidence,
not newly rerun here: Wear 459 full tests / 52 suites plus final 40-test and
18-test narrow runs; 461 final inventory was not fully rerun. Emulator readiness
77/77; builds/lint/isolation and 40 Python tests remain D5-F evidence. No full
unit/emulator/build/lint matrix or physical-device action was run for prose/licensing.

## Publication checklist

- [x] GPL-3.0-only base, canonical LICENSE unchanged, narrow owner-authorized exception.
- [x] Actual Google artifact/version scope and source ownership reviewed.
- [x] Third-party licenses/notices intact; no other incompatibility identified.
- [x] No real credential/private signing material identified in bounded current/history scans.
- [x] Main history sanitized; no unsafe logs/artifacts/media or private identifiers identified.
- [x] Author metadata public-safe; ignore rules and aggregate metrics checked.
- [x] Developer/Codex documentation, local links and whitespace pass.
- [x] D5-F status accurate; D5-G pending; D6 not started.
- [x] Target/auth confirmed: grassified/WristPrompt, private/empty, owner admin/push access.
- [x] Normal main-only source push and public visibility explicitly authorized.

Remote checks before publication: repository exists, private and empty; branch
setting main; authentication/permissions valid. Dependabot alerts were disabled;
private vulnerability-reporting and SBOM endpoints returned 404 on the empty private
repository, so their availability/enabled state was not established. Recommend
verifying/enabling suitable repo-level security features after public publication;
SECURITY.md does not invent a private email or claim reporting is enabled. No
account/organization security setting was changed.

Both apps remain versionName 0.1.0 / versionCode 1. No tag/release or binary upload
is planned. Publishing source does not close D5-G or imply production readiness.
The GitHub repository README, license, docs, commit and public visibility must be
verified after the initial push. Product debt remains in [CODEX_HANDOFF.md](CODEX_HANDOFF.md).
Next owner action: D5-G physical P1–P13. After supplied results, close/remediate Day 5;
only then separately authorize D6-0 read-only persistence architecture audit.

## Changed files

Handoff: .gitignore, README.md, LICENSE, LICENSE_EXCEPTION.md, AGENTS.md,
PROJECT_SPEC.md, ROADMAP.md, DECISIONS.md, DEVELOPMENT.md, CONTRIBUTING.md,
SECURITY.md, PRIVACY.md, CHANGELOG.md, THIRD_PARTY_NOTICES.md, DAY5_STATUS.md,
.github/pull_request_template.md, and docs/{ARCHITECTURE,TESTING,CODEX_HANDOFF,
PUBLICATION_AUDIT,HISTORY_REWRITE}.md. Current report emulator-label sanitation:
DAY3_STATUS.md, DAY4_REPORT.md, DAY4_STATUS.md, DAY5_C_REPORT.md, DAY5_E_REPORT.md.
Historical path-only changes are listed in HISTORY_REWRITE.md; current source,
build/test/tooling and aggregate measurements are unchanged.
