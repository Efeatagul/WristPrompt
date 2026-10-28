package io.github.grassified.wristprompt.openai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import io.github.grassified.wristprompt.R

fun TransformingLazyColumnScope.inferenceProofItems(state: ProofState, viewModel: InferenceViewModel) {
    item { Text(stringResource(R.string.connected_chatgpt)) }
    item { Text(stringResource(R.string.using_chatgpt_plan)) }
    item {
        val selected = state.models.firstOrNull { it.slug == state.selectedSlug }
        Text(stringResource(R.string.selected_model, selected?.displayName ?: stringResource(R.string.model_not_selected)))
    }
    for (model in state.models) item {
        Button(onClick = { viewModel.select(model.slug) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(), label = {
                Text((if (model.slug == state.selectedSlug) "✓ " else "") + model.displayName)
            })
    }
    item {
        Text(stringResource(when (state.stage) {
            ProofStage.IDLE -> R.string.proof_idle
            ProofStage.LOADING -> R.string.proof_loading
            ProofStage.READY -> R.string.proof_ready
            ProofStage.STREAMING -> R.string.proof_streaming
            ProofStage.SUCCEEDED -> R.string.proof_success
            ProofStage.OUTPUT_MISMATCH -> R.string.proof_mismatch
            ProofStage.FAILED -> problemText(state.problem)
            ProofStage.CANCELLED -> R.string.proof_cancelled
        }))
    }
    state.protocolDetail?.let { detail ->
        item { Text(stringResource(R.string.proof_protocol_detail, detail.name)) }
    }
    state.httpStatus?.let { status -> item { Text(stringResource(R.string.proof_http_status, status)) } }
    if (state.problem != null && state.stage == ProofStage.IDLE) item { Text(stringResource(problemText(state.problem))) }
    if (state.problem in setOf(ApiProblem.USAGE_LIMIT, ApiProblem.USAGE_UNAVAILABLE, ApiProblem.NOT_ELIGIBLE)) {
        item { Button(onClick = viewModel::manageUsage, label = { Text(stringResource(R.string.manage_usage)) }) }
    }
    if (state.usageHint) item { Text(stringResource(R.string.manage_usage_hint)) }
    item {
        Button(onClick = viewModel::loadModels, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.load_models)) })
    }
    item {
        Button(onClick = viewModel::test,
            enabled = !state.busy && state.selectedSlug != null && state.stage != ProofStage.FAILED,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.test_inference)) })
    }
    if (state.busy) item { Button(onClick = viewModel::stop, label = { Text(stringResource(R.string.stop_generation)) }) }
    if (state.output.isNotEmpty()) item { Text(state.output) }
    if (state.completedObserved) item { Text(stringResource(R.string.completed_observed)) }
}

private fun problemText(problem: ApiProblem?): Int = when (problem) {
    ApiProblem.REAUTHORIZE -> R.string.proof_reauthorize
    ApiProblem.PLAN_DISABLED -> R.string.plan_disabled
    ApiProblem.REAUTHORIZING -> R.string.authorizing
    ApiProblem.USAGE_LIMIT -> R.string.proof_usage_limit
    ApiProblem.USAGE_UNAVAILABLE -> R.string.proof_usage_unavailable
    ApiProblem.NOT_ELIGIBLE -> R.string.proof_not_eligible
    ApiProblem.PERMISSION -> R.string.proof_permission
    ApiProblem.TEMPORARY -> R.string.proof_temporary
    ApiProblem.CONFIGURATION -> R.string.proof_configuration
    ApiProblem.INVALID_REQUEST -> R.string.proof_invalid_request
    ApiProblem.NETWORK -> R.string.proof_network
    ApiProblem.NO_MODELS -> R.string.proof_no_models
    ApiProblem.INTERRUPTED -> R.string.proof_interrupted
    ApiProblem.INCOMPLETE -> R.string.proof_incomplete
    ApiProblem.STORAGE -> R.string.setup_storage_error
    ApiProblem.STALE_ACCOUNT -> R.string.proof_stale_account
    ApiProblem.PROTOCOL, null -> R.string.proof_protocol
}
