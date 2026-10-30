package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.openai.*
import org.junit.Assert.*
import org.junit.Test

class ShellPresentationTest {
    private val account = WatchAccountState(
        accounts = listOf(AccountLabel("synthetic-client", "Test account · abcdef")),
        activeClientId = "synthetic-client", connected = true, planEnabled = true,
    )
    private val key = ApiKeyState(loaded = true, configured = true)
    private val proof = ProofState(stage = ProofStage.READY,
        models = listOf(AiModel("synthetic-model", "Test model")), selectedSlug = "synthetic-model")
    private fun present(a: WatchAccountState = account, k: ApiKeyState = key, p: ProofState = proof,
        bridge: WatchAuthState = WatchAuthState.IDLE, changing: Boolean = false) =
        shellPresentation(a, k, p, bridge, changing)

    @Test fun planSummaryIncludesIdentityModelAndEligibleBilling() {
        val ui = present()
        assertEquals("ChatGPT plan", ui.billing)
        assertEquals("Eligible plan usage", ui.billingNote)
        assertEquals("Test account · abcdef", ui.account)
        assertEquals("Test model", ui.model)
        assertEquals("Ready for proof", ui.status)
        assertTrue(ui.canTest)
        assertTrue(ui.showSignOut)
        assertTrue(ui.showManageUsage)
        assertTrue(ui.showRemoveKey)
    }

    @Test fun apiSummaryDoesNotImplyPlanBillingOrHideIndependentAccountSettings() {
        val ui = present(k = key.copy(mode = CredentialMode.OPENAI_API_KEY),
            p = proof.copy(mode = CredentialMode.OPENAI_API_KEY))
        assertEquals("OpenAI API", ui.billing)
        assertEquals("Separate API billing", ui.billingNote)
        assertNull(ui.account)
        assertEquals("Test model", ui.model)
        assertTrue(ui.canTest)
        assertTrue(ui.showSignOut)
        assertTrue(ui.showManageUsage)
        assertTrue(ui.showRemoveKey)
    }

    @Test fun unconfiguredApiNeverOffersProofUsingAvailableChatGpt() {
        val ui = present(k = key.copy(mode = CredentialMode.OPENAI_API_KEY, configured = false),
            p = proof.copy(mode = CredentialMode.OPENAI_API_KEY))
        assertFalse(ui.proofAvailable)
        assertFalse(ui.canTest)
        assertFalse(ui.showRemoveKey)
        assertEquals("Set up an API key to continue", ui.status)
    }

    @Test fun stopRemainsReachableEvenDuringConnectionTransitionOrError() {
        for (stage in listOf(ProofStage.LOADING, ProofStage.STREAMING)) {
            val ui = present(k = key.copy(storageError = true), p = proof.copy(stage = stage), changing = true)
            assertTrue(ui.showStop)
            assertFalse(ui.canTest)
            assertEquals("Secure storage unavailable", ui.status)
        }
        val stopped = present(p = proof.copy(stage = ProofStage.CANCELLED, output = "partial"))
        assertFalse(stopped.showStop)
        assertTrue(stopped.showOutput)
        assertTrue(present(a = account.copy(planWelcome = true), p = proof.copy(stage = ProofStage.STREAMING)).showStop)
        assertEquals("Stopped · output is partial", stopped.status)
    }

    @Test fun completionMismatchIncompleteAndInterruptedStayDistinct() {
        val states = listOf(
            proof.copy(stage = ProofStage.SUCCEEDED), proof.copy(stage = ProofStage.OUTPUT_MISMATCH),
            proof.copy(stage = ProofStage.CANCELLED),
            proof.copy(stage = ProofStage.FAILED, problem = ApiProblem.INCOMPLETE),
            proof.copy(stage = ProofStage.FAILED, problem = ApiProblem.INTERRUPTED),
        )
        assertEquals(states.size, states.map { present(p = it).status }.distinct().size)
        assertEquals("Response incomplete", present(p = states[3]).status)
        assertEquals("Response interrupted", present(p = states[4]).status)
    }

