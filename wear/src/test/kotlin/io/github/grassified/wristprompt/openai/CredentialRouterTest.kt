package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialRouterTest {
    private class Record : SecretRecord {
        var bytes: ByteArray? = null
        override fun read() = bytes?.clone()
        override fun write(plaintext: ByteArray) { bytes = plaintext.clone() }
    }
    private val time = 1_000_000L
    private val host = BridgeProtocol.newHostId()
    private fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, time)
    private suspend fun addKey(keys: ApiKeyRepository) {
        val r = request(); keys.prepare("phone", r)
        keys.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, r.requestId, BridgeProtocol.newRequestId(), host, "synthetic-user-key")))
    }
    private suspend fun addPlan(p: WatchProfiles) {
        val r = request(); p.prepare("phone", r)
        p.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, r.requestId, BridgeProtocol.newRequestId(), CredentialProfile(
            RegistrationIdentity("oaiapp_test", OpenAiAuth.ISSUER, "subject"), host, "Test", null, false,
            "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", 3600, time, OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)))))
    }
    @Test fun bothCredentialsCoexistButOnlyExplicitModeRoutesAndSurvivesRecreation() = runTest {
        val oauthRecord = Record(); val keyRecord = Record()
        val p = WatchProfiles(oauthRecord) { time }; val k = ApiKeyRepository(keyRecord) { time }
        addPlan(p); addKey(k)
        var refreshes = 0
        val router = CredentialRouter(p, k, { refreshes++; error("No refresh expected") }, backgroundScope)
        assertEquals("synthetic-access", router.current().accessToken)
        val old = router.current(); val oauthBytes = oauthRecord.bytes!!.clone()
        k.selectMode(CredentialMode.OPENAI_API_KEY)
        assertFalse(router.isCurrent(old)); assertEquals("synthetic-user-key", router.current().accessToken)
        assertEquals(0, refreshes); assertArrayEquals(oauthBytes, oauthRecord.bytes)
        val recreated = ApiKeyRepository(keyRecord) { time }; recreated.load()
        assertEquals(CredentialMode.OPENAI_API_KEY, recreated.state.value.mode)
        k.remove(); assertArrayEquals(oauthBytes, oauthRecord.bytes)
        try { router.current(); fail("No fallback") } catch (f: ApiFailure) { assertEquals(ApiProblem.API_KEY_MISSING, f.problem) }
        assertEquals(CredentialMode.OPENAI_API_KEY, k.snapshot().mode)
        k.selectMode(CredentialMode.CHATGPT_PLAN); assertEquals("synthetic-access", router.current().accessToken)
    }
    @Test fun signedOutPlanNeverFallsBackAndPlanSignOutDoesNotDeleteApiKey() = runTest {
        val p = WatchProfiles(Record()) { time }; val k = ApiKeyRepository(Record()) { time }; addKey(k); k.load(); p.load()
        val router = CredentialRouter(p, k, { error("No refresh") }, backgroundScope)
        try { router.current(); fail("No fallback") } catch (_: ApiFailure) { }
        assertTrue(k.snapshot().configured); assertEquals(CredentialMode.CHATGPT_PLAN, k.state.value.mode)
        addPlan(p)
        val keyLeaseMode = k.snapshot().mode
        val keyBefore = k.state.value.keyRevision
        p.signOut { RevocationResult.CONFIRMED }
        assertTrue(k.snapshot().configured); assertEquals(keyBefore, k.state.value.keyRevision); assertEquals(keyLeaseMode, k.state.value.mode)
        k.selectMode(CredentialMode.OPENAI_API_KEY); assertEquals("synthetic-user-key", router.current().accessToken)
    }
    @Test fun modeSwitchInvalidatesEvenWhenReturningToSameCredential() = runTest {
        val p = WatchProfiles(Record()) { time }; val k = ApiKeyRepository(Record()) { time }; addKey(k)
        k.selectMode(CredentialMode.OPENAI_API_KEY)
        val router = CredentialRouter(p, k, { error("No OAuth") }, backgroundScope)
        val lease = router.current(); k.selectMode(CredentialMode.CHATGPT_PLAN); k.selectMode(CredentialMode.OPENAI_API_KEY)
        assertFalse(router.isCurrent(lease)); assertTrue(router.isCurrent(router.current()))
    }
    @Test fun planAndApiFailuresNeverUseTheOtherCredentialOrMutateEitherRecord() = runTest {
        val oauth = Record(); val api = Record()
        val p = WatchProfiles(oauth) { time }; val k = ApiKeyRepository(api) { time }
        addPlan(p); addKey(k)
        val router = CredentialRouter(p, k, { error("Fresh synthetic session") }, backgroundScope)
        val failures = listOf(
            401 to "subscription_sharing_invalid_user", 403 to "subscription_sharing_user_not_eligible",
            429 to "subscription_sharing_usage_limit_exceeded", 503 to "subscription_sharing_usage_unavailable",
            400 to "subscription_sharing_unsupported_capability", 403 to "subscription_sharing_route_not_supported",
            503 to "subscription_sharing_user_unavailable", 401 to "invalid_api_key", 429 to "insufficient_quota",
        )
        for (mode in CredentialMode.entries) {
            k.selectMode(mode)
            val oauthBefore = oauth.bytes!!.clone(); val apiBefore = api.bytes!!.clone()
            for ((status, code) in failures) {
                val used = mutableListOf<CredentialMode>()
                val provider = object : AiProvider {
                    override suspend fun models(credential: CredentialLease): List<AiModel> {
                        used += credential.mode; return listOf(AiModel("model", "Model"))
                    }
                    override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) {
                        used += credential.mode
                        assertEquals(if (mode == CredentialMode.CHATGPT_PLAN) "synthetic-access" else "synthetic-user-key", credential.accessToken)
                        throw ApiErrors.forMode(ApiErrors.parse(status, """{"error":{"code":"$code"}}"""), credential.mode)
                    }
                }
                val session = InferenceSession(router, provider, router::isCurrent)
                try { session.test(session.catalog(), "model", {}, {}); fail("Expected failure") } catch (_: ApiFailure) { }
                assertEquals(listOf(mode, mode), used)
                assertEquals(mode, k.snapshot().mode)
                assertArrayEquals(oauthBefore, oauth.bytes); assertArrayEquals(apiBefore, api.bytes)
            }
        }
    }

}
