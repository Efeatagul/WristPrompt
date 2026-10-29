# Capability synchronization follow-up — 2026-10-29

Baseline commit: `35eed8d test: add paired-emulator Data Layer diagnostics`.
No commit/push performed in this investigation.

## A. Executive result

**UNRESOLVED — STRONGLY ISOLATED TO GMS/EMULATOR**

**VERIFIED ON EMULATOR:** nearby node discovery and credential-free RPC work in
both directions. Static capabilities exist in each APK **and in GMS's local-node
capability table**, but remote capability queries stay empty. Explicit dynamic
registration and measured retries do not restore them. Production code is unchanged.
The internal GMS synchronization/filtering defect is not identified by this public
API evidence; this is not a claim of a particular acknowledged Google bug.

## B. Root cause boundary

The failure occurs after GMS recognizes the local app capability and before it
exposes that capability on the other node through CapabilityClient. Both
getCapability filters and getAllCapabilities omit all remote app capabilities,
while the same package/signature communicates using MessageClient.sendRequest.

This excludes a missing resource, filename, string-reference encoding, wrong
lookup constant/filter, unawaited Task, short two-second wait, nearby rejection,
stale production cache, or general Data Layer message-channel failure as the
explanation supported by these experiments. It does not distinguish GMS's internal
advertisement exchange, remote index, and query filtering. These are closed-service
internals; no specific source-level GMS cause is claimed.

## C. Static resource audit

| Stage | Phone | Watch |
| --- | --- | --- |
| Source in values/strings.xml | android_wear_capabilities -> @string/local_capability | same |
| local_capability value | wristprompt_mobile | wristprompt_wear |
| Merged values XML | one array, one reference item | same |
| Packaged values XML intermediate | one array, one reference item | same |
| Final aapt2 resource table | array/android_wear_capabilities, size=1, reference item; target string correct | same |
| Final GMS service local-node table | wristprompt_mobile listed | wristprompt_wear listed |

Inspected both modules' source resources/raw keep rules, Gradle variant/source-set
configuration, merger intermediates, packaged resources, final merged manifests,
and final APK resource tables/manifests. The relevant intermediate paths are:

- `<module>/build/intermediates/incremental/debug/mergeDebugResources/merged.dir/values/values.xml`
- `<module>/build/intermediates/packaged_res/debug/packageDebugResources/values/values.xml`
- `<module>/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`

Commands used aapt2 dump resources, aapt2 dump xmltree --file AndroidManifest.xml,
Python ZIP reads of resources.arsc, and XML inspection. GMS service inspection was
read-only and emitted only known capability names/local-table booleans; its raw
service dump was not logged or saved. This goes beyond app Resources.getStringArray
success: GMS itself lists each original static declaration under its local node.

### Canonical filename experiment

Moved only the array into values/wear.xml with the documented tools:keep root,
leaving the same reference item and capability values. Both APKs' complete
resources.arsc remained **byte-for-byte identical** to baseline:

- Phone SHA-256: `fca62425dc5c55c39d1a3bd7d82a8f3861b48c94339ea3b8657cb53a93878cc9`
- Watch SHA-256: `da9be5d4f80497daa609688e53883575a9cd62c3a6a8a65be45ab1f5d13144a1`

Rebuilt, installed with -r and matching signing, reran diagnostics: no change.
The filename hypothesis is disproven for these builds. **Reverted.**

### Literal-item experiment

Separately replaced the reference item with the exact literal capability string
in the canonical file, matching the sample's item representation. aapt2 then
showed `["wristprompt_mobile"]` / `["wristprompt_wear"]` rather than a reference.
After install/relaunch, and again during the longer timing probe, remote queries
remained empty while RPC succeeded. **Reverted.** Final production resources are
unchanged and their compiled resource tables again match baseline exactly.

### Packaging/manifest conclusions

Both application IDs and namespaces match; debug certificates match exactly
(suffix `79d37061`). No applicationIdSuffix, product flavor, dynamic feature or
split APK is involved. The declared source set compiles shared main once and
shared debug once per app. Debug overlays add only diagnostic components; they do
not replace production listeners, capabilities or identity.

Debug minification/resource shrinking are not enabled. Existing keep.xml retains
the array and referenced string. The final merged manifests include the GMS
version metadata from its library, the exported REQUEST_RECEIVED services, and
the expected application class/identity. No undocumented capability metadata or
new permission was added. Official capability guidance uses the named array;
there is no demonstrated missing manifest requirement here.

## D. Dynamic capability audit

The original probe waited two seconds and requested removal asynchronously from
Activity.onDestroy. That was insufficient to characterize propagation timing and
could race a subsequent screen launch. The retained debug-only experiment now:

1. Awaits a wildcard prefix listener and exact listeners for both production
   capability names and the diagnostic capability.
2. Awaits removal of any prior **diagnostic-only** advertisement; 4007 means absent.
3. Awaits fresh addLocalCapability(wristprompt_debug_connectivity).
4. Holds the Activity/registration alive, sampling at 0, 1, 5, 15 and 30 seconds
   using elapsedRealtime. It holds another five seconds for the peer's last query.
