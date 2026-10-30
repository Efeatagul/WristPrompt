# Contributing

Read [AGENTS.md](AGENTS.md), [PROJECT_SPEC.md](PROJECT_SPEC.md),
[ROADMAP.md](ROADMAP.md) and relevant [DECISIONS.md](DECISIONS.md) sections first.
The next task is owner D5-G acceptance, not D6 implementation.

Use the JDK/SDK versions and setup in [README.md](README.md) and
[DEVELOPMENT.md](DEVELOPMENT.md). Build both debug apps with the wrapper and use
matching package/signing certificates for Data Layer. Keep credentials and signing
keys outside Git. A different Codex account needs no previous chat or owner secret.

Use Kotlin, Gradle Kotlin DSL, Compose, coroutines/Flow, ViewModel, OkHttp,
kotlinx.serialization, Preferences DataStore and manual dependency injection.
Keep state immutable, protocols centralized and changes within the authorized phase.
Prefer focused fixes over frameworks or unrelated refactors. Never silently switch
billing modes or introduce credential fallback.

Run focused affected tests while editing. After stabilization, run the appropriate
complete affected validation once; [docs/TESTING.md](docs/TESTING.md) lists commands.
Documentation-only changes need whitespace, links and secret/privacy/license checks,
not the full emulator/build matrix. Debug scenario code must remain absent from
release. Record commands actually run and pending checks in the day's status/report.

Use VERIFIED AUTOMATICALLY for unit/static/build evidence, VERIFIED ON EMULATOR
for synthetic/device-emulator evidence and VERIFIED PHYSICALLY only for recorded
owner/device results. Pending physical work remains pending. Never run destructive
physical tests, clear owner data or revoke sessions without exact authorization.

Never commit secrets, raw personal logs, account IDs, private conversations,
local-machine paths or signing material. Report vulnerabilities according to
[SECURITY.md](SECURITY.md); do not put credential-bearing reproductions in issues.

Keep commits focused and describe the actual change and validation. Use the PR
template and identify security/privacy impact and known debt. Do not commit/push
or change external services without user authorization. Contributions to WristPrompt
are expected to be provided under **GPL-3.0-only plus the project linking exception**
in [LICENSE_EXCEPTION.md](LICENSE_EXCEPTION.md) for WristPrompt-owned source.
Contributors retain their copyright; no copyright assignment, CLA or DCO is required
by this policy. Submit only material you have authority to license on these terms.
This policy states expectations for future contributions; it does not claim consent
from earlier contributors or grant permissions for separately licensed third-party
code. Preserve third-party notices. GPLv3 section 7 permits downstream removal of
additional permissions as described in the exception.
