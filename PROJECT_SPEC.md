# WristPrompt v0.1.0

Your key. Your model. Your watch.

Kotlin Wear OS AI client; repository `WristPrompt`; application ID
`io.github.grassified.wristprompt`; license GPL-3.0-only with the narrow linking exception in
[LICENSE_EXCEPTION.md](LICENSE_EXCEPTION.md). Primary physical
test device: Samsung Galaxy Watch 7 with a paired Android phone.

## Product and scope

Open source, local-first, backend-free, BYOK-friendly, watch-native, private,
and lightweight. No WristPrompt account or developer-operated server. The watch
connects directly to OpenAI. Local-first does not mean offline inference: prompts
and required conversation context go to OpenAI. Google Play services may relay
Data Layer traffic; see DECISIONS.md.

Required for release:

- Native Wear Material 3 chat with text input, model picker, visible active model,
  rotary scrolling, streaming state, and an obvious Stop action.
- **Continue with ChatGPT**, official open-source sign-in, eligible ChatGPT-plan
  Responses requests, account discovery/selection, and account-specific models.
- Explicit OpenAI API-key fallback with a visible billing-path indicator.
- Secure credentials, refresh, sign-out/revocation, and useful authentication,
  network, policy, model-access, and usage-limit errors.
- Local conversation context supplied on every request. Initially keep one
  conversation in watch process memory; process death clears it. Persist only
  settings and protected credentials for v0.1; no conversation database.
- Optional voice-to-text through the platform input experience, only after the
  authentication/inference gates pass. No OpenAI transcription dependency.

Out of scope unless the user changes scope: other providers (Claude, Gemini,
OpenRouter, custom compatible servers), images/vision/attachments, web search,
tools/function calling, MCP, cloud sync, conversation organization/search,
personas, marketplaces, payments/subscriptions, custom backends, large architecture
frameworks, and unnecessary animations. No analytics, tracking, advertising, or
remote crash reporting.

## Authorized next scope — 2026-10-31 (D6 implementation in progress)

The memory-only and image-exclusion statements above describe the delivered D5
baseline. Owner-authorized D6/D7 planning supersedes those scope exclusions only
for persistent conversations/history, explicit named USER prompt presets managed
on phone and inserted/editable on watch, and a managed imported multi-image
library feeding text + N image Responses messages. Watch review and explicit Send
remain mandatory. No hidden always-on prompt, automatic inference, model switch
or billing fallback. Image generation/viewer is optional STRETCH, not a core gate.
D6-A must decide storage/schema first, separate display from replay, anticipate
Text/ImageAttachment, and avoid per-token writes/UI-object persistence. See
[ROADMAP.md](ROADMAP.md) and [handoff](docs/CODEX_HANDOFF.md). D6-B/C are implemented
on contributor branches with consolidated acceptance pending; D7 is NOT STARTED.

## Runtime responsibilities

| Runtime | Owns |
| --- | --- |
| `:wear` | Chat, model/account selection, settings, local context, direct inference, protected profiles, refresh, sign-out |
| `:mobile` | Minimal setup, browser OAuth, phone loopback listener, identity/scope validation, protected credential handoff; no chat |

`AiProvider` has one implementation, `OpenAiProvider`. `OpenAiCredentialSource`
supplies either `ChatGptPlanCredential` or `ApiKeyCredential`; shared Responses
transport consumes a Bearer credential without implementing login. The selected
profile and billing path remain explicit in application state. Account/model
catalog parsing can differ between OAuth and API-key routes.

## Authentication contract

