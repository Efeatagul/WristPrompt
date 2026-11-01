package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.images.*
import io.github.grassified.wristprompt.storage.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGenerationPipelineTest {
    @get:Rule val folder = TemporaryFolder()
    private val vms = mutableListOf<InferenceViewModel>()
    private val content = ImageContent(byteArrayOf(1,2,3), byteArrayOf(4,5), 16, 12)
    private val mode = CredentialMode.OPENAI_API_KEY
    private val access = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true, mode = mode))
    private val lease = CredentialLease("synthetic", 0, "synthetic", Long.MAX_VALUE, mode)
    private class Store : ConversationStore {
        val scope = ImageLimits.newId()
        var entry: ConversationEntry? = null
        var saves = 0
        var unreadable = false
        var completing: (suspend () -> Unit)? = null
        override suspend fun owner(identityHash: String) = scope
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (entry.snapshot.terminal == "COMPLETED") completing?.invoke()
            entry.snapshot.validate(); saves++
            return entry.copy(revision = entry.revision + 1).also { this.entry = it }
        }
        override suspend fun load(id: String, owner: String): ConversationEntry? {
            if (unreadable) throw ConversationStorageException()
            return entry?.takeIf { it.id == id && it.owner == owner }
        }
        override suspend fun list(owner: String) = listOfNotNull(entry?.takeIf { it.owner == owner }?.let {
            ConversationSummary(it.id, it.title, it.model, it.route, it.updated, it.revision)
        })
        override suspend fun delete(id: String, owner: String, revision: Long) { check(entry?.id == id); entry = null }
        override suspend fun rename(id: String, owner: String, revision: Long, title: String) = error("unused")
    }
    private class Provider : AiProvider {
        var images = 0; var texts = 0
        val inputs = mutableListOf<List<JsonObject>>()
        val configurations = mutableListOf<InferenceConfiguration>()
        var script: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { emit -> emit(event()) }
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("gpt-6.1-sol", "Synthetic", thinking = OpenAiThinkingCapabilities.forModel("gpt-6.1-sol", credential.mode),
            imageGeneration = OpenAiGeneration.capability("gpt-6.1-sol", credential.mode)))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("unused")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            texts++; inputs += input; onEvent(ResponseEvent.Completed("text follow-up"))
        }
        override suspend fun stream(credential: CredentialLease, model: AiModel, input: List<JsonObject>,
            configuration: InferenceConfiguration, onEvent: suspend (ResponseEvent) -> Unit) {
            InferenceControls.validate(configuration, model, credential.mode)
            configurations += configuration
            stream(credential, model.slug, input, onEvent)
        }
        override suspend fun generateImage(credential: CredentialLease, model: AiModel, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            OpenAiGeneration.request(model, credential.mode, input); images++; inputs += input; script(onEvent)
        }
    }
    companion object {
        fun event() = ResponseEvent.Completed("", listOf(buildJsonObject { put("type", "image_generation_call"); put("id", "ig_synthetic") }), GeneratedOutput(byteArrayOf(1,2,3)))
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { vms.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun files(root: File = folder.newFolder(), prepare: suspend (GeneratedOutput) -> ImageContent = { content }) =
        // Windows cannot open directory FileChannels. Native fsync/promotion is covered on Android.
        GeneratedImages(root, prepare, { _, image -> image }, syncDirectory = {})
    private fun vm(store: Store, p: Provider, files: GeneratedImages): InferenceViewModel = InferenceViewModel(access,
        InferenceSession({ lease }, p) { true }, conversationStore = store, conversationIdentity = { "a".repeat(64) }, generatedImages = files).also { vms += it }
    private suspend fun InferenceViewModel.done() = withContext(Dispatchers.Default) { withTimeout(5000) { chat.first { !it.busy && it.messages.isNotEmpty() } } }
    @Test fun explicitOnlyCompletionPersistsTypedOwnedImageReopensAndReplaysIdWithoutUploading() = runTest {
        val root = folder.newFolder(); val files = files(root); val store = Store(); val p = Provider(); val vm = vm(store, p, files); runCurrent()
        vm.selectThinking("medium"); runCurrent()
        assertEquals(0, p.images)
        vm.draft("draw synthetic image"); assertEquals(0, p.images)
        vm.generateImage(); vm.generateImage(); vm.done()
        assertEquals(1, p.images); assertEquals(ChatStage.COMPLETED, vm.chat.value.stage)
        assertEquals("medium", vm.state.value.configuration.controls.single().value)
        val saved = checkNotNull(store.entry); val ref = checkNotNull(vm.chat.value.messages.last().generated)
        assertArrayEquals(content.image, files.content(saved.owner, saved.id, ref).image)
        val (body, replay) = ConversationCodec.encode(saved)
        assertTrue(body.contains("GeneratedImage")); assertFalse(body.contains("AQID")); assertFalse(replay.contains("result"))
        val restored = ChatConversation().apply { restore(ConversationCodec.decode(body, replay)) }
        assertEquals(ref, restored.state.messages.last().generated)
        val library = ImageFileStore(folder.newFolder())
        val imported = library.importBatch(1) { content }.records.single()
        library.delete(imported.id) // Imported ownership cannot reach generated files.
        files.recover(store)
        assertArrayEquals(content.image, files.content(saved.owner, saved.id, ref).image)
        vm.draft("explicit follow-up"); vm.send(); vm.done()
        assertEquals(1, p.images); assertEquals(1, p.texts)
        assertEquals("medium", p.configurations.single().controls.single().value)
        assertEquals("image_generation_call", p.inputs.last()[1]["type"]!!.jsonPrimitive.content)
        assertFalse(JsonArray(p.inputs.last()).toString().contains("image_url"))
        vm.deleteChat(saved.id)
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.history.first { !it.loading && it.entries.isEmpty() } } }
        assertFalse(File(File(File(root, saved.owner), saved.id), "${ref.id}-image.jpg").exists())
        assertFalse(File(File(File(root, saved.owner), saved.id), "${ref.id}-thumb.jpg").exists())
    }
    @Test fun stopDuringImagePreparationWinsAndLeavesNoCompletedFileOrReplay() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val root = folder.newFolder(); val files = files(root) { entered.complete(Unit); release.await(); content }
        val store = Store(); val p = Provider(); val vm = vm(store, p, files); runCurrent()
        vm.draft("draw"); vm.generateImage(); entered.await(); vm.stop(); release.complete(Unit); runCurrent()
        assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
        assertTrue(store.entry!!.snapshot.replay.isEmpty()); assertTrue(root.walkTopDown().none { it.isFile })
        assertTrue(vm.chat.value.messages.none { it.generated != null }); assertEquals(1, p.images)
    }
    @Test fun leaseInvalidatedDuringPreparationCannotAcquireCompletionOwnership() = runTest {
        var current = true
        val root = folder.newFolder(); val files = files(root) { current = false; content }
        val store = Store(); val p = Provider()
        val vm = InferenceViewModel(access, InferenceSession({ lease }, p) { current }, conversationStore = store,
            conversationIdentity = { "a".repeat(64) }, generatedImages = files).also { vms += it }
        runCurrent(); vm.draft("draw"); vm.generateImage()
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.chat.first { it.stage in setOf(ChatStage.COMPLETED, ChatStage.FAILED) } } }
        assertEquals(ChatStage.FAILED, vm.chat.value.stage)
        assertEquals(ApiProblem.STALE_ACCOUNT, vm.chat.value.problem)
        assertTrue(store.entry!!.snapshot.replay.isEmpty())
        assertTrue(vm.chat.value.messages.none { it.generated != null })
        assertTrue(root.walkTopDown().none { it.isFile }); assertEquals(1, p.images)
    }
    @Test fun ownedImageCommitWinsStopAndDuplicateTerminalCannotDuplicateCompletion() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = Store().apply { completing = { entered.complete(Unit); release.await() } }
        val p = Provider().apply { script = { emit -> emit(event()); emit(event()); emit(ResponseEvent.Delta("late")) } }
        val vm = vm(store, p, files()); runCurrent(); vm.draft("draw"); vm.generateImage(); entered.await()
        vm.stop(); release.complete(Unit); vm.done()
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage); assertEquals(2, store.saves)
        assertEquals(1, vm.chat.value.messages.count { it.generated != null }); assertEquals(1, p.images)
    }
    @Test fun saveFailureDoesNotPublishCompletedImageOrReplayAndRecoveryCleansOnlyOrphans() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val root = folder.newFolder(); val files = files(root)
        val store = Store().apply { completing = { entered.complete(Unit); release.await(); throw ConversationStorageException() } }
        val p = Provider(); val vm = vm(store, p, files); runCurrent(); vm.draft("draw"); vm.generateImage(); entered.await()
        vm.stop(); release.complete(Unit); vm.done()
        assertEquals(ChatStage.FAILED, vm.chat.value.stage); assertEquals(ApiProblem.CHAT_STORAGE, vm.chat.value.problem)
        assertTrue(store.entry!!.snapshot.replay.isEmpty()); assertTrue(vm.chat.value.messages.none { it.generated != null })
        assertTrue(root.walkTopDown().any { it.isFile }) // Reservation is not canonical ownership.
        files.recover(store); assertTrue(root.walkTopDown().none { it.isFile })
        store.completing = null; vm.draft("explicit retry"); vm.generateImage(); vm.done()
        assertEquals(2, p.images); assertEquals(1, p.inputs.last().size)
    }
    @Test fun lateProviderCallbackAfterStopIsIgnoredAndUserCanExplicitlyRetry() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val p = Provider().apply { script = { emit -> entered.complete(Unit); withContext(NonCancellable) { release.await(); emit(event()) } } }
        val store = Store(); val vm = vm(store, p, files()); runCurrent(); vm.draft("draw"); vm.generateImage(); entered.await(); vm.stop()
        release.complete(Unit); runCurrent(); assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
        assertTrue(store.entry!!.snapshot.replay.isEmpty()); assertEquals(1, p.images)
        p.script = { it(event()) }; vm.draft("explicit retry"); vm.generateImage(); vm.done(); assertEquals(2, p.images)
    }
    @Test fun missingResultInvalidOutputAndFileStorageFailureNeverComplete() = runTest {
        for (case in 0..2) {
            val p = Provider().apply { if (case == 0) script = { it(ResponseEvent.Completed("missing")) } }
            val root = folder.newFolder()
            val f = when (case) {
                1 -> files(root) { throw ApiFailure(ApiProblem.GENERATION_OUTPUT) }
                2 -> { root.delete(); root.writeText("synthetic obstruction"); files(root) }
                else -> files(root)
            }
            val store = Store(); val vm = vm(store, p, f); runCurrent(); vm.draft("draw"); vm.generateImage(); vm.done()
            assertEquals(ChatStage.FAILED, vm.chat.value.stage)
            assertTrue(store.entry!!.snapshot.replay.isEmpty()); assertTrue(vm.chat.value.messages.none { it.generated != null })
            assertEquals(1, p.images); vm.viewModelScope.cancel()
        }
    }
    @Test fun unreadableCanonicalStoragePreservesReservationsAndCleanupKeepsUnknownFiles() = runTest {
        val root = folder.newFolder(); val files = files(root); val store = Store()
        val chat = ChatConversation().apply { draft("synthetic"); begin() }
        val entry = ConversationEntry(owner = store.scope, route = mode.name, model = "gpt-6.1-sol", title = "Synthetic",
            updated = 0, snapshot = chat.snapshot())
        val prepared = files.prepare(GeneratedOutput(byteArrayOf(1)))
        try { files.commit(entry, prepared) { throw ConversationStorageException() }; fail("Expected failed save") }
        catch (_: ConversationStorageException) { }
        val directory = File(File(root, entry.owner), entry.id)
        val unknown = File(directory, "unknown.keep").apply { writeText("synthetic sentinel") }
        File(directory, "${prepared.reference.id}-image.jpg.part").writeBytes(byteArrayOf(1))
        val owned = listOf("${prepared.reference.id}-image.jpg", "${prepared.reference.id}-thumb.jpg", "${prepared.reference.id}-image.jpg.part")
            .map { File(directory, it) }
        assertTrue(owned.all { it.exists() })
        store.unreadable = true; files.recover(store)
        assertTrue(owned.all { it.exists() })
        store.unreadable = false; files.recover(store)
        assertTrue(owned.none { it.exists() }); assertTrue(unknown.isFile)
    }
    @Test fun missingAndTamperedGeneratedFilesFailSafelyWithoutDroppingCanonicalOwnership() = runTest {
        val root = folder.newFolder(); val files = files(root); val store = Store(); val p = Provider()
        val vm = vm(store, p, files); runCurrent(); vm.draft("draw"); vm.generateImage(); vm.done()
        val entry = checkNotNull(store.entry); val reference = checkNotNull(vm.chat.value.messages.last().generated)
        val image = File(File(File(root, entry.owner), entry.id), "${reference.id}-image.jpg")
        image.writeBytes(byteArrayOf(9, 9, 9))
        for (missing in listOf(false, true)) {
            if (missing) check(image.delete())
            try { files.content(entry.owner, entry.id, reference); fail("Expected unavailable image") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.IMAGE_UNAVAILABLE, failure.problem) }
        }
        files.recover(store)
        assertNotNull(store.entry!!.snapshot.messages.last().parts.filterIsInstance<ContentPart.GeneratedImage>().single())
        assertEquals(1, p.images); assertEquals(0, p.texts)
    }
    @Test fun generatedStorageQuotaFailsBeforeCanonicalSaveWithoutEvictingExistingFiles() = runTest {
        val root = folder.newFolder(); val files = files(root); val store = Store()
        val existing = File(root, "existing.keep")
        java.io.RandomAccessFile(existing, "rw").use { it.setLength(GeneratedImages.MAX_TOTAL_BYTES) }
        val chat = ChatConversation().apply { draft("synthetic"); begin() }
        val entry = ConversationEntry(owner = store.scope, route = mode.name, model = "gpt-6.1-sol", title = "Synthetic",
            updated = 0, snapshot = chat.snapshot())
        var saved = false
        try { files.commit(entry, files.prepare(GeneratedOutput(byteArrayOf(1)))) { saved = true }; fail("Expected capacity rejection") }
        catch (_: ConversationStorageException) { }
        assertFalse(saved); assertEquals(GeneratedImages.MAX_TOTAL_BYTES, existing.length())
    }
}
