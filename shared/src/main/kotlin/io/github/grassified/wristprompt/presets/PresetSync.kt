package io.github.grassified.wristprompt.presets

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PresetPacket(val version: Int = 1, val source: String, val catalog: PresetCatalog) {
    override fun toString() = "PresetPacket([redacted])"
}

object PresetWire {
    const val PREFIX = "/wristprompt/presets/v1/"
    const val MAX_PACKET_BYTES = 65536
    private val json = Json { encodeDefaults = true }
    fun validId(value: String) = value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
    fun path(record: PromptPreset) = PREFIX + record.id
    fun encode(packet: PresetPacket): ByteArray {
        if (packet.version != 1 || !validId(packet.source) || packet.catalog.records.size != 1) invalid()
        PresetCodec.encode(packet.catalog)
        return json.encodeToString(packet).encodeToByteArray().also { if (it.size > MAX_PACKET_BYTES) invalid() }
    }
    fun decode(path: String, bytes: ByteArray): PresetPacket {
        if (bytes.size > MAX_PACKET_BYTES) invalid()
        val packet = try { json.decodeFromString<PresetPacket>(bytes.decodeToString(throwOnInvalidSequence = true)) }
        catch (_: Exception) { invalid() }
        if (!encode(packet).contentEquals(bytes) || path != path(packet.catalog.records.single())) invalid()
        return packet
    }
    fun invalid(): Nothing = throw PresetException(PresetProblem.INVALID_DATA)
}

/** No payload or peer metadata is printable. */
class PresetDelivery(val path: String, val bytes: ByteArray) {
    override fun toString() = "PresetDelivery([redacted])"
}
