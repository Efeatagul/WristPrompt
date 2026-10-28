package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.BridgeProtocol
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class OAuthTransactionTest {
    private fun transaction(identity: RegistrationIdentity? = null) = OAuthTransaction(
        BridgeProtocol.newHostId(), "http://127.0.0.1:23456/auth/callback", identity, if (identity != null) "synthetic-id-token" else null)
    private fun params(url: String) = URI(url).rawQuery.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
    @Test fun pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuthRandom.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun randomValuesHaveSufficientEntropyAndAreFreshPerAttempt() {
        val values = (1..100).map { OAuthRandom.value() }
        assertEquals(100, values.toSet().size)
        assertTrue(values.all { Regex("[A-Za-z0-9_-]{43}").matches(it) })
        val first = transaction(); val second = transaction()
        assertNotEquals(first.state, second.state); assertNotEquals(first.nonce, second.nonce)
        assertNotEquals(first.verifier, second.verifier)
    }
    @Test fun initialParametersAreExactAndVerifierNeverLeavesClient() {
        val t = transaction(); val p = params(t.authorizationUrl())
        assertEquals("https://auth.openai.com/api/accounts/authorize", t.authorizationUrl().substringBefore('?'))
        assertEquals("dynamic_agent_client", p["client_id"]); assertEquals("WristPrompt", p["agent_name_hint"])
        assertEquals(t.hostId, p["ext_agent_host_id"]); assertEquals(t.redirectUri, p["redirect_uri"])
        assertEquals("code", p["response_type"]); assertEquals("https://api.openai.com/v1", p["resource"])
        assertEquals(OpenAiAuth.REQUESTED_SCOPES, p["scope"])
        assertEquals(t.state, p["state"]); assertEquals(t.nonce, p["nonce"])
        assertEquals("S256", p["code_challenge_method"]); assertEquals(OAuthRandom.challenge(t.verifier), p["code_challenge"])
        assertFalse(p.containsKey("code_verifier")); assertFalse(t.authorizationUrl().contains(t.verifier))
    }
    @Test fun returningSignInKeepsIssuedClientAndHintsWithoutNewNameHint() {
        val t = transaction(RegistrationIdentity("oaiapp_saved", OpenAiAuth.ISSUER, "subject"))
        val p = params(t.authorizationUrl())
        assertEquals("oaiapp_saved", p["client_id"]); assertEquals("synthetic-id-token", p["id_token_hint"])
        assertFalse(p.containsKey("agent_name_hint"))
        assertEquals("oaiapp_saved", t.callback("/auth/callback?state=${t.state}&code=synthetic").clientId)
        assertThrows(AuthException::class.java) { t.callback("/auth/callback?state=${t.state}&code=synthetic&client_id=oaiapp_other") }
    }
    @Test fun dynamicCallbackRequiresIssuedClientAndOriginalState() {
        val t = transaction()
        val result = t.callback("/auth/callback?state=${t.state}&code=a%2Bb&client_id=oaiapp_new&scope=openid")
        assertEquals("a+b", result.code); assertEquals("oaiapp_new", result.clientId)
        for (query in listOf("state=${t.state}&code=x", "state=wrong&code=x&client_id=oaiapp_x", "code=x&client_id=oaiapp_x", "state=${t.state}&code=x&client_id=dynamic_agent_client")) {
            assertThrows(AuthException::class.java) { t.callback("/auth/callback?$query") }
        }
    }
    @Test fun stateIsValidatedBeforeProviderErrorsAndDenialNeverExchanges() {
        val t = transaction()
        assertEquals(AuthProblem.STATE, assertThrows(AuthException::class.java) { t.callback("/auth/callback?state=wrong&error=access_denied") }.problem)
        assertEquals(AuthProblem.DENIED, assertThrows(AuthException::class.java) { t.callback("/auth/callback?state=${t.state}&error=access_denied") }.problem)
    }
    @Test fun malformedAmbiguousAndWrongPathCallbacksFailClosed() {
        val t = transaction()
        for (target in listOf("/callback?state=${t.state}&code=x", "/auth/callback?state=${t.state}&state=${t.state}&code=x", "/auth/callback?state=${t.state}&code=x&error=no", "/auth/callback?state=${t.state}&code=%00", "http://evil/auth/callback?state=${t.state}&code=x", "/auth/callback?state=%Q&code=x")) {
            assertThrows(AuthException::class.java) { t.callback(target) }
        }
        assertEquals("[REDACTED]", t.toString())
    }
    @Test fun redirectCannotUseWildcardLocalhostOrWrongPath() {
        for (uri in listOf("http://localhost:2345/auth/callback", "http://0.0.0.0:2345/auth/callback", "http://127.0.0.1:2345/callback", "http://127.0.0.1:2345/auth/callback?x=y")) {
            assertThrows(IllegalArgumentException::class.java) { OAuthTransaction(BridgeProtocol.newHostId(), uri, null, null) }
        }
    }
    @Test fun signInAfterLocalSignOutReusesIssuedClientAndHostWithoutIdTokenHint() {
        val host = BridgeProtocol.newHostId()
        val identity = RegistrationIdentity("oaiapp_saved", OpenAiAuth.ISSUER, "subject")
        val t = OAuthTransaction(host, "http://127.0.0.1:23456/auth/callback", identity, null)
        val p = params(t.authorizationUrl())
        assertEquals(identity.clientId, p["client_id"]); assertEquals(host, p["ext_agent_host_id"])
        assertFalse(p.containsKey("id_token_hint")); assertFalse(p.containsKey("agent_name_hint"))
        assertEquals(identity.clientId, t.callback("/auth/callback?state=${t.state}&code=synthetic").clientId)
    }

}
