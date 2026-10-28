package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import org.junit.Assert.*
import org.junit.Test

class ProfileProtocolTest {
    private fun profile(scopes: Set<String> = OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)) = CredentialProfile(
        RegistrationIdentity("oaiapp_test", OpenAiAuth.ISSUER, "synthetic-subject"), BridgeProtocol.newHostId(),
        "Test", "test@example.invalid", true, "synthetic-id-token", "synthetic-access", "synthetic-refresh", "Bearer", 3600, 1_000_000, scopes)
    @Test fun credentialProfileAndEnvelopeRoundTripExplicitly() {
        val value = ProfileTransfer(1, BridgeProtocol.newRequestId(), BridgeProtocol.newRequestId(),
            profile().copy(earliestRefreshAt = kotlinx.serialization.json.JsonPrimitive("opaque-service-time")))
        assertEquals(value, ProfileProtocol.decodeTransfer(ProfileProtocol.encode(value)))
        assertEquals("[REDACTED]", value.toString()); assertEquals("[REDACTED]", value.profile.toString())
        assertEquals("[REDACTED]", value.profile.identity.toString())
    }
    @Test fun scopeParsingSeparatesIdentityFromPlanAndDoesNotInferFromToken() {
        assertEquals(setOf("openid", "email"), OpenAiAuth.scopes(" openid  email openid "))
        assertFalse(profile(setOf("openid")).planUsageEnabled)
        assertTrue(profile().planUsageEnabled)
        assertThrows(IllegalArgumentException::class.java) { OpenAiAuth.scopes("openid\nemail") }
        assertThrows(IllegalArgumentException::class.java) { profile(setOf("openid", OpenAiAuth.PLAN_SCOPE)).validate() }
    }
    @Test fun expiresAtUsesReceiptTimeAndRejectsOverflowAndInvalidDurations() {
        assertEquals(4_600_000L, profile().expiresAtEpochMs)
        assertThrows(IllegalArgumentException::class.java) { OpenAiAuth.expiresAt(1, 0) }
        assertThrows(ArithmeticException::class.java) { OpenAiAuth.expiresAt(Long.MAX_VALUE, 3600) }
    }
    @Test fun malformedVersionsDuplicateKeysAndOversizedPayloadsAreRejectedWithoutContents() {
        val envelope = ProfileTransfer(1, BridgeProtocol.newRequestId(), BridgeProtocol.newRequestId(), profile())
        val encoded = ProfileProtocol.encode(envelope).decodeToString()
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0xc3.toByte()), ByteArray(65_537),
            encoded.replace("\"version\":1", "\"version\":2").encodeToByteArray(),
            encoded.replace("\"version\":1", "\"version\":1,\"version\":1").encodeToByteArray(),
            encoded.replace("synthetic-subject", "").encodeToByteArray())) {
            val failure = assertThrows(BridgeProtocolException::class.java) { ProfileProtocol.decodeTransfer(bytes) }
            assertNull(failure.cause); assertFalse(failure.message.orEmpty().contains("synthetic"))
        }
    }
    @Test fun incompleteCredentialSetsAndDynamicClientAreRejected() {
        for (p in listOf(profile().copy(refreshToken = null), profile().copy(tokenType = "Other"), profile().copy(accessToken = null),
            profile().copy(identity = RegistrationIdentity(OpenAiAuth.DYNAMIC_CLIENT, OpenAiAuth.ISSUER, "subject")))) {
            assertThrows(IllegalArgumentException::class.java) { p.validate() }
        }
    }
}
