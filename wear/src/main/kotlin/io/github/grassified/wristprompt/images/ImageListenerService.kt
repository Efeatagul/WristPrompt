package io.github.grassified.wristprompt.images

import com.google.android.gms.wearable.*
import io.github.grassified.wristprompt.WristPromptApplication
import io.github.grassified.wristprompt.bridge.BridgeProtocol
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class ImageListenerService : WearableListenerService() {
    private fun refresh() {
        runBlocking { withTimeoutOrNull(65_000) { (application as WristPromptApplication).imageSync.refresh() } }
    }
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(ImageWire.PREFIX) == true }) refresh()
        // DataItem removal is not authority to delete a local image; only tombstones are.
    }
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == BridgeProtocol.MOBILE_CAPABILITY) refresh()
    }
}
