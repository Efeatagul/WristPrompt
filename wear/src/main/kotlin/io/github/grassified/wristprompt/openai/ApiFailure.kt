package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.json.*

enum class ApiProblem {
    API_KEY_MISSING, INVALID_API_KEY, API_LIMIT, REAUTHORIZE, PLAN_DISABLED, REAUTHORIZING, USAGE_LIMIT, USAGE_UNAVAILABLE, NOT_ELIGIBLE,
    PERMISSION, TEMPORARY, REFRESH_NOT_READY, CONFIGURATION, INVALID_REQUEST, NETWORK, NO_MODELS,
    INTERRUPTED, INCOMPLETE, PROTOCOL, STORAGE, CHAT_STORAGE, STALE_ACCOUNT,
}
enum class ErrorShape { STRUCTURED, DETAIL, OTHER }
// Static failure categories only: never include provider bodies, headers or exceptions.
enum class ProtocolDetail {
    REFRESH_NOT_READY, REFRESH_TIMING_UNDOCUMENTED, BODY_TOO_LARGE, INVALID_UTF8, CATALOG_JSON, CATALOG_MODELS, CATALOG_MODEL, CATALOG_DUPLICATE,
    RESPONSE_JSON, RESPONSE_HTML, RESPONSE_MISSING_TYPE, RESPONSE_OTHER_TYPE,
    SSE_LINE_LIMIT, SSE_FRAME_LIMIT, SSE_EVENT_JSON, SSE_EVENT_TYPE, SSE_EVENT_MISMATCH,
    SSE_EVENT_SHAPE, OUTPUT_LIMIT, CLIENT_RESPONSE,
}

class ApiFailure(
    val problem: ApiProblem, val status: Int? = null, val code: String? = null,
    val requestId: String? = null, val shape: ErrorShape = ErrorShape.OTHER,
    val protocolDetail: ProtocolDetail? = null,
    val requestNotSent: Boolean = false,
) : IllegalStateException("OpenAI operation: ${problem.name}") {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

object ApiErrors {
    private val terminalRefresh = setOf("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")
    fun unusableRefresh(code: String?): Boolean = code in terminalRefresh
    fun parse(status: Int?, body: String, requestId: String? = null, refresh: Boolean = false): ApiFailure {
        val root = try { Json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }
        val error = root?.get("error")
        val code = safeCode(((error as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
            ?: (error as? JsonPrimitive)?.contentOrNull ?: (root?.get("code") as? JsonPrimitive)?.contentOrNull)
        val shape = when { error != null -> ErrorShape.STRUCTURED; root?.containsKey("detail") == true -> ErrorShape.DETAIL; else -> ErrorShape.OTHER }
        val problem = when {
            refresh && unusableRefresh(code) -> ApiProblem.REAUTHORIZE
            refresh && status == 429 -> ApiProblem.TEMPORARY
            !refresh && code == "model_not_found" -> ApiProblem.NO_MODELS
            code == "invalid_client" -> ApiProblem.CONFIGURATION
            code == "subscription_sharing_usage_limit_exceeded" || status == 429 -> ApiProblem.USAGE_LIMIT
            code == "subscription_sharing_usage_unavailable" -> ApiProblem.USAGE_UNAVAILABLE
            code == "subscription_sharing_user_not_eligible" -> ApiProblem.NOT_ELIGIBLE
            code in setOf("chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context", "subscription_sharing_invalid_user") -> ApiProblem.PERMISSION
            code == "subscription_sharing_unsupported_capability" -> ApiProblem.INVALID_REQUEST
            code == "subscription_sharing_route_not_supported" -> ApiProblem.CONFIGURATION
            code in setOf("subscription_sharing_user_unavailable", "server_error") -> ApiProblem.TEMPORARY
            status == 401 -> if (refresh) ApiProblem.CONFIGURATION else ApiProblem.PERMISSION
            status == 403 -> ApiProblem.PERMISSION
            status != null && status >= 500 -> ApiProblem.TEMPORARY
            else -> ApiProblem.INVALID_REQUEST
        }
        return ApiFailure(problem, status, code, safeRequestId(requestId), shape)
    }
    fun forMode(failure: ApiFailure, mode: io.github.grassified.wristprompt.auth.CredentialMode): ApiFailure {
        if (mode != io.github.grassified.wristprompt.auth.CredentialMode.OPENAI_API_KEY) return failure
        val problem = when {
            failure.status == 401 || failure.code == "invalid_api_key" -> ApiProblem.INVALID_API_KEY
            failure.status == 429 || failure.problem == ApiProblem.USAGE_LIMIT || failure.code in setOf("insufficient_quota", "rate_limit_exceeded", "credit_balance_exhausted", "slow_down") -> ApiProblem.API_LIMIT
            failure.problem in setOf(ApiProblem.USAGE_UNAVAILABLE, ApiProblem.NOT_ELIGIBLE) -> ApiProblem.TEMPORARY
            else -> failure.problem
        }
        return ApiFailure(problem, failure.status, failure.code, failure.requestId, failure.shape, failure.protocolDetail, failure.requestNotSent)
    }
    fun withResponseContext(failure: ApiFailure, status: Int, requestId: String?): ApiFailure = ApiFailure(
        failure.problem, failure.status ?: status, failure.code, failure.requestId ?: safeRequestId(requestId),
        failure.shape, failure.protocolDetail, failure.requestNotSent,
    )
    private fun safeRequestId(value: String?): String? = value?.takeIf { Regex("req_[A-Za-z0-9_-]{1,100}").matches(it) }
    private fun safeCode(value: String?): String? = value?.takeIf { Regex("[a-zA-Z0-9_]{1,100}").matches(it) }
}
