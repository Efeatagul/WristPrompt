# Development baseline

This document covers setup only. Product/security rules remain in PROJECT_SPEC.md
and AGENTS.md. The dated audit and Git inventory are in DAY2_BASELINE.md.

## Pinned build

- Full JDK 17 (validated: Temurin 17.0.21+9); Java runtime alone is insufficient.
- Wrapper Gradle 9.6.0, checksum pinned in gradle/wrapper/gradle-wrapper.properties.
- AGP 9.4.1; application Kotlin/Compose/serialization compiler 2.4.21.
  Gradle's embedded Kotlin 2.3.21 is separate from the application compiler.
- SDK platform `platforms;android-37.0`, Build Tools `build-tools;36.0.0`.
  Install `platform-tools` for ADB. Accept SDK licenses interactively.
- Both apps compileSdk 37 / targetSdk 36; minSdk Wear 30, mobile 26.
- Both apps: `io.github.grassified.wristprompt`, version 0.1.0 / code 1.

Use the wrapper; no global Gradle/Kotlin installation is needed. Keep dependency
pins unchanged unless separately reviewed. See DECISIONS.md for SDK rationale.

## Historical verified environment (paths generalized)

The user migrated the baseline on 2026-10-28. Tests, builds, lint and certificate
verification passed using these locations (all resolve within persistent home,
with no symlink into temporary storage):

| Variable | Path |
| --- | --- |
| JAVA_HOME | `$HOME/.local/share/wristprompt/jdk-17.0.21+9` |
| ANDROID_HOME | `$HOME/Android/Sdk` |
| ANDROID_USER_HOME | `$HOME/.local/share/wristprompt/android` |
| GRADLE_USER_HOME | `$HOME/.gradle` |

No required build/signing configuration depends on the former temporary tools.
The agent shell does not inherit these exports automatically; set them explicitly
as below. SDK selection is also recorded in ignored `local.properties`:
`sdk.dir=...` (an expanded absolute SDK path; local.properties does not expand `$HOME`).

## Preserve existing owner installations

Both modules resolve to the persistent private key:
`$HOME/.local/share/wristprompt/android/debug.keystore` (mode 0600).
Both signing reports and verified debug APKs retain the Day 1 public SHA-256:

`fd8491b6238368a04d58bd0653c35ff54a3a17fc9b31e53a6af7110579d37061`

Retain a protected offline/encrypted backup outside the repository. Independent
backup existence was not inspected. Keep its password in your password manager,
never in shell history, source or documentation. `keytool -list -keystore PATH`
prompts for it; compare the public fingerprint above. An APK cannot recover the
private key. Do not generate a replacement or overwrite another key: a new key
cannot update the existing debug installations, and changing only one side breaks
Data Layer compatibility. Do not uninstall authenticated apps to bypass a mismatch.

## Setup in a fresh shell

On another machine install a full JDK 17 and the pinned SDK platform/build-tools
using official downloads and published checksums. For a new developer, build both
apps with a new local debug identity and use a
separate test-device pair. Existing owner installations require the original
compatible identity; coordinate any update separately without transferring keys
through Git or chat. The historical SDK inspection reported an unaccepted
platform-tools license; use Android
Studio SDK Manager or official command-line tools to install/accept platform-tools
when needed for ADB. This warning does not block the verified build/test/lint gate.
No SDK packages or licenses were changed during verification.

Use these exports in a fresh shell (and optionally your user zsh configuration):

```sh
export JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android"
export GRADLE_USER_HOME="$HOME/.gradle"
unset ANDROID_SDK_ROOT
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

For a fresh checkout, create ignored `local.properties` in the repository root:

```sh
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
```

This replaces the current file's sole SDK setting; preserve any additional local
settings first if you have added them. Configure Android Studio's Gradle JDK to
the same JDK if using the IDE. Do not commit absolute paths or local properties.
`ANDROID_HOME` selects the SDK; deprecated `ANDROID_SDK_ROOT` must not conflict.
`ANDROID_USER_HOME` selects the shared debug key location for both modules.

## Verify the baseline in a new shell

For updates to existing owner installations, confirm the expected key exists and
inspect signing reports; do not silently replace it. A fresh developer may generate
a normal local Android debug key for their own separate matched test pair.

```sh
test -f "$ANDROID_USER_HOME/debug.keystore"
java -version
javac -version
./gradlew --version
./gradlew :wear:signingReport :mobile:signingReport --no-daemon --console=plain
./gradlew :wear:testDebugUnitTest --rerun :mobile:testDebugUnitTest --rerun :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs wear/build/outputs/apk/debug/wear-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs mobile/build/outputs/apk/debug/mobile-debug.apk
git diff --check
git status --short
```

Both debug reports must name the persistent key and both APKs must retain the
fingerprint above. Check paths with `readlink -f` and `findmnt -T` to exclude
symlinks/transient mounts. The migrated baseline passed these checks on 2026-10-28.
No device installation is part of this migration. To record connected devices
without changing app state, use `adb devices -l`, then for a selected serial:

```sh
adb -s SERIAL shell getprop ro.product.model
adb -s SERIAL shell getprop ro.build.version.sdk
adb -s SERIAL shell dumpsys package io.github.grassified.wristprompt | rg 'versionCode=|versionName='
```

Do not capture app data, OAuth callbacks, logcat payloads, tokens or account fields.
Keep HARs, debug captures, private configuration and signing backups outside Git;
ignore rules are defense in depth, not a guarantee against forced additions.

Official references checked 2026-10-28:
[SDK environment variables](https://developer.android.com/tools/variables),
[app signing](https://developer.android.com/studio/publish/app-signing),
[Data Layer identity requirements](https://developer.android.com/training/wearables/data/overview),
[command-line tools](https://developer.android.com/studio#command-tools).
