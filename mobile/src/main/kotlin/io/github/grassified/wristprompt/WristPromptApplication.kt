package io.github.grassified.wristprompt

import io.github.grassified.wristprompt.auth.IssuedRegistrationStore
import io.github.grassified.wristprompt.auth.ApiKeyInbox
import io.github.grassified.wristprompt.auth.OpenAiAuthorization
import io.github.grassified.wristprompt.storage.EncryptedRecord
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.grassified.wristprompt.bridge.AuthRequestInbox
import io.github.grassified.wristprompt.bridge.AuthStartReceiver
import io.github.grassified.wristprompt.bridge.BridgeProtocol
import io.github.grassified.wristprompt.bridge.WearConnectivity

private val Context.bridgeStore by preferencesDataStore(name = "bridge_receipt")

class WristPromptApplication : Application() {
    val connectivity by lazy { WearConnectivity(this, BridgeProtocol.WEAR_CAPABILITY) }
    val issuedRegistrations by lazy { IssuedRegistrationStore(EncryptedRecord(this, "issued_registrations")) }
    val authorization by lazy { OpenAiAuthorization() }
    val apiKeyInbox by lazy { ApiKeyInbox(connectivity::reachablePeers) }
    val authInbox by lazy { AuthRequestInbox(bridgeStore) }
    val authReceiver by lazy { AuthStartReceiver(authInbox, connectivity::reachablePeers) }
}
