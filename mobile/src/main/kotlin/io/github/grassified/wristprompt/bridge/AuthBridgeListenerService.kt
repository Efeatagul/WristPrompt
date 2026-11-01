package io.github.grassified.wristprompt.bridge

import io.github.grassified.wristprompt.auth.ApiKeyProtocol
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableListenerService
import io.github.grassified.wristprompt.WristPromptApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.asTask
import kotlinx.coroutines.withTimeout

class AuthBridgeListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path !in setOf(BridgeProtocol.AUTH_START_PATH, ApiKeyProtocol.START_PATH)) return null
        return scope.async {
            try {
                withTimeout(BridgeProtocol.TIMEOUT_MS) {
                    val app = application as WristPromptApplication
                    if (path == ApiKeyProtocol.START_PATH) app.apiKeyInbox.receive(nodeId, request)
                    else app.authReceiver.receive(nodeId, request)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BridgeLog.warn(BridgeDiagnostic.REQUEST_REJECTED)
                throw BridgeProtocolException()
            }
        }.asTask()
    }

    override fun onCapabilityChanged(info: com.google.android.gms.wearable.CapabilityInfo) {
        if (info.name == BridgeProtocol.WEAR_CAPABILITY) (application as WristPromptApplication).queuePresets()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
