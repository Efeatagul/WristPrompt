# Decisions and readiness

Updated 2026-10-28 (Europe/Istanbul). Product/security contracts live in
PROJECT_SPEC.md; execution gates in ROADMAP.md. The full-Day-1 proposal below
includes later-phase dependencies; implemented phases use the subset pinned in
`gradle/libs.versions.toml`.

## Phase A implementation

- Two application modules, one activity and manual application container each.
  Watch uses Wear Material 3 with native scroll scaffolding; phone uses Material 3.
  Disabled watch actions have no authentication behavior.
- `WearConnectivity.reachablePeers()` discovers the opposite app capability and
  propagates failures. It is not called by the placeholder UI. No message protocol,
  listener service, credential storage, OAuth, or inference is implemented.
- Only Compose, Activity, coroutines, and Play services Wearable are added. The
  serialization, networking, DataStore, ViewModel, and JOSE dependencies from the
  full-Day-1 proposal are deferred until needed.
- Both apps disable backup/cleartext traffic, with explicit cloud/device-transfer
  exclusions. There are no custom signing configurations; debug uses the same
  machine-local Android debug key. Keys, local SDK config, and output are ignored.
- Use AGP 9.4.1, the stable patch reported by lint and verified in
  [Google Maven](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.1/gradle-9.4.1.pom).
  Keep Gradle 9.6.0, the documented compatible AGP 9.4 baseline, rather than
  upgrading the wrapper only to silence a newer-version advisory.
