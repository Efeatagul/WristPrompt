package io.github.grassified.wristprompt.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretRecord {
    fun read(): ByteArray?
    fun write(plaintext: ByteArray)
}

class SecureStorageException : IllegalStateException("Protected storage unavailable")

/** Each app has its own Keystore key; only ciphertext reaches AtomicFile or its backup. */
class EncryptedRecord(context: Context, name: String) : SecretRecord {
    private val file = AtomicFile(File(context.noBackupFilesDir, "$name.enc"))
    private val alias = "io.github.grassified.wristprompt.$name.v1"
    private val aad = alias.encodeToByteArray()

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        // A missing key must not silently reset an existing credential record.
        if (!create) throw SecureStorageException()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    @Synchronized override fun read(): ByteArray? = safe {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
        else {
            if (file.baseFile.length() > 1_048_576) throw SecureStorageException()
            GcmRecord.decrypt(file.readFully(), key(false), aad)
        }
    }

    @Synchronized override fun write(plaintext: ByteArray) = safe {
        require(plaintext.size <= 1_000_000)
        val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val encrypted = GcmRecord.encrypt(plaintext, key(!exists), aad)
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
            val persisted = file.readFully()
            try {
                if (!java.security.MessageDigest.isEqual(encrypted, persisted)) throw SecureStorageException()
            } finally { persisted.fill(0) }
        } catch (_: Exception) {
            file.failWrite(stream)
            throw SecureStorageException()
        } finally { encrypted.fill(0) }
    }

    private inline fun <T> safe(block: () -> T): T = try { block() } catch (_: Exception) { throw SecureStorageException() }
}

object GcmRecord {
    fun encrypt(plaintext: ByteArray, key: SecretKey, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }
    fun decrypt(record: ByteArray, key: SecretKey, aad: ByteArray): ByteArray {
        require(record.size >= 29 && record[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, record.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(record, 13, record.size - 13)
    }
}
