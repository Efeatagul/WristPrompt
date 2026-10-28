package io.github.grassified.wristprompt.auth

import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableListenerService
import io.github.grassified.wristprompt.WristPromptApplication
import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.asTask

class ProfileListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path !in setOf(ProfileProtocol.CONTEXT_PATH, ProfileProtocol.TRANSFER_PATH, ProfileProtocol.FAILURE_PATH, ApiKeyProtocol.TRANSFER_PATH)) return null
        return scope.async {
            try {
                withTimeout(BridgeProtocol.TIMEOUT_MS) {
                    val app = application as WristPromptApplication
                    if (path == ApiKeyProtocol.TRANSFER_PATH) {
                        val revision = app.apiKeys.state.value.keyRevision
                        val ack = app.apiKeys.accept(nodeId, request)
                        if (app.apiKeys.state.value.keyRevision != revision) app.inference.cancel(ApiKeyRepository.API_CLIENT)
                        return@withTimeout ProfileProtocol.encode(ack)
                    }
                    val profiles = app.profiles
                    when (path) {
                        ProfileProtocol.CONTEXT_PATH -> ProfileProtocol.encode(profiles.context(nodeId, ProfileProtocol.decode(request)))
                        ProfileProtocol.TRANSFER_PATH -> ProfileProtocol.encode(profiles.accept(nodeId, request))
                        else -> {
                            profiles.failed(nodeId, ProfileProtocol.decode(request))
                            byteArrayOf(1)
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                BridgeLog.warn(BridgeDiagnostic.REQUEST_REJECTED)
                throw BridgeProtocolException()
            }
        }.asTask()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
