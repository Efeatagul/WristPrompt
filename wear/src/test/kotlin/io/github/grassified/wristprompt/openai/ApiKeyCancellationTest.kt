package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiKeyCancellationTest {
    private class Record : SecretRecord {
        var bytes: ByteArray? = null
        override fun read() = bytes?.clone()
        override fun write(plaintext: ByteArray) { bytes = plaintext.clone() }
    }
    private suspend fun keys(): ApiKeyRepository {
        val k = ApiKeyRepository(Record()) { 1000 }
        val r = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), 1000)
        k.prepare("phone", r); k.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, r.requestId, BridgeProtocol.newRequestId(), r.hostId, "synthetic-user-key")))
        k.selectMode(CredentialMode.OPENAI_API_KEY); return k
    }
    @Test fun removalCancelsApiDiscoveryAndBlocksNewLeases() = runTest {
        val k = keys(); val started = CompletableDeferred<Unit>()
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> { started.complete(Unit); awaitCancellation() }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val session = InferenceSession(k::credential, provider, k::current)
        val job = launch { session.catalog() }; started.await()
        session.cancel(ApiKeyRepository.API_CLIENT); k.remove(); job.join(); assertTrue(job.isCancelled)
        try { session.catalog(); fail("Removed key") } catch (f: ApiFailure) { assertEquals(ApiProblem.API_KEY_MISSING, f.problem) }
    }
    @Test fun removedOrSwitchedLeaseCannotPublishLateStreamCompletion() = runTest {
        for (remove in listOf(true, false)) {
            val k = keys(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val provider = object : AiProvider {
                override suspend fun models(credential: CredentialLease) = listOf(AiModel("api-model", "api-model"))
                override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) {
                    started.complete(Unit); release.await(); onEvent(ResponseEvent.Completed(ResponsesProtocol.API_EXPECTED_TEXT))
                }
            }
            val session = InferenceSession(k::credential, provider, k::current); val catalog = session.catalog()
            var progress = 0; var failure: ApiFailure? = null
            val job = launch { try { session.test(catalog, "api-model", {}, { progress++ }) } catch (f: ApiFailure) { failure = f } }
            started.await()
            if (remove) k.remove() else k.selectMode(CredentialMode.CHATGPT_PLAN)
            release.complete(Unit); job.join()
            assertEquals(0, progress); assertEquals(ApiProblem.STALE_ACCOUNT, failure?.problem)
        }
    }
    @Test fun chatGptCancellationDoesNotCancelApiWork() = runTest {
        val k = keys(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> { started.complete(Unit); release.await(); return listOf(AiModel("api-model", "API")) }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val session = InferenceSession(k::credential, provider, k::current)
        val result = async { session.catalog() }; started.await(); session.cancel("oaiapp_test"); release.complete(Unit)
        assertEquals("api-model", result.await().models.single().slug)
    }
}
