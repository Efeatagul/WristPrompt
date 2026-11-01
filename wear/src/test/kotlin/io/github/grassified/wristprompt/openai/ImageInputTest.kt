package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.images.*
import io.github.grassified.wristprompt.storage.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import java.util.UUID

class ImageInputTest {
    private val model = "gpt-6.1-sol"
    private val text = "  Exact \"text\"\nç 👋  "
    private fun result() = StreamResult().apply { accept(ResponseEvent.Completed("answer")); end() }
    private fun entry(snapshot: ConversationSnapshot) = ConversationEntry(owner = UUID.randomUUID().toString(),
        route = "CHATGPT_PLAN", model = model, title = "Synthetic", updated = 1, snapshot = snapshot)
    @Test fun deterministicZeroOneAndMultipleSerializationForBothRoutes() = runTest {
        for (mode in CredentialMode.entries) for (count in listOf(0, 1, 3, 6)) {
            val ids = List(count) { ImageLimits.newId() }
            val bytes = ids.mapIndexed { index, id -> id to byteArrayOf(index.toByte(), 2, 3) }.toMap()
            val local = listOf(ImageInput.user(text, ids))
            val input = ImageInput.materialize(local) { bytes.getValue(it) }
            val body = ResponsesProtocol.request(model, input, mode = mode)
            val root = Json.parseToJsonElement(body).jsonObject
            assertEquals(false, root.getValue("store").jsonPrimitive.boolean)
            assertEquals(true, root.getValue("stream").jsonPrimitive.boolean)
            assertFalse(root.containsKey("previous_response_id"))
            val content = root.getValue("input").jsonArray.single().jsonObject.getValue("content")
            if (count == 0) assertEquals(text, content.jsonPrimitive.content) else {
                assertEquals(count + 1, content.jsonArray.size)
                assertEquals(text, content.jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content)
                content.jsonArray.drop(1).forEachIndexed { index, part ->
                    val obj = part.jsonObject
                    assertEquals(setOf("type", "image_url"), obj.keys)
                    assertEquals("input_image", obj.getValue("type").jsonPrimitive.content)
                    // Never print encoded bodies/URLs in failed assertions.
                    assertTrue("Ordered normalized data URL", obj.getValue("image_url").jsonPrimitive.content ==
                        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.getValue(ids[index])))
                }
            }
            assertTrue("Deterministic request", body == ResponsesProtocol.request(model, input, mode = mode))
        }
    }
    @Test fun imagesOnlyIsAnExplicitlyRejectedProductPolicy() {
        assertThrows(IllegalArgumentException::class.java) { ImageInput.user(" \n", listOf(ImageLimits.newId())) }
    }
    @Test fun capabilityPolicyUsesExactDocumentedIdsForEachRouteAndNoNameInference() {
        for (mode in CredentialMode.entries) {
            assertTrue(OpenAiImageCapabilities.supports(model, mode))
            for (unknown in listOf("gpt-6.1-sol-unknown", "sol", "vision", "gpt-6.1-sol-2026-11-01")) {
                assertFalse(OpenAiImageCapabilities.supports(unknown, mode))
                val failure = assertThrows(ApiFailure::class.java) {
                    ResponsesProtocol.request(unknown, listOf(ImageInput.user(text, listOf(ImageLimits.newId()))), mode = mode)
                }
                assertEquals(ApiProblem.IMAGES_UNSUPPORTED, failure.problem); assertTrue(failure.requestNotSent)
                assertTrue(ResponsesProtocol.request(unknown, listOf(ImageInput.user(text)), mode = mode).isNotEmpty())
            }
        }
    }
    @Test fun localReferencesCannotEscapeToHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            ResponsesProtocol.request(model, listOf(ImageInput.user(text, listOf(ImageLimits.newId()))))
        }
    }
    @Test fun completedOneAndMultipleTurnsRoundTripWithoutBinaryAndReplayInOrder() = runTest {
        for (count in listOf(1, 3)) {
            val ids = List(count) { ImageLimits.newId() }
            val chat = ChatConversation().apply { draft(text); begin(ids); complete(result()) }
            val (body, replay) = ConversationCodec.encode(entry(chat.snapshot()))
            assertFalse(body.contains("base64")); assertFalse(replay.contains("base64")); assertFalse(replay.contains("image_url"))
            val restored = ChatConversation().apply { restore(ConversationCodec.decode(body, replay)) }
            assertEquals(ids, restored.state.messages.first().attachments)
            restored.draft("next")
            val input = restored.begin()!!
            assertEquals(ids, ImageInput.ids(input.first())); assertEquals(3, input.size)
            val resolved = ImageInput.materialize(input) { byteArrayOf(1, 2, 3) }
            assertEquals(count + 1, resolved.first().getValue("content").jsonArray.size)
        }
    }
    @Test fun textOnlyV1AndV2MigrationRemainUnchanged() {
        val chat = ChatConversation().apply { draft(text); begin(); complete(result()) }
        for (version in listOf(1, 2)) {
            val old = chat.snapshot().copy(version = version)
            val (body, replay) = ConversationCodec.encode(entry(old))
            val restored = ChatConversation().apply { restore(ConversationCodec.decode(body, replay)); draft("next") }
            assertEquals(text, restored.begin()!!.first().getValue("content").jsonPrimitive.content)
        }
    }
    @Test fun stoppedFailedAndInterruptedAttachmentsRemainExcludedAfterRestart() {
        for (terminal in listOf("stop", "fail", "interrupt")) {
            val chat = ChatConversation().apply { draft(text); begin(listOf(ImageLimits.newId())); progress("partial") }
            if (terminal != "interrupt") chat.fail(ApiProblem.NETWORK, terminal == "stop")
            val (body, replay) = ConversationCodec.encode(entry(chat.snapshot()))
            val restored = ChatConversation().apply { restore(ConversationCodec.decode(body, replay)); draft("next") }
            assertEquals(1, restored.begin()!!.size)
            assertEquals(ChatMessageStatus.EXCLUDED, restored.state.messages.first().status)
        }
    }
    @Test fun countBudgetIncludesAllReplayedOccurrencesAndDoesNotTruncate() = runTest {
        var reads = 0
        val id = ImageLimits.newId()
        val input = List(7) { ImageInput.user(text, listOf(id)) }
        try { ImageInput.materialize(input) { reads++; byteArrayOf(1) }; fail("Expected count failure") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.IMAGE_LIMIT, failure.problem) }
        assertEquals(0, reads)
        assertThrows(IllegalArgumentException::class.java) { ImageInput.user(text, List(7) { ImageLimits.newId() }) }
    }
    @Test fun maximumNormalizedFilesFitEncodedBoundAndOversizeFailsBeforeEncoding() = runTest {
        val ids = List(6) { ImageLimits.newId() }
        val input = ImageInput.materialize(listOf(ImageInput.user(text, ids))) { ByteArray(ImageLimits.MAX_IMAGE_BYTES) }
        assertTrue(ResponsesProtocol.request(model, input).toByteArray().size <= ImageInput.MAX_REQUEST_BYTES)
        try { ImageInput.materialize(listOf(ImageInput.user(text, ids.take(1)))) { ByteArray(ImageLimits.MAX_IMAGE_BYTES + 1) }; fail("Expected size failure") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.IMAGE_UNAVAILABLE, failure.problem); assertNull(failure.cause) }
    }
    @Test fun archiveSurvivesLibraryDeletionRestartAndLibraryOrphanCleanup() = runTest {
        val directory = Files.createTempDirectory("synthetic-image-replay").toFile()
        try {
            val library = ImageFileStore(directory.resolve("library"))
            val ids = library.importBatch(3) { ImageContent(byteArrayOf(it.toByte(), 2), byteArrayOf(3), 1, 1) }.records.map { it.id }
            val root = directory.resolve("replay"); val chat = ImageLimits.newId()
            fun archive() = ConversationImages(root, library) { record, content -> ImageWire.validateContent(record, content); content }
            val local = listOf(ImageInput.user(text, ids))
            val before = archive().prepare(chat, ids, local)
            ids.forEach { library.delete(it) }; library.load()
            val after = archive().prepare(chat, emptyList(), local)
            assertTrue("Replay bytes survive library deletion", before == after)
            try { archive().prepare(chat, ids, local); fail("Deleted selection must fail") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.IMAGE_UNAVAILABLE, failure.problem) }
            archive().delete(chat)
            assertFalse(root.resolve(chat).exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun missingCorruptIntegrityMismatchedAndOversizedFilesFailSanitized() = runTest {
        for (damage in listOf("missing", "corrupt", "oversize")) {
            val directory = Files.createTempDirectory("synthetic-image-damage").toFile()
            try {
                val library = ImageFileStore(directory.resolve("library"))
                val id = library.importBatch(1) { ImageContent(byteArrayOf(1, 2), byteArrayOf(3), 1, 1) }.records.single().id
                val archive = ConversationImages(directory.resolve("replay"), library) { record, content -> ImageWire.validateContent(record, content); content }
                val file = directory.resolve("library/$id-1-image.jpg")
                when (damage) {
                    "missing" -> file.delete()
                    "corrupt" -> file.writeBytes(byteArrayOf(5, 6)) // Same size, incorrect integrity.
                    else -> file.writeBytes(ByteArray(ImageLimits.MAX_IMAGE_BYTES + 1))
                }
                try { archive.prepare(ImageLimits.newId(), listOf(id), listOf(ImageInput.user(text, listOf(id)))); fail("Expected file rejection") }
                catch (failure: ApiFailure) {
                    assertEquals(ApiProblem.IMAGE_UNAVAILABLE, failure.problem); assertNull(failure.cause)
                    assertFalse(failure.message.orEmpty().contains(directory.path))
                }
            } finally { directory.deleteRecursively() }
        }
    }
    @Test fun missingReplayManifestPreservesRecoverableFilesAndFailsClosed() = runTest {
        val directory = Files.createTempDirectory("synthetic-missing-manifest").toFile()
        try {
            val library = ImageFileStore(directory.resolve("library"))
            val id = library.importBatch(1) { ImageContent(byteArrayOf(1), byteArrayOf(2), 1, 1) }.records.single().id
            val root = directory.resolve("replay"); val chat = ImageLimits.newId()
            fun archive() = ConversationImages(root, library) { record, content -> ImageWire.validateContent(record, content); content }
            val user = ImageInput.user(text, listOf(id))
            archive().prepare(chat, listOf(id), listOf(user))
            root.resolve("$chat/catalog.json").delete()
            val continuation = listOf(user, buildJsonObject { put("role", "assistant"); put("content", "answer") }, ImageInput.user("next"))
            try { archive().prepare(chat, emptyList(), continuation); fail("Expected missing metadata rejection") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.IMAGE_UNAVAILABLE, failure.problem) }
            assertTrue(root.resolve("$chat/$id-1-image.jpg").isFile)
        } finally { directory.deleteRecursively() }
    }
    @Test fun missingRequiredReplayFileNeverFallsBackToLibraryOrSilentlyDropsPart() = runTest {
        val directory = Files.createTempDirectory("synthetic-required-replay").toFile()
        try {
            val library = ImageFileStore(directory.resolve("library"))
            val id = library.importBatch(1) { ImageContent(byteArrayOf(1), byteArrayOf(2), 1, 1) }.records.single().id
            val root = directory.resolve("replay"); val chat = ImageLimits.newId()
            val archive = ConversationImages(root, library) { record, content -> ImageWire.validateContent(record, content); content }
            val input = listOf(ImageInput.user(text, listOf(id)))
            archive.prepare(chat, listOf(id), input)
            root.resolve("$chat/$id-1-image.jpg").delete()
            try { archive.prepare(chat, emptyList(), input); fail("Expected missing replay rejection") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.IMAGE_UNAVAILABLE, failure.problem) }
        } finally { directory.deleteRecursively() }
    }
}
