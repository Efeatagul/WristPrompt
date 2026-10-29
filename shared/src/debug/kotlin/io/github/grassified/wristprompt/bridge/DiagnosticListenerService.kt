package io.github.grassified.wristprompt.bridge

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService

/** Fixed empty-request probe: no credential access, auth action, arbitrary output or payload logging. */
class DiagnosticListenerService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != PATH || request.isNotEmpty()) return null
        filesDir.resolve("data-layer-probe-received.txt").writeText("DEBUG_RPC_LISTENER_INVOKED")
        return Tasks.forResult(byteArrayOf(1))
    }

    companion object {
        const val PATH = "/wristprompt/debug/connectivity"
        const val CAPABILITY = "wristprompt_debug_connectivity"
    }
}
