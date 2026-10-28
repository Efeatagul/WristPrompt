package io.github.grassified.wristprompt.storage

import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class GcmRecordTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun authenticatedCiphertextRoundTripsWithFreshIv() {
        val value = "synthetic-secret".encodeToByteArray(); val aad = "record-v1".encodeToByteArray()
        val first = GcmRecord.encrypt(value, key, aad); val second = GcmRecord.encrypt(value, key, aad)
        assertFalse(first.contentEquals(second)); assertFalse(first.decodeToString().contains("synthetic-secret"))
        assertArrayEquals(value, GcmRecord.decrypt(first, key, aad))
    }
    @Test fun tamperingWrongContextAndWrongKeyFailClosed() {
        val aad = "watch".encodeToByteArray()
        val encrypted = GcmRecord.encrypt("synthetic-secret".encodeToByteArray(), key, aad)
        val tampered = encrypted.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { GcmRecord.decrypt(tampered, key, aad) }
        assertThrows(Exception::class.java) { GcmRecord.decrypt(encrypted, key, "phone".encodeToByteArray()) }
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(Exception::class.java) { GcmRecord.decrypt(encrypted, other, aad) }
    }
}
