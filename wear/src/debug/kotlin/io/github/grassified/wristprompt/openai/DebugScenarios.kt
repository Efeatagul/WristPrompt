package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.ui.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject

internal enum class DebugScenario {
    SIGNED_OUT, CHATGPT_AUTHENTICATED, API_KEY_CONFIGURED, MODELS_LOADING, MODELS_LOADED,
    MANY_MODELS, MODEL_UNAVAILABLE, EMPTY_CHAT, LONG_USER_PROMPT, ONE_TURN_COMPLETED,
    MULTI_TURN_CHAT, LONG_ASSISTANT_OUTPUT, STREAMING_SHORT, STREAMING_LONG, STOPPED_PARTIAL,
    ERROR_401_REAUTH, ERROR_401_API_KEY, ERROR_429, ERROR_503, ERROR_OFFLINE, LONG_ERROR_MESSAGE,
    RECREATED_COMPLETED_STATE, RECREATED_CANCELLED_STATE,
    ACTIVE_STREAM_RECREATION, BACKGROUNDED_STREAM, SCREEN_OFF_STYLE_RESUME, COMPOSER_RECREATION,
    PROCESS_RESTART_IDLE, PROCESS_RESTART_AFTER_COMPLETED,
    PROCESS_RESTART_DURING_ACTIVE, PROCESS_RESTART_AFTER_PARTIAL;
    companion object { fun parse(name: String?): DebugScenario? = entries.singleOrNull { it.name == name } }
}

/** No Context, repository, preference store, HTTP client or serializable credential record. */
internal class DebugScenarios(val scenario: DebugScenario) {
    val mode = if (scenario in setOf(DebugScenario.API_KEY_CONFIGURED, DebugScenario.ERROR_401_API_KEY))
        CredentialMode.OPENAI_API_KEY else CredentialMode.CHATGPT_PLAN
    val accounts = WatchAccountState(connected = scenario != DebugScenario.SIGNED_OUT, planEnabled = true,
        accounts = if (scenario == DebugScenario.SIGNED_OUT) emptyList() else listOf(AccountLabel("debug", "Synthetic account")),
        activeClientId = "debug".takeUnless { scenario == DebugScenario.SIGNED_OUT })
    val key = ApiKeyState(loaded = true, mode = mode, configured = mode == CredentialMode.OPENAI_API_KEY)
    val surface = when (scenario) {
        DebugScenario.MANY_MODELS, DebugScenario.MODELS_LOADED, DebugScenario.MODEL_UNAVAILABLE -> Surface.MODELS
        DebugScenario.LONG_USER_PROMPT, DebugScenario.COMPOSER_RECREATION -> Surface.COMPOSER
        DebugScenario.SIGNED_OUT -> Surface.CONNECTION
        else -> Surface.CHAT
    }
    private val access = MutableStateFlow(InferenceAccessState(client = "debug", usable = scenario != DebugScenario.SIGNED_OUT, mode = mode))
    // Transient lease required by InferenceSession; empty bearer cannot be an API key/token.
    // It is passed only to this synthetic provider, never to a persistence or HTTP boundary.
    private val lease = CredentialLease("debug", 0, "", Long.MAX_VALUE, mode)
    var generations = 0; private set
    var discoveries = 0; private set
    var completions = 0; private set
    val lifecycleScenario get() = scenario in setOf(DebugScenario.ACTIVE_STREAM_RECREATION,
        DebugScenario.BACKGROUNDED_STREAM, DebugScenario.SCREEN_OFF_STYLE_RESUME)
    val processScenario get() = scenario.name.startsWith("PROCESS_RESTART_")
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
            if (loading) awaitCancellation()
            if (unavailable) { unavailable = false; throw ApiFailure(ApiProblem.NO_MODELS) }
            return (1..if (scenario == DebugScenario.MANY_MODELS) 40 else 3).map { AiModel("debug-$it", "Synthetic model $it") }
        }
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit): Unit = error("Chat only")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            generations++; inputs += input
            failure()?.let { throw it }
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
    val vm = InferenceViewModel(access, InferenceSession({ lease }, provider) { it === lease })
    fun failure(): ApiFailure? = when (scenario) {
        // A bare plan admission 401 means permission, not terminal refresh failure (D3-E).
        DebugScenario.ERROR_401_REAUTH -> ApiErrors.parse(401, "{}")
        DebugScenario.ERROR_401_API_KEY -> ApiErrors.forMode(ApiErrors.parse(401, "{}"), mode)
        DebugScenario.ERROR_429 -> ApiErrors.parse(429, "{}")
        DebugScenario.ERROR_503, DebugScenario.LONG_ERROR_MESSAGE -> ApiErrors.parse(503, "{}")
        DebugScenario.ERROR_OFFLINE -> ApiFailure(ApiProblem.NETWORK)
        else -> null
    }
    suspend fun prepare() {
        if (scenario == DebugScenario.SIGNED_OUT) return
        if (scenario == DebugScenario.MODELS_LOADING) { vm.state.first { it.stage == ProofStage.LOADING }; return }
        if (scenario == DebugScenario.MODEL_UNAVAILABLE) { vm.state.first { it.problem == ApiProblem.NO_MODELS }; vm.draft("Blocked until models reload"); return }
        vm.state.first { it.stage == ProofStage.READY }
        when (scenario) {
            DebugScenario.LONG_USER_PROMPT -> vm.draft("Long synthetic question with readable words. ".repeat(190).take(ChatConversation.MAX_PROMPT_CHARS))
            DebugScenario.ONE_TURN_COMPLETED, DebugScenario.RECREATED_COMPLETED_STATE,
            DebugScenario.MULTI_TURN_CHAT, DebugScenario.LONG_ASSISTANT_OUTPUT -> repeat(if (scenario == DebugScenario.MULTI_TURN_CHAT) 4 else 1) {
                vm.draft("Synthetic question ${it + 1}"); vm.send(); vm.chat.first { chat -> chat.stage == ChatStage.COMPLETED }
            }
            DebugScenario.STOPPED_PARTIAL, DebugScenario.RECREATED_CANCELLED_STATE -> {
                vm.draft("Stop this synthetic request"); vm.send(); vm.chat.first { it.stage == ChatStage.RECEIVING }; vm.stop()
            }
            DebugScenario.STREAMING_SHORT, DebugScenario.STREAMING_LONG,
            DebugScenario.ACTIVE_STREAM_RECREATION, DebugScenario.BACKGROUNDED_STREAM,
            DebugScenario.SCREEN_OFF_STYLE_RESUME -> {
                vm.draft("Stream a synthetic answer"); vm.send(); vm.chat.first { it.stage == ChatStage.RECEIVING }
            }
            else -> if (failure() != null) { vm.draft("Synthetic failure request"); vm.send(); vm.chat.first { it.stage == ChatStage.FAILED } }
        }
    }
}
