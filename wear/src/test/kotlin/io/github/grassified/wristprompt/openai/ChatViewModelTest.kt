package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private var vm: InferenceViewModel? = null
    private val access = MutableStateFlow(InferenceAccessState(client = "client", usable = true))
    private class Provider : AiProvider {
        val inputs = mutableListOf<List<JsonObject>>()
        val modes = mutableListOf<CredentialMode>()
        var answer: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { emit ->
            emit(ResponseEvent.Delta("Any answer")); emit(ResponseEvent.Completed("Any answer"))
        }
        var catalog: suspend () -> List<AiModel> = { listOf(AiModel("sol", "Sol"), AiModel("terra", "Terra")) }
        override suspend fun models(credential: CredentialLease) = catalog()
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat must not use proof")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            inputs += input; modes += credential.mode; answer(onEvent)
        }
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { vm?.viewModelScope?.cancel(); Dispatchers.resetMain() }
    private fun create(provider: Provider = Provider()): InferenceViewModel {
        var saved = "terra"
        val session = InferenceSession({ lease() }, provider) { lease -> lease.clientId == access.value.client &&
            lease.generation == access.value.generation && lease.mode == access.value.mode && lease.modeRevision == access.value.modeRevision }
        return InferenceViewModel(access, session, { saved }, { _, slug -> saved = slug }).also { vm = it }
    }
    private fun lease() = CredentialLease(access.value.client!!, access.value.generation, "synthetic", Long.MAX_VALUE,
        access.value.mode, access.value.modeRevision)
    private fun send(model: InferenceViewModel, prompt: String = "User question") { model.draft(prompt); model.send() }
    @Test fun presetsOnlyMutateDraftUntilExplicitSendAndKeepOwnership(): Unit = runTest {
        val provider = Provider(); val model = create(provider); runCurrent()
        val mode = access.value.mode
        val selected = model.state.value.selectedSlug
        assertTrue(model.applyPreset("Preset", false, "", model.inputToken()!!))
        assertEquals("Preset", model.chat.value.draft)
        model.draft(model.chat.value.draft + " edited")
        assertEquals("Preset edited", model.chat.value.draft)
        assertTrue(model.applyPreset("Next", false, "Preset edited", model.inputToken()!!))
        assertEquals("Preset edited\n\nNext", model.chat.value.draft)
        assertTrue(model.applyPreset("Replacement", true, model.chat.value.draft, model.inputToken()!!))
        assertEquals("Replacement", model.chat.value.draft)
        assertEquals(selected, model.state.value.selectedSlug)
        assertEquals(mode, access.value.mode)
        assertTrue(provider.inputs.isEmpty())
        model.send(); runCurrent()
        assertEquals(1, provider.inputs.size)
        assertEquals("Replacement", provider.inputs.single().single()["content"]!!.jsonPrimitive.content)
    }
    @Test fun oversizeOrChangedDraftRejectsPreservingExactContent(): Unit = runTest {
        val provider = Provider(); val model = create(provider); runCurrent()
        val original = "  Original\r\n\t"
        model.draft(original)
        assertFalse(model.applyPreset("x".repeat(8192), false, original, model.inputToken()!!))
        assertEquals(original, model.chat.value.draft)
        assertFalse(model.applyPreset("x".repeat(8193), true, original, model.inputToken()!!))
        assertEquals(original, model.chat.value.draft)
        val token = model.inputToken()!!
        model.draft("User edit")
        assertFalse(model.applyPreset("Preset", true, original, token))
        assertEquals("User edit", model.chat.value.draft)
        model.newChat()
        assertFalse(model.applyPreset("Preset", true, "", token))
        assertTrue(provider.inputs.isEmpty())
    }
    @Test fun pickerCancelDeleteUpdateAndSyncNeverOwnDraftOrInvokeInference(): Unit = runTest {
        val provider = Provider(); val model = create(provider); runCurrent()
        val memory = io.github.grassified.wristprompt.presets.PresetMemoryStore()
        val store = io.github.grassified.wristprompt.presets.SyncedPresetStore(memory)
        val source = io.github.grassified.wristprompt.presets.PresetCodec.newId()
        val preset = io.github.grassified.wristprompt.presets.PromptPreset(io.github.grassified.wristprompt.presets.PresetCodec.newId(), 1, "Name", "Synthetic")
        suspend fun sync(record: io.github.grassified.wristprompt.presets.PromptPreset) {
            val wire = io.github.grassified.wristprompt.presets.PresetWire
            store.accept("phone", setOf("phone"), listOf(io.github.grassified.wristprompt.presets.PresetDelivery(wire.path(record), wire.encode(
                io.github.grassified.wristprompt.presets.PresetPacket(source = source, catalog = io.github.grassified.wristprompt.presets.PresetCatalog(records = listOf(record)))))))
        }
        val picker = io.github.grassified.wristprompt.presets.WatchPresetsViewModel(store) { true }
        suspend fun awaitCondition(condition: () -> Boolean) {
            withContext(Dispatchers.Default) { withTimeout(5000) { while (!condition()) delay(5) } }
            runCurrent()
        }
        try {
            sync(preset); awaitCondition { picker.state.value.catalog.records.size == 1 }
            picker.select(preset, model)
            picker.apply(io.github.grassified.wristprompt.presets.PresetChoice.INSERT, model)
            awaitCondition { model.chat.value.draft == "Synthetic" }
            sync(preset.copy(revision = 2, text = "Updated"))
            awaitCondition { picker.state.value.catalog.records.single().revision == 2L }
            assertEquals("Synthetic", model.chat.value.draft)
            val original = "  Draft\r\n\t"
            model.draft(original)
            picker.refresh(); picker.select(preset, model)
            picker.apply(io.github.grassified.wristprompt.presets.PresetChoice.CANCEL, model); runCurrent()
            assertEquals(original, model.chat.value.draft)
            picker.select(preset, model)
            sync(preset.copy(revision = 3, name = "", text = "", deleted = true)); awaitCondition { picker.state.value.catalog.records.single().deleted }
            picker.apply(io.github.grassified.wristprompt.presets.PresetChoice.REPLACE, model); awaitCondition { picker.state.value.error != null }
            assertEquals(original, model.chat.value.draft)
            assertNotNull(picker.state.value.error)
            assertTrue(provider.inputs.isEmpty())
            assertFalse(picker.state.value.toString().contains("Synthetic"))
        } finally { picker.viewModelScope.cancel() }
    }
    @Test fun twoTurnsUseChatPayloadAndAppendExactlyOnceWithoutProofMatch(): Unit = runTest {
        val provider = Provider(); val model = create(provider); runCurrent()
        send(model, "Arbitrary text"); runCurrent()
        assertEquals(ChatStage.COMPLETED, model.chat.value.stage)
        send(model, "Follow-up"); runCurrent()
        assertEquals(listOf("Arbitrary text", "Any answer", "Follow-up"), provider.inputs[1].map { it["content"]!!.jsonPrimitive.content })
        assertEquals(4, model.chat.value.messages.size)
        assertEquals("terra", model.state.value.selectedSlug)
    }
    @Test fun duplicateSendWhileActiveAndAfterCompletionDoesNotReplay(): Unit = runTest {
        val gate = CompletableDeferred<Unit>(); val p = Provider().apply { answer = { gate.await(); it(ResponseEvent.Completed("Done")) } }
        val model = create(p); runCurrent(); send(model); model.send(); runCurrent()
        assertEquals(1, p.inputs.size); assertEquals(1, model.chat.value.messages.size)
        gate.complete(Unit); runCurrent(); model.send(); runCurrent(); assertEquals(1, p.inputs.size)
    }
    @Test fun emptyAndWhitespaceNeverCallProvider(): Unit = runTest {
        val p = Provider(); val model = create(p); runCurrent()
        for (text in listOf("", " \n\t")) { send(model, text); runCurrent() }
        assertTrue(p.inputs.isEmpty()); assertTrue(model.chat.value.messages.isEmpty())
    }
    @Test fun failurePreservesUserWithoutFabricatingAssistant(): Unit = runTest {
        val p = Provider().apply { answer = { throw ApiFailure(ApiProblem.USAGE_LIMIT) } }
        val model = create(p); runCurrent(); send(model); runCurrent()
        assertEquals(ChatStage.FAILED, model.chat.value.stage)
        assertEquals(listOf(ChatRole.USER), model.chat.value.messages.map { it.role })
        assertEquals(CredentialMode.CHATGPT_PLAN, model.state.value.mode)
    }
    @Test fun stopAndIncompleteRetainOnlyCompletedContext(): Unit = runTest {
        for (stop in listOf(false, true)) {
            val p = Provider().apply { answer = { emit ->
                emit(ResponseEvent.Delta("Partial"))
                if (stop) awaitCancellation() else emit(ResponseEvent.Incomplete)
            } }
            val model = create(p); runCurrent(); send(model, "Unfinished"); runCurrent()
            if (stop) model.stop()
            assertEquals("Partial", model.chat.value.partial)
            assertEquals(if (stop) ChatStage.CANCELLED else ChatStage.FAILED, model.chat.value.stage)
            p.answer = { it(ResponseEvent.Completed("Done")) }
            send(model, "Next"); runCurrent(); assertEquals(1, p.inputs.last().size)
            model.viewModelScope.cancel()
        }
    }
    @Test fun newChatInvalidatesLateResultAndInputResult(): Unit = runTest {
        val gate = CompletableDeferred<Unit>(); val p = Provider().apply { answer = { emit ->
            withContext(NonCancellable) { gate.await(); emit(ResponseEvent.Completed("Late")) }
        } }
        val model = create(p); runCurrent(); val token = model.inputToken()!!
        send(model); runCurrent(); model.newChat(); model.draft("Old input", token)
        gate.complete(Unit); runCurrent()
        assertEquals(ChatState(), model.chat.value)
    }
    @Test fun accountBillingGenerationAndModeRevisionChangesClearContext(): Unit = runTest {
        val p = Provider(); val model = create(p); runCurrent()
        val changes: List<(InferenceAccessState) -> InferenceAccessState> = listOf(
            { it.copy(client = "other") }, { it.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = 1) },
            { it.copy(generation = it.generation + 1) }, { it.copy(modeRevision = it.modeRevision + 1) })
        for (change in changes) {
            model.newChat()
            send(model, "Old context"); runCurrent(); assertEquals(2, model.chat.value.messages.size)
            val oldToken = model.inputToken()!!
            access.value = change(access.value); runCurrent()
            model.draft("Stale composer", oldToken); assertEquals(ChatState(), model.chat.value)
            send(model, "Fresh"); runCurrent(); assertEquals(1, p.inputs.last().size)
        }
    }
    @Test fun modelChangeClearsContextAndSameModelReloadPreservesIt(): Unit = runTest {
        val model = create(); runCurrent(); send(model); runCurrent()
        model.loadModels(); runCurrent(); assertEquals(2, model.chat.value.messages.size)
        val oldToken = model.inputToken()!!; model.select("sol"); runCurrent()
        assertEquals(ChatState(), model.chat.value); assertEquals("sol", model.state.value.selectedSlug)
        model.draft("Late", oldToken); assertEquals("", model.chat.value.draft)
    }
    @Test fun accountOrBillingSwitchWhileReceivingRejectsLateAssistant(): Unit = runTest {
        for (billing in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val p = Provider().apply { answer = { emit ->
                emit(ResponseEvent.Delta("Old partial"))
                withContext(NonCancellable) { gate.await(); emit(ResponseEvent.Completed("Old answer")) }
            } }
            val model = create(p); runCurrent(); send(model); runCurrent()
            access.value = if (billing) access.value.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = access.value.modeRevision + 1)
                else access.value.copy(client = "other-client", generation = access.value.generation + 1)
            runCurrent(); gate.complete(Unit); runCurrent()
            assertEquals(ChatState(), model.chat.value)
            model.viewModelScope.cancel()
        }
    }
    @Test fun modelFallbackAfterFailedReloadCannotCarryOldContext(): Unit = runTest {
        val p = Provider(); val model = create(p); runCurrent(); send(model); runCurrent()
        p.catalog = { throw ApiFailure(ApiProblem.NETWORK) }
        model.loadModels(); runCurrent(); assertNull(model.state.value.selectedSlug)
        p.catalog = { listOf(AiModel("sol", "Sol")) }
        model.loadModels(); runCurrent()
        assertEquals("sol", model.state.value.selectedSlug); assertEquals(ChatState(), model.chat.value)
        send(model, "Fresh model"); runCurrent(); assertEquals(1, p.inputs.last().size)
    }
    @Test fun apiFailureNeverFallsBackToPlan(): Unit = runTest {
        access.value = access.value.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = 1)
        val p = Provider().apply { answer = { throw ApiFailure(ApiProblem.INVALID_API_KEY) } }
        val model = create(p); runCurrent(); send(model); runCurrent()
        assertEquals(listOf(CredentialMode.OPENAI_API_KEY), p.modes)
        assertEquals(CredentialMode.OPENAI_API_KEY, access.value.mode)
        assertEquals(ApiProblem.INVALID_API_KEY, model.chat.value.problem)
    }
    @Test fun reconstructedViewModelHasNoConversationHistory(): Unit = runTest {
        val p = Provider(); val old = create(p); runCurrent(); send(old); runCurrent(); old.viewModelScope.cancel()
        val restored = create(p); runCurrent(); assertEquals(ChatState(), restored.chat.value)
        send(restored, "New process"); runCurrent(); assertEquals(1, p.inputs.last().size)
    }
}
