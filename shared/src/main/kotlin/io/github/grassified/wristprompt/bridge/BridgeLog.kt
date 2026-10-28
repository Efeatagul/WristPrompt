package io.github.grassified.wristprompt.bridge

import android.util.Log

object BridgeLog {
    fun warn(event: BridgeDiagnostic) {
        Log.w("WristPrompt", SafeDiagnostics.message(event))
    }
}
