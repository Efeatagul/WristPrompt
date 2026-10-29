# Paired-emulator Data Layer investigation — 2026-10-29

This is the initial investigation record at `35eed8d`. The current, expanded
resource/timing/restart audit and probe lifecycle are documented in
[EMULATOR_CAPABILITY_INVESTIGATION.md](EMULATOR_CAPABILITY_INVESTIGATION.md).

Status: **VERIFIED AUTOMATICALLY** (unit/build/lint and paired-emulator evidence).
Physical-device evidence: none collected in this investigation. The physical phone
and Galaxy Watch were not accessed or modified. No milestone is advanced.

## A. APK signing check

Before edits, both existing debug APKs passed apksigner verification and their
complete SHA-256 digests matched exactly. Both APKs pulled from the identified
emulators also matched that digest. Only its suffix is recorded: `79d37061`.
Package identity is `io.github.grassified.wristprompt` on both.

A first rebuild selected the default `~/.android/debug.keystore` (suffix
`f1bc8b80`), producing matching new APKs that could not update the installed apps.
Android rejected both updates with INSTALL_FAILED_UPDATE_INCOMPATIBLE; nothing
was uninstalled or cleared. The existing lab key under
`~/.local/share/wristprompt/android/debug.keystore` matches `79d37061`.
Setting ANDROID_USER_HOME to that existing directory restored signing continuity;
both diagnostic APKs then updated successfully using install -r. No signing key,
production signing configuration or credential format was changed.

## B. Current architecture

- Shared main sources are compiled into :wear and :mobile; no extra module.
- NodeClient is not used by production discovery.
- WearConnectivity queries the opposite capability with FILTER_REACHABLE on every
  reachablePeers call. It does not cache discovery or subscribe to capability events.
- Static android_wear_capabilities arrays declare wristprompt_mobile on phone
  and wristprompt_wear on watch. The packaged arrays resolve to the expected
  strings; raw/keep.xml retains them. CapabilityClient excludes the local node,
  so querying one's own capability is not a local-advertisement confirmation.
- WatchAuthBridge prefers exactly one nearby capable peer; one non-nearby capable
  peer is also accepted. Multiple unresolved peers cause ambiguity. Nearby is
  therefore a preference, not a blanket rejection of remote peers.
- Empty capable-peer discovery returns false before preparing/sending an auth RPC.
  WatchAuthViewModel maps false to COMPANION_UNAVAILABLE; exceptions have separate
  ERROR/MULTIPLE_PHONES states. A new tap performs fresh discovery; the displayed
  failure does not automatically clear when connectivity changes.
- Production uses MessageClient.sendRequest, not sendMessage. The phone
  AuthBridgeListenerService receives onRequest via REQUEST_RECEIVED and wear://*.
  AuthStartReceiver decodes and validates freshness, verifies the source against
  wristprompt_wear capable peers, and writes the bounded non-secret request inbox.
- The inbox Flow drives CompanionViewModel and PhoneAuthViewModel (WAITING -> READY).
  Receipt follows durable inbox acceptance. Production credential response RPCs use
  ProfileListenerService on watch, with transaction validation in the existing code.
- Data Layer enforces matching package/signature. Discovery is not credential
  consent; existing source/request/host/expiry validation remains unchanged.

## C–D. Failure layer and evidence

**Observed failing boundary: capability advertisement synchronization / remote
CapabilityClient discovery (layers 3–4).** The available APIs do not distinguish
which internal GMS side drops the capability advertisement.

| Observation | Phone | Watch |
| --- | --- | --- |
| Dynamically verified AVD | WristPrompt_Phone | WristPrompt_Watch7 |
| Characteristics | emulator | emulator,nosdcard,watch |
| GMS availability | 0 (SUCCESS) | 0 (SUCCESS) |
| GMS version | 26.42.31 (260800-1005867181) | 25.11.34 (240800-740024306) |
| Local node suffix | e637d4 | edfb08 |
| Connected remote node | Watch7, nearby=true | Phone, nearby=true |
| Packaged local capability present | true | true |
| Expected remote capability, FILTER_ALL | empty | empty |
| Expected remote capability, FILTER_REACHABLE | empty | empty |
| Temporary debug capability local registration | success | success |
| Remote temporary debug capability | empty | empty |
| Actual production reachablePeers decision | NODE_EXISTS_CAPABILITY_MISSING | NODE_EXISTS_CAPABILITY_MISSING |
| Empty debug sendRequest / fixed response | success | success |
| Debug listener invoked on receiving app | confirmed | confirmed |

Repeated snapshots reproduced the capability failure. Successful bidirectional
RPC proves that connected nodes, package/signature routing, MessageClient RPC,
and debug WearableListenerService reception work in this pair. The additional
programmatically registered capability also stays undiscovered, so an XML-only
static declaration explanation is insufficient. A repeated registration can
report API_STATUS_4006 (already exists); it does not mean the original registration
failed. No raw GMS exceptions or credential payloads were captured.

