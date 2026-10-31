package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.*
import org.junit.Assert.*
import org.junit.Test

class ErrorPresentationTest {
    @Test fun chatSaveFailureDoesNotSuggestCredentialRecoveryEvenAfterStop() {
        val error = chatError(ChatState(stage = ChatStage.CANCELLED, problem = ApiProblem.CHAT_STORAGE))!!
        assertEquals("Chat could not be saved", error.title)
        assertEquals(RecoveryAction.DIAGNOSTICS, error.action)
        assertFalse(error.text.contains("Sign in")); assertFalse(error.text.contains("API key"))
    }
    @Test fun largeFontAccountRecoveryRemainsRecognizableWithoutChangingItsAction() {
        for (problem in listOf(ApiProblem.REAUTHORIZE, ApiProblem.INVALID_API_KEY, ApiProblem.API_LIMIT)) {
            val action = errorPresentation(problem).action!!
            assertEquals(RecoveryAction.ACCOUNT, action)
            assertEquals("Account", action.primaryLabel(true))
            assertEquals("Account & Connection", action.primaryLabel(false))
        }
        for (action in RecoveryAction.entries.filter { it != RecoveryAction.ACCOUNT })
            assertEquals(action.label, action.primaryLabel(true))
    }
    private fun failure(problem: ApiProblem, partial: String = "") = ChatState(
        messages = listOf(ChatMessage(1, ChatRole.USER, "q", ChatMessageStatus.EXCLUDED)),
        stage = ChatStage.FAILED, problem = problem, partial = partial)

