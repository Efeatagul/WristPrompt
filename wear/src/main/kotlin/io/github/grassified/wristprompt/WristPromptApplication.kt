package io.github.grassified.wristprompt

import io.github.grassified.wristprompt.auth.WatchProfiles
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
    val profiles by lazy { WatchProfiles(EncryptedRecord(this, "chatgpt_profiles")) }
    private val openAiHttp by lazy { OpenAiHttp() }
    private val refresh by lazy { OpenAiRefresh(openAiHttp) }
    val inference by lazy {
        InferenceSession(OpenAiCredentialSource { profiles.credential(refresh::refresh) },
            OpenAiProvider(openAiHttp), profiles::isCurrent)
    }
    val authBridge by lazy { WatchAuthBridge(hostIds::getOrCreate, connectivity, profiles::prepare) }
}
