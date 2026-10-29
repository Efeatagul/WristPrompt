package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Real encrypted disk, repositories, router and model preferences across transient failures. */
class ConnectivityCredentialTest : ReconstructionFixture() {
    @Test fun inferenceNetworkAndServerFailuresPreserveExactProtectedRecordsInBothModes(): Unit = runBlocking {
        for (mode in CredentialMode.entries) for (problem in listOf(ApiProblem.NETWORK, ApiProblem.INTERRUPTED, ApiProblem.TEMPORARY)) {
            val oauth = Disk("oauth-$mode-$problem"); val api = Disk("api-$mode-$problem"); seed(oauth, api)
            val keys = ApiKeyRepository(api.record()) { time }; keys.load()
            if (mode == CredentialMode.OPENAI_API_KEY) {
                val req = request(); keys.prepare("phone", req)
                keys.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, req.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
                keys.selectMode(mode)
            }
            val catalog = Catalog().apply { answer = { throw ApiFailure(problem) } }
            val r = runtime(oauth, api, catalog); r.start(); r.ready(if (mode == CredentialMode.CHATGPT_PLAN) terra else "api-model")
            val beforeOauth = oauth.snapshot(); val beforeApi = api.record().read()
            val writes = oauth.writes.get() to api.writes.get(); val lease = r.router.current()
            withContext(main) { r.vm.draft("A"); r.vm.send() }
            withTimeout(5000) { r.vm.chat.first { it.stage == ChatStage.FAILED } }
            assertEquals(problem, r.vm.chat.value.problem); assertFalse(r.p.state.value.reauthorizationRequired)
            assertArrayEquals(beforeOauth, oauth.snapshot()); assertArrayEquals(beforeApi, api.record().read())
            assertEquals(writes, oauth.writes.get() to api.writes.get()); assertEquals(mode, r.router.state.value.mode)
            catalog.answer = { it(ResponseEvent.Completed("Recovered")) }
            withContext(main) { assertEquals(1, catalog.generations.get()); r.vm.draft("B"); r.vm.send() }
            withTimeout(5000) { r.vm.chat.first { it.stage == ChatStage.COMPLETED } }
            assertEquals(2, catalog.generations.get()); assertEquals(1, catalog.inputs.last().size)
            assertEquals(lease.accessToken, r.router.current().accessToken)
            assertArrayEquals(beforeOauth, oauth.snapshot()); assertArrayEquals(beforeApi, api.record().read())
            r.close()
        }
    }

    @Test fun planDiscoveryNetworkFailurePreservesCredentialsAndPreferenceUntilExplicitReload(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val catalog = Catalog().apply { before = { throw ApiFailure(ApiProblem.NETWORK) } }
        val r = runtime(oauth, api, catalog); val before = oauth.snapshot(); val writes = oauth.writes.get()
        r.start(); withTimeout(5000) { r.vm.state.first { it.stage == ProofStage.FAILED } }
        assertEquals(ApiProblem.NETWORK, r.vm.state.value.problem); assertTrue(r.vm.state.value.models.isEmpty())
        assertArrayEquals(before, oauth.snapshot()); assertEquals(writes, oauth.writes.get())
        assertEquals(terra, r.p.selectedModel("client_one")); assertFalse(r.p.state.value.reauthorizationRequired)
        catalog.before = {}; catalog.order = listOf(terra, sol)
        withContext(main) { assertEquals(1, catalog.calls.get()); r.vm.loadModels() }
        r.ready(terra); assertEquals(2, catalog.calls.get()); assertEquals(0, catalog.generations.get())
        assertArrayEquals(before, oauth.snapshot()); assertEquals(writes, oauth.writes.get())
    }
}
