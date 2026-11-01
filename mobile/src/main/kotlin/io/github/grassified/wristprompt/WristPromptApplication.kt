package io.github.grassified.wristprompt

import io.github.grassified.wristprompt.auth.IssuedRegistrationStore
import io.github.grassified.wristprompt.auth.ApiKeyInbox
import io.github.grassified.wristprompt.auth.OpenAiAuthorization
import io.github.grassified.wristprompt.storage.EncryptedRecord
import io.github.grassified.wristprompt.presets.PresetRepository
import kotlinx.coroutines.launch
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.grassified.wristprompt.bridge.AuthRequestInbox
import io.github.grassified.wristprompt.bridge.AuthStartReceiver
import io.github.grassified.wristprompt.bridge.BridgeProtocol
import io.github.grassified.wristprompt.bridge.WearConnectivity

private val Context.bridgeStore by preferencesDataStore(name = "bridge_receipt")

private val Context.presetStore by preferencesDataStore(name = "prompt_presets")

class WristPromptApplication : Application() {
    private val presetScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val presets by lazy { PresetRepository(presetStore) { queuePresets() } }
    val presetPublisher by lazy { io.github.grassified.wristprompt.presets.PresetPublisher(presets::load, presets::source,
        { connectivity.reachablePeers().isNotEmpty() }, connectivity::putPreset) }
    fun queuePresets() { presetScope.launch { presetPublisher.publish() } }
    val images by lazy { io.github.grassified.wristprompt.images.ImageFileStore(java.io.File(noBackupFilesDir, "image_library_v1")) }
    val imageSync by lazy { io.github.grassified.wristprompt.images.ImageDataSync(this, connectivity, images) }
    fun queueImages() { presetScope.launch { imageSync.publish() } }
    override fun onCreate() { super.onCreate(); queuePresets(); queueImages() }
    val connectivity by lazy { WearConnectivity(this, BridgeProtocol.WEAR_CAPABILITY) }
    val issuedRegistrations by lazy { IssuedRegistrationStore(EncryptedRecord(this, "issued_registrations")) }
    val authorization by lazy { OpenAiAuthorization() }
    val apiKeyInbox by lazy { ApiKeyInbox(connectivity::reachablePeers) }
    val authInbox by lazy { AuthRequestInbox(bridgeStore) }
    val authReceiver by lazy { AuthStartReceiver(authInbox, connectivity::reachablePeers) }
}