5. Captures getAllCapabilities and individual getCapability with FILTER_ALL (0)
   and FILTER_REACHABLE (1), including safe capability names/node associations.
6. Records bounded change callbacks, then awaits advertisement/listener cleanup
   in a bounded, non-cancellable finally block. No production capability is changed.

Final observation on **both** emulators:

| Offset after registration | getAllCapabilities(0/1) | getCapability for all three names (0/1) |
| --- | --- | --- |
| 0 seconds | {} / {} | [] for every query |
| 1 second | {} / {} | [] for every query |
| 5 seconds | {} / {} | [] for every query |
| 15 seconds | {} / {} | [] for every query |
| 30 seconds | {} / {} | [] for every query |

All listener registrations, fresh advertisement registrations and final cleanups
succeeded. Final change callbacks: phone **2**, watch **2**, all for the diagnostic
capability with `nodes=[]`. Exact and prefix subscriptions can duplicate callbacks;
these are not independent sightings. No callback contained a remote node.
Earlier literal-resource timing run similarly had only empty-node callbacks
(phone 4, watch 2). Thus callback delivery itself works, but no remote capability
became visible, even transiently in the observed events.

**Scope limitation:** capabilities are app-scoped. getAllCapabilities returning
empty proves all remote capabilities visible to WristPrompt are absent; it cannot
establish that unrelated packages' capability discovery is globally broken.

## E. Production Data Layer audit

No real production defect found. Phone requests WEAR_CAPABILITY, watch requests
MOBILE_CAPABILITY. WearConnectivity awaits each getCapability(FILTER_REACHABLE)
Task and does not cache its result. WatchAuthBridge prefers exactly one nearby
capable peer, accepts a single capable non-nearby peer, and fails on ambiguity.
Empty peers stop before prepare/send. Receiver eligibility uses the reciprocal
capability lookup; malformed, stale and wrong-node requests remain rejected.

MessageClient.sendRequest/onRequest is the existing RPC path. Profile and API-key
listeners use the same adapter and maintain their existing transaction/security
checks. Inbox/Flow/ViewModel wiring was inspected; no production handoff or account
operation was performed. No emulator-specific fallback or lifecycle hook was
introduced into production code.

## F. Experiments performed

| Hypothesis | Action | Result | Final disposition |
| --- | --- | --- | --- |
| Existing baseline changed | Ran original runner before edits | Nearby nodes/RPC pass; capability queries empty | Evidence retained |
| GMS needs wear.xml filename | Move only array; build; install -r; run | Byte-identical resource tables; no discovery | Reverted |
| GMS rejects reference items | Literal items; build; install -r; run | Literal compiled form; still empty, RPC passes | Reverted |
| Update or process lifecycle is stale | Same-signed install -r and explicit app force-stop/relaunch | Fresh app execution still empty; RPC passes | Runner retains explicit restart to make observation lifetime reliable |
| Two seconds is too short / listeners required | Awaited listeners and live dynamic registration through 30s | All queries empty; only empty-node events; RPC passes | Debug instrumentation retained |
| Guest restart fixes GMS state | Ordinary adb reboot of dynamically verified AVDs | Initially no connected nodes; not valid capability evidence | No data reset; temporary disconnection documented |
| Saved connection needs to resume after reboot | Wake/unlock emulators, resume existing Pixel Watch companion, refresh emulator-only ADB connections | Existing pair returns to nearby nodes and successful RPC; capabilities still empty | No re-pair, no forwarding rule added |
| Official release notes identify a supported fix | Checked Wear emulator, system-service and Wearable SDK notes | No specific matching capability fix established | No SDK/GMS/image update or sideload performed |

The canonical/literal probe initially encountered one launch/report timeout and
one temporary RPC 4000 (target disconnected). A force-stop/cold relaunch restored
RPC but left all capabilities empty. The longer, final post-reconnection run
completed normally on both emulators; those earlier transient outcomes are not
used as proof of capability failure.

Runtime GMS remains phone 26.42.31 and watch 25.11.34; availability SUCCESS on both.
The versions differ, but no documented requirement that they match was found.
Runtime GMS versions are distinct from the app's play-services-wearable 20.0.1 SDK.
Official notes describe 20.0.1's security fix and 26.42 device-connectivity changes,
not a demonstrated cure for this symptom. The newer canary emulator notes are
not evidence that changing images would fix this existing Wear OS 6 lab.

## G. Final emulator diagnostic

Executed from the final working tree, with no install flag:

```sh
python3 tools/emulator_data_layer.py
```

The runner dynamically selected the exact two AVD names and validated watch
characteristics. It never selected physical hardware. Important final output:

