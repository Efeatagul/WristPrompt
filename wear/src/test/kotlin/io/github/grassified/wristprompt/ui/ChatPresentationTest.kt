package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.*
import org.junit.Assert.*
import org.junit.Test

class ChatPresentationTest {
    private fun complete(text: String, role: ChatRole = ChatRole.ASSISTANT) = ChatState(
        messages = if (role == ChatRole.USER) listOf(ChatMessage(1, role, text)) else
            listOf(ChatMessage(1, ChatRole.USER, "Q"), ChatMessage(2, role, text)), stage = ChatStage.COMPLETED)
    private fun streaming(text: String) = ChatState(messages = listOf(ChatMessage(1, ChatRole.USER, "Q")),
        partial = text, stage = ChatStage.RECEIVING)
    private fun answer(p: ChatPresentation) = p.rows.filter { it.type == TranscriptType.ASSISTANT || it.type == TranscriptType.TAIL }
    private fun restored(rows: List<TranscriptRow>) = rows.joinToString("") { it.text }
    @Test fun assistantPreservesExactWhitespaceAndOrdering() {
        val text = "  First\tline\r\n\nSecond\n  third  "
        assertEquals(text, restored(answer(PlainTextProjection(PresentationMetrics()).project(complete(text)))))
    }
    @Test fun userPreservesExactTextAndUsesCompactType() {
        val text = "  user\ttext\n".repeat(700)
        val rows = PlainTextProjection(PresentationMetrics()).project(complete(text, ChatRole.USER)).rows
        assertEquals(text, restored(rows)); assertTrue(rows.all { it.type == TranscriptType.USER })
        assertEquals(1, rows.count { it.exchangeStart })
    }
    @Test fun eightyCharacterChunkingIsGone() {
        assertEquals(1, answer(PlainTextProjection(PresentationMetrics()).project(complete("a".repeat(400)))).size)
    }
    @Test fun newlinesArePrimaryBoundariesWithoutInsertedBreaks() {
        val text = "one\n\ntwo\r\nthree"
        assertEquals(listOf("one\n\ntwo\r\n", "three"), answer(PlainTextProjection(PresentationMetrics()).project(complete(text))).map { it.text })
    }
    @Test fun longParagraphIsBoundedFlatAndLossless() {
        val text = "Long paragraph text ".repeat(3000)
        val rows = answer(PlainTextProjection(PresentationMetrics()).project(complete(text)))
        assertEquals(text, restored(rows)); assertTrue(rows.all { it.text.length <= PlainTextProjection.SEGMENT_CHARS })
        assertTrue(rows.all { it.type == TranscriptType.ASSISTANT && !it.exchangeStart })
    }
    @Test fun unbrokenTokenIsBoundedAndLossless() {
        val text = "https://" + "aB9_".repeat(16_000)
        val rows = answer(PlainTextProjection(PresentationMetrics()).project(complete(text)))
        assertEquals(text, restored(rows)); assertTrue(rows.all { it.text.length <= 512 })
    }
    @Test fun unicodeCombiningFlagsAndZwjSequencesAreNotSplit() {
        for (unit in listOf("e\u0301", "👨‍👩‍👧‍👦", "🇹🇷", "👍🏽", "İstanbul العربية 中文 😀 ")) {
            val text = unit.repeat(700)
            val rows = answer(PlainTextProjection(PresentationMetrics()).project(complete(text)))
            assertEquals(text, restored(rows))
            if (!unit.contains(' ')) assertTrue("unsafe split for $unit", rows.all { it.text.length % unit.length == 0 })
            assertTrue(rows.none { it.text.first().isLowSurrogate() || it.text.last().isHighSurrogate() })
        }
    }
    @Test fun unusuallyLongSingleGraphemeIsKeptWhole() {
        val text = "a" + "\u0301".repeat(1000) + "z"
        assertEquals(text, restored(answer(PlainTextProjection(PresentationMetrics()).project(complete(text)))))
    }
    @Test fun settledRowsAndKeysSurviveDeltasByReference() {
        val p = PlainTextProjection(PresentationMetrics()); val base = streaming("one\n" + "a".repeat(600))
        val first = p.project(base); val next = p.project(base.copy(partial = base.partial + "more"))
        assertSame(answer(first).first(), answer(next).first())
        assertEquals(answer(first).map { it.key }, answer(next).map { it.key })
        assertEquals(TranscriptType.TAIL, answer(next).last().type)
    }
    @Test fun completedHistoryIsNotResegmentedForDeltas() {
        val p = PlainTextProjection(PresentationMetrics()); val history = complete("history").messages + ChatMessage(3, ChatRole.USER, "next")
        val base = ChatState(history, stage = ChatStage.RECEIVING, partial = "first")
        val first = p.project(base); val before = p.segmentations
        val next = p.project(base.copy(partial = "first second"))
        assertSame(first.rows[1], next.rows[1]); assertEquals(before + 1, p.segmentations)
    }
    @Test fun completedAnswerKeepsKeysAndFinalCanonicalTextWins() {
        val p = PlainTextProjection(PresentationMetrics()); val base = streaming("one\n" + "a".repeat(600))
        val before = p.project(base)
        val final = p.project(complete(base.partial + " final"))
        assertEquals(answer(before).first().key, answer(final).first().key)
        assertEquals(base.partial + " final", restored(answer(final)))
        assertEquals(ChatStage.COMPLETED, final.stage)
        assertFalse(final.rows.any { it.type == TranscriptType.STATUS })
    }
    @Test fun correctedFinalReplacesEveryOldSegment() {
        val p = PlainTextProjection(PresentationMetrics()); p.project(streaming("old\n".repeat(1000)))
        assertEquals("corrected", restored(answer(p.project(complete("corrected")))))
    }
    @Test fun separateAnswersNeverHaveDuplicateKeys() {
        val p = PlainTextProjection(PresentationMetrics()).project(ChatState(listOf(ChatMessage(1, ChatRole.USER,"one"),
            ChatMessage(2, ChatRole.ASSISTANT,"first"), ChatMessage(3, ChatRole.USER,"two"), ChatMessage(4, ChatRole.ASSISTANT,"second"))))
        assertEquals(p.rows.size, p.rows.map { it.key }.toSet().size)
    }
    @Test fun stoppedInterruptedIncompleteAndFailureKeepExclusionSemantics() {
        for ((stage, problem, label) in listOf(Triple(ChatStage.CANCELLED, null, "Response stopped"),
            Triple(ChatStage.FAILED, ApiProblem.INTERRUPTED, "Response interrupted"),
            Triple(ChatStage.FAILED, ApiProblem.INCOMPLETE, "Response incomplete"),
            Triple(ChatStage.FAILED, ApiProblem.NETWORK, ""))) {
            val p = PlainTextProjection(PresentationMetrics()).project(streaming("partial").copy(stage = stage, problem = problem))
            assertEquals("partial", restored(answer(p)))
            val status = p.rows.single { it.type == TranscriptType.STATUS }.text
            assertTrue(status.startsWith(label)); assertTrue(status.contains("Not used for future replies."))
            assertTrue(p.rows.any { it.type == TranscriptType.RECOVERY })
        }
    }
    @Test fun resetReleasesCacheAndSettledTail() {
        val p = PlainTextProjection(PresentationMetrics())
        repeat(100) { p.project(complete("a".repeat(65000))); p.project(streaming("b\n".repeat(500))); p.project(ChatState())
            assertEquals(0, p.cachedBytes) }
    }
    @Test fun completedCacheStaysWithinBudgetWithoutChangingHistory() {
        val messages = (1L..12L).map { ChatMessage(it, ChatRole.ASSISTANT,"x".repeat(20000)) }
        val p = PlainTextProjection(PresentationMetrics()); val output = p.project(ChatState(messages))
        assertTrue(p.cachedBytes <= PlainTextProjection.CACHE_BYTES)
        assertEquals(240000, output.rows.sumOf { it.text.length }); assertEquals(12, messages.size)
    }
    @Test fun latestOnlyAppearsWhenContentExistsBelowAndHasValidTarget() {
        assertFalse(latestAvailable(false,true)); assertFalse(latestAvailable(true,false)); assertTrue(latestAvailable(true,true))
        assertEquals(19, latestTarget(20)); assertEquals(0, latestTarget(0))
        assertEquals(PrimaryAction.STOP, primaryAction(Surface.CHAT, true, false))
    }
    @Test fun logicalAnchorSurvivesInsertedRowsAndFallsBackSafely() {
        val position = ChatReadingPosition("answer-1-4", 8, -17)
        assertEquals(3, restoreChatIndex(position,listOf("header","new","user","answer-1-4","tail")))
        assertEquals(0, restoreChatIndex(position,emptyList()))
        val nav = ShellNavigationViewModel(); nav.chatPosition = position
        nav.open(Surface.MODELS); nav.back(); assertEquals(position,nav.chatPosition)
    }
    @Test fun historicalExcludedUserRemainsLabelledAfterANewAttempt() {
        val chat = ChatState(listOf(ChatMessage(1, ChatRole.USER, "old", ChatMessageStatus.EXCLUDED),
            ChatMessage(2, ChatRole.USER, "new", ChatMessageStatus.PENDING)), stage = ChatStage.STARTING)
        val rows = PlainTextProjection().project(chat).rows
        assertTrue(rows.any { it.key == "excluded-1" && it.text.contains("not used for future replies") })
        assertFalse(rows.any { it.key == "excluded-2" })
    }

