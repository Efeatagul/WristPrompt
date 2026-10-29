package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The real VM/session/provider/OkHttp adapter, with synthetic bodies instead of OpenAI traffic. */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingHttpCancellationTest {
    private lateinit var main: ExecutorCoroutineDispatcher
    private val models = mutableListOf<InferenceViewModel>()
    private val partial = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"
    private val completed = "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"fresh\",\"annotations\":[]}]}]}}\n\n"
    @Before fun setup() { main = Executors.newSingleThreadExecutor().asCoroutineDispatcher(); Dispatchers.setMain(main) }
    @After fun cleanup(): Unit = runBlocking {
        models.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain(); main.close()
    }
    private suspend fun model(client: OkHttpClient, mode: CredentialMode = CredentialMode.CHATGPT_PLAN): InferenceViewModel {
        val http = OpenAiProvider(OpenAiHttp(client))
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("model", "Model"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat only")
            override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) =
                http.stream(credential, model, input, onEvent)
        }
        val lease = CredentialLease("client", 0, "synthetic", Long.MAX_VALUE, mode)
        val vm = withContext(main) {
            InferenceViewModel(MutableStateFlow(InferenceAccessState(client = "client", usable = true, mode = mode)),
                InferenceSession({ lease }, provider) { true })
        }
        models += vm
        withTimeout(3000) { vm.state.first { it.stage == ProofStage.READY } }
        return vm
    }
    private fun response(chain: Interceptor.Chain, body: ResponseBody) = Response.Builder().request(chain.request())
        .protocol(Protocol.HTTP_1_1).code(200).message("Synthetic").body(body).build()

    @Test fun stopCancelsActualCallUnblocksReadClosesBodyAndNextPromptWorksInBothModes(): Unit = runBlocking {
        for (mode in CredentialMode.entries) {
            val firstCall = AtomicReference<Call>()
            val cancelled = CountDownLatch(1); val blocked = CompletableDeferred<Unit>(); val closed = CompletableDeferred<Unit>()
            val inputs = java.util.concurrent.CopyOnWriteArrayList<JsonArray>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { if (call === firstCall.get()) cancelled.countDown() }
            }).addInterceptor { chain ->
                inputs += Json.parseToJsonElement(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()).jsonObject["input"]!!.jsonArray
                assertEquals("Bearer synthetic", chain.request().header("Authorization"))
                if (inputs.size > 1) return@addInterceptor response(chain, completed.toResponseBody("text/event-stream".toMediaType()))
                firstCall.set(chain.call())
                val source = object : Source {
                    var delivered = false
                    override fun timeout() = Timeout.NONE
                    override fun close() { closed.complete(Unit) }
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (!delivered) { delivered = true; sink.writeUtf8(partial); return partial.toByteArray().size.toLong() }
                        blocked.complete(Unit)
                        check(cancelled.await(3, TimeUnit.SECONDS)) { "Synthetic cancellation deadline" }
                        check(chain.call().isCanceled())
                        throw IOException("Synthetic cancelled read")
                    }
                }.buffer()
                response(chain, object : ResponseBody() {
                    override fun contentType() = "text/event-stream".toMediaType()
                    override fun contentLength() = -1L
                    override fun source() = source
                })
            }.build()
            val vm = model(client, mode)
            withContext(main) { vm.draft("A"); vm.send() }
            withTimeout(3000) { blocked.await(); vm.chat.first { it.partial == "partial" } }
            withContext(main) {
                vm.stop()
                assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
                assertEquals("partial", vm.chat.value.partial)
                vm.draft("B"); vm.send(); vm.send()
            }
            withTimeout(3000) { closed.await(); vm.chat.first { it.stage == ChatStage.COMPLETED } }
            assertTrue(firstCall.get().isCanceled()); assertEquals(2, inputs.size)
            assertEquals("B", inputs.last().single().jsonObject["content"]!!.jsonPrimitive.content)
            assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.first().status)
            assertEquals(listOf("A", "B", "fresh"), vm.chat.value.messages.map { it.text })
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
    @Test fun stopBeforeResponseCancelsCallWithoutInventingPartialOutput(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>(); val cancelled = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.countDown() }
        }).addInterceptor { chain ->
            call.set(chain.call()); entered.complete(Unit)
            check(cancelled.await(3, TimeUnit.SECONDS)) { "Synthetic cancellation deadline" }
            throw IOException("Synthetic cancelled before headers")
        }.build()
        val vm = model(client)
        withContext(main) { vm.draft("A"); vm.send() }
        withTimeout(3000) { entered.await() }
        withContext(main) { vm.stop() }
        val state = vm.chat.value
        assertEquals(ChatStage.CANCELLED, state.stage); assertEquals("", state.partial)
        withTimeout(3000) { vm.viewModelScope.coroutineContext[Job]!!.children.toList().filter { it.isCancelled }.forEach { it.join() } }
        assertTrue(call.get().isCanceled()); assertEquals(ChatMessageStatus.EXCLUDED, state.messages.single().status)
    }
    @Test fun eofMalformedAndFailedTerminalAfterPartialRemainDistinctAndExcluded(): Unit = runBlocking {
        val tails = listOf(
            "" to ApiProblem.INTERRUPTED,
            "data: nope\n\n" to ApiProblem.PROTOCOL,
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"incomplete\"}}\n\n" to ApiProblem.PROTOCOL,
            "data: {\"type\":\"response.incomplete\",\"response\":{\"status\":\"incomplete\"}}\n\n" to ApiProblem.INCOMPLETE,
            "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"server_error\"}}}\n\n" to ApiProblem.TEMPORARY,
        )
        for ((tail, problem) in tails) {
            val vm = model(OkHttpClient.Builder().addInterceptor { response(it, (partial + tail).toResponseBody("text/event-stream".toMediaType())) }.build())
            withContext(main) { vm.draft("A"); vm.send() }
            val state = withTimeout(3000) { vm.chat.first { it.stage == ChatStage.FAILED } }
            assertEquals(problem, state.problem); assertEquals("partial", state.partial)
            assertEquals(ChatMessageStatus.EXCLUDED, state.messages.single().status)
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
    @Test fun ignoredLifecycleEventsDoNotDuplicateTextAndTransportStopsAtCompleted(): Unit = runBlocking {
        val body = "data: {\"type\":\"response.created\"}\n\n" + partial + completed + partial + completed
        val vm = model(OkHttpClient.Builder().addInterceptor { response(it, body.toResponseBody("text/event-stream".toMediaType())) }.build())
        withContext(main) { vm.draft("A"); vm.send() }
        val state = withTimeout(3000) { vm.chat.first { it.stage == ChatStage.COMPLETED } }
        assertEquals(listOf("A", "fresh"), state.messages.map { it.text })
        assertEquals("", state.partial)
    }
}
