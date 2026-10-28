package io.github.grassified.wristprompt.bridge

enum class BridgeDiagnostic { REQUEST_REJECTED, CONNECTION_FAILED, STORAGE_UNAVAILABLE }

object SafeDiagnostics {
    const val REDACTED = "[REDACTED]"

    // Do not inspect or stringify the input, even if a future caller passes an entire credential profile.
    fun redact(@Suppress("UNUSED_PARAMETER") value: Any?): String = REDACTED

    // Log sinks accept only these allowlisted events, never a message, payload, or Throwable.
    fun message(event: BridgeDiagnostic): String = "WristPrompt bridge: ${event.name}"
}
