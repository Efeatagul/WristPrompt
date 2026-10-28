# WristPrompt

Your key. Your model. Your watch.

Day 1 Phase D implementation for a lightweight Wear OS AI client. Continue with ChatGPT
requests setup on the paired phone. An explicit phone action opens OpenAI OAuth
in the browser; verified credentials are transferred to protected watch storage.
Watch can now discover account models and stream a fixed Responses API proof prompt,
with serialized token refresh when needed. The user confirmed the physical
Galaxy Watch 7 sign-in-to-completed-inference proof on 2026-10-28. Refresh and
recovery hardening remain open; see [DAY1_REPORT.md](DAY1_REPORT.md).
The API-key action stays disabled. There is no mobile chat interface.

## Build

Use a full JDK 17 and Android SDK with Platform 37 and Build Tools 36.0.0. Set
`JAVA_HOME` and `ANDROID_HOME`, or configure the SDK using Android Studio's ignored
`local.properties`. Gradle 9.6.0 is downloaded and checksum-verified by the wrapper.

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew :wear:lintDebug :mobile:lintDebug
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest
```

Focused unit tests cover host-ID generation/persistence, protocol validation,
redaction, node selection, timeouts, phone receipt storage, PKCE/callbacks, signed
ID-token validation, profile transfer/replay protection, AES-GCM tamper rejection,
model/request decoding, SSE terminal handling, token rotation and cancellation.
Shared protocol tests compile and run in both apps. Build output lives under each module's ignored
`build/` directory. Auth checks are in [BRIDGE_TESTING.md](BRIDGE_TESTING.md);
the required real inference proof is in [INFERENCE_TESTING.md](INFERENCE_TESTING.md).

| Module | Platform | minSdk | compileSdk | targetSdk |
| --- | --- | --- | --- | --- |
| `:wear` | Wear OS, Wear Material 3 | 30 | 37 | 36 |
| `:mobile` | Android, Material 3 | 26 | 37 | 36 |

Both applications use `io.github.grassified.wristprompt`. Install the appropriate
APK on each device. Both debug builds use the same local default debug key; builds
from different machines may have different keys. Matching package and signing
certificates are required for Data Layer discovery. Never add signing keys or
credentials to the repository.

## Connectivity and security boundary

Each app advertises its own Data Layer capability. Watch generates and persists a
random `urn:uuid:<UUIDv4>` once, then discovers one eligible phone and sends a
versioned, expiring request through MessageClient RPC. Phone validates the payload
and source node, stores non-secret request metadata, and returns a correlated
receipt. A background listener allows receipt while phone UI is closed; open
WristPrompt on phone to see it. Requests do not open an activity or browser
automatically; tap Continue with ChatGPT on phone to authorize.

The canonical codec, protocol constants, Data Layer adapter, and safe diagnostics
live under `shared/src/` and compile into the two apps without another module.
The phone's Preferences DataStore is for non-secret setup metadata only. OAuth
transaction state and tokens remain in phone memory; only incomplete issued-client
metadata is encrypted on phone. Watch profiles use Keystore AES-GCM in atomic
no-backup files. A separate expiring node/request/registration binding protects
profile transfer. Retry a lost ACK without repeating OAuth or overwriting tokens.
See BRIDGE_TESTING.md for the Phase C boundary and acceptance checks.

Both apps disable backups, exclude app data from cloud/device-transfer backups,
and disable cleartext traffic. The short-lived incoming listener binds only to
127.0.0.1; outgoing OpenAI requests use HTTPS. The watch sends the test prompt
directly to the public Responses API using the selected ChatGPT registration.
Explicit watch sign-out now attempts discovery-backed renewable-session revocation,
clears local token secrets and retains the saved registration for later sign-in.
Unconfirmed remote revocation is reported explicitly; see
[SIGN_OUT_REVIEW.md](SIGN_OUT_REVIEW.md) and the end-of-day checklist in
[DAY2_STATUS.md](DAY2_STATUS.md). There is no BYOK, production chat UI or analytics.
Phone process death discards
untransferred tokens; keep setup alive during authorization.

See [PROJECT_SPEC.md](PROJECT_SPEC.md) for the product/security contract,
[ROADMAP.md](ROADMAP.md) for gates, [DECISIONS.md](DECISIONS.md) for versions and
validation evidence, and [AGENTS.md](AGENTS.md) for engineering rules. Apache-2.0
licensing and release documentation remain scheduled release-preparation work.
