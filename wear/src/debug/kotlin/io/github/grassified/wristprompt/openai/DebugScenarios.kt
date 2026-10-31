package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.ui.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject

internal enum class DebugScenario {
    SIGNED_OUT, CHATGPT_AUTHENTICATED, API_KEY_CONFIGURED, MODELS_LOADING, MODELS_LOADED,
    MANY_MODELS, THOUSAND_MODELS, SINGLE_MODEL, LONG_MODEL_NAMES, MODEL_UNAVAILABLE, EMPTY_CHAT, LONG_USER_PROMPT, ONE_TURN_COMPLETED,
    MULTI_TURN_CHAT, LONG_ASSISTANT_OUTPUT, STREAMING_SHORT, STREAMING_LONG, STOPPED_PARTIAL,
    ERROR_401_REAUTH, ERROR_401_API_KEY, ERROR_429, ERROR_503, ERROR_OFFLINE, LONG_ERROR_MESSAGE,
    RECREATED_COMPLETED_STATE, RECREATED_CANCELLED_STATE,
    ACTIVE_STREAM_RECREATION, BACKGROUNDED_STREAM, SCREEN_OFF_STYLE_RESUME, COMPOSER_RECREATION,
    PROCESS_RESTART_IDLE, PROCESS_RESTART_AFTER_COMPLETED,
    PROCESS_RESTART_DURING_ACTIVE, PROCESS_RESTART_AFTER_PARTIAL,
    NETWORK_LOST_BEFORE_SEND, NETWORK_LOST_DURING_STREAM, NETWORK_RECOVERED,
    MODEL_DISCOVERY_OFFLINE, MODEL_DISCOVERY_RECOVERED, STOP_NETWORK_RACE,
    PHONE_ABSENT, WATCH_ONLY_AUTHENTICATED, WATCH_ONLY_CHAT, WATCH_ONLY_REFRESHED,
    REAUTH_REQUIRED_PHONE_ABSENT, API_KEY_WATCH_ONLY,
    DENSE_LONG_ANSWER, STREAMING_FINE_GRAINED, LONG_UNBROKEN_TEXT, UNICODE_LONG_RESPONSE,
    MARKDOWN_BASIC, MARKDOWN_LONG, MARKDOWN_CODE, MARKDOWN_CODE_LONG_LINE, MARKDOWN_CODE_STRESS,
    MARKDOWN_OPEN_FENCE, MARKDOWN_TABLE, MARKDOWN_TABLE_MALFORMED, MARKDOWN_TABLE_OVER_LIMIT,
    MARKDOWN_LINK, MARKDOWN_BLOCKED_LINK, MARKDOWN_RAW_HTML, MARKDOWN_IMAGE,
    MARKDOWN_DEEP_NESTING, MARKDOWN_DELIMITER_STRESS, STREAMING_MARKDOWN, STREAMING_MARKDOWN_OPEN_SYNTAX,
    INTERRUPTED_MARKDOWN, STOPPED_MARKDOWN, TERMINAL_MARKDOWN_REPLACEMENT, MARKDOWN_LIST_LONG,
    MARKDOWN_MALFORMED, MARKDOWN_LONG_URL, ERROR_API_LIMIT, INCOMPLETE_PARTIAL,
    CONVERSATION_LIMIT, SECURE_STORAGE_FAILURE, STREAM_TO_COMPLETED_PLAIN,
    STREAM_TO_COMPLETED_MARKDOWN, STREAM_TO_COMPLETED_LONG, SINGLE_LONG_PARAGRAPH,
    REAL_MULTI_PARAGRAPH, DIAGNOSTIC_PROOF_CURRENT_ATTEMPT, SAVED_CHATS_HISTORY;
    companion object { fun parse(name: String?): DebugScenario? = entries.singleOrNull { it.name == name } }
}

