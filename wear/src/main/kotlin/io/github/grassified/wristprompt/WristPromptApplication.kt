package io.github.grassified.wristprompt

import io.github.grassified.wristprompt.auth.WatchProfiles
import io.github.grassified.wristprompt.auth.ApiKeyRepository
import io.github.grassified.wristprompt.auth.ApiKeySetupBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import io.github.grassified.wristprompt.auth.OpenAiRevocation
import io.github.grassified.wristprompt.storage.EncryptedRecord
import io.github.grassified.wristprompt.openai.*
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.grassified.wristprompt.bridge.BridgeProtocol
import io.github.grassified.wristprompt.bridge.WatchAuthBridge
import io.github.grassified.wristprompt.bridge.WearConnectivity
import io.github.grassified.wristprompt.storage.HostIdRepository

private val Context.installationStore by preferencesDataStore(name = "installation")

class WristPromptApplication : Application() {
    private val connectivity by lazy { WearConnectivity(this, BridgeProtocol.MOBILE_CAPABILITY) }
    private val hostIds by lazy { HostIdRepository(installationStore) }
    private val sessionClock = SessionClock.SYSTEM
    val profiles by lazy { WatchProfiles(EncryptedRecord(this, "chatgpt_profiles"), now = sessionClock) }
    val apiKeys by lazy { ApiKeyRepository(EncryptedRecord(this, "api_key"), sessionClock) }
    val conversations by lazy { io.github.grassified.wristprompt.storage.SqliteConversationStore(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val credentials by lazy { CredentialRouter(profiles, apiKeys, refresh::refresh, appScope) }
    val apiKeyBridge by lazy { ApiKeySetupBridge(hostIds::getOrCreate, connectivity::reachablePeers, connectivity::request, apiKeys::prepare) }
    private val openAiHttp by lazy { OpenAiHttp() }
    val revocation by lazy { OpenAiRevocation(openAiHttp) }
    private val refresh by lazy { OpenAiRefresh(openAiHttp, sessionClock) }
    val inference by lazy {
        InferenceSession(credentials, OpenAiProvider(openAiHttp), credentials::isCurrent)
    }
    val authBridge by lazy { WatchAuthBridge(hostIds::getOrCreate, connectivity, profiles::prepare) }
}