Follow the official [registration guide](https://developers.openai.com/siwc/token-sharing-open-source/sign-in).
Phone opens the system browser only after binding a short-lived listener to
`127.0.0.1`. Choose `http://127.0.0.1:<port>/auth/callback`; only its port may vary
between attempts. Authorization and exchange use the identical URI. Neither
`localhost`, a custom scheme, nor RemoteAuthClient's Google redirect substitutes.

Use Authorization Code/OIDC, fresh cryptographically random state/nonce/verifier,
PKCE S256, `resource=https://api.openai.com/v1`, and scopes
`openid profile email offline_access resource.invoke chatgpt.tokens.use.direct`.
Initial registration uses `dynamic_agent_client` and `agent_name_hint=WristPrompt`.
Persist the callback-issued client ID before exchange; reuse it for returning
sign-in and omit the name hint. Reject a conflicting returned client ID.

Validate state before consuming callback results, including errors. Fail closed
on missing/ambiguous parameters, invalid signatures/issuer/audience/expiry/nonce,
or returning-identity mismatch. Verify ID tokens against trusted OpenAI JWKS
using a maintained JOSE library. Check the token response's granted scopes;
missing `chatgpt.tokens.use.direct` means identity-only, with inference disabled.

Each encrypted profile keeps issued client ID, verified issuer/subject, account
display metadata, token set, granted scopes, and expiry together. Email is not a
registration key. Never mix registrations or activate a partially validated one.
Generate one opaque UUIDv4 `urn:uuid:…` host ID per Wear installation, persist it,
and pass it through the phone's pending transaction. Never derive it from personal
or hardware identifiers. Retain registration/host mapping after sign-out.

For [sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
refresh on watch near expiry with a per-profile lock; atomically replace tokens,
scopes, and expiry. Terminal refresh failures require reauthorization with the
saved client ID; transient failures preserve the profile. Sign-out cancels
requests, attempts refresh-token revocation using trusted OIDC discovery, and
clears local secrets. If revocation cannot be confirmed, say so and offer the
ChatGPT disconnect action. Preserve no usable token copy on the phone after handoff.

## Phone/watch trust boundary

Use matching application IDs and signing certificates for both apps, including
debug and Play-delivered builds. Send secrets only to the requesting paired node
over Wearable Data Layer messages; never broadcast or put credentials in durable
DataItems. Require a pending, expiring request tied to node, Wear host ID, profile,
and single-use transfer ID. Validate version/size, reject unsolicited/replayed or
wrong-node results, and acknowledge only after atomic encrypted watch storage.

Phone may retain an encrypted pending envelope for a bounded handoff retry, but
must never refresh it. Watch is the sole refresh owner once accepted. Lost ACKs
must permit idempotent acknowledgment without reinstalling stale tokens. Timeout
must not revoke a possibly accepted session. Account switching/sign-out invalidates
pending operations and late results. Detailed rationale is in DECISIONS.md.

## Inference and UX contract

Use `POST https://api.openai.com/v1/responses`, `store=false`, `stream=true` for
both paths. Supply required history as an input array; omit HTTP
`previous_response_id`. No ChatGPT private/backend-api endpoints. Limit the body
to supported text fields; do not expose temperature/top_p or send unsupported
preview fields (including `max_output_tokens`). Use instructions/developer input
if needed, not explicit system-message items. See [preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).

For plan users, [discover models](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
at `GET https://api.openai.com/v1/models` using the active profile. Keep
`models[]` with `visibility="list"` in service order; show `display_name`, send
`slug`. Reload after profile changes. Availability does not guarantee inference.

Render text deltas; only `response.completed` means success. Failed, incomplete,
cancelled, and prematurely closed streams remain distinct. Stop cancels the
OkHttp call and coroutine, closes resources, and prevents late UI updates; partial
text is marked partial. Never auto-replay a generation after streaming starts.

Handle [errors](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
before and during streams, including nonstandard admission bodies. Show bounded,
sanitized messages; keep only safe status/code/request-ID diagnostics. Usage-limit
and eligibility errors must not loop OAuth or switch to billable BYOK. Temporary
unavailability uses bounded recovery without discarding credentials.

Follow [OpenAI UX guidance](https://developers.openai.com/siwc/ui-ux-guidelines):
approved button branding/wording, connected account identity, first-use plan
confirmation, **Using ChatGPT plan** near the composer/model, and **Manage usage**
as the primary limit action. Open the phone browser for usage management when
needed. BYOK clearly says OpenAI API key / API billing. Do not imply official
OpenAI affiliation. Never bundle a key.

## Release-blocking storage and diagnostic rules

- Android Keystore-backed AES-GCM, fresh IV per encryption, atomic app-private
  credential records, and no plaintext fallback if keys/storage fail. Use platform
  crypto; avoid deprecated AndroidX Security Crypto APIs.
- Disable backups and explicitly exclude credential/host records from cloud and
  device-transfer backup rules. No plaintext token persistence or clipboard output.
- No Authorization headers, API/access/refresh/ID tokens, authorization codes,
  PKCE verifiers, or authorization URLs with hints in logs, notifications,
  diagnostics, source control, or crash reports. Centralize safe diagnostic output
  and redaction; never log raw serialization/network exceptions or chat payloads.
- HTTPS with normal certificate/hostname verification for OpenAI; no permissive
  trust manager. Incoming phone loopback HTTP does not justify global cleartext
  permission. Bound listener lifetime, request size, and callback path/method.
- Security and billing separation cannot be traded for optional voice or polish.

## Official source index

Protocol snapshot checked 2026-10-28; recheck before implementation. In addition
to the linked contracts: [Quickstart](https://developers.openai.com/siwc/quickstart),
[open-source overview](https://developers.openai.com/siwc/token-sharing-open-source),
[token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference),
and [credential transfer](https://developers.openai.com/siwc/token-sharing-open-source/self-hosted-vms).
These establish protocol behavior, not proof that the Android bridge has worked.
