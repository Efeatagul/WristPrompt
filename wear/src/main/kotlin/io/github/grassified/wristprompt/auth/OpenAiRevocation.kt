package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import io.github.grassified.wristprompt.openai.ApiFailure
import io.github.grassified.wristprompt.openai.ApiProblem
import io.github.grassified.wristprompt.openai.OpenAiHttp
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.Request

// Only these two fields are held for the bounded remote operation.
class RevocationCredential(val clientId: String, val refreshToken: String) {
    override fun toString() = SafeDiagnostics.REDACTED
}
enum class RevocationResult { CONFIRMED, UNCONFIRMED }

class OpenAiRevocation(private val http: OpenAiHttp) {
    suspend fun revoke(credential: RevocationCredential): RevocationResult = try {
        withTimeout(12_000) {
            require(OpenAiAuth.validClient(credential.clientId) && credential.refreshToken.isNotBlank())
            val endpoint = http.execute(Request.Builder().url(OpenAiAuth.DISCOVERY).get().build(), 3) { response ->
                require(response.code == 200)
                endpoint(http.body(response))
            }
            val body = FormBody.Builder().add("token", credential.refreshToken)
                .add("token_type_hint", "refresh_token").add("client_id", credential.clientId).build()
            var result = RevocationResult.UNCONFIRMED
            for (attempt in 0..1) {
                val retry = try {
                    http.execute(Request.Builder().url(endpoint).post(body).build(), 4) { response ->
                        if (response.code == 200 && !response.body.source().request(1)) result = RevocationResult.CONFIRMED
                        response.code in 500..599
                    }
                } catch (failure: ApiFailure) { failure.problem == ApiProblem.NETWORK }
                if (result == RevocationResult.CONFIRMED || !retry || attempt == 1) break
                delay(250)
            }
            result
        }
    } catch (_: Exception) { RevocationResult.UNCONFIRMED }

    private fun endpoint(body: String): String {
        val document = Json.parseToJsonElement(body).jsonObject
        require(document["issuer"]?.jsonPrimitive?.content == OpenAiAuth.ISSUER)
        val endpoint = document["revocation_endpoint"]?.jsonPrimitive?.content ?: error("Missing endpoint")
        val uri = URI(endpoint)
        // Discovery chooses the path; secrets can only go to the trusted issuer origin.
        require(uri.scheme == "https" && uri.host == "auth.openai.com" && uri.port in listOf(-1, 443))
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        document["revocation_endpoint_auth_methods_supported"]?.let {
            require(it.jsonArray.any { method -> method.jsonPrimitive.content == "none" })
        }
        return endpoint
    }
}
