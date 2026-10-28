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
