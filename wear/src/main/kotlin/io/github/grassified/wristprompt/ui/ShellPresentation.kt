package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.openai.*

/** Display metadata and reachability only; never chooses a credential or writes a preference. */
internal data class ShellPresentation(
    val billing: String,
    val billingNote: String,
    val account: String?,
    val model: String,
    val status: String,
    val proofAvailable: Boolean,
    val canTest: Boolean,
    val showStop: Boolean,
    val showOutput: Boolean,
    val showContinue: Boolean,
    val canContinue: Boolean,
    val showSignOut: Boolean,
    val showManageUsage: Boolean,
    val showRemoveKey: Boolean,
)

internal fun shellPresentation(
    accounts: WatchAccountState, key: ApiKeyState, proof: ProofState,
    bridge: WatchAuthState, changing: Boolean,
): ShellPresentation {
    val plan = key.mode == CredentialMode.CHATGPT_PLAN
    val available = key.loaded && !key.storageError && !changing && proof.mode == key.mode &&
        if (plan) accounts.connected && accounts.planEnabled && !accounts.authorizing && !accounts.storageError &&
            !accounts.signingOut && !accounts.signedOut && !accounts.reauthorizationRequired
        else key.configured
    val showContinue = !accounts.connected || accounts.reauthorizationRequired
    val status = when {
        key.storageError -> "Protected storage unavailable"
        !key.loaded -> "Loading connection…"
        changing -> "Updating connection…"
        plan && accounts.storageError -> "Protected storage unavailable"
        plan && accounts.signingOut -> "Signing out…"
        plan && accounts.signedOut -> if (accounts.revocation == RevocationResult.CONFIRMED)
            "Signed out · revocation confirmed" else "Signed out · remote revocation unconfirmed"
        plan && accounts.expired -> "Setup expired · retry from watch"
        plan && accounts.authorizing -> "Finish sign-in on phone"
        plan && accounts.failed -> "Phone authorization failed · retry from watch"
        plan && accounts.reauthorizationRequired -> "Authentication required"
        plan && !accounts.connected -> when (bridge) {
            WatchAuthState.LOOKING_FOR_PHONE -> "Finding phone…"
            WatchAuthState.REQUEST_SENT -> "Open WristPrompt on phone"
            WatchAuthState.COMPANION_UNAVAILABLE -> "Phone unavailable"
            WatchAuthState.MULTIPLE_PHONES -> "Multiple phones · connect one"
            WatchAuthState.ERROR -> "Setup not confirmed · retry from watch"
            WatchAuthState.IDLE -> "Authentication required"
        }
        plan && !accounts.planEnabled -> "ChatGPT plan usage disabled"
        !plan && !key.configured -> "API key not configured · no fallback"
        proof.mode != key.mode -> "Loading connection…"
        else -> proofSummary(proof)
    }
    return ShellPresentation(
        billing = when { key.storageError -> "Billing mode unavailable"; !key.loaded -> "Loading billing mode…"; plan -> "ChatGPT plan"; else -> "OpenAI API" },
        billingNote = when { key.storageError || !key.loaded -> "Requests blocked"; plan -> "Eligible plan usage"; else -> "Separate API billing" },
        account = if (plan && accounts.connected && !accounts.signingOut && !accounts.signedOut &&
            !accounts.reauthorizationRequired && !accounts.storageError) accounts.accounts.firstOrNull { it.clientId == accounts.activeClientId }?.label else null,
        model = if (available) proof.models.firstOrNull { it.slug == proof.selectedSlug }?.displayName ?: "Not selected" else "Not selected",
        status = status, proofAvailable = available,
        canTest = available && !proof.busy && proof.selectedSlug != null && proof.stage != ProofStage.FAILED,
        showStop = proof.busy,
        showOutput = proof.mode == key.mode && proof.output.isNotEmpty(),
        showContinue = showContinue,
        canContinue = bridge != WatchAuthState.LOOKING_FOR_PHONE && !accounts.signingOut && !accounts.storageError,
        showSignOut = accounts.activeClientId != null && !accounts.signedOut,
        showManageUsage = accounts.connected && !accounts.signedOut,
        showRemoveKey = key.configured,
    )
}

internal fun proofSummary(proof: ProofState): String = when (proof.stage) {
    ProofStage.LOADING -> "Checking connection…"
    ProofStage.STREAMING -> "Streaming…"
    ProofStage.SUCCEEDED -> "Completed · exact proof matched"
    ProofStage.OUTPUT_MISMATCH -> "Completed · proof did not match"
    ProofStage.CANCELLED -> "Stopped · output is partial"
    ProofStage.IDLE, ProofStage.READY, ProofStage.FAILED -> proof.problem?.let(::problemSummary)
        ?: if (proof.stage == ProofStage.READY) "Ready for proof" else "Load models to begin"
}

internal fun problemSummary(problem: ApiProblem): String = when (problem) {
    ApiProblem.API_KEY_MISSING -> "API key not configured"
    ApiProblem.INVALID_API_KEY -> "API key rejected · no fallback"
    ApiProblem.API_LIMIT -> "API rate or credit limit"
    ApiProblem.REAUTHORIZE -> "Authentication required"
    ApiProblem.PLAN_DISABLED -> "ChatGPT plan usage disabled"
    ApiProblem.REAUTHORIZING -> "Finish sign-in on phone"
    ApiProblem.USAGE_LIMIT -> "ChatGPT usage limit"
    ApiProblem.USAGE_UNAVAILABLE -> "Usage unavailable"
    ApiProblem.NOT_ELIGIBLE -> "Plan not eligible"
    ApiProblem.PERMISSION -> "Permission not accepted"
    ApiProblem.TEMPORARY -> "Temporarily unavailable"
    ApiProblem.REFRESH_NOT_READY -> "Refresh not ready"
    ApiProblem.CONFIGURATION -> "Configuration needs attention"
    ApiProblem.INVALID_REQUEST -> "Request or model rejected"
    ApiProblem.NETWORK -> "Network error"
    ApiProblem.NO_MODELS -> "No models available"
    ApiProblem.INTERRUPTED -> "Stream interrupted · output is partial"
    ApiProblem.INCOMPLETE -> "Response incomplete · output is partial"
    ApiProblem.PROTOCOL -> "Response could not be processed"
    ApiProblem.STORAGE -> "Protected storage unavailable"
    ApiProblem.STALE_ACCOUNT -> "Connection changed · reload models"
}
