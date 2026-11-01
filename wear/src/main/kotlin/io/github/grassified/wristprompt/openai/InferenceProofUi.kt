package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.R

internal fun problemText(problem: ApiProblem?): Int = when (problem) {
    ApiProblem.API_KEY_MISSING -> R.string.api_key_missing
    ApiProblem.INVALID_API_KEY -> R.string.api_key_invalid
    ApiProblem.API_LIMIT -> R.string.api_limit
    ApiProblem.REAUTHORIZE -> R.string.proof_reauthorize
    ApiProblem.PLAN_DISABLED -> R.string.plan_disabled
    ApiProblem.REAUTHORIZING -> R.string.authorizing
    ApiProblem.USAGE_LIMIT -> R.string.proof_usage_limit
    ApiProblem.USAGE_UNAVAILABLE -> R.string.proof_usage_unavailable
    ApiProblem.NOT_ELIGIBLE -> R.string.proof_not_eligible
    ApiProblem.PERMISSION -> R.string.proof_permission
    ApiProblem.REFRESH_NOT_READY -> R.string.refresh_not_ready
    ApiProblem.TEMPORARY -> R.string.proof_temporary
    ApiProblem.CONFIGURATION -> R.string.proof_configuration
    ApiProblem.INVALID_REQUEST -> R.string.proof_invalid_request
    ApiProblem.NETWORK -> R.string.proof_network
    ApiProblem.NO_MODELS -> R.string.proof_no_models
    ApiProblem.INTERRUPTED -> R.string.proof_interrupted
    ApiProblem.INCOMPLETE -> R.string.proof_incomplete
    ApiProblem.STORAGE -> R.string.setup_storage_error
    ApiProblem.GENERATION_UNSUPPORTED -> R.string.generation_unsupported
    ApiProblem.GENERATION_OUTPUT -> R.string.generation_output
    ApiProblem.GENERATION_PROMPT -> R.string.generation_prompt
    ApiProblem.IMAGE_TEXT_REQUIRED -> R.string.image_text_required
    ApiProblem.IMAGES_UNSUPPORTED -> R.string.images_unsupported
    ApiProblem.IMAGE_UNAVAILABLE -> R.string.image_unavailable
    ApiProblem.IMAGE_LIMIT -> R.string.image_limit
    ApiProblem.THINKING_UNSUPPORTED -> R.string.thinking_unsupported
    ApiProblem.CHAT_STORAGE -> R.string.chat_storage_error
    ApiProblem.STALE_ACCOUNT -> R.string.proof_stale_account
    ApiProblem.PROTOCOL, null -> R.string.proof_protocol
}
