package io.github.grassified.wristprompt.auth

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.*
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class IdTokenVerifierTest {
    private val key = RSAKeyGenerator(2048).keyID("test-key").keyUse(KeyUse.SIGNATURE).generate()
    private val now = 1_000_000L
    private fun claims() = JWTClaimsSet.Builder().issuer(OpenAiAuth.ISSUER).subject("verified-subject")
        .audience("oaiapp_test").issueTime(Date(now - 1000)).expirationTime(Date(now + 3600_000))
        .claim("nonce", "nonce").claim("email", "test@example.invalid").claim("email_verified", true)
    private fun token(builder: JWTClaimsSet.Builder = claims(), signer: RSAKey = key, algorithm: JWSAlgorithm = JWSAlgorithm.RS256): String =
        SignedJWT(JWSHeader.Builder(algorithm).keyID("test-key").build(), builder.build()).apply {
            sign(RSASSASigner(signer))
        }.serialize()
    private fun verify(raw: String, expected: RegistrationIdentity? = null) = IdTokenVerifier().verify(
        raw, JWKSet(key.toPublicJWK()).toString(), "oaiapp_test", "nonce", now, null, expected)
    @Test fun signatureAndVerifiedClaimsProduceBoundIdentity() {
        val result = verify(token())
        assertEquals("verified-subject", result.identity.subject)
        assertEquals("oaiapp_test", result.identity.clientId)
        assertTrue(result.emailVerified)
        assertEquals("[REDACTED]", result.toString())
    }
    @Test fun invalidSignatureFailsClosedWithoutOriginalToken() {
        val other = RSAKeyGenerator(2048).generate()
        val failure = assertThrows(AuthException::class.java) { verify(token(signer = other)) }
        assertEquals(AuthProblem.IDENTITY, failure.problem); assertNull(failure.cause)
    }
    @Test fun issuerAudienceNonceExpiryAndFutureClaimsAreRejected() {
        val bad = listOf(claims().issuer("https://evil.invalid"), claims().audience("other"),
            claims().claim("nonce", "wrong"), claims().expirationTime(Date(now - 6000)),
            claims().issueTime(Date(now + 6000)), claims().notBeforeTime(Date(now + 6000)),
            claims().claim("auth_time", now / 1000 + 6), claims().subject(""))
        for (value in bad) assertThrows(AuthException::class.java) { verify(token(value)) }
    }
    @Test fun multipleAudiencesRequireMatchingAuthorizedParty() {
        assertThrows(AuthException::class.java) { verify(token(claims().audience(listOf("other", "oaiapp_test")))) }
        verify(token(claims().audience(listOf("other", "oaiapp_test")).claim("azp", "oaiapp_test")))
        assertThrows(AuthException::class.java) { verify(token(claims().claim("azp", "other"))) }
    }
    @Test fun returningIdentityMismatchCannotReplaceRegistration() {
        assertThrows(AuthException::class.java) {
            verify(token(), RegistrationIdentity("oaiapp_test", OpenAiAuth.ISSUER, "different-subject"))
        }
    }
    @Test fun unsignedAndUnexpectedAlgorithmsAreRejected() {
        val unsigned = PlainJWT(claims().build()).serialize()
        assertThrows(AuthException::class.java) { verify(unsigned) }
        assertThrows(AuthException::class.java) { verify(token(algorithm = JWSAlgorithm.RS512)) }
    }
    @Test fun accessTokenHashAndMissingRequiredClaimsAreRejected() {
        assertThrows(AuthException::class.java) { verify(token(claims().claim("at_hash", "invalid"))) }
        val missing = JWTClaimsSet.Builder().issuer(OpenAiAuth.ISSUER).audience("oaiapp_test").subject("subject").claim("nonce", "nonce")
        assertThrows(AuthException::class.java) { verify(token(missing)) }
    }
}
