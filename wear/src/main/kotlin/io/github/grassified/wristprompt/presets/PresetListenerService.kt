package io.github.grassified.wristprompt.presets

import com.google.android.gms.wearable.*
import io.github.grassified.wristprompt.WristPromptApplication
import io.github.grassified.wristprompt.bridge.BridgeProtocol
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Callbacks run off Main. Re-read durable DataItems; never retain a callback buffer. */
class PresetListenerService : WearableListenerService() {
    private fun refresh() {
        runBlocking { withTimeoutOrNull(10000) { (application as WristPromptApplication).presetSync.refresh() } }
    }
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith(PresetWire.PREFIX) == true }) refresh()
        // Removing a DataItem is never interpreted as deleting a preset.
    }
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == BridgeProtocol.MOBILE_CAPABILITY) refresh()
    }
}