    @Test fun everyProblemHasConciseDistinctSummaryWithoutProviderPayload() {
        val summaries = ApiProblem.entries.map(::problemSummary)
        assertTrue(summaries.all { it.isNotBlank() })
        assertTrue(summaries.all { it.length <= 60 && '\n' !in it })
        assertEquals("Account temporarily unavailable", problemSummary(ApiProblem.REFRESH_NOT_READY))
        assertEquals("API key rejected", problemSummary(ApiProblem.INVALID_API_KEY))
        assertEquals("ChatGPT usage unavailable", problemSummary(ApiProblem.USAGE_UNAVAILABLE))
    }

    @Test fun signedOutNeverLooksUsableAndRemoteOutcomeRemainsHonest() {
        for (result in RevocationResult.entries) {
            val ui = present(a = account.copy(connected = false, signedOut = true,
                reauthorizationRequired = true, revocation = result))
            assertFalse(ui.canTest)
            assertFalse(ui.showSignOut)
            assertTrue(ui.showContinue)
            assertTrue(ui.canContinue)
            assertTrue(ui.status.contains(if (result == RevocationResult.CONFIRMED) "revocation confirmed" else "revocation unconfirmed"))
        }
    }

    @Test fun retainedIdentityAndCatalogAreHiddenDuringSignOutAndAuthenticationFailure() {
        for (a in listOf(
            account.copy(signingOut = true),
            account.copy(connected = false, signedOut = true, reauthorizationRequired = true),
            account.copy(reauthorizationRequired = true),
            account.copy(storageError = true),
        )) {
            val ui = present(a = a)
            assertNull(ui.account)
            assertEquals("Not selected", ui.model)
            assertFalse(ui.canTest)
            assertFalse(ui.proofAvailable)
        }
    }

    @Test fun failedAndExpiredSetupHaveVisibleWatchRetryPath() {
        for (a in listOf(account.copy(connected = false, failed = true), account.copy(connected = false, expired = true))) {
            val ui = present(a = a)
            assertTrue(ui.status.contains("retry from watch"))
            assertTrue(ui.showContinue)
            assertTrue(ui.canContinue)
        }
        assertFalse(present(a = account.copy(connected = false), bridge = WatchAuthState.LOOKING_FOR_PHONE).canContinue)
    }

    @Test fun storageAndScopeFailuresCannotLookReadyOrOfferInference() {
        for (a in listOf(account.copy(storageError = true), account.copy(planEnabled = false), account.copy(signingOut = true))) {
            val ui = present(a = a)
            assertFalse(ui.proofAvailable)
            assertFalse(ui.canTest)
            assertNotEquals("Ready for proof", ui.status)
        }
        val unloaded = present(k = key.copy(loaded = false))
        assertEquals("Loading billing mode…", unloaded.billing)
        assertEquals("Requests blocked", unloaded.billingNote)
        assertFalse(unloaded.canTest)
        assertEquals("Billing mode unavailable", present(k = key.copy(storageError = true)).billing)
    }

    @Test fun mismatchedModeCannotDisplayPreviousCatalogOrEnableProof() {
        val ui = present(k = key.copy(mode = CredentialMode.OPENAI_API_KEY), p = proof.copy(output = "old plan output"))
        assertFalse(ui.showOutput)
        assertEquals("Not selected", ui.model)
        assertFalse(ui.proofAvailable)
        assertFalse(ui.canTest)
    }

    @Test fun outputRowsPreserveTextAndUnicodeExactly() {
        val text = "Line one\n\n" + "word ".repeat(60) + "x".repeat(79) + "😀" + "tail".repeat(80)
        val chunks = readableChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 80 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
        assertTrue(readableChunks("").isEmpty())
    }
}
