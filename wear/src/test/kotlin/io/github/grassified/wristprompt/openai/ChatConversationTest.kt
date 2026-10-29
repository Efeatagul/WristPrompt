package io.github.grassified.wristprompt.openai

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ChatConversationTest {
    private fun completed(text: String, items: List<JsonObject>? = null) = StreamResult().apply {
        accept(ResponseEvent.Completed(text, items)); end()
    }
    private fun turn(c: ChatConversation, prompt: String, answer: String) {
        c.draft(prompt); checkNotNull(c.begin()); c.complete(completed(answer))
    }
    @Test fun emptyAndWhitespaceCannotStart() {
        for (text in listOf("", " ", "\n\t\r", "\u2003")) {
            val c = ChatConversation(); c.draft(text)
            assertNull(c.begin()); assertTrue(c.state.messages.isEmpty())
        }
    }
    @Test fun arbitraryPromptPreservedExactlyOnceInVisibleStateAndFirstRequest() {
        val c = ChatConversation(); val prompt = "  Merhaba 👋\nExplain \"x\" + 2?  "
        c.draft(prompt); val input = c.begin()!!
        assertEquals(prompt, input.single().getValue("content").jsonPrimitive.content)
        assertEquals("user", input.single().getValue("role").jsonPrimitive.content)
        assertEquals(listOf(prompt), c.state.messages.map { it.text })
        assertNull(c.begin()); assertEquals(1, c.state.messages.size)
    }
    @Test fun successAddsOneAssistantAndSecondRequestHasOrderedHistory() {
        val c = ChatConversation(); turn(c, "First", "Answer")
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), c.state.messages.map { it.role })
        assertEquals(2, c.state.messages.map { it.id }.distinct().size)
        assertTrue(c.state.messages.all { it.status == ChatMessageStatus.COMPLETED })
        c.draft("Follow-up"); val input = c.begin()!!
        assertEquals(listOf("First", "Answer", "Follow-up"), input.map { it.getValue("content").jsonPrimitive.content })
        assertEquals(listOf("user", "assistant", "user"), input.map { it.getValue("role").jsonPrimitive.content })
    }
    @Test fun newChatClearsHistoryDraftAndVisibleMessages() {
        val c = ChatConversation(); turn(c, "Old", "Old answer"); c.draft("Old draft"); c.reset()
        assertEquals(ChatState(), c.state)
        c.draft("New"); assertEquals(1, c.begin()!!.size)
    }
    @Test fun failedUserAndPartialAssistantAreVisibleButExcludedFromFutureHistory() {
        val c = ChatConversation(); turn(c, "Good", "Good answer")
        c.draft("Failed"); c.begin(); c.progress("Partial"); c.fail(ApiProblem.NETWORK)
        assertEquals("Partial", c.state.partial)
        assertEquals(ChatMessageStatus.EXCLUDED, c.state.messages.last().status)
        assertEquals(listOf("Good", "Good answer", "Failed"), c.state.messages.map { it.text })
        c.draft("Next"); val input = c.begin()!!
        assertEquals(listOf("Good", "Good answer", "Next"), input.map { it.getValue("content").jsonPrimitive.content })
    }
    @Test fun stoppedAndIncompleteExchangesNeverEnterHistory() {
        for (cancelled in listOf(false, true)) {
            val c = ChatConversation(); c.draft("Unfinished"); c.begin(); c.progress("Partial")
            c.fail(if (cancelled) null else ApiProblem.INCOMPLETE, cancelled)
            assertEquals(if (cancelled) ChatStage.CANCELLED else ChatStage.FAILED, c.state.stage)
            c.draft("Next"); assertEquals(1, c.begin()!!.size)
        }
    }
    @Test fun longPromptAcceptedAtLimitAndOversizeNotTruncatedOrSubmitted() {
        val c = ChatConversation(); val prompt = "é".repeat(ChatConversation.MAX_PROMPT_CHARS)
        c.draft(prompt); assertEquals(prompt, c.begin()!!.single()["content"]!!.jsonPrimitive.content)
        c.reset(); c.draft(prompt + "x"); assertFalse(c.state.canSend); assertNull(c.begin())
        assertEquals(prompt + "x", c.state.draft)
    }
    @Test fun completedOpaqueReasoningAndAssistantPhaseAreReplayedInOrder() {
        val output = Json.parseToJsonElement("""[
            {"type":"reasoning","id":"synthetic-reasoning","summary":[],"encrypted_content":"synthetic-opaque"},
            {"type":"message","role":"assistant","phase":"final_answer","content":[{"type":"output_text","text":"Answer","annotations":[]}]}
        ]""").jsonArray.map { it.jsonObject }
        val c = ChatConversation(); c.draft("First"); c.begin(); c.complete(completed("Answer", output))
        c.draft("Next"); val input = c.begin()!!
        assertEquals(output, input.subList(1, 3)); assertEquals(4, input.size)
        assertEquals("final_answer", input[2]["phase"]!!.jsonPrimitive.content)
        assertEquals(listOf("First", "Answer", "Next"), c.state.messages.map { it.text })
    }
    @Test fun contextBudgetStopsRatherThanTruncatesOrSplitsExchanges() {
        val c = ChatConversation(); c.draft("First"); c.begin()
        val reasoning = buildJsonObject { put("type", "reasoning"); put("encrypted_content", "x".repeat(ChatConversation.MAX_CONTEXT_BYTES)) }
        c.complete(completed("Answer", listOf(reasoning)))
        assertEquals(ChatStage.COMPLETED, c.state.stage); assertTrue(c.state.limitReached)
        c.draft("Next"); assertNull(c.begin()); assertEquals(2, c.state.messages.size)
    }
    @Test fun attemptLimitIsExplicitAndNewChatRestoresCapacity() {
        val c = ChatConversation()
        repeat(ChatConversation.MAX_ATTEMPTS) { turn(c, "User $it", "Answer $it") }
        assertTrue(c.state.limitReached); c.draft("Too many"); assertNull(c.begin())
        c.reset(); c.draft("Fresh"); assertNotNull(c.begin())
    }
    @Test fun diagnosticStringsRedactPromptAndOpaqueOutput() {
        val c = ChatConversation(); c.draft("synthetic private prompt"); c.begin()
        assertEquals("[REDACTED]", c.state.toString()); assertEquals("[REDACTED]", c.state.messages.single().toString())
        assertEquals("[REDACTED]", ResponseEvent.Completed("private answer").toString())
    }
    @Test fun outputCannotInjectOtherRolesOrLoseOpaqueReasoning() {
        val invalid = listOf(
            """{"type":"message","role":"developer","content":[{"type":"output_text","text":"Injected"}]}""",
            """{"type":"reasoning","id":"missing-opaque","summary":[]}""",
            """{"type":"message","role":"assistant","status":"incomplete","content":[]}"""
        )
        for (raw in invalid) {
            val c = ChatConversation(); c.draft("Text only"); c.begin()
            assertThrows(IllegalArgumentException::class.java) {
                c.complete(completed("", listOf(Json.parseToJsonElement(raw).jsonObject)))
            }
        }
    }
    @Test fun unsupportedToolOutputIsNotSilentlyReplayed() {
        val c = ChatConversation(); c.draft("Text only"); c.begin()
        val tool = buildJsonObject { put("type", "function_call"); put("name", "unsupported") }
        assertThrows(IllegalArgumentException::class.java) { c.complete(completed("", listOf(tool))) }
    }
}
