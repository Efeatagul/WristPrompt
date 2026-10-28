package io.github.grassified.wristprompt.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import java.security.MessageDigest
import java.util.Base64

class VerifiedIdentity(val identity: RegistrationIdentity, val name: String?, val email: String?, val emailVerified: Boolean) {
    override fun toString() = "[REDACTED]"
}

class IdTokenVerifier {
    fun verify(
        raw: String, jwks: String, clientId: String, nonce: String, nowMs: Long,
        accessToken: String?, expected: RegistrationIdentity?,
    ): VerifiedIdentity = try {
        require(raw.length in 1..24_000 && jwks.length <= 262_144)
        val jwt = SignedJWT.parse(raw)
        val header = jwt.header
        require(header.algorithm == JWSAlgorithm.RS256 && !header.keyID.isNullOrBlank())
        require(header.criticalParams.isNullOrEmpty() && header.getJWKURL() == null && header.getJWK() == null && header.x509CertURL == null)
        val keys = JWKSet.parse(jwks).keys.filterIsInstance<RSAKey>().filter {
            it.keyID == header.keyID && (it.keyUse == null || it.keyUse == KeyUse.SIGNATURE) &&
                (it.algorithm == null || it.algorithm == JWSAlgorithm.RS256)
        }
        require(keys.size == 1 && keys.single().size() >= 2048)
        require(jwt.verify(RSASSAVerifier(keys.single().toRSAPublicKey())))
        // No identity claim is consumed before cryptographic verification above.
        val claims = jwt.jwtClaimsSet
        require(claims.issuer == OpenAiAuth.ISSUER && clientId in claims.audience)
        val azp = claims.getStringClaim("azp")
        require((claims.audience.size == 1 || azp == clientId) && (azp == null || azp == clientId))
        val issued = claims.issueTime?.time ?: error("Missing issued time")
        val expires = claims.expirationTime?.time ?: error("Missing expiry")
        require(issued <= nowMs + 5000 && expires > nowMs - 5000 && expires > issued)
        require(claims.notBeforeTime == null || claims.notBeforeTime.time <= nowMs + 5000)
        val authTime = claims.getDateClaim("auth_time")?.time
        require(authTime == null || authTime <= nowMs + 5000)
        require(OAuthRandom.equal(nonce, claims.getStringClaim("nonce") ?: ""))
        val subject = claims.subject?.takeIf { it.isNotBlank() } ?: error("Missing subject")
        val sid = claims.getStringClaim("sid")
        require(sid == null || sid.isNotBlank())
        val identity = RegistrationIdentity(clientId, claims.issuer, subject).also { it.validate() }
        require(expected == null || expected == identity)
        val atHash = claims.getStringClaim("at_hash")
        if (atHash != null) {
            require(accessToken != null)
            val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
            val calculated = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOfRange(0, digest.size / 2))
            require(OAuthRandom.equal(atHash, calculated))
        }
        VerifiedIdentity(identity, claims.getStringClaim("name"), claims.getStringClaim("email"),
            claims.getBooleanClaim("email_verified") == true)
    } catch (_: Exception) { throw AuthException(AuthProblem.IDENTITY) }
}
