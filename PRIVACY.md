# Privacy

WristPrompt has no developer-operated backend or WristPrompt account. Normal
inference travels directly from the watch to OpenAI over HTTPS. Prompts, required
conversation context, and generated responses are processed by OpenAI; provider
policies and selected account/billing access apply. `store=false` is a request
setting, not a guarantee about all provider retention practices.

The phone companion handles browser OAuth through a short-lived loopback callback,
identity validation and protected Data Layer handoff. It is not a chat proxy.
Google Play services may relay Data Layer traffic. The system browser and keyboard,
Android/Wear OS and Google services have their own data handling outside this app.

Watch credentials use Android Keystore-backed AES-GCM in atomic app-private
no-backup records. The phone retains protected setup/registration metadata and
bounded handoff state; API-key entry is not persisted by the phone. Both manifests
disable backup and exclude app data from cloud/device-transfer backup.
Encryption cannot protect credentials from every compromised device/input method.

Conversation and replay state are memory-only; process death loses chat. Settings,
registration identifiers and protected credentials are persisted separately.
WristPrompt does not implement cloud conversation synchronization. There are no
analytics, advertising or remote crash-reporting SDKs in the inspected production
dependencies/source. Safe diagnostics expose bounded codes rather than raw tokens,
chat payloads or network exceptions. This scope does not describe telemetry collected
by Android, Google services or OpenAI.

Markdown is rendered natively. Raw HTML is inactive, remote Markdown images are
not fetched, and links are inspected locally rather than automatically opened.
Never publish raw device logs, account information or private conversation captures.