```text
Phone:
certificate SHA-256 suffix=[79d37061]
GMS availability=0
connectedNodes=[idSuffix=edfb08 name=WristPrompt_Watch7 nearby=true]
temporary debug capability registration=OK
getAllCapabilities filter=0={}   # at every measured offset
getAllCapabilities filter=1={}
capability=wristprompt_wear filter=0=[]
capability=wristprompt_wear filter=1=[]
capability events count=2; every event nodes=[]
production discovery decision=NODE_EXISTS_CAPABILITY_MISSING; auth sendRequest NOT_ATTEMPTED
debug RPC=SUCCESS (remote debug listener replied)

Watch:
certificate SHA-256 suffix=[79d37061]
GMS availability=0
connectedNodes=[idSuffix=e637d4 name=WristPrompt_Phone nearby=true]
temporary debug capability registration=OK
getAllCapabilities filter=0={}   # at every measured offset
getAllCapabilities filter=1={}
capability=wristprompt_mobile filter=0=[]
capability=wristprompt_mobile filter=1=[]
capability events count=2; every event nodes=[]
production discovery decision=NODE_EXISTS_CAPABILITY_MISSING; auth sendRequest NOT_ATTEMPTED
debug RPC=SUCCESS (remote debug listener replied)
```

## H. Files changed

- Production: **none**; both resource experiments fully reverted.
- Debug-only: shared DataLayerDiagnosticsActivity delegates the timing experiment
  and adds a completedTimestamp; new shared CapabilityTimingExperiment holds,
  queries, observes and cleans up the temporary advertisement/listeners.
- Tools: emulator_data_layer.py force-stops only the validated emulator app before
  each observation and measures report freshness from completion, not its start.
  This is necessary for the longer observation; the original 15-second freshness
  test against the start timestamp would reject every 35-second report.
- Documentation: this report, a historical-record pointer in
  EMULATOR_DATA_LAYER_REPORT.md, and the current evidence note in DECISIONS.md.

No authentication, encryption, request payload, billing, inference, refresh,
revocation, dependency, production manifest or release-source changes. Debug
sources remain compiled only into the debug variant. No raw exception, credential,
authorization URL/header, account ID or full certificate is included in reports.

## I. Automated test results

**VERIFIED AUTOMATICALLY:**

- Focused bridge suites: Wear **22**, mobile **26**; zero failures/errors/skips.
- Final full suites: Wear **189**, mobile **70**; zero failures/errors/skips.
- Both assembleDebug and both lintDebug tasks: PASS.
- Remaining lint warnings: Wear 5, mobile 3, same IDs as baseline. No new warnings.
- Python runner AST/syntax and changed-text whitespace checks: PASS.
- git diff --check: PASS.

Commands actually executed, using the original lab signing directory/full JDK:

```sh
export JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9"
export ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android"
./gradlew :wear:testDebugUnitTest --tests 'io.github.grassified.wristprompt.bridge.*' \
  :mobile:testDebugUnitTest --tests 'io.github.grassified.wristprompt.bridge.*'
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest \
  :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug
python3 tools/emulator_data_layer.py --install
python3 tools/emulator_data_layer.py
git diff --check
```

The experiments separately ran both assembleDebug tasks before each install.
No JVM test is claimed as emulator or physical evidence. No physical device was
modified or tested. No milestone or Day 3 acceptance was advanced.

## J. Recommendation

Continue using normal CapabilityClient in production. Keep the expanded debug
probe and current emulator testing policy. Do not keep ineffective resource edits,
add production connectedNodes fallback, recreate/re-pair the lab, or require daily
physical tests. An offered official GMS update can be tested later with this same
probe; the current evidence does not justify a specific version/image replacement.

Capability-dependent authentication acceptance remains
**NOT VERIFIABLE ON CURRENT EMULATOR LAB**, deferred to physical release-candidate
verification unless a future emulator/GMS update restores discovery.

If development later needs simulated phone-handoff scenarios, the smallest proposed
workaround is an explicitly launched debug-only scenario harness using fixed
synthetic data and the existing diagnostic RPC endpoint. It must remain separate
from real authentication and credential transfer. **Not implemented.** A connected-
node fallback is unnecessary for this investigation and was not added.

## K. Suggested commit message

`test: measure emulator capability propagation and document isolation`

## Primary sources checked

- [Android string-array resources](https://developer.android.com/guide/topics/resources/string-resource#StringArray): resource identity is the name; values filenames are arbitrary and item references are supported.
- [Wear app capability configuration](https://developer.android.com/training/wearables/apps/standalone-apps): canonical wear.xml examples.
- [Official phone sample](https://github.com/android/wear-os-samples/blob/main/DataLayer/Application/src/main/res/values/wear.xml) and [watch sample](https://github.com/android/wear-os-samples/blob/main/DataLayer/Wearable/src/main/res/values/wear.xml): literal static arrays.
- [CapabilityClient API](https://developers.google.com/android/reference/com/google/android/gms/wearable/CapabilityClient): app scope, local-node exclusion, filter/listener semantics and dynamic registration.
- [Google System Services release notes](https://support.google.com/product-documentation/answer/14343500): runtime release information.
- [Play services SDK release notes](https://developers.google.com/android/guides/releases): Wearable 20.0.1 release.
- [October 2026 Wear emulator notes](https://developer.android.com/training/wearables/versions/emulator/2026/10): newer canary image information, no demonstrated fix for this lab.

Two guessed advertise-capabilities documentation URLs were inaccessible; the
accessible canonical guide, API reference and official sample files above were
used instead. No inaccessible page is claimed as reviewed.
