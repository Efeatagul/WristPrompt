# Day 2 Phase A baseline audit — 2026-10-28

Status: **SAFE TO CHECKPOINT** after user migration and successful persistent-path
verification. This supersedes the initial Phase A temporary-storage blocker.
The Day 1 development baseline is reproducible with the documented environment
and preserved debug identity. This is source-checkpoint readiness, not release
acceptance or completion of Day 2 authentication work.

Only DEVELOPMENT.md and this report changed during migration verification.
No product code, dependencies, signing configuration, keys, SDK packages, device
state or external services changed. No staging, commit or push occurred.

## Persistent toolchain and signing evidence

| Item | Verified evidence |
| --- | --- |
| Full JDK | Temurin 17.0.21+9, javac 17.0.21 |
| JAVA_HOME | `$HOME/.local/share/wristprompt/jdk-17.0.21+9` |
| ANDROID_HOME / SDK | `$HOME/Android/Sdk` |
| ANDROID_USER_HOME | `$HOME/.local/share/wristprompt/android` |
| GRADLE_USER_HOME | `$HOME/.gradle` |
| Debug key | `$HOME/.local/share/wristprompt/android/debug.keystore`, mode 0600 |
| Gradle | 9.6.0; launcher and daemon use the persistent JDK |
| AGP / application Kotlin | 9.4.1 / 2.4.21 (Gradle embedded Kotlin 2.3.21) |
| SDK platform / Build Tools | android-37.0 / 36.0.0, present in persistent SDK |
| SDK levels | compile 37, target 36; min Wear 30 / mobile 26 |

Ignored local.properties selects `sdk.dir=$HOME/Android/Sdk`.
The agent shell does not inherit the intended exports, so validation explicitly
set all four variables. ANDROID_SDK_ROOT was unset. DEVELOPMENT.md provides the
fresh-shell setup; system-wide configuration was not changed.

Canonical paths remain within persistent home (ext4); scans of the SDK, JDK/key
parent and Gradle cache found no symlink into /tmp or /var/tmp. Repository build
scripts, version catalog, wrapper and local SDK config have no required temporary
path. Historical reports mention old paths; those are not active configuration.
Disposable JVM test directories and temporary validation logs are not toolchain
or signing dependencies. No former temporary toolchain environment was used.

Both module signingReport tasks name the persistent key. Both resulting debug
APKs verify and retain the Day 1 public certificate SHA-256:

`fd8491b6238368a04d58bd0653c35ff54a3a17fc9b31e53a6af7110579d37061`

The working identity is preserved persistently; an independent offline/encrypted
backup was not inspected and remains recommended. Never replace the key or
uninstall authenticated apps to resolve a mismatch.

## Git and secret safety

Zero tracked files, zero commits, empty index; 95 untracked candidates. No signing
material, local.properties, generated build output or capture files are tracked
or eligible for normal addition. The wrapper JAR is intentional versioned tooling.
Ignored paths include .gradle/, .kotlin/, all build/ directories, local.properties,
IDE local state, signing material/backups, credentials/config files and OAuth/debug
captures. git check-ignore probes confirmed the private/config/capture exclusions.
Source and documentation remain eligible. Forced additions can bypass Git ignores.

A fresh values-suppressed scan of all 95 candidate files found no key-like live
credential, private-key block, JWT-like token, literal signing/client-secret
assignment or captured authenticated callback. Prior protocol/header/token names
in production code, security documentation and synthetic test fixtures remain
legitimate. No values were included in findings. Generated APK/cache binaries are
ignored; this targeted scan does not claim exhaustive binary forensic analysis.
The persistent private signing key is outside the repository and is not stageable
from this worktree. No real credentials were read or exported.

## Commands actually run and results

Read AGENTS.md, DEVELOPMENT.md, the previous DAY2_BASELINE.md, relevant specification,
roadmap/decision sections, and current root/module build configuration. Inspected
Git status/diff, candidate inventory, local SDK selection, environment variables,
canonical paths, mount backing, key permissions, overrides and temporary symlinks.

Ran with explicit persistent environment:

```sh
export JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android
export GRADLE_USER_HOME=$HOME/.gradle
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./gradlew --version
./gradlew :wear:signingReport :mobile:signingReport :wear:testDebugUnitTest --rerun :mobile:testDebugUnitTest --rerun :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
```

