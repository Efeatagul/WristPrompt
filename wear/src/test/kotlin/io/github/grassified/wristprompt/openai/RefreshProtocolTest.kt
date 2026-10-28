package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import org.junit.Assert.*
import org.junit.Test

internal fun testProfile(now: Long = 1_000_000L) = CredentialProfile(
    RegistrationIdentity("oaiapp_test", OpenAiAuth.ISSUER, "subject"), BridgeProtocol.newHostId(), "Test", null, false,
    "verified-id-hint", "synthetic-access", "synthetic-refresh", "Bearer", 3600, now,
    OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES))

class RefreshProtocolTest {
    @Test fun expiryUsesMarginAndExactBoundaryAndRejectsBackwardClock() {
        val p = testProfile()
        assertFalse(TokenExpiry.needsRefresh(p, 4_539_999))
        assertTrue(TokenExpiry.needsRefresh(p, 4_540_000))
        assertTrue(TokenExpiry.needsRefresh(p, 4_600_000))
        assertThrows(ApiFailure::class.java) { TokenExpiry.needsRefresh(p, 1) }
    }
    @Test fun rotationReplacesAllTokenStateWhilePreservingVerifiedRegistration() {
        val old = testProfile()
        val next = RefreshProtocol.update(old, """{"access_token":"new-access","refresh_token":"new-refresh","token_type":"Bearer","expires_in":3600,"scope":"openid resource.invoke chatgpt.tokens.use.direct offline_access","id_token":"untrusted-unused-id"}""", 4_550_000)
        assertEquals("new-access", next.accessToken); assertEquals("new-refresh", next.refreshToken)
        assertEquals(old.identity, next.identity); assertEquals(old.hostId, next.hostId)
        assertEquals(old.idToken, next.idToken); assertEquals(8_150_000L, next.expiresAtEpochMs)
        assertTrue(next.planUsageEnabled); assertEquals(4, next.scopes.size)
        assertEquals("[REDACTED]", next.toString())
    }
    @Test fun omittedScopeRetainsGrantButExplicitScopeRemovalDisablesPlan() {
        val body = """{"access_token":"new-access","refresh_token":"new-refresh","token_type":"Bearer","expires_in":3600}"""
        assertEquals(testProfile().scopes, RefreshProtocol.update(testProfile(), body, 4_550_000).scopes)
        val disabled = RefreshProtocol.update(testProfile(), body.dropLast(1)+",\"scope\":\"openid\"}", 4_550_000)
        assertFalse(disabled.planUsageEnabled)
    }
    @Test fun missingRotationExpiryOrWrongTypeIsRejectedWithoutSecrets() {
        for (body in listOf("""{"access_token":"synthetic-secret"}""", """{"access_token":"x","refresh_token":"r","token_type":"Basic","expires_in":3600}""",
            """{"access_token":"x","refresh_token":"r","token_type":"Bearer","expires_in":0}""")) {
            val failure = assertThrows(ApiFailure::class.java) { RefreshProtocol.update(testProfile(), body, 4_550_000) }
            assertFalse(failure.message.orEmpty().contains("synthetic-secret")); assertNull(failure.cause)
        }
    }
    @Test fun errorsPreserveSafeClassificationAndHideRawMessagesAndCredentialObjects() {
        val failure = ApiErrors.parse(429, """{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"synthetic-secret"}}""", "req_example")
        assertEquals(ApiProblem.USAGE_LIMIT, failure.problem); assertEquals("req_example", failure.requestId)
        assertEquals(ErrorShape.STRUCTURED, failure.shape); assertEquals("[REDACTED]", failure.toString())
        assertEquals(ApiProblem.REAUTHORIZE, ApiErrors.parse(400, """{"error":"invalid_grant"}""", refresh = true).problem)
        assertEquals(ApiProblem.CONFIGURATION, ApiErrors.parse(401, """{"error":"invalid_client"}""", refresh = true).problem)
        assertEquals(ErrorShape.DETAIL, ApiErrors.parse(503, """{"detail":"synthetic-secret"}""").shape)
        val lease = CredentialLease("client", 1, "synthetic-secret", 99)
        assertEquals("[REDACTED]", lease.toString()); assertEquals("[REDACTED]", SafeDiagnostics.redact(lease))
        assertEquals("[REDACTED]", PreparedCatalog(lease, emptyList()).toString())
    }
    @Test fun undocumentedEarliestMetadataNeverTriggersAnOptimisticRefresh() {
        val old = testProfile()
        for (metadata in listOf(kotlinx.serialization.json.JsonPrimitive(123), kotlinx.serialization.json.JsonPrimitive("2099-01-01T00:00:00Z"),
            kotlinx.serialization.json.JsonObject(emptyMap()))) {
            val constrained = old.copy(earliestRefreshAt = metadata)
            assertFalse(TokenExpiry.needsRefresh(constrained, 4_000_000))
            assertFalse(TokenExpiry.needsRefresh(constrained, 4_540_000))
            val failure = assertThrows(ApiFailure::class.java) { TokenExpiry.needsRefresh(constrained, 4_600_000) }
            assertEquals(ApiProblem.CONFIGURATION, failure.problem)
            assertEquals(ProtocolDetail.REFRESH_TIMING_UNDOCUMENTED, failure.protocolDetail)
        }
        assertTrue(TokenExpiry.needsRefresh(old.copy(earliestRefreshAt = kotlinx.serialization.json.JsonNull), 4_540_000))
    }

}