    @Test fun everyKnownCategoryHasBoundedSafeCopyAndAtMostOneAction() {
        ApiProblem.entries.forEach {
            val error = errorPresentation(it)
            assertTrue(error.title.length in 1..60)
            assertTrue(error.text.length < 180)
            assertFalse(error.text.contains("HTTP"))
            assertFalse(error.text.contains("refresh", ignoreCase = true))
            assertFalse(error.text.contains("parser", ignoreCase = true))
            assertFalse(error.text.contains("token", ignoreCase = true))
            assertFalse(error.text.contains("fallback", ignoreCase = true))
        }
    }
    @Test fun completedHasNoCompletionOrRecoveryRow() {
        val chat = ChatState(messages = listOf(ChatMessage(1, ChatRole.USER, "q"),
            ChatMessage(2, ChatRole.ASSISTANT, "**exact**")), stage = ChatStage.COMPLETED)
        val plain = PlainTextProjection().project(chat)
        assertNull(terminalText(chat)); assertNull(plain.recovery)
        assertTrue(plain.rows.none { it.type in setOf(TranscriptType.STATUS, TranscriptType.RECOVERY) })
    }
    @Test fun stoppedInterruptedIncompletePreserveExactPartialAndExclusion() {
        for ((stage, problem, title) in listOf(Triple(ChatStage.CANCELLED, null, "Response stopped"),
            Triple(ChatStage.FAILED, ApiProblem.INTERRUPTED, "Response interrupted"),
            Triple(ChatStage.FAILED, ApiProblem.INCOMPLETE, "Response incomplete"))) {
            val chat = failure(problem ?: ApiProblem.NETWORK, "**partial\n").copy(stage = stage, problem = problem)
            val projected = PlainTextProjection().project(chat)
            assertEquals("**partial\n", projected.rows.filter { it.type == TranscriptType.ASSISTANT }.joinToString("") { it.text })
            assertEquals("$title\n$EXCLUDED_RESPONSE", terminalText(chat))
            assertEquals(RecoveryAction.ASK, projected.recovery)
        }
    }
    @Test fun networkBeforeOutputIsStandaloneWithoutEmptyAnswerOrPartialClaim() {
        val result = PlainTextProjection().project(failure(ApiProblem.NETWORK))
        assertEquals("Couldn't connect\nCheck the watch's internet connection.", result.rows.last().text)
        assertTrue(result.rows.none { it.type == TranscriptType.ASSISTANT })
        assertEquals(RecoveryAction.ASK, result.recovery)
    }
    @Test fun serviceFailureDoesNotPromiseRecovery() {
        assertEquals("Service unavailable\nTry again with a new message later.", errorPresentation(ApiProblem.TEMPORARY).text)
    }
    @Test fun reauthAndKeyRejectionUseAccountWithoutOAuthOrBillingMutation() {
        assertEquals("Sign in again to continue", errorPresentation(ApiProblem.REAUTHORIZE).title)
        assertEquals("API key rejected\nCheck your OpenAI API key.", errorPresentation(ApiProblem.INVALID_API_KEY).text)
        for (problem in listOf(ApiProblem.REAUTHORIZE, ApiProblem.INVALID_API_KEY, ApiProblem.API_KEY_MISSING))
            assertEquals(RecoveryAction.ACCOUNT, errorPresentation(problem).action)
    }
    @Test fun planAndApiLimitsStayDistinct() {
        assertEquals("ChatGPT usage limit reached", errorPresentation(ApiProblem.USAGE_LIMIT).title)
        assertEquals(RecoveryAction.USAGE, errorPresentation(ApiProblem.USAGE_LIMIT).action)
        assertEquals("API usage unavailable", errorPresentation(ApiProblem.API_LIMIT).title)
        assertEquals(RecoveryAction.ACCOUNT, errorPresentation(ApiProblem.API_LIMIT).action)
        assertFalse(errorPresentation(ApiProblem.API_LIMIT).text.contains("ChatGPT"))
    }
    @Test fun modelFailureAndLoadingOnlyDescribeExplicitRecovery() {
        assertEquals(RecoveryAction.MODELS, errorPresentation(ApiProblem.NO_MODELS).action)
        val loading = modelCatalogPresentation(ProofState(stage = ProofStage.LOADING))!!
        assertEquals("Loading models…", loading.title); assertNull(loading.action)
        val failed = modelCatalogPresentation(ProofState(stage = ProofStage.FAILED, problem = ApiProblem.NETWORK))!!
        assertEquals("Couldn't load models", failed.title); assertEquals(RecoveryAction.MODELS, failed.action)
    }
    @Test fun storageFailureOffersDiagnosticsRatherThanFakeRepair() {
        val error = errorPresentation(ApiProblem.STORAGE)
        assertEquals("Secure storage unavailable\nRequests are blocked to protect your account.", error.text)
        assertEquals(RecoveryAction.DIAGNOSTICS, error.action)
    }
    @Test fun conversationLimitOffersExplicitResetWithoutLosingAnswer() {
        val chat = ChatState(messages = listOf(ChatMessage(1, ChatRole.ASSISTANT, "answer")),
            stage = ChatStage.COMPLETED, limitReached = true)
        val result = PlainTextProjection().project(chat)
        assertEquals("Start a new chat to continue", result.rows.last().text)
        assertEquals("answer", result.rows.first().text)
        assertEquals(RecoveryAction.NEW_CHAT, result.recovery)
    }
    @Test fun markdownFallbackPreservesCompletedSuccessAndHasNoErrorUX() {
        val source = "**exact**\nsource"
        val chat = ChatState(messages = listOf(ChatMessage(1, ChatRole.USER, "q"),
            ChatMessage(2, ChatRole.ASSISTANT, source)), stage = ChatStage.COMPLETED)
        val result = MarkdownProjection(parse = { error("private diagnostic") })
            .project(chat, PlainTextProjection().project(chat))
        assertEquals(source, result.rows.filter { it.type == TranscriptType.ASSISTANT }.joinToString("") { it.text })
        assertEquals(ChatStage.COMPLETED, result.stage); assertNull(result.recovery)
        assertTrue(result.rows.none { it.type == TranscriptType.STATUS })
    }
    @Test fun markdownTerminalMetadataRemainsSeparateFromFormatting() {
        for (problem in listOf(ApiProblem.INTERRUPTED, ApiProblem.INCOMPLETE)) {
            val chat = failure(problem, "**partial**")
            val result = MarkdownProjection().project(chat, PlainTextProjection().project(chat))
            assertEquals("partial", result.rows.first { it.markdown != null }.text)
            assertEquals(errorPresentation(problem).text, result.rows.last().text)
            assertEquals(RecoveryAction.ASK, result.recovery)
        }
    }
    @Test fun reauthAccountRowHidesPreviousAuthenticatedLabel() {
        val account = io.github.grassified.wristprompt.auth.AccountLabel("synthetic", "Previous identity")
        val state = io.github.grassified.wristprompt.auth.WatchAccountState(
            accounts = listOf(account), activeClientId = "synthetic", reauthorizationRequired = true)
        assertEquals("Sign-in required", accountRowName(state, account))
    }
    @Test fun signedOutSetupHasOneAccountActionAndNoIdentity() {
        val ui = shellPresentation(io.github.grassified.wristprompt.auth.WatchAccountState(),
            io.github.grassified.wristprompt.auth.ApiKeyState(loaded = true), ProofState(),
            io.github.grassified.wristprompt.bridge.WatchAuthState.IDLE, false)
        assertEquals("Sign in to continue", ui.recovery?.title)
        assertEquals(RecoveryAction.ACCOUNT, ui.recovery?.action)
        assertNull(ui.account); assertFalse(ui.proofAvailable)
    }
    @Test fun mappingIsSideEffectFreeAndDoesNotChangeDomainState() {
        val chat = failure(ApiProblem.NO_MODELS, "partial")
        repeat(100) { PlainTextProjection().project(chat); errorPresentation(ApiProblem.API_LIMIT) }
        assertEquals(ChatStage.FAILED, chat.stage); assertEquals("partial", chat.partial)
        assertEquals(ApiProblem.NO_MODELS, chat.problem)
        assertEquals(RecoveryAction.MODELS, chatError(chat)?.action)
    }
}
