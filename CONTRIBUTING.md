# Contributing

Read [AGENTS.md](AGENTS.md), [PROJECT_SPEC.md](PROJECT_SPEC.md),
[ROADMAP.md](ROADMAP.md), and relevant [DECISIONS.md](DECISIONS.md) sections
before changing the project. Check [docs/CODEX_HANDOFF.md](docs/CODEX_HANDOFF.md)
for current status and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for ownership.

## Branches

Start each task from the latest `main`. Fetch and update it before creating a
fresh branch dedicated to one issue, feature, fix, or coherent maintenance task.
Use a short, scope-revealing name, for example `d7/18-thinking-controls`,
`fix/1-markdown-tables`, or `docs/contribution-workflow`.

Do not work directly on `main`, reuse a long-lived development branch for
multiple issues, or carry unrelated unmerged work into a new task. After a PR
merges, return to `main`, update it, and create a new branch for the next task.

## One issue / one PR

Open one focused PR per branch. Keep it to the smallest coherent change that
satisfies its linked issue. Do not add unrelated refactors, formatting, warning
cleanup, dependency upgrades, documentation rewrites, independent features, or
speculative cleanup. Do not silently expand the issue's scope. If the issue
requires a large change, explain why it cannot reasonably be smaller; there is
no arbitrary line-count limit.

## Dependent or stacked work

Prefer waiting for a dependency PR to merge. If work must continue first, mark
the PR as stacked, document its dependency, and intentionally base it on that
dependency branch. Make clear that its cumulative diff is not independent. Once
the dependency merges, rebase or reconstruct the branch on current `main` and
check that the PR contains only its intended change.

## Commit discipline

Use a small number of meaningful commits (often one to three for a normal task).
Describe the actual logical change; avoid placeholder or repetitive commits and
one trivial commit per file. Preserve contributor authorship. Do not rewrite or
squash another contributor's history without authorization, and do not
force-push shared or external branches without explicit authorization.

## Before opening or updating a PR

Fetch current `main`, determine whether it has moved materially, and integrate
or rebase safely where appropriate. Inspect the final diff and commit range
against `main`; confirm there are no unrelated files or commits. Rerun affected
validation after conflict resolution and record the commands and results in the
PR. Use the [pull request template](.github/pull_request_template.md).

Resolve conflicts deliberately: understand both changes, preserve fixes already
merged into `main`, and do not choose one side blindly. Keep the original
contributor's authorship where applicable. Never discard working-tree changes
that belong to someone else.

## Validation, security, and evidence

Use the JDK/SDK setup in [README.md](README.md) and [DEVELOPMENT.md](DEVELOPMENT.md).
Run focused affected validation, then the appropriate complete affected checks
once stabilized; [docs/TESTING.md](docs/TESTING.md) lists commands. Documentation-
only changes need relevant link, whitespace, privacy/security, and licensing
checks, not the Android build or emulator matrix.

Keep credentials, signing keys, private conversations, account identifiers,
unsanitized logs, and local-machine paths out of Git and public issues. Report
vulnerabilities according to [SECURITY.md](SECURITY.md). Use synthetic
credentials in tests. Never silently change authentication or billing mode or
add credential fallback.

Label evidence accurately: `VERIFIED AUTOMATICALLY` for unit/static/build
checks, `VERIFIED ON EMULATOR` for emulator results, and `VERIFIED PHYSICALLY`
only for recorded owner/device results. Pending physical checks remain pending.
Never run destructive physical tests, clear owner data, or revoke sessions
without exact authorization.

## Licensing

WristPrompt-owned contributions are expected under **GPL-3.0-only plus the
project linking exception** in [LICENSE_EXCEPTION.md](LICENSE_EXCEPTION.md).
Contributors retain their copyright; this policy does not require copyright
assignment, a CLA, or a DCO. Submit only material you have authority to license
on these terms. This expectation for future contributions does not claim consent
from earlier contributors or grant rights to separately licensed third-party
code. Preserve third-party notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
