package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Retains an issued client before exchange so failed new registrations aren't recreated blindly.
 * No tokens, codes, verifier, or browser transaction state are persisted on the phone.
 */
class IssuedRegistrationStore(private val record: SecretRecord) {
    private val mutex = Mutex()
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private fun read(): Map<String, String> {
        val bytes = record.read() ?: return emptyMap()
        return try { ProfileProtocol.json.decodeFromString(serializer, bytes.decodeToString()) }
        finally { bytes.fill(0) }
    }
    suspend fun client(hostId: String): String? = withContext(Dispatchers.IO) { mutex.withLock { read()[hostId] } }
    suspend fun save(hostId: String, clientId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(OpenAiAuth.validClient(clientId))
            val values = read() + (hostId to clientId)
            require(values.size <= 20)
            val bytes = ProfileProtocol.json.encodeToString(serializer, values).encodeToByteArray()
            try { record.write(bytes) } finally { bytes.fill(0) }
        }
    }
    suspend fun clear(hostId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = ProfileProtocol.json.encodeToString(serializer, read() - hostId).encodeToByteArray()
            try { record.write(bytes) } finally { bytes.fill(0) }
        }
    }
}