/** No Context, persistent repository, preference store, HTTP client or credential record. */
internal class DebugScenarios(val scenario: DebugScenario) {
    val mode = if (scenario in setOf(DebugScenario.API_KEY_CONFIGURED, DebugScenario.ERROR_401_API_KEY, DebugScenario.API_KEY_WATCH_ONLY, DebugScenario.ERROR_API_LIMIT))
        CredentialMode.OPENAI_API_KEY else CredentialMode.CHATGPT_PLAN
    private val reauth = scenario == DebugScenario.REAUTH_REQUIRED_PHONE_ABSENT
    val accounts = WatchAccountState(storageError = scenario == DebugScenario.SECURE_STORAGE_FAILURE, reauthorizationRequired = reauth, connected = scenario != DebugScenario.SIGNED_OUT, planEnabled = true,
        accounts = if (scenario == DebugScenario.SIGNED_OUT) emptyList() else listOf(AccountLabel("debug", "Synthetic account")),
        activeClientId = "debug".takeUnless { scenario == DebugScenario.SIGNED_OUT })
    val key = ApiKeyState(storageError = scenario == DebugScenario.SECURE_STORAGE_FAILURE, loaded = true, mode = mode, configured = mode == CredentialMode.OPENAI_API_KEY)
    val surface = when (scenario) {
        DebugScenario.SAVED_CHATS_HISTORY -> Surface.HISTORY
        DebugScenario.DIAGNOSTIC_PROOF_CURRENT_ATTEMPT -> Surface.PROOF
        DebugScenario.MANY_MODELS, DebugScenario.THOUSAND_MODELS, DebugScenario.SINGLE_MODEL, DebugScenario.LONG_MODEL_NAMES, DebugScenario.MODELS_LOADED, DebugScenario.MODEL_UNAVAILABLE,
        DebugScenario.MODEL_DISCOVERY_OFFLINE, DebugScenario.MODEL_DISCOVERY_RECOVERED -> Surface.MODELS
        DebugScenario.LONG_USER_PROMPT, DebugScenario.COMPOSER_RECREATION -> Surface.COMPOSER
        DebugScenario.SIGNED_OUT, DebugScenario.REAUTH_REQUIRED_PHONE_ABSENT -> Surface.CONNECTION
        else -> Surface.CHAT
    }
    private val access = MutableStateFlow(InferenceAccessState(client = "debug", usable = scenario !in setOf(DebugScenario.SIGNED_OUT, DebugScenario.SECURE_STORAGE_FAILURE) && !reauth, mode = mode,
        problem = if (scenario == DebugScenario.SECURE_STORAGE_FAILURE) ApiProblem.STORAGE else if (reauth) ApiProblem.REAUTHORIZE else null))
    // Transient lease required by InferenceSession; empty bearer cannot be an API key/token.
    // It is passed only to this synthetic provider, never to a persistence or HTTP boundary.
    private val lease = CredentialLease("debug", 0, "", Long.MAX_VALUE, mode)
    val phoneScenario get() = scenario in setOf(DebugScenario.PHONE_ABSENT, DebugScenario.WATCH_ONLY_AUTHENTICATED,
        DebugScenario.WATCH_ONLY_CHAT, DebugScenario.WATCH_ONLY_REFRESHED,
        DebugScenario.REAUTH_REQUIRED_PHONE_ABSENT, DebugScenario.API_KEY_WATCH_ONLY)
    var bridgeLookups = 0; private set
    var bridgeRequests = 0; private set
    var refreshes = 0; private set
    val authState get() = if (phoneScenario) WatchAuthState.COMPANION_UNAVAILABLE else WatchAuthState.IDLE
    // No Android/Data Layer client: a normal path touching this companion fails immediately.
    private val absentPhone = object : AuthBridgeTransport {
        override suspend fun reachablePeers(): List<WearPeer> { bridgeLookups++; error("Companion touched") }
        override suspend fun requestStart(nodeId: String, payload: ByteArray): ByteArray { bridgeRequests++; error("Companion touched") }
    }
    val authBridge = WatchAuthBridge({ "urn:uuid:00000000-0000-4000-8000-000000000001" }, absentPhone)
    var generations = 0; private set
    var discoveries = 0; private set
    var completions = 0; private set
    val lifecycleScenario get() = scenario in setOf(DebugScenario.ACTIVE_STREAM_RECREATION,
        DebugScenario.BACKGROUNDED_STREAM, DebugScenario.SCREEN_OFF_STYLE_RESUME)
    val markdownScenario get() = markdownFixture(scenario) != null
    val presentationScenario get() = markdownScenario || scenario in setOf(DebugScenario.STREAMING_LONG, DebugScenario.STREAMING_FINE_GRAINED,
        DebugScenario.DENSE_LONG_ANSWER, DebugScenario.LONG_UNBROKEN_TEXT, DebugScenario.UNICODE_LONG_RESPONSE)
    val processScenario get() = scenario.name.startsWith("PROCESS_RESTART_")
    val networkScenario get() = scenario in setOf(DebugScenario.NETWORK_LOST_BEFORE_SEND,
        DebugScenario.NETWORK_LOST_DURING_STREAM, DebugScenario.NETWORK_RECOVERED,
        DebugScenario.MODEL_DISCOVERY_OFFLINE, DebugScenario.MODEL_DISCOVERY_RECOVERED, DebugScenario.STOP_NETWORK_RACE)
    private var online = !networkScenario
    private val networkLoss = CompletableDeferred<Unit>()
    fun restoreNetwork() { if (networkScenario) online = true } // No action/reload/replay.
    fun nextRequest() { if (networkScenario || presentationScenario) { vm.draft("Explicit recovered request"); vm.send() } }
    fun failAndStop() { if (scenario == DebugScenario.STOP_NETWORK_RACE) { networkLoss.complete(Unit); vm.stop() } }
    private var seeded = false
    suspend fun seedProcess() {
        if (!processScenario || seeded) return
        seeded = true
        if (scenario == DebugScenario.PROCESS_RESTART_IDLE) {
            vm.select("debug-2"); vm.state.first { it.selectedSlug == "debug-2" }; return
        }
        vm.draft("Synthetic process-boundary request"); vm.send()
        vm.chat.first { when (scenario) {
            DebugScenario.PROCESS_RESTART_AFTER_COMPLETED -> it.stage == ChatStage.COMPLETED
            DebugScenario.PROCESS_RESTART_AFTER_PARTIAL -> it.stage == ChatStage.RECEIVING
            else -> it.stage == ChatStage.STARTING
        } }
    }
    private val steps = Channel<Boolean>(Channel.UNLIMITED)
    fun advance(complete: Boolean = false) { if (lifecycleScenario) steps.trySend(complete) }
    val inputs = mutableListOf<List<JsonObject>>()
    private var unavailable = scenario == DebugScenario.MODEL_UNAVAILABLE
    private val loading = scenario == DebugScenario.MODELS_LOADING
    private val provider = object : AiProvider {
        override suspend fun models(credential: CredentialLease): List<AiModel> {
            discoveries++
            if (scenario in setOf(DebugScenario.MODEL_DISCOVERY_OFFLINE, DebugScenario.MODEL_DISCOVERY_RECOVERED) && !online)
                throw ApiFailure(ApiProblem.NETWORK)
            if (loading) awaitCancellation()
            if (unavailable) { unavailable = false; throw ApiFailure(ApiProblem.NO_MODELS) }
            val count = when (scenario) {
                DebugScenario.MANY_MODELS -> 40
                DebugScenario.THOUSAND_MODELS -> 1000
                DebugScenario.SINGLE_MODEL -> 1
                else -> 3
            }
            return (1..count).map { AiModel("debug-$it", if (scenario == DebugScenario.LONG_MODEL_NAMES)
                "Synthetic authoritative model name with a long service supplied label $it" else "Synthetic model $it") }
        }
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) {
            check(scenario == DebugScenario.DIAGNOSTIC_PROOF_CURRENT_ATTEMPT)
            generations++
            onEvent(ResponseEvent.Delta("WristPrompt "))
            delay(100)
            onEvent(ResponseEvent.Delta("works."))
            onEvent(ResponseEvents.decode(null, """{"type":"response.completed","response":{"status":"completed","output":[]}}"""))
            completions++
        }
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            generations++; inputs += input
            failure()?.let { throw it }
            if (scenario == DebugScenario.INCOMPLETE_PARTIAL) {
                onEvent(ResponseEvent.Delta("**Synthetic partial** response."))
                throw ApiFailure(ApiProblem.INCOMPLETE)
            }
            if (networkScenario && !online) {
                if (scenario == DebugScenario.STOP_NETWORK_RACE) {
                    onEvent(ResponseEvent.Delta("Synthetic interrupted partial")); networkLoss.await()
                } else if (scenario == DebugScenario.NETWORK_LOST_DURING_STREAM) {
                    onEvent(ResponseEvent.Delta("Synthetic interrupted partial"))
                }
                throw ApiFailure(if (scenario in setOf(DebugScenario.NETWORK_LOST_DURING_STREAM, DebugScenario.STOP_NETWORK_RACE))
                    ApiProblem.INTERRUPTED else ApiProblem.NETWORK)
            }
            if (scenario == DebugScenario.PROCESS_RESTART_DURING_ACTIVE) awaitCancellation()
            if (scenario == DebugScenario.PROCESS_RESTART_AFTER_PARTIAL) {
                onEvent(ResponseEvent.Delta("Synthetic process partial")); awaitCancellation()
            }
            if (lifecycleScenario) {
                var chunk = 1
                onEvent(ResponseEvent.Delta("Lifecycle chunk 1. "))
                for (complete in steps) {
                    if (complete) { completions++; onEvent(ResponseEvent.Completed(null)); return }
                    chunk++; onEvent(ResponseEvent.Delta("Lifecycle chunk $chunk. "))
                }
                return
            }
            if (scenario == DebugScenario.STREAMING_FINE_GRAINED) {
                val text = "Fine-grained synthetic output for presentation testing. ".repeat(200)
                for (c in text) { onEvent(ResponseEvent.Delta(c.toString())); delay(1) }
                completions++; onEvent(ResponseEvent.Completed(text)); return
            }
            val markdown = markdownFixture(scenario)
            if (markdown != null) {
                val streamingMarkdown = scenario in setOf(DebugScenario.STREAMING_MARKDOWN, DebugScenario.STREAMING_MARKDOWN_OPEN_SYNTAX)
                if (streamingMarkdown || scenario.name.startsWith("STREAM_TO_COMPLETED_")) {
                    for (chunk in markdown.chunked(if (streamingMarkdown) 8 else 64)) { onEvent(ResponseEvent.Delta(chunk)); delay(100) }
                } else onEvent(ResponseEvent.Delta(markdown))
                if (scenario == DebugScenario.STOPPED_MARKDOWN) awaitCancellation()
                if (scenario == DebugScenario.INTERRUPTED_MARKDOWN) throw ApiFailure(ApiProblem.INTERRUPTED)
                if (scenario == DebugScenario.STREAMING_MARKDOWN_OPEN_SYNTAX) awaitCancellation()
                completions++
                if (scenario.name.startsWith("STREAM_TO_COMPLETED_")) {
                    onEvent(ResponseEvents.decode(null, """{"type":"response.completed","response":{"status":"completed","output":[]}}"""))
                    return
                }
                onEvent(ResponseEvent.Completed(if (scenario == DebugScenario.TERMINAL_MARKDOWN_REPLACEMENT) "# Authoritative replacement\n\n**Final** text wins." else markdown))
                return
            }
            val special = when (scenario) {
                DebugScenario.DENSE_LONG_ANSWER -> (1..200).joinToString("\n\n") { "Paragraph $it. " + "Plain text for dense reading on the watch. ".repeat(6) }
                DebugScenario.LONG_UNBROKEN_TEXT -> "https://example.invalid/" + "aB9_".repeat(15000)
                DebugScenario.UNICODE_LONG_RESPONSE -> "İstanbul: öğle, ışık. العربية 中文 e\u0301 👨‍👩‍👧‍👦 🇹🇷 👍🏽\n".repeat(700)
                else -> null
            }
            if (special != null) { onEvent(ResponseEvent.Delta(special)); completions++; onEvent(ResponseEvent.Completed(special)); return }
            val long = scenario in setOf(DebugScenario.STREAMING_LONG, DebugScenario.LONG_ASSISTANT_OUTPUT)
            val streaming = scenario in setOf(DebugScenario.STREAMING_LONG, DebugScenario.STREAMING_SHORT)
            val stopped = scenario in setOf(DebugScenario.STOPPED_PARTIAL, DebugScenario.RECREATED_CANCELLED_STATE) && generations == 1
            val text = if (stopped) "Synthetic partial answer. This unfinished exchange is excluded." else
                (1..if (long) 80 else 3).joinToString(" ") { "Section $it: deterministic synthetic answer for scrolling and reading." }
            if (stopped) { onEvent(ResponseEvent.Delta(text)); awaitCancellation() }
            val output = StringBuilder()
            for (chunk in text.chunked(60)) {
                output.append(chunk); onEvent(ResponseEvent.Delta(chunk))
                if (streaming) delay(400)
            }
            completions++; onEvent(ResponseEvent.Completed(text))
        }
    }
    private val historyStore = if (scenario == DebugScenario.SAVED_CHATS_HISTORY) DebugHistoryStore() else null
    val vm = InferenceViewModel(access, InferenceSession({
        if (reauth) throw ApiFailure(ApiProblem.REAUTHORIZE)
        // Presentation-only renewal boundary. Real rotation/storage/HTTP is covered in PhoneIndependenceTest.
        if (scenario == DebugScenario.WATCH_ONLY_REFRESHED && refreshes == 0) refreshes++
        lease
    }, provider) { it === lease }, conversationStore = historyStore, conversationIdentity = { "a".repeat(64) })
    fun failure(): ApiFailure? = when (scenario) {
        // A bare plan admission 401 means permission, not terminal refresh failure (D3-E).
        DebugScenario.ERROR_401_REAUTH -> ApiErrors.parse(401, "{}")
        DebugScenario.ERROR_401_API_KEY -> ApiErrors.forMode(ApiErrors.parse(401, "{}"), mode)
        DebugScenario.ERROR_429 -> ApiErrors.parse(429, "{}")
        DebugScenario.ERROR_503, DebugScenario.LONG_ERROR_MESSAGE -> ApiErrors.parse(503, "{}")
        DebugScenario.ERROR_API_LIMIT -> ApiFailure(ApiProblem.API_LIMIT)
        DebugScenario.ERROR_OFFLINE -> ApiFailure(ApiProblem.NETWORK)
        else -> null
    }
    suspend fun prepare() {
        if (scenario == DebugScenario.SIGNED_OUT) return
        if (scenario == DebugScenario.SECURE_STORAGE_FAILURE) { vm.state.first { it.problem == ApiProblem.STORAGE }; return }
        if (reauth) { vm.state.first { it.problem == ApiProblem.REAUTHORIZE }; return }
        if (scenario == DebugScenario.MODELS_LOADING) { vm.state.first { it.stage == ProofStage.LOADING }; return }
        if (scenario == DebugScenario.MODEL_UNAVAILABLE) { vm.state.first { it.problem == ApiProblem.NO_MODELS }; vm.draft("Blocked until models reload"); return }
        if (scenario in setOf(DebugScenario.MODEL_DISCOVERY_OFFLINE, DebugScenario.MODEL_DISCOVERY_RECOVERED)) {
            vm.state.first { it.stage == ProofStage.FAILED && it.problem == ApiProblem.NETWORK }; return
        }
        vm.state.first { it.stage == ProofStage.READY }
        if (scenario == DebugScenario.DIAGNOSTIC_PROOF_CURRENT_ATTEMPT) {
            repeat(2) { vm.test(); vm.state.first { it.stage == ProofStage.SUCCEEDED } }
            return
        }
        if (scenario == DebugScenario.CONVERSATION_LIMIT) {
            repeat(ChatConversation.MAX_ATTEMPTS) {
                vm.draft("Synthetic bounded question"); vm.send(); vm.chat.first { it.stage == ChatStage.COMPLETED }
            }
            return
        }
        if (scenario == DebugScenario.INCOMPLETE_PARTIAL) {
            vm.draft("Synthetic incomplete request"); vm.send(); vm.chat.first { it.stage == ChatStage.FAILED }; return
        }
        if (networkScenario) {
            vm.draft("Synthetic network failure request"); vm.send()
            vm.chat.first { if (scenario == DebugScenario.STOP_NETWORK_RACE) it.stage == ChatStage.RECEIVING else it.stage == ChatStage.FAILED }
            return
        }
        if (markdownScenario) {
            vm.draft("Synthetic Markdown request"); vm.send()
            vm.chat.first { chat -> when (scenario) {
                DebugScenario.STREAMING_MARKDOWN, DebugScenario.STREAMING_MARKDOWN_OPEN_SYNTAX, DebugScenario.STOPPED_MARKDOWN,
                DebugScenario.STREAM_TO_COMPLETED_PLAIN, DebugScenario.STREAM_TO_COMPLETED_MARKDOWN, DebugScenario.STREAM_TO_COMPLETED_LONG -> chat.stage == ChatStage.RECEIVING
                DebugScenario.INTERRUPTED_MARKDOWN -> chat.stage == ChatStage.FAILED
                else -> chat.stage == ChatStage.COMPLETED
            } }
            if (scenario == DebugScenario.STOPPED_MARKDOWN) vm.stop()
            return
        }
        when (scenario) {
            DebugScenario.LONG_USER_PROMPT -> vm.draft("Long synthetic question with readable words. ".repeat(190).take(ChatConversation.MAX_PROMPT_CHARS))
            DebugScenario.ONE_TURN_COMPLETED, DebugScenario.RECREATED_COMPLETED_STATE,
            DebugScenario.MULTI_TURN_CHAT, DebugScenario.LONG_ASSISTANT_OUTPUT,
            DebugScenario.DENSE_LONG_ANSWER, DebugScenario.LONG_UNBROKEN_TEXT, DebugScenario.UNICODE_LONG_RESPONSE,
            DebugScenario.WATCH_ONLY_CHAT, DebugScenario.API_KEY_WATCH_ONLY -> repeat(if (scenario == DebugScenario.MULTI_TURN_CHAT) 4 else if (scenario == DebugScenario.WATCH_ONLY_CHAT) 2 else 1) {
                vm.draft("Synthetic question ${it + 1}"); vm.send(); vm.chat.first { chat -> chat.stage == ChatStage.COMPLETED }
            }
            DebugScenario.STOPPED_PARTIAL, DebugScenario.RECREATED_CANCELLED_STATE -> {
                vm.draft("Stop this synthetic request"); vm.send(); vm.chat.first { it.stage == ChatStage.RECEIVING }; vm.stop()
            }
            DebugScenario.STREAMING_SHORT, DebugScenario.STREAMING_LONG, DebugScenario.STREAMING_FINE_GRAINED,
            DebugScenario.ACTIVE_STREAM_RECREATION, DebugScenario.BACKGROUNDED_STREAM,
            DebugScenario.SCREEN_OFF_STYLE_RESUME -> {
                vm.draft("Stream a synthetic answer"); vm.send(); vm.chat.first { it.stage == ChatStage.RECEIVING }
            }
            else -> if (failure() != null) { vm.draft("Synthetic failure request"); vm.send(); vm.chat.first { it.stage == ChatStage.FAILED } }
        }
    }
}
