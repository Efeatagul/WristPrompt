package io.github.grassified.wristprompt.ui

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhysicalRemediationTest {
    @Test fun completionSummaryWithoutRepeatedOutputKeepsStreamAndReplay() {
        val c = ChatConversation(); c.draft("Question"); c.begin()
        val result = StreamResult()
        for (part in listOf("**Canonical** ", "answer.")) {
            result.accept(ResponseEvent.Delta(part)); c.progress(result.output)
        }
        result.accept(ResponseEvents.decode(null,
            """{"type":"response.completed","response":{"status":"completed","output":[]}}"""))
        c.complete(result)
        assertEquals("**Canonical** answer.", c.state.messages.last().text)
        assertEquals("Canonical answer.", MarkdownProjection().project(c.state, PlainTextProjection().project(c.state)).rows.last().text)
        c.draft("Next"); assertEquals(3, c.begin()!!.size)
    }

    @Test fun liveOwnerKeepsCanonicalCompletedAnswerThroughReconciliation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val conversation = ChatConversation()
        val canonical = MutableStateFlow(conversation.state)
        val owner = ChatPresentationOwner(canonical, dispatcher = StandardTestDispatcher(testScheduler))
        val observed = mutableListOf<ChatPresentation>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.state.collect { observed.add(it) } }
        try {
            owner.visible(true); runCurrent()
            conversation.draft("Question"); conversation.begin(); canonical.value = conversation.state; runCurrent()
            val result = StreamResult()
            for (delta in listOf("# Answer\n\n", "**Bold** and ".repeat(120), "*italic*.")) {
                result.accept(ResponseEvent.Delta(delta)); conversation.progress(result.output)
                canonical.value = conversation.state; advanceTimeBy(110); runCurrent()
                assertTrue(owner.state.value.rows.any { it.type == TranscriptType.TAIL })
            }
            observed.clear()
            result.accept(ResponseEvent.Completed("# Final\n\n**Canonical** answer."))
            conversation.complete(result); canonical.value = conversation.state; runCurrent()
            assertEquals("# Final\n\n**Canonical** answer.", conversation.state.messages.last().text)
            assertEquals(1, conversation.state.messages.count { it.role == ChatRole.ASSISTANT })
            assertTrue(observed.isNotEmpty())
            assertTrue(observed.all { p -> p.rows.any { it.type == TranscriptType.ASSISTANT && it.text.isNotEmpty() } })
            assertEquals(listOf("Final", "Canonical answer."), owner.state.value.rows.filter { it.markdown != null }.map { it.text })
            conversation.draft("Next"); assertEquals(3, conversation.begin()!!.size)
        } finally { owner.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun sparseCompletionThroughSessionViewModelAndOwnerNeverPublishesBlank() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val source = "# Heading\n\nA **bold** and *italic* paragraph with `inline`.\n\n" +
            "1. Ordered\n2. Second\n\n- Bullet\n\n> Quote\n\n---\n\n```kt\ncode()\n```\n\n|Name|Value|\n|---|---|\n|Row|Cell|"
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("model", "Model"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat only")
            override suspend fun stream(credential: CredentialLease, model: String, input: List<kotlinx.serialization.json.JsonObject>,
                onEvent: suspend (ResponseEvent) -> Unit) {
                requests++
                for (delta in source.chunked(40)) { onEvent(ResponseEvent.Delta(delta)); delay(110) }
                gate.await()
                onEvent(ResponseEvents.decode(null, """{"type":"response.completed","response":{"status":"completed","output":[]}}"""))
            }
        }
        val vm = InferenceViewModel(MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true)),
            InferenceSession({ CredentialLease("synthetic", 0, "synthetic", Long.MAX_VALUE) }, provider) { true })
        val owner = ChatPresentationOwner(vm.chat, dispatcher = StandardTestDispatcher(testScheduler))
        val publications = mutableListOf<ChatPresentation>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.state.collect { publications.add(it) } }
        try {
            runCurrent(); owner.visible(true)
            vm.draft("Question"); vm.send(); runCurrent(); advanceTimeBy(2000); runCurrent()
            assertEquals(source, vm.chat.value.partial)
            assertTrue(owner.state.value.rows.any { it.type == TranscriptType.TAIL })
            assertTrue(owner.state.value.rows.none { it.markdown != null })
            publications.clear(); gate.complete(Unit); runCurrent()
            assertEquals(ChatStage.COMPLETED, vm.chat.value.stage)
            assertEquals("", vm.chat.value.partial)
            assertEquals(source, vm.chat.value.messages.single { it.role == ChatRole.ASSISTANT }.text)
            assertTrue(publications.isNotEmpty())
            assertTrue(publications.all { it.rows.any { row -> row.type == TranscriptType.ASSISTANT && row.text.isNotEmpty() } })
            val blocks = owner.state.value.rows.mapNotNull { it.markdown }
            assertEquals(MarkdownProjection().blocks(source), blocks)
            assertTrue(blocks.map { it.kind }.containsAll(listOf(MarkdownKind.PARAGRAPH, MarkdownKind.HEADING,
                MarkdownKind.LIST_ITEM, MarkdownKind.QUOTE, MarkdownKind.RULE, MarkdownKind.CODE, MarkdownKind.TABLE_RECORD)))
            assertEquals(1, owner.state.value.rows.count { it.type == TranscriptType.USER })
            assertEquals(1, requests)
            vm.draft("Next"); runCurrent()
            assertEquals(blocks, owner.state.value.rows.mapNotNull { it.markdown })
            assertEquals(1, requests)
        } finally { owner.viewModelScope.cancel(); vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun oneLogicalParagraphHasOnlyOneBlockStartAndRealParagraphsKeepBoundaries() {
        val sentence = "One continuous sentence with no newline ".repeat(200)
        val blocks = MarkdownProjection().blocks(sentence)
        assertTrue(blocks.size > 1)
        assertEquals(1, blocks.count { !it.continuation })
        assertEquals(sentence.trimEnd(), blocks.joinToString("") { it.inline.text })
        val shell = shellTextSegments(sentence)
        assertTrue(shell.size > 1)
        assertEquals(1, shell.count { !it.continuation })
        assertEquals(sentence, shell.joinToString("") { it.text })
        val two = sentence + "\n\nSecond paragraph."
        assertEquals(2, MarkdownProjection().blocks(two).count { !it.continuation })
        assertEquals(2, shellTextSegments(two).count { !it.continuation })
        assertEquals(two, shellTextSegments(two).joinToString("") { it.text })
    }

    @Test fun stoppedAndInterruptedRetainMarkdownAndNeverEnterReplay() {
        for (cancelled in listOf(true, false)) {
            val c = ChatConversation(); c.draft("Question"); c.begin(); c.progress("**Partial** answer")
            c.fail(if (cancelled) null else ApiProblem.INTERRUPTED, cancelled)
            val display = MarkdownProjection().project(c.state, PlainTextProjection().project(c.state))
            assertEquals("Partial answer", display.rows.single { it.markdown != null }.text)
            assertEquals(ChatMessageStatus.EXCLUDED, c.state.messages.single().status)
            assertTrue(display.rows.any { it.type == TranscriptType.STATUS && it.text.contains(EXCLUDED_RESPONSE) })
            c.draft("Next"); assertEquals(1, c.begin()!!.size)
        }
    }

    @Test fun authoritativeReplacementAndExplicitEmptyTextAreDifferentFromMissingOutput() {
        for (replacement in listOf("Canonical replacement", "")) {
            val json = """{"type":"response.completed","response":{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"$replacement"}]}]}}"""
            val event = ResponseEvents.decode(null, json) as ResponseEvent.Completed
            assertEquals(replacement, event.finalText)
            val result = StreamResult(); result.accept(ResponseEvent.Delta("preview")); result.accept(event)
            assertEquals(replacement, result.output)
            assertEquals(1, result.outputItems!!.size)
        }
    }

    @Test fun parserProducingNoVisibleBlocksKeepsCanonicalSourceInsteadOfErasingIt() {
        val source = "[reference]: https://example.invalid"
        assertEquals(source, MarkdownProjection().blocks(source).joinToString("") { it.inline.text })
        val emptyParser = MarkdownProjection(parse = { org.commonmark.node.Document() })
        assertEquals("canonical", emptyParser.blocks("canonical").single().inline.text)
    }

    @Test fun staleMarkdownCannotEraseOrOverwriteCurrentCompletedAttempt() = runTest {
        val user = ChatMessage(1, ChatRole.USER, "Old")
        var current = ChatState(listOf(user, ChatMessage(2, ChatRole.ASSISTANT, "**old**")), stage = ChatStage.COMPLETED)
        val newest = ChatState(listOf(ChatMessage(3, ChatRole.USER, "New"), ChatMessage(4, ChatRole.ASSISTANT, "**current**")), stage = ChatStage.COMPLETED)
        val publications = mutableListOf<ChatPresentation>()
        val projection = MarkdownProjection(parse = {
            if (it == "**old**") current = newest
            org.commonmark.parser.Parser.builder().build().parse(it)
        })
        val worker = MarkdownWorker(backgroundScope, { current }, publications::add, StandardTestDispatcher(testScheduler), projection)
        val plain = PlainTextProjection()
        worker.accept(current, plain.project(current)); runCurrent()
        assertTrue(publications.isEmpty())
        val immediate = worker.accept(current, plain.project(current))
        assertEquals("**current**", immediate.rows.last().text)
        runCurrent()
        assertEquals("current", publications.single().rows.last().text)
        assertEquals(1, publications.single().rows.count { it.type == TranscriptType.ASSISTANT })
    }

}
