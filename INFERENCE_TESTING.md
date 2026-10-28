# Day 1 Phase D physical acceptance

This is the required real-service gate. Unit tests with synthetic credentials or
responses do not prove it. No authenticated physical device was attached during
implementation. Do not copy tokens, credential files, callback URLs, or headers
into logs, screenshots, support messages or repository files.

## Install without destroying the authenticated installation

Use your existing Galaxy Watch 7 + Android phone pairing, automatic date/time,
and a watch internet connection. Keep the same signing key as the installed apps.
Do not uninstall, clear app data, or replace the signing identity to install this
build: that destroys the installation/Keystore relationship. If Android reports
a signature mismatch, resolve the build signing relationship before continuing.

From the repository root with JDK 17 and the configured Android SDK:

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug
adb devices -l
wristprompt_watch_serial=WATCH_SERIAL
wristprompt_phone_serial=PHONE_SERIAL
adb -s "$wristprompt_watch_serial" install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s "$wristprompt_phone_serial" install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s "$wristprompt_phone_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
adb -s "$wristprompt_watch_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
```

For watch wireless ADB use its shown pairing/debugging ports, as described in
[BRIDGE_TESTING.md](BRIDGE_TESTING.md). Install both updated apps together.

## Required acceptance sequence

1. On watch, tap **Continue with ChatGPT** if not already connected. On phone,
   open WristPrompt and tap **Continue with ChatGPT** within two minutes. Complete
   OpenAI browser consent and return to WristPrompt. Confirm watch **Connected**
   and plan permission enabled. Dismiss the first plan notice. Identity-only access
   cannot run this proof; it must remain visibly disabled.
2. With the desired saved account selected, expect **Connected with ChatGPT**,
   **Using ChatGPT plan**, and the account-specific model list to load automatically.
   If needed tap **Reload models**. Model buttons show human-readable service names.
3. Choose any listed model. Verify **Model:** displays that name, then tap
   **Test inference** exactly once. It sends exactly:

   ```text
   Reply with exactly: WristPrompt works.
   ```

4. Watch should show **Streaming output…** and received text. Wait for final text:

   ```text
   WristPrompt works.
   ```

5. Require BOTH **Observed: response.completed** and
   **Success: response.completed received and exact test output matched.**
   Text alone is not success. A completed response with different wording or no
   text delta does not pass the proof. Record a failure rather than editing output
   or switching to an API key to make it appear successful.
6. Record device OS, browser version, selected model's human name/slug, date,
   exact nonsensitive test output and observed completion/success states. Keep the
   live gate unchecked until this sequence actually passes.

Inference runs directly from watch to api.openai.com. After authentication, phone
need not be online for model discovery/inference if watch has its own internet.
Do not disconnect the pair until credential handoff has completed.

## Focused physical follow-ups

- **Refresh:** Leave the session for about an hour; keep watch date/time correct.
  Reload models and run the same proof. Expect local refresh/correct catalog,
  not a second phone sign-in unless OpenAI requires reauthorization. Repeat after
  watch app restart. No diagnostic display exposes token values/expiry files.
- **Unknown refresh outcome:** Interrupted token rotation must not retry an old
  rotating token. The UI asks for Continue with ChatGPT on the saved registration;
  do that explicitly. Normal temporary HTTP failures keep the profile and allow
  retry later. Do not modify encrypted timestamps/tokens to force this test.
- **Network/Stop:** Disconnect watch networking while a request is pending, or tap
  Stop. Output may be partial, but success must never appear without completion.
  Restore networking and explicitly reload/retry. No generation auto-replay.
- **Usage limit/eligibility:** If the service returns these conditions, expect a
  clear ChatGPT-plan failure and **Manage usage**. Its current hook displays phone
  instructions: ChatGPT → Settings → Usage. It does not automatically open a phone
  browser yet. No API-key switch, repeated OAuth, or guessed reset time is allowed.
- **Account change:** Switch saved account during a pending operation. Previous
  output/models must clear; late results must not append or report success for
  the new account. Reload its catalog, select a model and run the same proof.
- **Empty catalog:** A useful error must replace a hard-coded model fallback.
  Keep catalog/admission/model failures as failures; listing alone grants no guarantee.

No BYOK or production chat work is part of this phase. Day 2 remains unopened.

## Diagnosing the physical catalog failure

The 2026-10-28 user test reached Connected/Using ChatGPT plan but model loading
failed before any inference. Update the watch using install -r with the existing
signing key; keep its app data. Tap Reload models and report the static Failure
category displayed below the error. BODY_TOO_LARGE identifies the bounded HTTP
body limit; INVALID_UTF8 identifies decoding; CATALOG_JSON, CATALOG_MODELS,
CATALOG_MODEL and CATALOG_DUPLICATE distinguish catalog contract checks. These
categories never contain provider bodies, tokens or account identifiers. A
category narrows the investigation; it is not evidence of a successful fix.

If Gradle says JAVA_COMPILER is unavailable, JAVA_HOME points to a runtime rather
than a full JDK. Select a full JDK 17 that includes bin/javac before rebuilding.
A failed build followed by a successful install can install an older APK already
on disk. ADB wireless ports may change; use the watch's current connection port.

The user subsequently reported BODY_TOO_LARGE. The updated catalog path now
streams picker fields with an independent 8 MiB limit. Install the latest watch
APK with -r, retain its account, tap Reload models, select a listed model, and
repeat the exact proof. Token/error-body and stream-event limits remain smaller.
If BODY_TOO_LARGE persists, report it; do not export the raw response or tokens.

## Remaining inference failure after catalog success

The user has now physically confirmed model discovery and selection. An inference
attempt still fails before a successful terminal event. The diagnostic watch build
shows Failure category and, when available, HTTP status. Report these static
values only. RESPONSE_JSON/HTML/MISSING_TYPE/OTHER_TYPE mean a successful HTTP
response was not the expected SSE media type; SSE_EVENT_* categories identify
framing/payload checks; SSE_LINE_LIMIT/SSE_FRAME_LIMIT/OUTPUT_LIMIT identify local
bounds; CLIENT_RESPONSE identifies an otherwise unclassified client processing
failure. These categories narrow the cause; they do not prove a server defect.
Reinstall with -r, retain the registration and repeat one explicit inference test.
Do not export raw streams or credentials, change endpoints or switch billing.

The user then reported RESPONSE_MISSING_TYPE with HTTP 200. The updated provider
allows an absent Content-Type through the SSE parser. Explicitly wrong/malformed
Content-Type remains rejected. After install -r, retain the selected account,
reload/select a model and test once. Require streamed WristPrompt works. plus
Observed: response.completed and the success message. HTTP 200 alone is not a
pass; malformed data, JSON/HTML and streams ending early must remain failures.
