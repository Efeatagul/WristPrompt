package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.images.*
import io.github.grassified.wristprompt.storage.*
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import okio.buffer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*

/** Real VM/session/canonical codec/private files; synthetic provider, no live traffic. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageChatIntegrationTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val directory = Files.createTempDirectory("synthetic-image-chat").toFile()
    private val models = mutableListOf<InferenceViewModel>()
    private val library = ImageFileStore(directory.resolve("library"))
    private fun archive() = ConversationImages(directory.resolve("replay"), library) { record, content ->
        ImageWire.validateContent(record, content); content
    }
    @Before fun setup() { Dispatchers.setMain(main) }
    @After fun cleanup() = runBlocking {
        models.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain(); main.close(); directory.deleteRecursively(); Unit
    }
    private class Store : ConversationStore {
        val scope = UUID.randomUUID().toString()
        val entries = linkedMapOf<String, ConversationEntry>()
        var writes = 0; var failAt = -1
        override suspend fun owner(identityHash: String) = scope
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (writes == failAt) throw ConversationStorageException()
            val (body, replay) = ConversationCodec.encode(entry)
            return entry.copy(revision = entry.revision + 1, snapshot = ConversationCodec.decode(body, replay)).also {
                writes++; entries[it.id] = it
            }
        }
        override suspend fun load(id: String, owner: String) = entries[id]?.takeIf { it.owner == owner }
        override suspend fun list(owner: String) = entries.values.filter { it.owner == owner }.map {
            ConversationSummary(it.id, it.title, it.model, it.route, it.updated, it.revision)
        }
        override suspend fun delete(id: String, owner: String, revision: Long) { check(entries[id]?.owner == owner); entries.remove(id) }
        override suspend fun rename(id: String, owner: String, revision: Long, title: String) { error("unused") }
    }
    private class Provider(var slug: String = "gpt-6.1-sol") : AiProvider {
        val inputs = java.util.concurrent.CopyOnWriteArrayList<List<JsonObject>>()
        val modes = java.util.concurrent.CopyOnWriteArrayList<CredentialMode>()
        var action: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { it(ResponseEvent.Completed("answer")) }
        override suspend fun models(credential: CredentialLease) = listOf(AiModel(slug, "Synthetic"), AiModel("unknown-vision", "Unknown"))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) { error("chat only") }
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            check(model == slug || model == "unknown-vision")
            inputs += input; modes += credential.mode; action(onEvent)
        }
    }
    private suspend fun create(store: Store, provider: Provider, mode: CredentialMode = CredentialMode.CHATGPT_PLAN,
        access: MutableStateFlow<InferenceAccessState> = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true, mode = mode)),
        images: ConversationImages = archive()): InferenceViewModel {
        val lease = CredentialLease("synthetic", 0, "synthetic-token", Long.MAX_VALUE, mode)
        return withContext(main) {
            InferenceViewModel(access, InferenceSession({ lease }, provider) { access.value.client == it.clientId && access.value.mode == it.mode },
                conversationStore = store, conversationIdentity = { "a".repeat(64) }, conversationImages = images).also { models += it }
        }.also { withTimeout(5000) { it.state.first { state -> state.stage == ProofStage.READY } } }
    }
    private suspend fun ids(count: Int) = library.importBatch(count) { ImageContent(byteArrayOf(it.toByte(), 1, 2), byteArrayOf(3), 1, 1) }.records.takeLast(count).map { it.id }
    private suspend fun select(vm: InferenceViewModel, selected: List<String>) = withContext(main) {
        check(vm.imageSelection.replace(vm.imageSelection.selected.value, selected, selected.toSet()))
    }
    private suspend fun send(vm: InferenceViewModel, text: String = "Exact question") = withContext(main) { vm.draft(text); vm.send(); vm.send() }
    private suspend fun completed(vm: InferenceViewModel) = withTimeout(5000) { vm.chat.first { it.stage == ChatStage.COMPLETED } }
    private suspend fun problem(vm: InferenceViewModel, problem: ApiProblem) = withTimeout(5000) { vm.chat.first { it.problem == problem } }

    @Test fun combinedReviewSendsExactEditedTextAndOrderedRemainingImagesExactlyOnce() = runBlocking {
        for (count in listOf(0, 1, 3)) for (preset in listOf(false, true)) {
            val store = Store(); val provider = Provider(); val vm = create(store, provider)
            val selected = if (count == 0) emptyList() else ids(count)
            withContext(main) {
                vm.draft("  ordinary\n")
                if (preset) check(vm.applyPreset("Preset", false, vm.chat.value.draft, vm.inputToken()!!))
            }
            select(vm, selected)
            val finalIds = if (count == 3) selected.filterIndexed { index, _ -> index != 1 } else selected
            val exact = "  ordinary\n" + (if (preset) "\n\nPreset edited" else "") + "\nAdditional text  "
            withContext(main) {
                if (count == 3) vm.removeDraftImage(selected[1])
                vm.draft(exact)
            }
            assertEquals(finalIds, vm.imageSelection.selected.value)
            assertTrue(provider.inputs.isEmpty())
            withContext(main) { vm.send(); vm.send() }
            completed(vm)
            assertEquals(1, provider.inputs.size)
            val message = store.entries.values.single().snapshot.messages.first()
            assertTrue("Exact reviewed text is canonical", message.parts.filterIsInstance<ContentPart.Text>().single().text == exact)
            assertEquals(finalIds, message.parts.filterIsInstance<ContentPart.ImageAttachment>().map { it.attachmentId })
            val content = provider.inputs.single().single().getValue("content")
            assertTrue("Exact reviewed text reaches provider", if (finalIds.isEmpty()) content.jsonPrimitive.content == exact
                else content.jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content == exact)
            if (finalIds.isNotEmpty()) assertTrue("Committed order reaches D7-D serializer", content.jsonArray.drop(1).map {
                it.jsonObject.getValue("image_url").jsonPrimitive.content
            } == finalIds.map { id -> "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(library.content(id).image) })
            assertEquals("", vm.chat.value.draft); assertTrue(vm.imageSelection.selected.value.isEmpty())
        }
    }
    @Test fun directRemovalInvalidatesPendingPickerAndDiscardClearsBothWithoutTouchingCompletedChat() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider)
        send(vm); completed(vm)
        val completed = vm.chat.value.messages
        val selected = ids(2); select(vm, selected)
        val picker = WatchImagesViewModel(library) { true }
        try {
            withContext(main) {
                vm.draft("keep exact"); check(picker.begin(vm))
                vm.removeDraftImage(selected.first())
                picker.apply(vm) { error("Stale picker must not apply") }
            }
            withTimeout(5000) { picker.error.first { it == "Draft or library changed. Cancel and select again." } }
            assertEquals(selected.drop(1), vm.imageSelection.selected.value)
            assertEquals("keep exact", vm.chat.value.draft)
            withContext(main) { picker.cancel(); vm.discardDraft() }
            assertEquals("", vm.chat.value.draft); assertTrue(vm.imageSelection.selected.value.isEmpty())
            assertEquals(completed, vm.chat.value.messages); assertEquals(1, provider.inputs.size)
        } finally { picker.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun retainedActivityOwnerKeepsCombinedDraftButFreshProcessRestoresOnlyCanonicalChat() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider)
        send(vm); completed(vm)
        val completed = vm.chat.value.messages
        val selected = ids(2); select(vm, selected)
        val retained = androidx.lifecycle.ViewModelStore()
        try {
            withContext(main) { vm.draft("unsent review"); retained.put("composer", vm) }
            val factory = androidx.lifecycle.viewmodel.viewModelFactory {
                initializer<InferenceViewModel> { error("Recreation must reuse retained owner") }
            }
            val recreated = androidx.lifecycle.ViewModelProvider.create(retained, factory)["composer", InferenceViewModel::class]
            assertSame(vm, recreated)
            assertEquals("unsent review", recreated.chat.value.draft); assertEquals(selected, recreated.imageSelection.selected.value)
            assertEquals(completed, recreated.chat.value.messages); assertEquals(1, provider.inputs.size)
            withContext(main) { retained.clear() }
            val fresh = create(store, provider)
            assertEquals("", fresh.chat.value.draft); assertTrue(fresh.imageSelection.selected.value.isEmpty())
            assertEquals(completed, fresh.chat.value.messages); assertEquals(1, provider.inputs.size)
        } finally { withContext(main) { retained.clear() } }
    }
    @Test fun navigationAndPickerVisitsKeepCombinedReviewCompletedChatAndReaderPositionInert() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider)
        send(vm); completed(vm)
        val completed = vm.chat.value.messages
        val selected = ids(2); select(vm, selected)
        val nav = io.github.grassified.wristprompt.ui.ShellNavigationViewModel()
        withContext(main) {
            vm.draft("  exact pending review\n")
            nav.rememberPosition(io.github.grassified.wristprompt.ui.Surface.CHAT, 8, -12)
            nav.open(io.github.grassified.wristprompt.ui.Surface.COMPOSER)
            for (surface in listOf(io.github.grassified.wristprompt.ui.Surface.MODELS,
                io.github.grassified.wristprompt.ui.Surface.PROMPTS, io.github.grassified.wristprompt.ui.Surface.IMAGES,
                io.github.grassified.wristprompt.ui.Surface.HISTORY)) {
                nav.open(surface); nav.back()
                assertEquals(io.github.grassified.wristprompt.ui.Surface.COMPOSER, nav.surface.value)
                assertEquals("  exact pending review\n", vm.chat.value.draft)
                assertEquals(selected, vm.imageSelection.selected.value)
            }
            nav.back()
            assertEquals(io.github.grassified.wristprompt.ui.Surface.CHAT, nav.surface.value)
            assertEquals(io.github.grassified.wristprompt.ui.ReadingPosition(8, -12), nav.position(nav.surface.value))
        }
        assertEquals(completed, vm.chat.value.messages); assertEquals(1, provider.inputs.size)
    }
    @Test fun explicitModelChangeRetainsReviewButInvalidatesOldInputWithoutSending() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider)
        val selected = ids(2); select(vm, selected)
        val token = withContext(main) { vm.draft("exact unsent"); vm.inputToken()!! }
        withContext(main) { vm.select("unknown-vision") }
        withTimeout(5000) { vm.state.first { it.selectedSlug == "unknown-vision" } }
        withContext(main) { vm.inputResult("stale keyboard", token) }
        assertEquals("exact unsent", vm.chat.value.draft); assertEquals(selected, vm.imageSelection.selected.value)
        assertTrue(provider.inputs.isEmpty())
        withContext(main) { vm.send() }
        problem(vm, ApiProblem.IMAGES_UNSUPPORTED)
        assertEquals("exact unsent", vm.chat.value.draft); assertEquals(selected, vm.imageSelection.selected.value)
        assertTrue(provider.inputs.isEmpty())
    }
    @Test fun explicitOneAndMultipleSendRestartReopenAndContinueOnBothBillingRoutes() = runBlocking {
        for (mode in CredentialMode.entries) for (count in listOf(1, 3)) {
            val store = Store(); val provider = Provider(); val selected = ids(count)
            val vm = create(store, provider, mode); select(vm, selected); send(vm); completed(vm)
            assertEquals(1, provider.inputs.size); assertEquals(listOf(mode), provider.modes)
            assertTrue(vm.imageSelection.selected.value.isEmpty())
            assertEquals(selected, store.entries.values.single().snapshot.messages.first().parts.filterIsInstance<ContentPart.ImageAttachment>().map { it.attachmentId })
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            val restored = create(store, provider, mode)
            assertEquals(1, provider.inputs.size)
            val opened = CompletableDeferred<Unit>()
            withContext(main) { restored.openChat(store.entries.keys.single()) { opened.complete(Unit) } }
            withTimeout(5000) { opened.await() }
            assertEquals(1, provider.inputs.size)
            selected.forEach { library.delete(it) }
            send(restored, "continue"); completed(restored)
            assertEquals(2, provider.inputs.size); assertEquals(3, provider.inputs.last().size)
            assertEquals(count + 1, provider.inputs.last().first().getValue("content").jsonArray.size)
        }
    }
    @Test fun unsupportedCapabilityAndMissingFilesPreserveExactDraftAndSelectionWithZeroRequests() = runBlocking {
        for (damage in listOf("unsupported", "missing", "corrupt", "deleted")) {
            val store = Store(); val provider = Provider(if (damage == "unsupported") "gpt-6.1-sol-unverified" else "gpt-6.1-sol")
            val selected = ids(1); val vm = create(store, provider); select(vm, selected)
            val file = directory.resolve("library/${selected.single()}-1-image.jpg")
            when (damage) { "missing" -> file.delete(); "corrupt" -> file.writeBytes(byteArrayOf(9, 9, 9)); "deleted" -> library.delete(selected.single()) }
            send(vm, "  keep exact draft\n")
            problem(vm, if (damage == "unsupported") ApiProblem.IMAGES_UNSUPPORTED else ApiProblem.IMAGE_UNAVAILABLE)
            assertEquals("  keep exact draft\n", vm.chat.value.draft); assertEquals(selected, vm.imageSelection.selected.value)
            assertEquals(0, provider.inputs.size); assertEquals(0, store.writes)
        }
    }
    @Test fun imagesOnlyShowsTextRequirementWithoutDispatch() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); val selected = ids(1)
        select(vm, selected); send(vm, " \n"); problem(vm, ApiProblem.IMAGE_TEXT_REQUIRED)
        assertEquals(selected, vm.imageSelection.selected.value); assertEquals(" \n", vm.chat.value.draft); assertTrue(provider.inputs.isEmpty())
    }
    @Test fun stopFailureMalformedAndInterruptionExcludeImageTurnWithoutRetryAndNextSendWorks() = runBlocking {
        for (terminal in listOf("stop", "http", "malformed", "interrupted")) {
            val store = Store(); val provider = Provider(); val vm = create(store, provider); val selected = ids(1)
            provider.action = { emit ->
                emit(ResponseEvent.Delta("partial"))
                when (terminal) {
                    "stop" -> awaitCancellation()
                    "http" -> throw ApiFailure(ApiProblem.TEMPORARY, status = 503)
                    "malformed" -> ResponseEvents.decode(null, "malformed synthetic")
                    else -> Unit // EOF without completed.
                }
            }
            select(vm, selected); send(vm)
            withTimeout(5000) { vm.chat.first { it.stage in setOf(ChatStage.FAILED, ChatStage.RECEIVING) } }
            if (terminal == "stop") withContext(main) { vm.stop() }
            withTimeout(5000) { vm.chat.first { it.stage in setOf(ChatStage.FAILED, ChatStage.CANCELLED) } }
            withContext(main) { }
            assertEquals(1, provider.inputs.size); assertTrue(store.entries.values.single().snapshot.replay.isEmpty())
            assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
            provider.action = { it(ResponseEvent.Completed("answer")) }
            send(vm, "explicit next"); completed(vm)
            assertEquals(2, provider.inputs.size); assertEquals(1, provider.inputs.last().size)
        }
    }
    @Test fun completionSaveFailureAndBeginFailureDoNotCommitImagesOrRetry() = runBlocking {
        for (failAt in listOf(0, 1)) {
            val store = Store().apply { this.failAt = failAt }; val provider = Provider(); val vm = create(store, provider)
            select(vm, ids(1)); send(vm); problem(vm, ApiProblem.CHAT_STORAGE)
            assertEquals(failAt, provider.inputs.size)
            assertTrue(store.entries.values.all { it.snapshot.replay.isEmpty() })
            assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
            store.failAt = -1
            send(vm, "explicit next"); completed(vm)
            assertEquals(failAt + 1, provider.inputs.size); assertEquals(1, provider.inputs.last().size)
        }
    }
    @Test fun replayBudgetValidationPreservesNextDraftAndSelectedPhotos() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider)
        val selected = ids(6); select(vm, selected); send(vm); completed(vm)
        select(vm, selected.take(1)); send(vm, "keep next draft"); problem(vm, ApiProblem.IMAGE_LIMIT)
        assertEquals(1, provider.inputs.size); assertEquals("keep next draft", vm.chat.value.draft)
        assertEquals(selected.take(1), vm.imageSelection.selected.value)
    }
    @Test fun requiredReplayCorruptionBlocksTextOnlyContinuationBeforeDispatch() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); val selected = ids(1)
        select(vm, selected); send(vm); completed(vm)
        directory.resolve("replay/${store.entries.keys.single()}/${selected.single()}-1-image.jpg").writeBytes(byteArrayOf(9, 9, 9))
        send(vm, "keep follow up"); problem(vm, ApiProblem.IMAGE_UNAVAILABLE)
        assertEquals(1, provider.inputs.size); assertEquals("keep follow up", vm.chat.value.draft)
        assertEquals(2, store.writes)
    }
    @Test fun imagePreflightStopAndAccountChangeNeverDispatch() = runBlocking {
        for (changeAccount in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>()
            val release = java.util.concurrent.CountDownLatch(1)
            val store = Store(); val provider = Provider()
            val access = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true))
            val images = ConversationImages(directory.resolve("preflight-$changeAccount"), library) { record, content ->
                entered.complete(Unit)
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                ImageWire.validateContent(record, content); content
            }
            val vm = create(store, provider, access = access, images = images)
            val selected = ids(1); select(vm, selected); send(vm, "preflight draft")
            try {
                withTimeout(5000) { entered.await() }
                withContext(main) {
                    if (changeAccount) access.value = InferenceAccessState(client = "other", usable = false)
                    else {
                        vm.stop()
                        assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
                        assertEquals("preflight draft", vm.chat.value.draft)
                        assertEquals(selected, vm.imageSelection.selected.value)
                    }
                }
            } finally { release.countDown() }
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            assertTrue(provider.inputs.isEmpty()); assertEquals(0, store.writes)
        }
    }
    @Test fun imageStreamStopCancelsActualHttpCallOnBothRoutes() = runBlocking {
        for (mode in CredentialMode.entries) {
            val store = Store(); val provider = Provider(); val vm = create(store, provider, mode)
            val cancelled = java.util.concurrent.CountDownLatch(1)
            val reading = CompletableDeferred<Unit>(); val closed = CompletableDeferred<Unit>()
            val call = java.util.concurrent.atomic.AtomicReference<okhttp3.Call>()
            val client = okhttp3.OkHttpClient.Builder().eventListener(object : okhttp3.EventListener() {
                override fun canceled(call: okhttp3.Call) { cancelled.countDown() }
            }).addInterceptor { chain ->
                call.set(chain.call())
                val source = object : okio.Source {
                    var delivered = false
                    override fun timeout() = okio.Timeout.NONE
                    override fun close() { closed.complete(Unit) }
                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                        if (!delivered) {
                            delivered = true
                            val frame = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"
                            sink.writeUtf8(frame); return frame.toByteArray().size.toLong()
                        }
                        reading.complete(Unit)
                        check(cancelled.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw java.io.IOException("Synthetic cancellation")
                    }
                }.buffer()
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("Synthetic")
                    .body(object : okhttp3.ResponseBody() {
                        override fun contentType() = "text/event-stream".toMediaType()
                        override fun contentLength() = -1L
                        override fun source() = source
                    }).build()
            }.build()
            val http = OpenAiProvider(OpenAiHttp(client))
            provider.action = { emit -> http.stream(CredentialLease("synthetic", 0, "synthetic-token", Long.MAX_VALUE, mode),
                provider.slug, provider.inputs.last(), emit) }
            select(vm, ids(2)); send(vm)
            withTimeout(5000) { reading.await() }
            withContext(main) { vm.stop() }
            withTimeout(5000) { closed.await() }
            assertTrue(call.get().isCanceled()); assertEquals(1, provider.inputs.size)
            assertTrue(store.entries.values.single().snapshot.replay.isEmpty())
            assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
        }
    }
    @Test fun selectionPickerThinkingModelChangesAndReloadAreInertUntilExplicitSend() = runBlocking {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); val selected = ids(2)
        select(vm, selected)
        val picker = WatchImagesViewModel(library) { true }
        try {
            withContext(main) {
                vm.draft("draft"); check(picker.begin(vm)); picker.toggle(selected.first()); picker.cancel()
                vm.selectThinking(null)
            }
            withTimeout(5000) { vm.state.first { !it.thinkingSaving } }
            withContext(main) { vm.select("unknown-vision") }
            withTimeout(5000) { vm.state.first { it.selectedSlug == "unknown-vision" } }
            withContext(main) { vm.loadModels() }
            withTimeout(5000) { vm.state.first { it.stage == ProofStage.READY } }
            assertTrue(provider.inputs.isEmpty())
        } finally { picker.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