Production discovery was executed directly by the probe and returned no peers.
Thus production selection stops before sending the sign-in request. Production
auth listener invocation, inbox writes and end-to-end ViewModel propagation were
not exercised by this credential-free probe. There is no evidence that they are
causing this symptom. The UI's last failed attempt can remain displayed; repeated
fresh discovery still failed, excluding stale UI as the cause of these snapshots.

## E. Changes

- One debug source directory shared by the two apps, attached only to debug builds.
- Debug-only snapshot Activity and fixed, empty-request RPC listener in debug
  manifests. They are absent from main/release source sets and manifests.
- Snapshot includes package/build, certificate suffix, GMS status/version, node
  names/suffixes/nearby flags, packaged declaration, capability queries, production
  discovery decision, and debug RPC attempt/result. No auth RPC is sent.
- Temporary `wristprompt_debug_connectivity` capability is diagnostic only;
  removal is requested when the Activity is destroyed. Abrupt process death may
  leave this debug advertisement registered; repeated runs are safe. It is never
  a production discovery target. No production capability is dynamically changed.
- Reports are overwritten in app-private debug files. The receiver writes only a
  fixed listener-invoked marker. No BridgeLog overload or arbitrary log path added.
- tools/emulator_data_layer.py discovers only emulator serials, requires both exact
  AVD names and validates watch characteristics before installing/running anything.
  It never selects a physical device, forwards ports, wipes data or reads secrets.
- This report and a short DECISIONS.md evidence pointer. No production behavior,
  authentication, storage, billing, dependencies or UI redesign changed.

## F. Automated validation

Final command (full JDK and original lab signing directory):

```sh
ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android" \
JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9" \
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest \
  :wear:lintDebug :mobile:lintDebug :wear:assembleDebug :mobile:assembleDebug
```

PASS: Wear 189 tests and mobile 70 tests; zero failures/errors/skips. Both debug
builds and both lint tasks passed. Existing lint warnings remain. git diff --check
passed; new text files were separately checked for trailing whitespace/conflict
markers. The emulator runner successfully updated both apps and captured fresh
reports with bidirectional RPC success. Initial attempts failed because the
sandbox could not write Gradle's cache, the system Java 25 lacked a compiler,
and the first diagnostic revision needed an API-26 signing-query fallback; all
were resolved before the final passing run. No dependencies were changed.

## G. Exact emulator retest steps

1. Boot the existing two named AVDs. Keep both unlocked. Do not reset/re-pair yet.
2. Run the build command above, preserving the original debug key. A different
   workstation must use its own already-matching lab key; never overwrite keys.
3. From the repository root:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
python3 tools/emulator_data_layer.py --install
```

This discovers the serials, updates only these AVDs with install -r, launches both
debug probes and prints fresh app-private reports. Omit --install for repeat
snapshots. Node IDs are suffixes only. Leave both probes open for a while and
rerun to check delayed capability synchronization. Each run queries afresh.

4. Expected healthy result: one connected node each, the opposite production
   capability returned with FILTER_REACHABLE, CAPABILITY_DISCOVERED, and debug
   RPC success. Merely seeing Bluetooth connected is not this result.
5. If capabilities remain empty, first inspect/install offered Google Play
   services and Pixel Watch companion updates **on these AVDs only**, using their
   normal Play Store/system update interfaces. Watch GMS is substantially older
   in this snapshot, but version difference alone is not proof of causality.
   Repeat the same probe after each change to establish an A/B result.
6. If updates do not help, restart the existing AVDs using Android Studio and
   let the saved pairing reconnect. Rerun the probe; do not wipe/recreate or clear
   GMS/app data. The official pairing assistant is the pairing mechanism.
7. Only after capabilities recover: open WristPrompt on both emulators and tap
   Continue with ChatGPT on watch once. Phone should leave Waiting and show its
   received-request action. Stop before phone authorization; real account copying
   or OAuth is unnecessary for this connectivity retest. That production
   listener/inbox/UI acceptance remains pending until capability recovery.

## H–I. Attribution and production decision

**Strong inference: emulator / Google Play services capability synchronization
problem in this lab; not a demonstrated WristPrompt production defect.** This is
not proof of a particular known Google bug, a Pixel Watch association root cause,
or a specific GMS version defect. The exact internal cause needs a successful
update/restart A/B test or a minimal independent Google sample reproduction.

Keep production Data Layer architecture and capability-based trust/selection
unchanged. Do not bypass capability eligibility for real sign-in/credentials.
Use the debug-only empty RPC as transport evidence while capability discovery is
broken. Physical-device acceptance is separate and was not repeated here.

## J. Suggested commit message

`Add debug-only paired-emulator Data Layer diagnostics`

No commit or push was performed.

## Primary references checked

- https://developer.android.com/training/wearables/data/overview — package/signature enforcement.
- https://developer.android.com/training/wearables/data/discover-devices — NodeClient vs capability discovery.
- https://developers.google.com/android/reference/com/google/android/gms/wearable/CapabilityClient — filters, local exclusion, dynamic capability API.
- https://developer.android.com/training/wearables/get-started/connect-phone — official AVD pairing assistant.
