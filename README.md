# WristPrompt

Your key. Your model. Your watch.

**Pre-release / active development.** A native Wear OS AI client for OpenAI,
with a dense watch-native interface and a minimal Android setup companion.
D5 is complete with known table-rendering debt. D6 storage/history are implemented
on the contributor branches; consolidated acceptance remains pending. This is a
development snapshot, with no production-readiness claim.

## What works

- Continue with ChatGPT and a separate OpenAI API-key/BYOK mode.
- Account-specific model discovery and explicit account/model selection.
- Watch-direct inference after setup, using Responses API streaming.
- Saved local chats with reopen/continue/rename/delete, real Stop that cancels HTTP transport,
  and explicit Latest to return to the bottom without forced autoscroll.
- Native Markdown, Wear-adapted tables and horizontally scrollable code.
- Account, Settings and sanitized Diagnostics with explicit recovery actions.

WristPrompt is local-first and backend-free: there is no WristPrompt-operated
server or account. Inference requires the internet and sends prompts and required
conversation context to OpenAI. The phone handles browser authorization and
credential setup/handoff; it is not the normal inference proxy.

## Two explicit billing paths

**ChatGPT plan** uses an eligible saved ChatGPT registration and account-specific
service access. **OpenAI API key** uses your key and separately billed OpenAI API
usage. A ChatGPT subscription does **not** include OpenAI API credits. Storing
another credential does not select it, and errors never switch billing paths.
Model discovery does not guarantee model eligibility, quota or successful inference.

## Current limits

Chats are saved in app-private storage on the watch; there is no cloud conversation
sync. Unsent drafts and live streaming tokens are not restored after process death.
Interrupted attempts stay excluded and never resend automatically. Continuing a saved
chat requires its original account/billing ownership and a currently validated matching
model; otherwise it is read-only. Background/screen-off continuation remains best
effort. D6 physical acceptance, spoken TalkBack and release-device profiling remain
pending. Funded BYOK success has not been physically verified.

## Requirements and build

| App | Minimum SDK | Compile / target SDK |
| --- | --- | --- |
| Watch (`:wear`) | 30, on a Wear OS device | 37 / 36 |
| Phone (`:mobile`) | 26 | 37 / 36 |

Primary owner test hardware is Galaxy Watch 7; this is not a promise that every
SDK-compatible device has been tested. Setup uses Google Play services/Wearable
Data Layer with matching application IDs and signing certificates on both apps.
Builds from different developers need their own matched pair of debug APKs.
Do not uninstall authenticated owner installations to fix a signing mismatch.

Use a full JDK 17, SDK Platform `android-37.0`, Build Tools `36.0.0`, and the
checksum-pinned Gradle 9.6.0 wrapper. AGP is 9.4.1; Kotlin is 2.4.21.
Install SDK packages and accept their licenses through official Android tooling.

```sh
export JAVA_HOME="/path/to/jdk17"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest
./gradlew :wear:lintDebug :mobile:lintDebug
```

See [testing](docs/TESTING.md) for release builds, synthetic emulator scenarios
and evidence rules, and [development setup](DEVELOPMENT.md) for signing details.
The Android version is currently `0.1.0` / code `1`; this does not denote a stable release.

## Developer guide

`:wear` owns chat, state, models, inference and protected watch credentials;
`:mobile` owns setup/browser OAuth; `shared/src` compiles protocol/storage helpers
into both apps without a third Gradle module. `tools/` contains emulator acceptance
runners. Start with [AGENTS.md](AGENTS.md) and [Codex handoff](docs/CODEX_HANDOFF.md).

- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md) and [changelog](CHANGELOG.md)
- [Security](SECURITY.md) and [privacy](PRIVACY.md)
- [Specification](PROJECT_SPEC.md), [roadmap](ROADMAP.md), [decisions](DECISIONS.md)
- [Day 5 evidence](DAY5_STATUS.md) and [publication audit](docs/PUBLICATION_AUDIT.md)

## License

WristPrompt is licensed under **GNU GPL version 3 only** (base SPDX:
**GPL-3.0-only**), with a [narrow linking exception](LICENSE_EXCEPTION.md) for four
specified Google Play services runtime libraries required to build and operate the
application on Android/Wear OS. [LICENSE](LICENSE) contains the unchanged canonical
GPLv3 text. The project-specific exception has no SPDX-listed exception identifier.

WristPrompt remains strong copyleft. Commercial use, modification and redistribution
are permitted under the applicable GPLv3 obligations; the exception does not waive
WristPrompt source obligations or allow proprietary relicensing. Third-party
components retain their own licenses and notices; see
[third-party notices and dependency review](THIRD_PARTY_NOTICES.md).