- The GitHub extension exposes no repository-creation operation. The authenticated
  CLI created private [grassified/WristPrompt](https://github.com/grassified/WristPrompt),
  then the extension verified its private visibility and empty state. A host-level
  check confirmed no `.git` existed: the restricted view's read-only directory was
  a sandbox placeholder. Initialized fresh local Git metadata and origin without
  replacing existing metadata, committing, or pushing.

### Verified SDK deviation

The first build with compileSdk 36 failed `:wear:checkDebugAarMetadata`.
Resolved `compose-material3:1.7.1` and `ui-android:1.12.1` AAR metadata explicitly
requires `minCompileSdk=37`. Both modules now compile against SDK 37 and still
target SDK 36; minSdk stays watch 30 / phone 26. AGP 9.4.0 supports API 37. No
compatibility-check suppression or prerelease dependency was used.

JDK Temurin 17.0.21, Gradle 9.6.0, Android SDK packages, and validation caches were
provisioned temporarily outside the repository. Official JDK/SDK/Gradle download
checksums were verified. Wrapper scripts/JAR were generated from that Gradle
distribution. A permanent developer toolchain is still needed after temporary
files are removed. README.md describes the portable build prerequisites.

## Initial local inspection (before Phase A)

The workspace contains no project files or existing engineering docs. `.git`,
`.agents`, and `.codex` are empty read-only directories in this execution context.
`git status --short` and `git rev-parse --show-toplevel` fail: no usable repository
metadata. Phase A later distinguished the sandbox placeholder from host metadata,
as recorded above; no existing repository was replaced.

| Item | Observed |
| --- | --- |
| Java | `/usr/bin/java`: OpenJDK runtime 25.0.4.1; headless runtime package, no `javac` |
| JDK | No full JDK detected; Java 21 directory contains only residual documentation |
| Gradle / Kotlin | No `gradle`, `kotlinc`, wrapper, or local Gradle cache detected |
| Android SDK | `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME` unset; no SDK in checked standard home, `/opt`, or `/usr` locations |
| ADB | `/usr/bin/adb`, android-tools 37.0.0; this alone cannot compile Android apps |
| Android Studio | No launcher, standard installation, desktop entry, or installed Flatpak detected |

Commands used: file inventory, ancestor AGENTS checks, Git status/root checks,
`command -v`, `java -version`, targeted environment/path inspection, RPM/Flatpak
inventory, and `adb version`. No device tests or SDK installation performed.
An installation in an undiscovered custom location remains possible.

Conflict: there is no installed stable Android/Kotlin/Compose build toolchain to
reuse. Provision a full JDK 17, SDK command-line tools, `platform-tools`,
`platforms;android-37.0`, and `build-tools;36.0.0` for the current build. An IDE is
optional. Use the wrapper for Gradle; no global Kotlin compiler is needed. The
current Java runtime is not a full development kit; do not infer incompatibility
solely from its version.

## D01 — two application modules, no framework

Full-Day-1 proposed skeleton; Phase A creates only the shell and bridge portions:

```text
WristPrompt/
  AGENTS.md, PROJECT_SPEC.md, ROADMAP.md, DECISIONS.md
  settings.gradle.kts              # rootProject.name = "WristPrompt"; :wear, :mobile
  build.gradle.kts                 # plugins and explicit KGP classpath
  gradle.properties
  gradle/libs.versions.toml         # pinned artifact/plugin catalog
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
  gradlew, gradlew.bat
  wear/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/kotlin/io/github/grassified/wristprompt/
      MainActivity.kt, WristPromptApplication.kt
      ui/, chat/, openai/, auth/, storage/, bridge/
    src/main/res/
    src/test/kotlin/
    src/androidTest/kotlin/
  mobile/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/kotlin/io/github/grassified/wristprompt/
      MainActivity.kt, WristPromptApplication.kt
      ui/, auth/, storage/, bridge/
    src/main/res/
    src/test/kotlin/
    src/androidTest/kotlin/
```

Both are `com.android.application`, with namespace and application ID
`io.github.grassified.wristprompt`, versionName `0.1.0`, initial versionCode `1`.
No dependency from one app module to the other. Phase B centralizes the actual
wire protocol, adapter, and diagnostics in `shared/src/`, compiled into both apps
with Android source sets; this is not another Gradle module. Keeping these
security-sensitive definitions identical now justifies sharing their source.
OAuth authorization/identity validation will exist only on phone, refresh only
on watch. Neither is implemented in Phase B.
If actual shared protocol/storage code becomes substantial, record that evidence
before extracting a library. No `buildSrc`, convention-plugin project, DI framework,
navigation framework, database, or future-provider modules on Day 1.

Use one manual application container per app and constructor injection. One
activity per module; simple explicit screen state is enough initially. Conversation
lifetime is intentionally process-local to avoid a persistence subsystem in six days.

## D02 — stable build and dependency proposal

Use `compileSdk=37`, `targetSdk=36` in both apps; `minSdk=30` on watch (Wear OS 3+),
`minSdk=26` on phone. Target level does not require devices to run API 36. Verify
the resolved dependency manifests against these minima before accepting the build.
The Phase A AAR requirement above justifies compileSdk 37; no targetSdk deviation
is needed.

[Play requirements](https://developer.android.com/google/play/requirements/target-sdk)
require API 36 for new phone apps/updates and API 35 for Wear from August 31, 2026;
36 meets both. [AGP 9.4.0](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
supports API 37 and specifies Gradle 9.6.0, Build Tools 36.0.0, and JDK 17 as its
minimum/default baseline. Choose JDK/JVM bytecode target 17 and no NDK.

| Component / coordinate | Pin | Where |
| --- | --- | --- |
| `com.android.application` | 9.4.1 | Both apps |
| Gradle wrapper | 9.6.0 | Root; verify official distribution checksum |
| Kotlin compiler / stdlib | 2.4.21 | Both apps; explicit KGP buildscript classpath override for built-in Kotlin |
| `org.jetbrains.kotlin.plugin.compose` | 2.4.21 | Both apps |
| `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 | Both apps |
| `androidx.core:core-ktx` | 1.19.1 | Both apps |
| `androidx.activity:activity-compose` | 1.13.0 | Both apps |
| `androidx.compose.runtime:runtime`, `androidx.compose.ui:ui`, `androidx.compose.foundation:foundation` | 1.12.1 | Both apps |
| `androidx.compose.ui:ui-tooling-preview`, `ui-tooling` | 1.12.1 | Both; tooling debug-only |
| `androidx.wear.compose:compose-material3`, `compose-foundation` | 1.7.1 | Watch only |
| `androidx.compose.material3:material3` | 1.4.0 | Phone only |
| `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | 2.11.0 | Both apps |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Both; non-secret preferences |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `kotlinx-coroutines-play-services` | 1.11.0 | Both; Data Layer Task awaiting |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | Both apps |
| `com.squareup.okhttp3:okhttp` | 5.5.1 | Both; OAuth on phone, API/refresh on watch |
| `com.google.android.gms:play-services-wearable` | 20.0.1 | Both apps |
| `com.nimbusds:nimbus-jose-jwt` | 10.10 | Phone ID-token validation |
| `junit:junit` | 4.13.2 | Unit tests |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Unit tests |
| `com.squareup.okhttp3:mockwebserver3` | 5.5.1 | Transport tests |

AGP 9 uses [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin):
do not apply `org.jetbrains.kotlin.android`. Explicitly align its resolved KGP,
Compose/serialization plugins, and stdlib to 2.4.21 using the documented
[higher-KGP override](https://developer.android.com/build/releases/agp-9-0-0-release-notes#kotlin-runtime-dependency);
verify it during initial sync. [Kotlin 2.4.21](https://kotlinlang.org/docs/releases.html) is stable.
These are proposed pins, not a claim that this combination has resolved locally.
Any toolchain incompatibility must be recorded before changing the baseline.

Use `google()` and `mavenCentral()` for dependencies; plugin management also uses
`gradlePluginPortal()`. Pin Compose components explicitly in the version catalog
for this small graph; Wear versions are independent. Enable Compose build features.
Inspect resolved versions/AAR minCompileSdk for conflicts rather than suppressing
compatibility checks. No alpha/beta dependencies are needed for this proposal.
System-browser `ACTION_VIEW` needs no Custom Tabs library on Day 1.

Primary version evidence, checked 2026-10-28:

- [AndroidX stable catalog](https://developer.android.com/jetpack/androidx/versions)
  and [Wear Compose setup](https://developer.android.com/training/wearables/compose).
- [Coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases) and
  [serialization releases](https://github.com/Kotlin/kotlinx.serialization/releases).
- [OkHttp upstream changelog](https://github.com/lysine-dev/okhttp/blob/main/CHANGELOG.md)
  (the former Square repository redirects here; 5.5.1 is a security patch).
- [Play services coordinates](https://developers.google.com/android/guides/setup).
- [Nimbus Android support](https://connect2id.com/products/nimbus-jose-jwt) and
  [maintainer's 10.10 usage](https://connect2id.com/blog/connect2id-server-19-16-1).
  Validate its Android/R8 behavior and dependency footprint on Day 1; avoid writing
  a JWT signature implementation to save a dependency.

## D03 — phone bootstrap, watch refresh ownership

Retain the requested architecture. OpenAI's [VM transfer guide](https://developers.openai.com/siwc/token-sharing-open-source/self-hosted-vms)
permits transferring an existing protected session and giving the destination
refresh ownership. Applying this pattern to phone/watch is an engineering
inference, not an Android-specific support guarantee. No simpler supported
watch-native redirect was established. Prove it before UI polish.

The watch defines the installation host and supplies its UUID host ID to the
phone transaction. Phone is a setup delegate, not a second inference host. Each
watch installation stays separate. UUID is the supported simpler alternative to
the recommended JWK thumbprint, which currently adds no possession proof.

Use MessageClient RPC for Phase B's bounded request/receipt exchange. The pinned
Wearable 20.0.1 API supports `sendRequest` and `WearableListenerService.onRequest`.
Matching package and
signature are [platform-enforced](https://developer.android.com/training/wearables/data/overview);
capability discovery alone is not transaction authorization. Bind the selected
node and pending request; never send a profile to every discovered device.
Data Layer may use Google's relay, with documented end-to-end encryption; this
does not introduce a WristPrompt backend or constitute chat cloud sync.

Phone retains only an encrypted pending transfer until ACK/expiry and never
refreshes. A duplicate transfer is acknowledged without overwriting watch state.
Watch owns refresh after durable acceptance; sign-out/account changes invalidate
old operations. Interruption recovery must not revoke an already transferred
session or restart OAuth automatically. Reauthorization requires coordinating a
pause in that watch profile's requests/refresh, then atomically replacing the
profile only after phone validation. Use a profile generation counter to reject
late results. No security-critical step is deferred to a cosmetic cleanup day.

## D04 — storage and streaming stay small

Use platform Keystore AES-GCM plus atomic app-private encrypted JSON records;
Preferences DataStore contains non-secrets. Disable/exclude backups for both
cloud and device transfer. Reinstallation/key loss requires sign-in again.
Keep one central safe diagnostic formatter instead of raw exception/body logging.

Implement a single OkHttp HTTP/SSE transport with coroutine cancellation. Bound
event sizes and retained context; preserve completed assistant output items needed
for subsequent context, including opaque reasoning items if the selected model
requires them. Test framing, chunk boundaries, unknown events, terminal failures,
and EOF. No auto-retry after generation starts; explicit retry makes additional
usage a user decision. Stop ends local streaming; it cannot promise zero server-side usage.

## Five largest technical risks

| Rank | Risk | Mitigation and acceptance evidence |
| --- | --- | --- |
| 1 | ChatGPT OAuth on Wear OS through a phone browser/loopback bridge is unproven; browser return and Android process lifetime can break it | Day 1 real watch→phone→OAuth→watch→catalog→completed inference proof; listener bound only to 127.0.0.1, exact callback, cancellation/restart tests. Never replace the callback or private-route inference to force success. |
| 2 | Credential handoff, replay, and rotating refresh-token races can leak or invalidate sessions | Matching signatures, pending node/profile/host binding, encrypted staging/storage, idempotent ACK, watch-only refresh, atomic rotation; negative tests plus disconnect/lost-ACK/process-death device checks. |
| 3 | Preview eligibility/scopes, model catalogs, and late usage errors differ from ordinary BYOK | Validate grants, isolate account registrations, parse OAuth catalog independently, omit unsupported fields, handle failures after deltas, expose Manage usage, prove eligible-plan inference without billing fallback. |
| 4 | Missing toolchain/Git plus fresh stable dependencies and delivery signing can consume the schedule | Provision JDK/SDK, restore a usable checkout, resolve the pinned graph, build/lint both apps on Day 1; verify matching release signing/Play configuration by Day 5. No install or signing changes in preparation. |
| 5 | Watch lifecycle, limited screen/memory, and unreliable networks can leave partial/stale output or poor Stop behavior | Keep foreground chat and bounded local context, explicit stream states/cancellation, profile generation checks, and physical Watch 7 network/screen-off/rotary tests; cut voice first. |

## Phase A validation — 2026-10-28

Executed with the temporary JDK/SDK and Gradle user cache:

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug :wear:testDebugUnitTest :mobile:testDebugUnitTest --no-daemon --console=plain
```

Final result: BUILD SUCCESSFUL, 96 tasks, zero lint errors. Unit-test tasks report
NO-SOURCE: there are no test sources in this shell phase, and no unit-test pass is
claimed. Lint retains two advisories: newer Gradle 9.8.2 exists, and phone targetSdk
36 is below the newest API. Keep the documented compatible wrapper and requested
target; neither warning is suppressed. Removed an API-27-only theme attribute
incompatible with phone minSdk 26, and explicitly set the Wear task affinity.

Emulator evidence: Android Emulator 37.2.12, API 36 Google APIs phone (Pixel 7
profile), API 36 Wear OS 6 signed image (small round 384×384 profile). Installed
each final APK using `adb install -r`; `adb shell am start -S -W -n
io.github.grassified.wristprompt/.MainActivity` reported Status: ok on both.
Processes survived, activities remained resumed, screenshots were inspected, and
UIAutomator dumps verified setup text, disabled watch actions, and focused scroll
content. A disabled ChatGPT tap stayed in the shell; touch scrolling exposed the
remaining onboarding content. Phone system-bar contrast was fixed and rechecked.

`apksigner verify --print-certs` succeeded for both final APKs and their certificate
SHA-256 digests matched. Source/XML checks verified backup/cleartext settings,
matching discovery classes, no machine paths in commit-candidate configuration,
and no OAuth/inference/token implementation. `git check-ignore` verified local SDK
config, key/secrets filenames, and build output exclusions. `git diff --check`
passed, with per-file no-index whitespace checks also covering untracked files.
Git has zero commits; no files were pushed or uploaded. PROJECT_SPEC.md is unchanged.

Still manual/unverified: paired Data Layer discovery, physical Galaxy Watch 7
launch/rotary behavior, large fonts/accessibility, declared minimum-API devices,
and Play-delivered signing. These are not emulator-proven. Validation tooling and
AVDs are temporary; provision permanent JDK/SDK locations for daily development.
Phase A is complete; the full Day 1 OAuth/inference gate is still open. Phase B
implementation and its separate verification evidence follow below.

## D05 — Phase B protocol and persistence

The official [host-ID guidance](https://developers.openai.com/siwc/token-sharing-open-source)
was rechecked on 2026-10-28. Use the supported random UUIDv4 URI instead of adding
a JWK key pair for an identifier that does not prove key possession. Wear's
`HostIdRepository` creates the ID on first setup request using an atomic DataStore
edit. Restarts and retries reuse it. Invalid/corrupt existing identity fails
closed; never silently replace it. This is non-secret installation metadata.

`BridgeProtocol` owns paths, version, limits, and runtime capability names. XML
capability advertisements must match these constants. `AUTH_START_PATH` is
`/wristprompt/v1/auth/start`. The start body contains only version, fresh UUIDv4
request ID, Wear host URI, and epoch creation time. Version 1 uses canonical JSON
from the shared codec: reject alternate encodings/duplicate keys, unknown fields,
invalid UTF-8/IDs, bodies over 1 KiB, age over two minutes, or more than 30 seconds
of future clock skew. No account data or credential fields are accepted.

Prefer one uniquely nearby eligible phone; otherwise accept a single reachable
phone. Ambiguity fails without broadcasting. Phone validates the source against
reachable WristPrompt Wear capability nodes and persists node/request/host binding
before returning a version/request-ID receipt. Data Layer binds the RPC response
to the chosen node/invocation; the watch validates the receipt's request ID.
Fifteen-second timeouts are bounded, and no automatic retry occurs. A timeout may
follow actual phone receipt; the UI states this rather than claiming failure to
deliver. An explicit retry from the same installation replaces its earlier setup
request; a different watch cannot overwrite an unexpired request. Duplicates are
idempotent. Stale metadata is ignored when the inbox is read.

The phone uses a manifest WearableListenerService with REQUEST_RECEIVED routing;
other paths are ignored. Its exported-service lint advisory is intentional:
Google's documented pattern requires exported delivery, and inspection of the
pinned 20.0.1 listener Binder confirmed Google Play services UID verification
before RPC dispatch. Matching package/signature enforcement remains a Data Layer
boundary, not a substitute for request/source validation. No custom unprotected
intent receiver or activity-extra injection route was added.

Phone does not force an activity open from the background. Its metadata survives
process restart and is surfaced by DataStore/StateFlow when the user opens setup
within the request lifetime. While already displayed, the receipt is a historical
notice, not an active OAuth session. The setup notice grants no permission to
transfer credentials. Phase C must add its own watch pending transaction and
secure profile-return path; see [BRIDGE_TESTING.md](BRIDGE_TESTING.md).

Logging uses an enum-only `BridgeLog` sink. Arbitrary values are fully redacted
without evaluating `toString`; protocol DTOs also redact that method. Codec
exceptions discard parser input/messages/causes. Future secret metadata must not
be added to these Preferences DataStore records or the Phase B payload schema.

New stable dependencies: Preferences DataStore 1.3.0, lifecycle 2.11.0,
kotlinx.serialization JSON 1.12.0 with Kotlin serialization plugin 2.4.21, JUnit
4.13.2, and coroutines-test 1.11.0. Toolchain/SDK targets remain unchanged.

### Phase B validation — 2026-10-28

Ran `:wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug
:mobile:assembleDebug :wear:lintDebug :mobile:lintDebug` together through the
wrapper with `--no-daemon --console=plain`: BUILD SUCCESSFUL. Both suites passed
26 tests each (52 executions, 36 distinct tests because shared tests run in both
modules). Tests cover real temporary DataStore persistence/reopening, concurrent
host creation, payload rejection/redaction, node selection, timeouts, receipt
correlation, and receiver persistence using fake transport/node discovery.

Lint reported no errors. Advisories remain for the newer available Gradle,
intentional targetSdk 36, and the documented exported Data Layer service above.
Both debug APK signatures verified and their signing certificates matched.

Installed and launched both APKs on the temporary API 36 phone/Wear emulators.
These emulators are unpaired. Inspected the waiting phone screen and actual
watch companion-unavailable feedback after a tap. Checked the watch's stored
UUIDv4 URI without printing it, force-stopped/restarted the app, retried, and
confirmed the stored ID was unchanged. This proves the unavailable path and
local persistence on an emulator; it does not prove paired Data Layer delivery.

Paired request/receipt delivery, background service wake-up, physical Galaxy
Watch 7 behavior, and minimum-API hardware remain manual acceptance gates in
BRIDGE_TESTING.md. No OAuth, credential transfer, or Phase C work is included.

## D06 — Phase C authorization and encrypted handoff

Rechecked all source-index OpenAI guides and Android's
[authentication/token sharing](https://developer.android.com/training/wearables/apps/auth-wear)
and [Data Layer security](https://developer.android.com/training/wearables/data/overview)
on 2026-10-28. No material specification conflict was found. Credential Manager
cannot replace OpenAI's required loopback callback. The VM-to-watch analogy in D03
remains an engineering interpretation; real paired OAuth acceptance is still required.

Phone waits for an explicit Continue with ChatGPT tap. It asks only the selected
watch/node for the pending transaction's registration context, then binds a
single-attempt listener to 127.0.0.1 on an available port before ACTION_VIEW.
The path is /auth/callback, method GET; headers/target are bounded. Wrong paths
are ignored, a state mismatch terminates the attempt, and five minutes/cancellation
closes the listener. Browser replies contain no callback data. No server, sockets,
or RemoteAuthClient are used for phone/watch communication.

[Public OIDC discovery](https://auth.openai.com/.well-known/openid-configuration)
currently agrees with the documented authorize/token/JWKS endpoints and RS256.
Discovery endpoints are checked against the trusted OpenAI origin and exact paths;
JWT-controlled key URLs are rejected. Use Nimbus JOSE+JWT 10.10, a maintained
Android-compatible library, rather than writing signature verification. Verify
signature, issuer, issued-client audience/azp, subject, nonce, exp/iat/nbf/auth_time,
and at_hash when supplied; tolerate five seconds of clock skew. The token reference
names earliest_refresh_at without specifying its encoding: retain that bounded
field opaquely until refresh semantics are verified in the refresh phase. Fetch trusted JWKS
for each exchange instead of adding a persistent cache. OkHttp 5.5.1 uses ordinary
platform TLS, bounded bodies/timeouts, no logging/cache/redirects or automatic retries.

Issued client metadata is encrypted on phone before code exchange. Verified
registrations on watch supply their exact client ID, issuer/subject and retained
ID token on later sign-in. New account selection stays separate; identical emails
never merge profiles. An incomplete initial registration's issued ID can be reused
when no saved watch accounts exist. Choosing Add another account when profiles
already exist deliberately starts a new registration; an incomplete additional
registration is not silently selected instead. Up to ten watch profiles are supported.

Access/refresh/ID tokens are never written to phone storage, preferences, saved
state, notifications, clipboard or diagnostics. A validated pending envelope stays
in ViewModel memory only, bounded by the watch's ten-minute transaction lifetime;
process death discards it. This is simpler than the optional encrypted phone
staging discussed in D03. Rotation keeps the ViewModel; process survival while a
browser is open is not guaranteed and remains a device acceptance risk. No token
refresh/revocation occurs on phone. In particular a lost ACK never triggers revocation.

Watch persists an expiring node/request/host/selected-identity/generation binding
before sending the initial request. Separate context/profile/failure RPC paths are
centralized in ProfileProtocol. Payloads are canonical, versioned and bounded to
64 KiB. Only the bound node/request can finish; switching accounts or starting a
new attempt invalidates old results. Exact transfer-ID/body-digest retries return
an idempotent ACK without overwriting tokens. A different body, host, sender,
registration, stale result or unsolicited result fails closed.

Watch uses a single encrypted vault with Keystore-generated AES-256-GCM and a
fresh provider-generated 96-bit IV per write. The version and record identity are
bound using authenticated data. AtomicFile receives ciphertext only; writes sync
and read back before success. Files live under noBackupFilesDir; backups remain
disabled/excluded. Missing keys, corrupt records and write failures block sign-in,
with no plaintext fallback. Android Keystore behavior needs device evidence;
JVM AES-GCM tests alone do not prove it.

Identity-only scope grants remain connected with plan usage disabled. The phone
claims success only after the watch ACK; transfer timeout exposes explicit retry
of the same envelope. No inference, BYOK, refresh, sign-out, or Phase D is added.

### Phase C validation — 2026-10-28

Using the temporary toolchain from Phase A, ran:

```sh
./gradlew :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug :mobile:lintDebug :wear:lintDebug --no-daemon --console=plain
```

Final result: BUILD SUCCESSFUL, 102 tasks. Mobile 54 and Wear 42 test executions
passed (96 total; 73 distinct tests because 23 shared tests run in both apps).
No tests were skipped. Signed-token negative cases use synthetic RSA keys and
tokens. JVM socket tests exercise the actual loopback listener, not an Android
browser. Phone handoff tests inject token exchange and Data Layer transport; watch
repository tests inject protected-record storage. JVM AES-GCM tests do not exercise
Android Keystore. No live OpenAI authorization or real paired delivery was tested.

Both lint reports have zero errors and three advisories each: the existing
wrapper/target-SDK choices, intentional framework-authenticated exported listener
services, and the official unmodified logo's long vector path. Retain the exact
approved logo instead of changing its shape to silence the vector advisory.

Installed and cold-launched both debug APKs on the temporary API 36 unpaired phone
and round Wear emulators; inspected waiting/onboarding screens and verified live
processes. Tapped the watch action and verified actual companion-unavailable
feedback. The phone emulator's System UI startup ANR was dismissed with Wait;
WristPrompt stayed alive and its waiting screen rendered correctly afterward.
This is launch/error-path smoke evidence only, not OAuth/Keystore/paired evidence.
Both final APK signatures verified and certificate digests matched.

Ran git diff --check plus per-file no-index whitespace checks because source is
still untracked. PROJECT_SPEC.md remains unchanged. No staging, commit, push,
inference, BYOK, or Phase D work occurred. Manual gates stay open in ROADMAP.md;
BRIDGE_TESTING.md contains real-pair consent, restart, denial and lost-ACK checks.
Phone process death drops untransferred tokens and requires fresh watch setup;
release acceptance also needs actual Keystore/key-failure and minimum-API checks.

## D07 — Day 1 Phase D proof, not production chat

Rechecked official [models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference),
[preview restrictions](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations),
[stream events](https://developers.openai.com/api/reference/resources/responses/streaming-events),
[sessions/refresh](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)
and [recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
on 2026-10-28. No material specification conflict was found. No physical device
was attached through ADB at task start; real acceptance remains a separate gate.

Add the already-pinned OkHttp 5.5.1 to watch. No new version, framework or Gradle
module. A small AiProvider/OpenAiProvider consumes an opaque, redacted credential
lease; the credential source owns protected profile access/refresh. GET /v1/models
keeps visibility=list entries in original order, displays display_name and sends
slug. No guessed default model or provider fallback. The first displayable entry
is initially selected, and every displayed model is selectable. A refreshed token
invalidates the earlier catalog; rediscover with the exact bearer used for POST.

The serialized request contains exactly model, input (one user message array),
store=false, stream=true. The prompt is fixed to Reply with exactly: WristPrompt
works. Output stays in process memory. No unsupported preview fields, conversation
history, BYOK, or production chat layout is added.

SSE framing supports UTF-8/BOM, LF/CRLF/CR, comments, multiple data lines and split
reads. Bound line length to 64 Ki characters, frame/body to 256 KiB, and displayed
output to 64 Ki characters. Only the documented completed event with a completed
response status succeeds; failed, incomplete, malformed, stopped and EOF before
completion remain distinct. An unterminated frame or [DONE] is not completion.
The separate proof gate also requires a nonempty streamed delta and exact final
WristPrompt works. text. Terminal output text, when supplied, is authoritative.
A completed but nonmatching/nonstreamed answer is visibly not a passed proof.

Refresh uses the issued client, refresh_token and resource in the documented form;
omit scope from the request. Refresh-response scope omission preserves the prior
grant; an explicit narrower grant disables plan inference. Persist access/rotating
refresh token, expiry, scope and saved time together. Keep the already-verified
identity and retained ID-token hint; an optional new ID token from refresh is not
used to establish or alter identity. earliest_refresh_at remains bounded opaque
metadata because the current guide gives no format/scheduling contract; expiry
minus 60 seconds drives renewal. Physical near-expiry behavior remains unverified.

One vault mutex serializes refreshes (including concurrent calls for one profile),
selection, and OAuth replacement. This stronger-than-per-profile lock avoids a
second locking system for one primitive proof screen. Bound refresh HTTP to 30
seconds; once a rotating grant starts, finish the bounded exchange and attempt
secure persistence even if the UI cancels. Persist an intent marker before contacting OpenAI.
After process death, network loss, malformed success or uncertain post-grant write,
never replay that refresh token. Preserve encrypted credentials/registration but
make them inert until fresh OAuth. Definitive temporary HTTP errors preserve the
profile, clear the marker, and impose a five-second manual-retry cooldown. Known
terminal refresh errors clear access/refresh tokens while retaining registration,
host, identity and ID-token hint for explicit reauthorization. No revocation occurs.

Starting watch sign-in increments the generation and pauses inference/refresh.
Account changes cancel active calls and clear output/catalog; generation checks
reject late results. Refresh itself does not change the account generation. A
lost phone transfer ACK remains idempotent after rotation and cannot reinstall
older tokens. These are necessary Phase D race protections, not Day 2 progression.

HTTP disables authenticated retries, redirects, caching and all header/body
logging. A streaming call is bounded to two minutes, with a 45-second read timeout;
Stop cancels the underlying call and leaves partial output explicitly incomplete.
Failures retain safe status/code/body-shape/recognized request-ID fields internally,
with redacted exception formatting and static UI messages. Raw provider messages
never enter diagnostics. Usage limits/eligibility do not erase registrations,
loop OAuth, auto-retry generation, infer reset times or change billing. Manage
usage currently exposes a callback and phone settings instructions; automatically
opening the phone browser is deliberately left as a future hook.

### Phase D validation — 2026-10-28

With the same temporary JDK/SDK/cache, ran:

```sh
./gradlew :wear:compileDebugKotlin :mobile:compileDebugKotlin --no-daemon --console=plain
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
```

Final full gate: BUILD SUCCESSFUL, 102 tasks. Wear 80 and mobile 54 test executions
passed (134 executions, 111 distinct tests), none skipped. The cancellation test
caught IOException replacing cancellation after a socket cancel; active-context
checks now preserve cancellation. Other tests cover exact request fields, catalog
filter/order, SSE framing/terminal distinctions, same-bearer discovery, serialized
rotation, process-death/uncertain-grant markers, pre/post-grant storage failure,
manual retry cooldown, account changes, idempotent transfer after rotation, and
redaction. HTTP adapter tests use interceptors with synthetic responses; they do
not contact OpenAI or prove watch networking. Repository tests inject SecretRecord;
they do not prove Android Keystore, physical persistence or real token rotation.

Lint has zero errors, three existing advisories per app (wrapper/target choices,
framework-authenticated exported listener services, and unchanged official logo
path). No new lint warning is suppressed. No emulator or physical inference was
run in Phase D. ADB listed no attached devices; no real credentials were read,
exported, injected or used. The physical proof and refresh gates remain unchecked.

Ran git diff --check and per-file no-index whitespace checks for untracked source;
validated XML and local documentation links. PROJECT_SPEC.md is unchanged. No
staging, commits, pushes, BYOK, production chat polish or Day 2 work occurred.
Exact physical installation/acceptance steps are in INFERENCE_TESTING.md.

## D08 — stream the account model catalog with a separate limit

The user’s physical test returned BODY_TOO_LARGE during model discovery. The
shared 256 KiB buffered response limit was too small for this account’s catalog.
Decode the successful catalog directly from its input stream with the existing
kotlinx.serialization library, retaining only slug/display_name/visibility and
skipping unrelated metadata. Preserve the official models[] contract and service
ordering; no endpoint, credential, billing or model fallback changes.

Set an independent 8 MiB decompressed input budget and retain the 1000-model,
256-character picker-field and duplicate-ID checks. Do not buffer a full catalog
String/JSON tree. Token/error bodies and SSE frames keep their existing limits.
Use the pinned stable library’s decodeFromStream extension with its required
ExperimentalSerializationApi opt-in; no new dependency or prerelease artifact.
A small counting/strict-UTF-8 input wrapper checks cancellation, rejects invalid
encoding even in skipped fields and retains only static failure categories when
the library wraps a stream exception. No raw response enters diagnostics.

This fixes the demonstrated client size-limit defect. Physical model loading and
completed inference still require a retry with the updated APK.

## D09 — validate headerless inference bodies as SSE

Physical inference returned HTTP 200 with RESPONSE_MISSING_TYPE: the client
rejected it before parsing the body. The underlying stream contents have not
been captured. Permit an absent Content-Type on this authenticated public
Responses request to proceed through the existing strict, bounded SSE parser.
Retain rejection of explicitly wrong or malformed media types. Do not interpret
HTTP status, missing headers, plain JSON, HTML, received text or EOF as success.
Only the documented completed event and exact streamed proof output pass.

This is a narrow HTTP interoperability choice, not a credential or endpoint
fallback. TLS/hostname checks, account/plan permission, cancellation, size limits,
failed/incomplete handling, billing choice and no automatic replay remain intact.
No response content or arbitrary header text is logged or shown. Synthetic tests
cover valid headerless completion, malformed/JSON/HTML bodies, premature EOF,
failed/incomplete events, non-200 errors and explicitly invalid Content-Type.
Physical completed inference remains unconfirmed until the user retries.


## D10 — Day 2 Phase B restart persistence

Keep explicit model selection per issued client ID in the existing encrypted
WatchVault selectedModels map. It is an ordinary preference stored with its
sensitive account association, avoiding another DataStore or module. Old vaults
default to an empty map. Reload a current catalog after process recreation; use
the remembered slug only if it is still listed for the selected account. Never
reuse a persisted catalog/bearer or infer availability from a saved preference.
Persist selection before publishing it; reject stale client/generation writes.

Vault loading now validates selected-client/model references and one common host
for saved registrations; login preparation rejects a changed host even when
adding another account. Startup suppresses automatic catalog loading when the
existing durable uncertain-session marker requires reauthorization. Refresh
transport/rotation/intent/cancellation semantics are unchanged.

At implementation handoff, physical A/B/C acceptance was pending. The user
subsequently confirmed all three PASS on 2026-10-28; D2-1 is VERIFIED PHYSICALLY
for authentication/account/reboot persistence and phone-independent discovery/
inference. Model restoration is separately PARTIAL: Terra changed to Sol after
restart while Terra remained listed, deferred as low-priority Day 3 debt.
Exact steps, evidence and state inventory are in PERSISTENCE_TESTING.md. Tests use synthetic records and one encrypted JVM-file
recreation; Android Keystore, OEM reboot persistence, actual watch internet routing
and natural expiry remain physical evidence. No secrets/host identifiers were
logged, and no new logging path or diagnostic payload was added.


Phase B automated verification: both debug builds and lint passed, and all 108
Wear tests passed with zero failures/errors/skips. Lint warnings are unchanged.
Exact commands, evidence limitations and remaining hardware checks are recorded
in PERSISTENCE_TESTING.md. That automated run did not itself establish physical
A/B/C behavior; the later user confirmation above supplies that evidence.


## D11 — bounded per-registration refresh hardening

Retain the existing encrypted vault, durable refresh intent and non-cancellable
bounded rotation/atomic commit. Use a per-issued-client mutex across renewal;
keep the vault mutex only around short reads/writes. Patch the latest vault's
matching profile so another registration's concurrent update is never lost.
Reauthorization of a live refreshing registration waits for explicit retry;
stale selected-account callers cannot receive another account's lease.

A shared SessionClock timestamps receipts and drives expiry/retry policy; tests
can inject coroutine scheduling for timeout. A refresh-only OkHttp send boundary
distinguishes definitely-unsent network failure from possible rotation. Preserve
full credentials on retryable rejection/unsent failure with bounded manual retry;
retain intent on lost/untrusted response. Only confirmed terminal token codes
clear unusable access/refresh material. invalid_client remains configuration.
The static runtime renewal indication is published only after durable commit.

Current token-reference docs still do not define earliest_refresh_at encoding.
Do not guess it. Missing/JSON-null uses the existing margin. Unknown non-null
metadata defers renewal while the bearer is valid and blocks expired renewal
with a configuration category; credentials remain preserved. The interpretation
is BLOCKED pending an authoritative contract, not silently treated as supported.
No vault schema migration or new auth/billing path. See REFRESH_REVIEW.md for
sources, matrix and pending end-of-day device evidence. D2-1 model debt unchanged.


## D12 — explicit local sign-out with bounded remote revocation

Persist a signOutPending marker/generation before cancelling session jobs or
waiting for the registration's bounded refresh. No vault lock crosses network
work or cancellation. After refresh settles, revoke the latest known refresh
token using the selected issued client and a validated discovery endpoint.
Unknown rotation cannot become confirmed revocation merely by revoking an older,
already-invalid token. Network/5xx revocation retries once with bounded backoff;
local cleanup completes with explicit unconfirmed-remote guidance if needed.

Clear access/refresh/retained ID token material and token timing/type while
retaining identity/client/host and account display metadata. The local tokenless
record permits an empty ID hint only with reauthorizationRequired; protocol
transfers still require a nonempty ID token. No profile transfer shape/version
change. Vault pending/result metadata defaults empty for existing records, with
no automatic credential migration. Abandoned sign-out completes local cleanup on
reconstruction rather than reviving the session. Storage errors never report
local success. Same-registration OAuth reuses client/host without id_token_hint,
as current docs specify after sign-out. No billing/account fallback.

See SIGN_OUT_REVIEW.md for sources, state/race review and test evidence. Device
sign-out/re-login/revocation is PENDING END-OF-DAY PHYSICAL TEST; no real session
was revoked by the agent. Prior model-selection and earliest-refresh issues remain.

## D13 — independent explicit ChatGPT-plan and API-key billing

Use a single OpenAiProvider/Responses/SSE transport with explicit mode-bearing
leases. Persist mode and API-specific preference in a separate api_key encrypted
record/Keystore alias; preserve the existing OAuth vault and stable host ID.
Never infer the active billing path from credential availability. Mode revisions
invalidate old work, including switches away and back. API receipt/removal and
ChatGPT sign-out never choose another mode. API work requires no OAuth state.

Prefer masked, process-memory-only phone entry in a watch-requested, short-lived,
node/request/host-bound v1 Data Layer handoff. ACK follows protected durable watch
storage; identical retries do not rewrite the key. Real Test API key uses GET
/v1/models with the API data[].id decoder, separate from the account catalog.
Listing is not proof of Responses support or quota; unsupported models fail safely.
Local BYOK is an advanced user-owned client-side tradeoff, not server isolation.
See BYOK_REVIEW.md for current official sources, security/race boundaries and
limits; DAY2_STATUS.md owns pending end-of-day device checks.


## D14 — Phase F connection settings and explicit usage language

Keep the existing persisted credential router, protected stores and per-mode
model preferences. Expose a fixed active billing indicator above Wear's scrolling
connection settings; show both available credentials independently, with manual
mode selection even when credentials are missing. Never infer mode on receipt,
removal, sign-out or failure. Render proof catalogs only under their matching
mode. Revalidate preferences against the current mode's catalog; Terra → Sol
restart restoration stays Day 3 debt.

Rechecked official [OpenAI UI/UX guidance](https://developers.openai.com/siwc/ui-ux-guidelines)
on 2026-10-28: eligible plan wording, connected identity, Using ChatGPT plan,
first-use confirmation and Manage usage. The notice now qualifies plan usage by
explicit selection; API billing is separate. Retain the watch usage instructions
and add a phone action opening https://chatgpt.com/settings/usage with a no-browser
fallback. Automatic watch-to-phone browser opening remains deferred; no usage
numbers or reset times are inferred.

Keep mobile setup-only. Clear the redundant key text once the existing bounded
retry envelope is prepared, hide the field during transfer/retry, and leave it
cleared after acknowledged protected storage. No plaintext field persistence or
new transfer/authentication contract. Existing wrong-ACK/expiry coverage now also
checks cleared entry during retry. Final automated evidence and the consolidated
physical checklist live in DAY2_STATUS.md. No Day 3 chat or voice implementation.


## D15 — Phase G failure isolation and stale action rejection

Reject inference started with a prepared catalog from another account,
generation, billing mode or mode revision before rediscovery/POST. A shared model
ID is not authorization to retarget a stale user action. Same-registration token
renewal may still rediscover with the fresh bearer and revalidate membership.

Guard phone OAuth context/result/ACK publication with cancellation and the current
request ID; validate exchanged client/host, keep expiry independent of mutable
pending state, and turn expired retry into a terminal timeout. Apply cancellation
checks to late API-key transfer exceptions too. No OAuth/transfer/store contract
or billing-selection rule changes.

Use current official OpenAI error categories: unsupported capability is an invalid
request; unsupported route is configuration; invalid_user/direct 401/403 is not
by itself terminal refresh/revocation proof; user/usage unavailability preserves
credentials. Keep actual HTTP status (including 200 with response.failed), bounded
code/shape/request ID and static UI messages, excluding provider text. No reset
estimate, automatic billing fallback or generation replay.

See DAY2_FAILURE_MATRIX.md and DAY2_STATUS.md for synthetic failure coverage,
security review, final validation and consolidated end-of-day physical checks.
Actual Android Keystore/AtomicFile faults are not established by fake-store/JVM
AES-GCM tests. Never destructively test key loss on the real watch. Prior unknown
refresh metadata and Terra → Sol debt remain unchanged.


## D16 — repair the D2-2 legacy refresh-timing regression (2026-10-29)

D11's blanket non-null earliest-metadata expiry guard regressed a real previously
working registration. Missing/null timing was already compatible; the reported
category implies a non-null hint and clock at/beyond known access expiry, not
proof that the real field was missing. No real record was extracted.

Follow first-party Sign-in devkit evidence linked by the official OpenAI cookbook:
numeric earliestRefreshAt is Unix seconds; date strings may encode an absolute
time. Accept ISO instants only for string values, round sub-ms precision upward,
and never guess ms or relative offsets. Known future constraints defer refresh;
expired-but-prohibited state reports REFRESH_NOT_READY. Unknown non-null values
remain conservative at expiry. No hint means ordinary known-expiry policy; no
new mandatory local metadata or vault migration. Missing known expiry is unsafe.

Keep per-registration locks, durable intent, atomic rotating-token replacement,
conservative uncertain outcomes and all sign-out/BYOK behavior. Only timing
interpretation/static error classification changed. Full evidence, source links
and exact registration-preserving retest: REFRESH_REGRESSION_REVIEW.md.
Broad acceptance is STOPPED pending that physical result. Real BYOK completion
is BLOCKED by no funded API credential, not a product failure; no purchase required.

## D17 — Day 3 Phase A readable proof shell (2026-10-29)

Replace the dense single settings/proof list with a main proof screen and separate
model, connection, details and output surfaces. Keep the active billing path in
a measured header outside scrolling content; eligible plan use and separate API
billing remain explicit. Keep Stop outside scrolling content while the existing
proof state is busy, including on secondary surfaces. No overlays, fixed text
heights, new design dependencies or custom animations. Long labels have two-line
button/summary limits; full selected identity/model remains in Details. Existing
safe account labels are retained, without introducing another identifier.

Use the existing Wear Material 3 AppScaffold/ScreenScaffold and
TransformingLazyColumn, shared scroll state, explicit spacing, side insets and
bottom content padding. Rechecked official Android
[lists](https://developer.android.com/training/wearables/compose/lists) and
[rotary input](https://developer.android.com/training/wearables/compose/rotary-input)
guidance. Retain native scrolling without adding scale/morph effects. First-use
plan confirmation uses the same confirmation action with a scrollable notice.

Concise status mappings preserve completion/mismatch, failure, incomplete,
interrupted and stopped distinctions. Existing long recovery strings and safe
category/HTTP status remain in Details. Render output live in a dedicated list;
split long text into bounded rows without altering the underlying proof output.
No request IDs, raw errors, bodies or new diagnostic fields are exposed.

Only presentation and its tests change: existing ViewModels, model-preference
read/write callbacks, auth/refresh/sign-out, stores, transport and billing router
remain unchanged. Terra -> Sol is still D3-2; text chat is D3-3; streaming chat
integration is later Day 3. Physical readability is not claimed; evidence and
the single end-of-day check are in DAY3_STATUS.md. Prior D16 STOPPED wording is
historical and superseded by final Day 2 acceptance, not a current gate.

## Emulator capability investigation — 2026-10-29

VERIFIED AUTOMATICALLY on the two named AVDs: matching installed APK identity,
connected nearby nodes and bidirectional debug RPC/listener reception, but empty
remote static and programmatically registered capabilities with both discovery
filters. Production reachablePeers therefore returns empty before auth send.
This isolates the observed failure to GMS capability synchronization/discovery;
it does not prove a specific Google defect or justify bypassing capability checks.
Keep production behavior unchanged. Debug-only source-set probes and an
emulator-only runner provide safe retests without credentials or alternate
transports. Original lab signing key continuity requires its existing
ANDROID_USER_HOME; the default debug key differed and updates were rejected safely.
Full evidence, passing validation, unresolved attribution and recovery steps:
[EMULATOR_DATA_LAYER_REPORT.md](EMULATOR_DATA_LAYER_REPORT.md).


## Emulator capability follow-up — 2026-10-29

VERIFIED ON EMULATOR: GMS lists each original static capability in its local-node
table, but remote discovery remains empty through 30 seconds for static and fresh
dynamic advertisements. Both getAllCapabilities filters are empty; registered
listeners deliver only empty-node events. Nearby nodes and bidirectional debug RPC
remain healthy after restoring the saved connection following an ordinary guest
restart. No re-pair, data clearing, forwarding rule or physical-device operation.
Moving the array to wear.xml produced byte-identical resource tables; literal
items also failed to restore discovery. Both experiments were reverted. Production
routing/resources stay unchanged; retain only debug timing/listener diagnostics.
All full tests (Wear 189/mobile 70), focused bridge suites, both debug builds/lint
and whitespace checks pass. No daily physical testing requirement is introduced.
Capability-dependent authentication is NOT VERIFIABLE ON CURRENT EMULATOR LAB;
defer it to physical RC acceptance unless discovery recovers. App-scoped API
results do not prove that unrelated applications have the same problem. Full
evidence and proposed, unimplemented debug scenario strategy are in
[EMULATOR_CAPABILITY_INVESTIGATION.md](EMULATOR_CAPABILITY_INVESTIGATION.md).

## D18 — one bounded in-memory text conversation with explicit replay (2026-10-29)

InferenceViewModel owns one ChatConversation alongside the existing model/proof
state. Immutable messages have a local ID, role, text and pending/completed/
excluded status; they contain no credential or registration state. Drafts,
visible messages and replay items remain in memory only. ViewModel destruction
or process death clears them; no SavedStateHandle, file, DataStore or database
persists conversation content.

A request consists of the completed exchange history followed by exactly one new
user item. Commit the user and returned assistant output items together only
after response.completed and the existing current-lease checks. Preserve opaque
encrypted reasoning and assistant phase/order in returned output items. Reject
unsupported tool output, non-assistant output messages, incomplete output items
and reasoning without replayable encrypted content; do not silently discard
required continuation data. Failed/cancelled/incomplete exchanges remain visibly
unfinished and are excluded from later request history. No automatic replay.

New Chat clears draft, visible messages and replay history. Every distinct
InferenceAccessState change conservatively clears that context, including
account/client, generation, billing mode/revision and access-gate changes. An
explicit model change or catalog fallback to another model also clears context;
a same-model catalog reload preserves it. Track the conversation's model across
failed reloads so temporary empty catalog state cannot hide a model change.
Normal bearer renewal within the same access identity/generation uses existing
rediscovery rules; it does not create a second conversation. Platform input
results are accepted only for the context revision that launched them.

Limits: 8192 UTF-16 code units per submitted prompt, 12 submitted attempts
(including failures), and 192 KiB UTF-8 serialized input/replay threshold. Never
truncate prompts or drop old turns to make room. A completed output is bounded
by the existing 256 KiB event/output-item budget; if adding it crosses the replay
threshold, retain the completed exchange but block further Send until New Chat.
Thus final replay storage is bounded by one input plus one bounded completion
(about 448 KiB), independently of the existing 65536-character visible response
limit. The UI explains when New Chat is required.

Keep store=false, stream=true and explicit input; no previous_response_id,
conversation service or added reasoning controls. The normal chat path supplies
arbitrary input to the same OpenAiProvider/OpenAiHttp/SSE transport and lease
validation as the proof path. The fixed proof remains a separate Details action
for regression checks, not a chat prompt or chat-success criterion.

Official sources rechecked for D3-C:
- [Plan models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [Plan preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [Stateless reasoning replay](https://developers.openai.com/api/docs/guides/reasoning)

The current stateless guide says encrypted reasoning is returned by default;
therefore no new include parameter is added to the plan request. Wear text entry
uses the platform RemoteInput activity and keyboard; Done returns a draft for a
separate app Send action. No voice feature, new dependency, auth/storage/billing
contract or Data Layer routing change. D3-D retains detailed streaming/Stop and
scroll-follow hardening. Current test/emulator evidence belongs in DAY3_STATUS.md.

## D19 — serialized generation ownership and Stop (2026-10-29)

Keep the existing ViewModel operation epoch as the UI request identity. Assign a
lazy Job to the operation field before starting it, including Main.immediate
execution, so synchronous/reentrant completion cannot publish an unowned job.
All chat mutations remain on Main. Busy state prevents duplicate Send; a new
prompt does not implicitly cancel/replay an active prompt.

InferenceSession serializes proof/chat generation through one mutex per session.
Register both active and queued generation Jobs before resolving credentials or
waiting for that lock. Resolve/revalidate the current account/generation/mode/
revision after acquiring it. Stop can immediately accept a new prompt into
STARTING, but its provider/HTTP work waits until the cancelled generation and
its transport cleanup have exited. Discovery-only calls retain their existing
behavior. No second authenticated generation overlaps the old cleanup.

Validate the captured owning Job before and after suspended lease checks and
progress callbacks, even if a provider invokes a callback in NonCancellable.
The ViewModel epoch rejects superseded state/error/completion publication. After
an accepted completed terminal, ignore further provider events; the real SSE
adapter already stops reading at the first terminal. Informational/unknown
well-formed events do not publish text progress. Only response.completed permits
successful finalization and completed-exchange context commit. EOF, failed,
incomplete, malformed and cancelled results remain distinct; no automatic replay.

Stop is a no-op while idle. While active it invalidates the epoch, cancels the
owning coroutine and marks the unfinished chat user turn excluded, retaining the
latest partial output. OpenAiHttp's existing cancellable await finally cancels
the actual OkHttp Call, unblocks the IO reader and closes the response. Keep this
existing transport behavior; tests now exercise it through the chat ViewModel.
Stop disappears when no request is active. Partial display remains until a new
request or New Chat replaces it; unfinished exchanges never enter replay context.
Cancellation cannot promise zero server-side usage.

Cache completed-message display chunks only when the immutable message list
changes, rather than re-splitting all history for every delta. Lazy content must
capture the chat snapshot and matching cache together: a live delegated state
read inside a deferred lazy builder can otherwise see newer message IDs with an
older cache. Emulator acceptance caught and verified the repair of this initial
cache integration error. No forced auto-scroll; manual upward scrolling is
preserved. Bounds and replay ownership remain D18's contract.

Official plan inference and streaming guides rechecked on 2026-10-29:
[plan terminal rules](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference),
[streaming events](https://developers.openai.com/api/docs/guides/streaming-responses).
No endpoint, payload field, OAuth, credential format, billing selection or Data
Layer routing change. A standalone debug-source-set synthetic preview exercises
the real ViewModel/session and shared chat renderer without credential stores,
network, capability bypass or intent-provided data. It is absent from the merged
release manifest. DAY3_STATUS.md owns exact validation and emulator limitations.

## D20 — terminal state recovery and current model ownership (2026-10-29)

A prepared catalog is usable only with the current usable account/client,
credential generation and explicit billing mode/revision. Check the latest access
snapshot at UI command entry as well as the durable lease in InferenceSession.
Revalidate after asynchronous model-preference reads before publishing discovery.
Cancellation of catalog/proof by the shared session must end the current busy
state; an older cancellation cannot replace a newer operation's state.

A selected model rejected during bearer rediscovery or by exact model_not_found
invalidates prepared catalog/selection/Ready state. Require explicit reload before
another Send. Preserve dormant completed context until reload determines the model:
same model preserves it; another model clears it under D18. Authentication-required,
stale-account, missing-key or storage failures invalidate model and incompatible
conversation state without modifying credential records. Ordinary transient request
failures preserve valid completed context. Editing a draft removes stale error
presentation but never sends automatically.

Keep existing specific error categories and explicit manual recovery/navigation.
Plan admission 401 remains a permission failure, not proof of terminal refresh;
API 401 remains API-key rejection, never a reason to change billing. Do not erase
credentials on transient failure. No automatic generation replay, OAuth loop or
credential fallback. Clip scrolling content within its viewport so fixed Stop and
other action controls remain readable. D18/D19 retain replay and transport ownership.

Official [errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
rechecked 2026-10-29. No OAuth protocol, inference request field, credential format,
storage, production Data Layer or billing-route change. DAY3_STATUS.md owns current
test/emulator evidence; these rules do not establish physical anomaly resolution.

## D21 — offline debug scenarios and release isolation (2026-10-29)

Keep deterministic Wear scenarios in debug source sets with one explicitly
launched Activity. Reuse the production ViewModel/session/conversation and renderer;
extract state/action presentation wiring only when needed to avoid accessing live
repositories. Supply inert synthetic account/billing actions, a synthetic provider,
an empty transient bearer lease and memory-only model selection. Never construct
real serializable credential records, secure stores, HTTP clients or production
bridge dependencies for scenarios. No production emulator fallback is permitted.

Validate absence from both merged release manifest and release APK DEX, absence of
production navigation references, and forbidden persistence/network dependencies
with tools/check_scenario_isolation.py. Fresh ready acknowledgements are fixed,
non-secret, debug-only developer artifacts. Activity recreation retains a VM;
process persistence is intentionally not simulated. WEAR_SCENARIOS.md owns the
launcher contract; DAY3_STATUS.md owns D3-F evidence, not final Day 3 acceptance.

## D22 — retained-process lifecycle and composer interruption (2026-10-29)

The Activity's ViewModelStore owns InferenceViewModel; that ViewModel owns the
in-memory conversation and operation Job. Recomposition and retained Activity
recreation do not create a generation. Home/background pauses lifecycle-aware UI
observation independently of generation ownership. Foreground return observes the
latest state without resending, rediscovering models or changing credentials.
Background and screen-off continuation is best effort while the owner/process
survives, never a promise of uninterrupted execution. Add no service, wake lock,
production keep-screen-on flag or automatic cancel-on-background policy.

Final ViewModel clear synchronously invalidates the UI epoch and pending input
revision, excludes an active exchange and cancels normal inference. Existing
session job checks and serialized generation mutex remain authoritative; the HTTP
adapter cancels the Call and closes its Response/body while reads unwind. Shared
application-owned session/provider objects are not destroyed with the Activity.

A platform-composer interaction belongs to its launching composition. Deliberately
discard its result across recreation: dispose the old single-use callback token,
retain only the surface and an interrupted-input UI marker with rememberSaveable,
and offer Write message again. Neither prompt text nor a request token is saved.
A normal result updates a draft only, requires the current account/billing
revision, and is consumed once. Send remains explicit. Existing drafts stay solely
in the retained ViewModel; no durable conversation/draft history is added.

Rotating refresh remains separate: WatchProfiles' durable refreshPending intent,
35-second bounded NonCancellable refresh and atomic commit/uncertainty behavior
are unchanged. Owner destruction cannot authorize reusing a possibly consumed
grant or deleting valid credentials. Process death is a separate D4-B boundary;
network transitions are D4-C. Neither phase is implemented by this decision.

Repository code and official Android [ViewModel lifetime](https://developer.android.com/topic/libraries/architecture/viewmodel),
[Compose state](https://developer.android.com/develop/ui/compose/state), and
[Activity Result registration](https://developer.android.com/training/basics/intents/result)
were reviewed. DAY4_STATUS.md owns automated/emulator evidence and pending
physical checks; emulator sleep is not Galaxy Watch power-management evidence.


## D23 — fresh-process shell state boundary (2026-10-29)

D4-B reproduced an Android saved-state boundary gap in D22: a new process/new
InferenceViewModel could restore the previous shell page via rememberSaveable.
Use an Activity-owned ShellNavigationViewModel for surface and pending-input UI
marker instead. They are retained through Activity recreation and cleared with
the owner/process. This supersedes only D22's saveable surface/marker choice;
composition-owned single-use result discard remains unchanged. No SavedStateHandle,
new persistence, request restoration or durable interruption marker is added.

D18's memory-only conversation and D19's no-replay rules remain authoritative.
Fresh repositories/router/session/ViewModels reconstruct only existing durable
configuration, perform current-catalog discovery/reconciliation, and start empty
chat. A killed client cannot establish the remote generation/usage outcome;
replaying its previous prompt is prohibited. Existing durable refresh uncertainty
and sign-out cleanup remain fail-closed; no credential schema or recovery change.
Actual background am-kill/task-resume and separately labelled force-stop acceptance
are recorded in DAY4_STATUS.md; hardware reboot/Keystore acceptance is separate.

Official Android [saved UI state](https://developer.android.com/develop/ui/compose/state-saving)
and [process lifetime](https://developer.android.com/guide/components/activities/process-lifecycle)
were checked. The process runner never claims force-stop is equivalent to every
system low-memory kill. D4-C connectivity work is not authorized by this decision.

## D24 — chat-first retained Wear shell (2026-10-30)

D5-A supersedes D17's fixed billing header and top-level proof layout only.
Keep AppScaffold/ScreenScaffold/TransformingLazyColumn and the existing version
pins. A static black-first Wear Material 3 palette uses restrained teal, native
shapes and system typography. The debug host uses the same theme.

The chat header (title, model affordance, compact explicit billing disclosure,
Menu) scrolls away naturally. Use a conventional measured Ask control, explicit
Send in draft review, and fixed Stop on every surface while chat/proof/catalog
work is busy. Reject EdgeButton for D5-A: its scaffold morphing/scroll-dependent
visibility is unnecessary for the required always-reachable control; no custom
animation or imitation. Composer billing remains visible immediately above Send.

Extend ShellNavigationViewModel with a bounded unique-destination back path and
plain reading-index/offset anchors per visited surface and a separate welcome
anchor. Lazy-list objects remain composition-owned and report anchors on disposal;
no old layout objects are retained by the navigation owner. Anchors never enter a
SavedStateHandle or durable record. Navigation/back changes do not call inference,
model reload or conversation reset. The process boundary remains D23.

Ask launches the existing composition-owned platform input interaction when no
draft exists; a retained draft returns to review. Returned text never auto-sends.
Existing revision/single-use/disposal checks remain authoritative. ShellInteraction
only connects shell events to those existing owners; it owns no generation.

Chat → Models / Composer / Menu; Menu → New Chat / Account & Connection /
Settings & About; Settings → Diagnostics → proof with inline output. Safe account,
credential setup/removal, explicit mode selection, usage and sign-out actions stay
available. D4-H signed-out identity redaction is preserved.

Only a distinct chat-busy projection is observed at shell level. The current
transcript/cache is collected in ChatScreen and draft text in ComposerScreen;
Send eligibility has a distinct boolean projection. These additional narrow
observers enable global Stop/Send without reading every delta into the shell.
No timers, coalescing, parser, auto-scroll or new background work. The matching
chat-snapshot/chunk-cache rule and existing centered 80-character renderer remain.

D5-D constraint: picker rows display only the authoritative discovered name and
selection state; raw ID when no friendly name is supplied. No inferred speed,
quality, reasoning, capability, descriptions or ranking. D5-B/C/D/E and D6 remain
unstarted. DAY5_STATUS.md owns evidence and physical/performance limitations.

## D25 — bounded plain-text transcript presentation (2026-10-30)

D5-B replaces only the chat transcript's centered 80-character presentation.
Keep canonical ChatState, ViewModel epochs, monotonic local message IDs, session
serialization, Stop/HTTP cancellation and completed-only replay authoritative.
No request, credential, storage, auth, bridge, billing or dependency change.

An Activity-owned ChatPresentationOwner remains retained across navigation and
recreation. It continuously receives cheap canonical notifications, but projects
only while Chat is STARTED and visible. Pause cancels pending work. Resume uses
one latest snapshot; empty/reset state also releases hidden projections. No
request is owned or invoked by this adapter, and no UI/layout object is retained
in navigation. No SavedStateHandle or durable conversation content.

An event-triggered coalescer publishes first text and state/attempt/terminal/reset
boundaries immediately. Text-only changes schedule one 100 ms flush, reading the
newest canonical snapshot at execution. There is no debounce starvation, queue,
polling or idle ticker. A scheduling generation plus existing monotonic user
message/attempt ID rejects stale delayed work; canonical busy state is rechecked
before flush. Projection/publication is synchronous on Main, so an asynchronous
old projection cannot race a terminal. Duplicate identical canonical snapshots
are weakly tracked without retaining another full partial string.

PlainTextProjection uses newline boundaries within a normal 512 UTF-16-unit row
budget. Multiple short paragraphs can share a bounded row; exceptional long
paragraphs use Unicode-safe internal cuts and zero added inter-segment spacing.
Preserve surrogate pairs, combining sequences, ZWJ/modifier/tag emoji and flags.
A single exceptionally long grapheme can exceed the normal budget rather than
be corrupted. Completed rows reference ranges of canonical text. Streaming rows
copy only settled slices and the bounded unfinished tail, never retaining a chain
of old full partial strings. Settled rows are reused during deltas; authoritative
terminal reconciliation preserves matching settled boundaries and replaces their
slices with canonical ranges. Corrections replace incompatible projections.

Cache only disposable message projections, estimating 128 bytes per range/key
plus 64 bytes per message, capped at 256 KiB. No TextLayoutResult, styled full-text
copy, replay context or measured Compose node is cached. Eviction never mutates
canonical state. The current stream's settled slices are separately bounded by
the existing 65,536-unit output limit. Counters are opt-in and supplied only by
tests/debug host; normal production/release does not allocate/update metrics.

Lazy keys derive from user/message ID and stable segment number; tail and settled
versions of a logical row share a key. Content types distinguish user, assistant,
tail, status and recovery, with header/action types for shell rows. Default body
is 16 sp / 20 sp, user 15 sp with 8 dp inset, start-aligned and native font-scaled.
Flat assistant text replaces enclosing bubbles and permanent completion labels.
A lazy boundary substitutes only its matching newline visually; accessibility
retains the original text, including whitespace and terminal trailing newlines.

There is no forced follow. Latest appears when transcript content is below the
viewport and is explicitly clicked; it shares the existing control band with a
separate Stop target. Large-font mode uses an accessible downward arrow labelled
Latest. Wear's scrollToItem centers a row, so one bounded follow-on scroll reaches
the bottom of a tall last row. Logical key plus offset restores reading state;
index is only a fallback if content genuinely disappeared. Existing library keys
maintain the anchor during list changes.

The D5-C seam is the row projection, not a parser/AST. D5-C can supply richer native
rows without changing shell actions or generation ownership. No Markdown syntax,
HTML, links or WebView is interpreted here. Existing string accumulation in
StreamResult remains unchanged; current debug samples do not isolate it as a
material runtime bottleneck. Consolidated release profiling remains D5-F.

Official native scrolling semantics checked against the pinned local API and
[Android's TransformingLazyColumnState reference](https://developer.android.com/reference/kotlin/androidx/wear/compose/foundation/lazy/TransformingLazyColumnState).
DAY5_STATUS.md owns exact test/emulator/performance evidence and physical debt.

## 2026-10-30 — D5-C disposable native Markdown

Use only official `org.commonmark:commonmark:0.31.0` and
`org.commonmark:commonmark-ext-gfm-tables:0.31.0` in Wear. Both exact Maven Central
artifacts resolve; core has no runtime dependencies and tables depends on core.
The exact tag's [README](https://github.com/commonmark/commonmark-java/blob/commonmark-parent-0.31.0/README.md)
states Java 11+, Android API 19+ on a best-effort basis; this app uses JDK 17 and
minSdk 30. Both JARs have Java 11 class version 55. Artifact sizes are 220,558 and
24,482 bytes (245,040 combined). Both use BSD-2-Clause; preserve the upstream
notice in THIRD_PARTY_NOTICES.md and accompany binary distribution at D7/RC.
Repository LICENSE remains unchanged. Build evidence is in DAY5_C_REPORT.md.

Canonical ChatState/ChatConversation, replay, transport, authentication and billing
remain authoritative and unchanged. One retained, visibility-gated presentation
worker converts parser AST into small native immutable display blocks and releases
AST references. No HTML intermediate, WebView, image decoder, URL launcher, DNS,
preview fetch or privileged intent is involved. Native spans are built off Main.
The existing 100 ms coalescer still immediately publishes first text, Stop and all
terminal/reset boundaries as plain text, independently of rich rendering latency.

Settlement is intentionally conservative: completed exchanges are the settled
prefix; the current answer stays in D5-B's bounded Unicode-safe plain reading
segments until terminal reconciliation. A blank line alone never establishes a
Markdown boundary. No current-answer full parse occurs during streaming. This
forgoes incremental rich formatting within an active answer rather than guessing
about lists, fences, tables, reference links and delimiters. On any terminal state,
parse authoritative final/replacement/retained partial text once as needed.
A conflated channel permits one active conversion and one newest pending snapshot;
publication requires the exact current canonical ChatState identity and visible
ownership. Pause/reset/owner clear reject stale publications. CPU parsing is bounded
but not forcibly interruptible; stale active work may finish without publishing.

Budgets: 16 open block parsers, 16 inline nesting, application conversion depth 32,
512 rich blocks, 2,048 UTF-16 units per styled/code segment (Unicode-safe boundary),
65,536 UTF-16 source units per answer, 262,144 accounted cache bytes, three visible
list levels, eight links per inline block and 2,048 units per destination. The
cache accounting includes source, display text, link destinations and fixed object
allowances; it is a budget estimate, not a measured heap allocator limit. Oversized
or failed rich projection falls back to complete, segmented plain source, never
canonical truncation or inference failure. Evicted history is not reparsed during
streaming; the existing plain projection supplies uncached history.

GFM tables become records: first column heading, remaining cells header/value in
source order, blank headers `Column N`, absent cells `(empty)`. Limits are eight
columns, 100 body rows, 16,384 table-source UTF-16 units and 808 parser table cells.
Excess cells (which GFM would drop), oversized tables or conversion failures use
exact plain-source fallback, preserving all data. No desktop grid is constructed.

Fenced code uses bounded monospace segments on neutral surfaces, horizontal touch
scroll only, no vertical child scroller. Source spans restore original CR/LF
separators and avoid synthesized trailing newlines in unclosed fences. Lists and
quotes flatten visually; headings cap at 19sp/17sp/16sp without suppressing font
scaling. Links are underlined with explicit local inspection of label, destination
and a URI-derived host. Only valid HTTP(S) with a host and no userinfo receives a
potential-web classification; all schemes remain non-executable in D5-C. Raw HTML
is literal; images show alt text without retaining/fetching their destinations.

No restrictive plain-text-only model instruction was found in production sources;
no request instructions or inference code were changed. Model-picker rows are
untouched and gain no Markdown demo metadata. D5-D/E/F/G and D6 stay separate.

## D27 — name-only picker and secondary screens (2026-10-30)

MODEL PICKER POLICY: Only authoritative model name + selected state.
No speculative metadata. Preserve service order and slug identity; indexed lazy
rows use slug keys, native selected semantics, visible checkmark and three-line
wrapping. Unselected rows are neutral; selected rows use muted teal. API catalogs
continue displaying raw IDs without inferred capabilities or rankings.

Account & Connection places explicit billing route before the separate ChatGPT
and API controls. Settings contains installed version, memory-only conversation
behavior and Diagnostics; engineering/proof details remain secondary. This is
presentation only: no protocol, routing, credential, persistence or navigation
owner change. Evidence and limitations: DAY5_D_REPORT.md.

## D28 — pure error/recovery presentation (2026-10-30)

ErrorPresentation.kt owns exhaustive ApiProblem title/explanation/action metadata,
plus terminal-chat and model-catalog display projections. These are pure UI
functions; no endpoint, error taxonomy, transport/session, credential, bridge or
billing decision moves into them. RecoveryAction names existing explicit commands.
Chat's fixed action uses the current security/setup gate before chat recovery;
Ask still opens composition/review and never resends. Manage usage retains the
existing usage guidance in Diagnostics without an automatic phone request. Catalog errors offer manual reload only when
current access permits it. A bare plan 401 remains permission failure, not proof
of a terminal grant; it must not trigger OAuth.

Completed chat has no status row. Stopped/interrupted/incomplete retain partial
text and an explicit future-context exclusion note. Before-output failures have
no empty assistant row. Markdown failure remains exact-source display fallback,
not inference failure; terminal metadata remains outside rich blocks. Diagnostics
retain existing allowlisted protocol category/HTTP detail; normal errors contain
only static product copy. No timer, polling, observer, retry or new persistence.
Evidence and complete copy matrix: DAY5_E_REPORT.md. D5-F/G and D6 remain separate.


## 2026-10-30 — GPLv3 and public development handoff

Owner selected GNU GPL version 3 ONLY (SPDX GPL-3.0-only), replacing the previous
Apache-2.0 project-license target. No root LICENSE had been tracked; canonical GNU
GPLv3 text is now supplied without custom restrictions. Third-party dependency and
Gradle wrapper licenses remain intact. Commercial use is permitted under GPLv3.

This pass changes documentation/license/ignore files only. No production, build,
authentication, storage, billing, signing or protocol behavior changed. D5-F stays
PASS WITH KNOWN DEBT; D5-G stays PHYSICAL ACCEPTANCE PENDING; D6 is NOT STARTED.

Initial publication review was BLOCKED — LICENSE COMPATIBILITY REVIEW REQUIRED
(resolved by the owner-authorized exception below): locally resolved
Google Play Services Wearable 20.0.1 POM declares the Android SDK License. An
applicable GPLv3-compatible grant/exception/boundary has not been established.
Owner-authorized sanitation replaced machine-specific home paths in 18 historical
Markdown blobs across all 28 main commits; author/date/message and source/build
content are unchanged. See docs/HISTORY_REWRITE.md for evidence ID mapping and
docs/PUBLICATION_AUDIT.md for scope. No new handoff commit, push, visibility change,
tag or release was made. Target repository was
verified private and empty. Full D5 validation is intentionally not rerun for prose.


## 2026-10-30 — narrowly scoped Google Play services additional permission

Owner explicitly authorized GPL-3.0-only plus the conventional GPLv3 section 7
linking-exception approach. LICENSE is unchanged canonical GPLv3. New
LICENSE_EXCEPTION.md covers only Google's unmodified play-services-wearable 20.0.1,
play-services-base 18.5.0, play-services-basement 18.9.0 and play-services-tasks
18.2.0, for the existing Wearable Data Layer/Tasks integration. No blanket
proprietary permission, third-party relicensing, proprietary WristPrompt relicensing,
source-obligation waiver for WristPrompt or fake SPDX exception identifier.

Both apps' releaseRuntimeClasspath dependency reports were resolved offline, and
existing debug/release dependency models agree on these four exact versions.
All four POMs declare Android SDK License. The exception avoids relying on the
System Libraries classification. GPL-3.0-only remains the base SPDX identifier;
describe the project as GPL-3.0-only with the documented narrow linking exception.

Ownership review: all 28 existing main commits have the owner's handle/noreply
identity as author and committer; no co-author trailer, separately attributed
WristPrompt source or copied third-party GPL material was identified. Ordinary
dependencies and upstream wrapper remain separately licensed. The owner grants
the permission only for their code. Future contributions are expected under the
base license plus exception without a CLA or copyright assignment; no retroactive
contributor consent is claimed. Later outside contributions require renewed review.

Bounded compatibility result: PASS — GPLv3 WITH NARROW LINKING EXCEPTION.
Third-party notices remain intact. Full D5 acceptance was not rerun for license/docs
changes; no production/build/tool behavior or phase boundary changed. See
THIRD_PARTY_NOTICES.md and docs/PUBLICATION_AUDIT.md for evidence and publication gates.

## 2026-10-31 — D5-H terminal summary and logical text boundaries

A terminal `output: []` decoded to empty text and an empty replay list; this
synthetically reproduced answer disappearance before the Markdown worker ran.
Treat no terminal items like the existing omitted-output summary: retain bounded
streamed text and use the existing completed-message replay construction. Explicit
terminal text, including explicit empty text, remains authoritative; nonempty
structured output/reasoning retains the prior validation/replay path. Only
response.completed commits success. No real service payload was captured, so
attribution to the owner's exact physical failure remains an inference. Official
Responses streaming and SIWC inference contracts were rechecked; no endpoint,
request fields, credential selection or auth change. See DAY5_H_REPORT.md.

Spacing is owned by logical items/blocks. Bounded shell/Markdown continuation
segments carry continuation metadata and receive no paragraph gap; real blocks
retain spacing. A thin shell list adapter preserves existing item/key/navigation
behavior and laziness; no giant whole-answer text container or background work.
Native max-font title breaks intentionally at the brand word boundary; Menu uses
a full-width independent target. Diagnostic proof remains exact streamed output
plus completion, labelled separately from chat and owned by the existing epoch.

## D6-B storage refinement — 2026-10-31

Use bounded typed domain checkpoints in separate SQLite display/replay columns,
committed with ownership/metadata in one transaction. This refines D6-A's proposed
normalized per-message schema for the existing twelve-attempt conversation bound.
No UI projections or rendered Markdown are serialized. Private binary attachment
lifecycle and its schema extension remain D7-C/D. See docs/D6_B_STORAGE.md.

An optional random conversationScope is added lazily to the encrypted API vault;
old v1 records decode with the default null field, and their secret/selection stay
unchanged. Replacement/removal changes the scope. This prevents revision-counter
reuse from attaching old chats to a newly provisioned key. No token hashes or raw
credential/registration identifiers enter chat metadata. Negative tests cover
migration, recreation, failed persistence and stale leases. No new dependency.
