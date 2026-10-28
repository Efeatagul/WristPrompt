package io.github.grassified.wristprompt.bridge

import org.junit.Assert.*
import org.junit.Test

class SafeDiagnosticsTest {
    @Test fun redactsEntirePayloadRegardlessOfCredentialFieldName() {
        for (value in listOf(null, "Bearer synthetic-key", "access_token=synthetic-secret", byteArrayOf(1, 2),
            mapOf("new_future_credential" to "synthetic-secret"), Exception("synthetic-secret"))) {
            assertEquals("[REDACTED]", SafeDiagnostics.redact(value))
        }
    }

    @Test fun redactionNeverInvokesToString() {
        val value = object { override fun toString(): String = error("Must not evaluate payload") }
        assertEquals("[REDACTED]", SafeDiagnostics.redact(value))
    }

    @Test fun diagnosticsContainOnlyAllowlistedEvents() {
        BridgeDiagnostic.entries.forEach {
            assertEquals("WristPrompt bridge: ${it.name}", SafeDiagnostics.message(it))
        }
    }
}