    @Test fun newlineLayoutUsesRowBoundaryAndRetainsFinalTrailingNewlineAndCarriageReturn() {
        val rows = answer(PlainTextProjection().project(complete("one\n\ntwo\n")))
        assertEquals(listOf("one\n\ntwo\n"), rows.map(::transcriptDisplayText))
        val carriage = answer(PlainTextProjection().project(complete("one\r"))).single()
        assertEquals("one\r", transcriptDisplayText(carriage))
    }

    @Test fun terminalReconciliationPreservesSettledParagraphBoundaries() {
        val p = PlainTextProjection()
        val first = streaming("first paragraph\nunfinished")
        val before = answer(p.project(first))
        val final = answer(p.project(complete(first.partial + " final")))
        assertEquals(before.first().key, final.first().key)
        assertEquals(before.first().text, final.first().text)
        assertEquals(first.partial + " final", restored(final))
    }
    @Test fun largeCompletedHistoryIsNotReprocessedDuringStreaming() {
        val p = PlainTextProjection(PresentationMetrics())
        val history = (1L..12L).map { ChatMessage(it, ChatRole.ASSISTANT, "x".repeat(20000)) } + ChatMessage(13, ChatRole.USER, "next")
        val state = ChatState(history, stage = ChatStage.RECEIVING, partial = "first")
        val before = p.project(state); val count = p.segmentations
        val after = p.project(state.copy(partial = "first second"))
        assertEquals(count + 1, p.segmentations); assertSame(before.rows[0], after.rows[0])
        assertTrue(p.cachedBytes <= PlainTextProjection.CACHE_BYTES)
    }

}
