package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import io.github.grassified.wristprompt.storage.ConversationSummary

data class ConversationHistory(
    val entries: List<ConversationSummary> = emptyList(), val loading: Boolean = false, val error: String? = null,
) { override fun toString() = SafeDiagnostics.REDACTED }
