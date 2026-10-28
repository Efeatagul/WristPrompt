package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RefreshUpgradeTest {
    private class Record(var bytes: ByteArray? = null) : SecretRecord {
        var writes = 0
        override fun read() = bytes?.clone()
        override fun write(plaintext: ByteArray) { bytes = plaintext.clone(); writes++ }
    }
    // Pre-D2-2 vault schema: no newly required locally-derived timing fields.
    private fun oldRecord(profile: CredentialProfile, omitHint: Boolean): Record {
        val fields = ProfileProtocol.json.encodeToJsonElement(CredentialProfile.serializer(), profile).jsonObject.toMutableMap()
        if (omitHint) fields.remove("earliestRefreshAt")
        val root = buildJsonObject {
            put("version", 1); put("generation", 7)
            put("profiles", JsonArray(listOf(JsonObject(fields))))
            put("selectedClientId", profile.identity.clientId)
            put("selectedModels", buildJsonObject { put(profile.identity.clientId, "saved-model") })
        }
        return Record(root.toString().encodeToByteArray())
    }
    private fun renewed(p: CredentialProfile, now: Long) = RefreshProtocol.update(p,
        """{"access_token":"synthetic-successor-access","refresh_token":"synthetic-successor-refresh","token_type":"Bearer","expires_in":3600}""", now)

    @Test fun absentOptionalHintReconstructsAndDiscoversAndInfersWithoutReauthorization(): Unit = runBlocking {
        for (time in listOf(4_000_000L, 4_540_000L, 4_600_000L)) {
            val p = testProfile(); val record = oldRecord(p, omitHint = true)
            assertFalse(record.bytes!!.decodeToString().contains("earliestRefreshAt"))
            val before = record.bytes!!.clone()
            val profiles = WatchProfiles(record) { time }; profiles.load()
            val keys = ApiKeyRepository(Record()) { time }; keys.load()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                var refreshes = 0
                val router = CredentialRouter(profiles, keys, { old -> refreshes++; renewed(old, time) }, scope)
                val provider = object : AiProvider {
                    override suspend fun models(credential: CredentialLease): List<AiModel> {
                        assertEquals(CredentialMode.CHATGPT_PLAN, credential.mode)
                        assertEquals(p.identity.clientId, credential.clientId)
                        return listOf(AiModel("saved-model", "Saved"))
                    }
                    override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) {
                        assertEquals(CredentialMode.CHATGPT_PLAN, credential.mode)
                        onEvent(ResponseEvent.Delta(ResponsesProtocol.EXPECTED_TEXT))
                        onEvent(ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT))
                    }
                }
                val session = InferenceSession(router, provider, router::isCurrent)
                val catalog = session.catalog()
                assertEquals("saved-model", router.selectedModel(catalog.credential))
                assertTrue(session.test(catalog, "saved-model", {}, {}).proofPassed)
                assertEquals(if (time < 4_540_000) 0 else 1, refreshes)
                assertFalse(profiles.state.value.reauthorizationRequired)
                assertEquals(p.identity.clientId, profiles.state.value.activeClientId)
                assertEquals(CredentialMode.CHATGPT_PLAN, keys.snapshot().mode)
                if (refreshes == 0) { assertEquals(0, record.writes); assertArrayEquals(before, record.bytes) }
                val restored = WatchProfiles(record) { time }; restored.load()
                val r = restored.credential { error("Durably renewed session must not rotate twice") }
                assertEquals(p.identity.clientId, r.clientId)
                assertEquals(if (refreshes == 0) p.accessToken else "synthetic-successor-access", r.accessToken)
            } finally { scope.cancel() }
        }
    }
    @Test fun legacyNonNullSupportedHintsNoLongerBlockAnExpiredSession(): Unit = runBlocking {
        val time = 4_600_000L
        for (hint in listOf(JsonPrimitive(4000), JsonPrimitive("1970-01-01T01:06:40Z"))) {
            val p = testProfile().copy(earliestRefreshAt = hint)
            val record = oldRecord(p, omitHint = false)
            val profiles = WatchProfiles(record) { time }; profiles.load()
            var calls = 0
            val lease = profiles.credential { calls++; renewed(it, time) }
            assertEquals(1, calls); assertEquals("synthetic-successor-access", lease.accessToken)
            assertEquals(p.identity.clientId, lease.clientId)
            assertTrue(profiles.state.value.sessionRenewed)
            assertFalse(profiles.state.value.reauthorizationRequired)
        }
    }
    @Test fun futureConstraintNeverSendsEarlyAndUsesValidBearerUntilExpiry(): Unit = runBlocking {
        for (hint in listOf(JsonPrimitive(4700), JsonPrimitive("1970-01-01T01:18:20Z"))) {
            var time = 4_540_000L
            val p = testProfile().copy(earliestRefreshAt = hint); val record = oldRecord(p, false)
            val profiles = WatchProfiles(record) { time }; profiles.load(); val before = record.bytes!!.clone()
            assertEquals(p.accessToken, profiles.credential { error("No early refresh") }.accessToken)
            time = 4_600_000
            try { profiles.credential { error("No prohibited refresh") }; fail("Wait for earliest") }
            catch (f: ApiFailure) {
                assertEquals(ApiProblem.REFRESH_NOT_READY, f.problem)
                assertEquals(ProtocolDetail.REFRESH_NOT_READY, f.protocolDetail)
                assertEquals("[REDACTED]", f.toString()); assertNull(f.cause)
            }
            assertEquals(0, record.writes); assertArrayEquals(before, record.bytes)
            assertFalse(profiles.state.value.reauthorizationRequired)
            time = 4_700_000
            assertEquals("synthetic-successor-access", profiles.credential { renewed(it, time) }.accessToken)
        }
    }
    @Test fun malformedHintsStayConservativeAndNeverWriteIntentOrDiscardTokens(): Unit = runBlocking {
        for (hint in listOf(JsonPrimitive(true), JsonPrimitive("synthetic-opaque-metadata"), JsonObject(emptyMap()),
            JsonPrimitive(-1), JsonPrimitive(1e100))) {
            var time = 4_550_000L
            val p = testProfile().copy(earliestRefreshAt = hint); val record = oldRecord(p, false)
            val profiles = WatchProfiles(record) { time }; profiles.load(); val before = record.bytes!!.clone()
            assertEquals(p.accessToken, profiles.credential { error("Unknown hint: do not refresh early") }.accessToken)
            time = 4_600_000
            try { profiles.credential { error("Unknown hint: no refresh") }; fail("Unsafe timing") }
            catch (f: ApiFailure) {
                assertEquals(ProtocolDetail.REFRESH_TIMING_UNDOCUMENTED, f.protocolDetail)
                assertFalse(f.message.orEmpty().contains("synthetic-opaque")); assertEquals("[REDACTED]", f.toString())
            }
            assertEquals(0, record.writes); assertArrayEquals(before, record.bytes)
            assertFalse(profiles.state.value.reauthorizationRequired)
        }
    }
    @Test fun timingBoundariesRoundUpAndDoNotGuessMillisecondsOrStringNumbers() {
        val p = testProfile()
        val fractional = p.copy(earliestRefreshAt = JsonPrimitive(4540.0001))
        assertFalse(TokenExpiry.needsRefresh(fractional, 4_540_000))
        assertTrue(TokenExpiry.needsRefresh(fractional, 4_540_001))
        val iso = p.copy(earliestRefreshAt = JsonPrimitive("1970-01-01T01:15:40.000000001Z"))
        assertFalse(TokenExpiry.needsRefresh(iso, 4_540_000)); assertTrue(TokenExpiry.needsRefresh(iso, 4_540_001))
        // A huge numeric value is seconds per the official SDK, never guessed as ms.
        assertThrows(ApiFailure::class.java) { TokenExpiry.needsRefresh(p.copy(earliestRefreshAt = JsonPrimitive(4_700_000)), 4_600_000) }
        assertThrows(ApiFailure::class.java) { TokenExpiry.needsRefresh(p.copy(earliestRefreshAt = JsonPrimitive("4700")), 4_600_000) }
    }
    @Test fun insufficientExpiryAndUncertainRotationAreNotCompatibilityBypasses(): Unit = runBlocking {
        assertThrows(ApiFailure::class.java) { TokenExpiry.needsRefresh(testProfile().copy(expiresIn = null), 4_600_000) }
        val p = testProfile(); val record = oldRecord(p, true)
        val root = Json.parseToJsonElement(record.bytes!!.decodeToString()).jsonObject
        record.bytes = JsonObject(root + ("refreshPending" to JsonArray(listOf(JsonPrimitive(p.identity.clientId))))).toString().encodeToByteArray()
        val profiles = WatchProfiles(record) { 4_600_000L }; profiles.load()
        try { profiles.credential { error("Must never replay uncertain rotation") }; fail("Uncertain grant") }
        catch (f: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, f.problem) }
        assertEquals(0, record.writes)
    }
}
