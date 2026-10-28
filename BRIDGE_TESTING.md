# Authentication bridge device acceptance

No real paired OAuth acceptance is claimed by unit tests. Never paste callback
URLs, credentials, plaintext records, or raw message bodies into diagnostics.

## Prepare a pair

**Emulators:** In Android Studio Device Manager, pair a Wear OS emulator with an
API 30+ phone AVD **with Google Play Store** using **Pair Wearable**. Complete the
system watch companion setup and wait for paired icons. ADB alone is not pairing.
See [Android pairing guidance](https://developer.android.com/training/wearables/get-started/connect-phone).
The temporary Google APIs phone used previously is unpaired and cannot prove delivery.

**Galaxy Watch 7:** Keep its existing Galaxy Wearable pairing to an Android phone;
do not reset/unpair. Enable USB debugging on phone and wireless debugging on watch.
Use the watch's displayed ports:

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:DEBUG_PORT
adb devices -l
```

Use automatic date/time on both. Both apps need matching package and signing
certificates. Do not mix a Play-signed install with a differently signed debug APK.

## Install

From the repository root with JDK 17/SDK configured, substitute actual serials:

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug
wristprompt_phone_serial=PHONE_SERIAL
wristprompt_watch_serial=WATCH_SERIAL
adb -s "$wristprompt_phone_serial" install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s "$wristprompt_watch_serial" install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s "$wristprompt_phone_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
adb -s "$wristprompt_watch_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
```

Open both apps at least once after install; manually reopen a force-stopped phone
app before expecting background receipt. Allow Google Play services to connect.

## Happy path

1. Tap **Continue with ChatGPT** on watch. Expect looking, then **Waiting for phone…**.
2. On phone, confirm **ChatGPT sign-in requested by your watch.** Tap its
   **Continue with ChatGPT** within two minutes. A system browser opens OpenAI.
   Watch shows **Authorizing…**. No browser should open merely from receiving a request.
3. Finish OpenAI consent using your own account. The browser's loopback page says
   to return to WristPrompt. Complete within five minutes; keep phone setup alive.
4. Return to phone. Success must follow watch confirmation, not just browser return.
   Watch shows **Connected**, a distinct registration/account label, and explicit
   plan-enabled or identity-only/plan-disabled state based on actual grants.
   Dismiss the first plan notice; it must not return for the same registration.
5. Force-stop/reopen watch. Connection/account selection should persist. Select a
   saved account and repeat sign-in: its issued client ID and identity must stay
   associated, with no new dynamic registration. Add another account separately;
   selecting accounts must never merge credentials, even for identical emails.

Phase D adds the watch-only inference proof and refresh; see
[INFERENCE_TESTING.md](INFERENCE_TESTING.md). BYOK and sign-out remain unimplemented.
Use ChatGPT settings to disconnect this development registration when done;
do not assume uninstalling revokes it remotely.

## Failure and recovery checks

- Unavailable phone/companion: stop the phone emulator or take phone fully offline,
  then tap watch action. Expect unavailable or a bounded error. Bluetooth-off may
  still permit Google's network relay. Restore connection and explicitly retry.
- Browser back/decline: deny in OpenAI. Expect denial on phone and useful failure on
  watch; existing saved accounts must remain unchanged. A browser closed without
  callback times out. Start a fresh watch attempt afterward.
- Clock/network: verify failed exchange/expired requests never show new Connected.
  Callback state/signature attacks are automated negative tests, not a production
  intent-injection backdoor. No sensitive callback diagnostic output is provided.
- Transfer interruption: disconnect watch after consent and before ACK. Phone
  must show confirmation missing, with **Retry watch transfer**. Reconnect and
  retry within the original ten-minute watch transaction. It must resend the same
  transfer, without another OAuth exchange or overwriting already accepted tokens.
- New watch request/account selection invalidates an old pending result. A new
  request arriving on phone cancels its old browser attempt. Late callbacks fail.
- Rotate phone during browser authorization; the ViewModel should keep the attempt.
  Phone process death/force-stop intentionally discards in-memory tokens and the
  listener. Restart from watch; if watch already shows Connected after a lost ACK,
  preserve that session. Never revoke a possibly accepted session automatically.
- After process death during an incomplete initial registration, phone retains
  only protected issued-client metadata, enabling a fresh OAuth attempt without
  blindly using dynamic_agent_client again. A new additional-account action is
  explicitly a new registration, not automatic selection of an incomplete one.
- Test watch secure-storage/key failure only on a disposable test installation.
  It must block new sign-in with no plaintext fallback and return no success ACK.
  Do not delete keys/data from your normal installation to perform this check.

## Persistence checks without displaying secrets

After requesting sign-in, check the host-only preferences checksum, restart watch,
request again, and check that it stays unchanged:

```sh
adb -s "$wristprompt_watch_serial" shell run-as io.github.grassified.wristprompt sha256sum files/datastore/installation.preferences_pb
adb -s "$wristprompt_watch_serial" shell am force-stop io.github.grassified.wristprompt
adb -s "$wristprompt_watch_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
```

A successful transfer creates `no_backup/chatgpt_profiles.enc` on watch. File
existence is safe to check; do not dump its contents. Confirm phone has no token
records in preferences/files, and diagnostics contain only allowlisted event names.
Android Keystore, atomic-write durability and device process/network behavior
still require physical evidence; JVM cipher/repository tests are not substitutes.

Record device/OS/browser versions, observed states, results, and safe error names.
Do not record account identity, host ID, tokens, codes, verifier, or URL queries.
