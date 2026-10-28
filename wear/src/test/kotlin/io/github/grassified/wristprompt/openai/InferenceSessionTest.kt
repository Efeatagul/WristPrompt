package io.github.grassified.wristprompt.openai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InferenceSessionTest {
    private class Provider : AiProvider {
        val catalogTokens = mutableListOf<String>(); val inferenceTokens = mutableListOf<String>()
        var models = listOf(AiModel("slug", "Model name"))
        var events = listOf<ResponseEvent>(ResponseEvent.Delta(ResponsesProtocol.EXPECTED_TEXT), ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT))
        override suspend fun models(credential: CredentialLease): List<AiModel> { catalogTokens += credential.accessToken; return models }
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) {
            inferenceTokens += credential.accessToken
            events.forEach { onEvent(it) }
        }
    }
    @Test fun discoveryAndInferenceUseSameCredentialAndOnlyExactCompletedStreamPasses() = runBlocking {
        val provider = Provider(); val lease = CredentialLease("client", 1, "token-a", 1000)
        val session = InferenceSession({ lease }, provider) { true }
        val result = session.test(session.catalog(), "slug", {}, {})
        assertTrue(result.proofPassed)
        assertEquals(provider.catalogTokens, provider.inferenceTokens)
    }
    @Test fun refreshForcesFreshDiscoveryBeforeInference() = runBlocking {
        val provider = Provider(); var lease = CredentialLease("client", 1, "old", 1000)
        val session = InferenceSession({ lease }, provider) { true }
        val catalog = session.catalog(); lease = CredentialLease("client", 1, "new", 2000)
        session.test(catalog, "slug", {}, {})
        assertEquals(listOf("old", "new"), provider.catalogTokens)
        assertEquals(listOf("new"), provider.inferenceTokens)
    }
    @Test fun missingSelectedModelAfterRefreshNeverPosts() = runBlocking {
        val provider = Provider(); var lease = CredentialLease("client", 1, "old", 1000)
        val session = InferenceSession({ lease }, provider) { true }; val catalog = session.catalog()
        provider.models = listOf(AiModel("different", "Different")); lease = CredentialLease("client", 1, "new", 2000)
        try { session.test(catalog, "slug", {}, {}); fail("Expected missing model") } catch (_: ApiFailure) { }
        assertTrue(provider.inferenceTokens.isEmpty())
    }
    @Test fun emptyCatalogAndChangedAccountFailWithoutGeneration() = runBlocking {
        val provider = Provider(); val lease = CredentialLease("client", 1, "token", 1000)
        val session = InferenceSession({ lease }, provider) { false }
        try { session.catalog(); fail("Expected stale account") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.STALE_ACCOUNT, failure.problem) }
        provider.models = emptyList()
        try { InferenceSession({ lease }, provider) { true }.catalog(); fail("Expected empty catalog") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.NO_MODELS, failure.problem) }
        assertTrue(provider.inferenceTokens.isEmpty())
    }
    @Test fun partialOutputThenEofIsInterruptedEvenWhenTextMatches() = runBlocking {
        val provider = Provider(); provider.events = listOf(ResponseEvent.Delta(ResponsesProtocol.EXPECTED_TEXT))
        val session = InferenceSession({ CredentialLease("client", 1, "token", 1000) }, provider) { true }
        var partial = ""
        try { session.test(session.catalog(), "slug", {}, { partial = it.output }); fail("Expected interruption") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.INTERRUPTED, failure.problem) }
        assertEquals(ResponsesProtocol.EXPECTED_TEXT, partial)
    }
}