BUILD SUCCESSFUL. Both unit tasks explicitly executed: Wear 99, mobile 55;
zero failures, errors or skips. Both debug builds initially reused up-to-date outputs. Then forced both builds
and lint to execute with the same persistent environment:

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --rerun-tasks --no-build-cache --no-daemon --console=plain
```

The forced gate also returned BUILD SUCCESSFUL; verified both rebuilt APK
certificates again. Lint has zero errors
and the same three warnings per module: Wear wrapper advisory / mobile target
advisory, exported Data Layer service and logo vector path. No new warning/error
or suppression. The SDK's existing unaccepted platform-tools license warning
remains non-blocking for this gate; no license was accepted automatically.

Ran the persistent SDK's `apksigner verify --print-certs` against both module
outputs successfully. Parsed XML test/lint reports to confirm counts and warning
identities. Ran `git diff --check`, plus whitespace/final-newline validation of
all untracked text candidates, and a content-hash comparison confirming no
checkpoint candidate except the two requested documents changed during this run.
Git status afterward remains entirely untracked; nothing staged or committed.

## Scope and remaining checks

No remaining blocker for the Day 1 source checkpoint. Use the explicit environment
in DEVELOPMENT.md for later shells/builds. An off-machine protected signing backup
is recommended but its absence is not a temporary-toolchain dependency.
No emulator or physical-device action was performed in migration verification.
The initial Phase A audit recorded phone SM-S721B, API 35, WristPrompt 0.1.0 / code 1;
watch API/version was unavailable then. Day 1 physical acceptance remains prior
user evidence. Refresh, recovery, key-loss, backup and release checks remain open
under their existing roadmap gates; none were marked complete by compilation.

Suggested commit message: `chore: verify the persistent Day 1 development baseline`.

## Complete untracked checkpoint candidate inventory

```text
.gitattributes
.gitignore
AGENTS.md
BRIDGE_TESTING.md
DAY1_REPORT.md
DAY1_REVIEW.md
DAY2_BASELINE.md
DECISIONS.md
DEVELOPMENT.md
INFERENCE_TESTING.md
PROJECT_SPEC.md
README.md
ROADMAP.md
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
mobile/build.gradle.kts
mobile/src/main/AndroidManifest.xml
mobile/src/main/kotlin/io/github/grassified/wristprompt/MainActivity.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/WristPromptApplication.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/IdTokenVerifier.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/IssuedRegistrationStore.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/LoopbackListener.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/OAuthTransaction.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/OpenAiAuthorization.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/PhoneAuthViewModel.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/bridge/AuthBridgeListenerService.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/bridge/AuthRequestInbox.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/bridge/AuthStartReceiver.kt
mobile/src/main/kotlin/io/github/grassified/wristprompt/bridge/CompanionViewModel.kt
mobile/src/main/res/drawable/ic_chatgpt.xml
mobile/src/main/res/drawable/ic_launcher.xml
mobile/src/main/res/raw/keep.xml
mobile/src/main/res/values/strings.xml
mobile/src/main/res/values/themes.xml
mobile/src/main/res/xml/backup_rules.xml
mobile/src/main/res/xml/data_extraction_rules.xml
mobile/src/test/kotlin/io/github/grassified/wristprompt/auth/IdTokenVerifierTest.kt
mobile/src/test/kotlin/io/github/grassified/wristprompt/auth/LoopbackListenerTest.kt
mobile/src/test/kotlin/io/github/grassified/wristprompt/auth/OAuthTransactionTest.kt
mobile/src/test/kotlin/io/github/grassified/wristprompt/auth/PhoneAuthViewModelTest.kt
mobile/src/test/kotlin/io/github/grassified/wristprompt/bridge/AuthStartReceiverTest.kt
settings.gradle.kts
shared/src/main/kotlin/io/github/grassified/wristprompt/auth/CredentialProfile.kt
shared/src/main/kotlin/io/github/grassified/wristprompt/auth/ProfileProtocol.kt
shared/src/main/kotlin/io/github/grassified/wristprompt/bridge/BridgeLog.kt
shared/src/main/kotlin/io/github/grassified/wristprompt/bridge/BridgeProtocol.kt
shared/src/main/kotlin/io/github/grassified/wristprompt/bridge/SafeDiagnostics.kt
shared/src/main/kotlin/io/github/grassified/wristprompt/bridge/WearConnectivity.kt
shared/src/main/kotlin/io/github/grassified/wristprompt/storage/EncryptedRecord.kt
shared/src/test/kotlin/io/github/grassified/wristprompt/auth/ProfileProtocolTest.kt
shared/src/test/kotlin/io/github/grassified/wristprompt/bridge/BridgeProtocolTest.kt
shared/src/test/kotlin/io/github/grassified/wristprompt/bridge/SafeDiagnosticsTest.kt
shared/src/test/kotlin/io/github/grassified/wristprompt/storage/GcmRecordTest.kt
wear/build.gradle.kts
wear/src/main/AndroidManifest.xml
wear/src/main/kotlin/io/github/grassified/wristprompt/MainActivity.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/WristPromptApplication.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/auth/ProfileListenerService.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/auth/WatchProfiles.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/bridge/WatchAuthBridge.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/bridge/WatchAuthViewModel.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/ApiFailure.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/InferenceProofUi.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/InferenceSession.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/InferenceViewModel.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/ModelCatalog.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/OpenAiHttp.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/OpenAiProvider.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/RefreshProtocol.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/ResponsesProtocol.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/openai/SseReader.kt
wear/src/main/kotlin/io/github/grassified/wristprompt/storage/HostIdRepository.kt
wear/src/main/res/drawable/ic_chatgpt.xml
wear/src/main/res/drawable/ic_launcher.xml
wear/src/main/res/raw/keep.xml
wear/src/main/res/values/strings.xml
wear/src/main/res/values/themes.xml
wear/src/main/res/xml/backup_rules.xml
wear/src/main/res/xml/data_extraction_rules.xml
wear/src/test/kotlin/io/github/grassified/wristprompt/auth/WatchProfilesTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/bridge/WatchAuthBridgeTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/InferenceSessionTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/InferenceViewModelTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/ModelCatalogTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/OpenAiProviderTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/RefreshProtocolTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/ResponsesProtocolTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/SseReaderTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/openai/WatchRefreshTest.kt
wear/src/test/kotlin/io/github/grassified/wristprompt/storage/HostIdRepositoryTest.kt
```
