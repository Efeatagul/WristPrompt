package io.github.grassified.wristprompt.openai

import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {
    private val visible = """{"slug":"account-model","display_name":"Account model","visibility":"list"}"""

    @Test fun largeCatalogMetadataIsSkippedAndOrderPreservedAcrossSplitReads() {
        val body = """{"ignored":"${"x".repeat(300_000)}","models":[$visible,{"slug":"hidden","visibility":"hidden","metadata":{"nested":[1,2,3]}},{"slug":"second","display_name":"Second","visibility":"list","extra":"${"y".repeat(300_000)}"}]}"""
        val input = object : ByteArrayInputStream(body.encodeToByteArray()) {
            override fun read(bytes: ByteArray, offset: Int, length: Int) = super.read(bytes, offset, minOf(7, length))
        }
        assertEquals(listOf(AiModel("account-model", "Account model"), AiModel("second", "Second")), ModelCatalog.read(input))
    }
    @Test fun providerCanReadCatalogLargerThanOldBodyLimit() = kotlinx.coroutines.runBlocking {
        val body = """{"models":[$visible],"ignored":"${"x".repeat(300_000)}"}"""
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("Synthetic").body(body.toResponseBody()).build()
        }.build()
        assertEquals("account-model", OpenAiProvider(OpenAiHttp(client)).models(CredentialLease("client", 0, "synthetic", 1)).single().slug)
    }
    @Test fun totalBodyLimitStillAppliesToDiscardedMetadata() {
        val body = """{"models":[$visible],"ignored":"${"x".repeat(ModelCatalog.MAX_BYTES.toInt())}"}"""
        val failure = assertThrows(ApiFailure::class.java) { ModelCatalog.read(body.byteInputStream()) }
        assertEquals(ProtocolDetail.BODY_TOO_LARGE, failure.protocolDetail)
        assertNull(failure.cause)
    }
    @Test fun excessiveModelCountAndDuplicateVisibleIdsAreRejected() {
        val count = assertThrows(ApiFailure::class.java) {
            ModelCatalog.read(("{\"models\":[" + List(1001) { "{}" }.joinToString(",") + "]}").byteInputStream())
        }
        assertEquals(ProtocolDetail.CATALOG_MODELS, count.protocolDetail)
        val duplicate = assertThrows(ApiFailure::class.java) { ModelCatalog.read("""{"models":[$visible,$visible]}""".byteInputStream()) }
        assertEquals(ProtocolDetail.CATALOG_DUPLICATE, duplicate.protocolDetail)
    }
    @Test fun malformedTruncatedTrailingAndInvalidUtf8FailClosed() {
        val malformedUtf8 = """{"models":[{"slug":"a","visibility":"list","display_name":"""".encodeToByteArray() +
            byteArrayOf(0xc3.toByte()) + "\"}]}".encodeToByteArray()
        for ((index, bytes) in listOf("{".encodeToByteArray(), """{"models":[]}{}""".encodeToByteArray(), malformedUtf8).withIndex()) {
            val failure = assertThrows("Malformed case $index", ApiFailure::class.java) { ModelCatalog.read(ByteArrayInputStream(bytes)) }
            assertEquals(ApiProblem.PROTOCOL, failure.problem)
            assertNull(failure.cause)
        }
    }
    @Test fun utf8ValidationHandlesSplitUnicodeAndRejectsOverlongSurrogateAndOutOfRangeBytes() {
        fun payload(name: ByteArray) =
            """{"models":[{"slug":"a","visibility":"list","display_name":"""".encodeToByteArray() + name + "\"}]}".encodeToByteArray()
        val valid = payload("Türkçe 😀".encodeToByteArray())
        val split = object : ByteArrayInputStream(valid) {
            override fun read(bytes: ByteArray, offset: Int, length: Int) = super.read(bytes, offset, minOf(1, length))
        }
        assertEquals("Türkçe 😀", ModelCatalog.read(split).single().displayName)
        for (name in listOf(
            byteArrayOf(0xc0.toByte(), 0x80.toByte()),
            byteArrayOf(0xe0.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf0.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
        )) {
            val failure = assertThrows(ApiFailure::class.java) { ModelCatalog.read(ByteArrayInputStream(payload(name))) }
            assertEquals(ProtocolDetail.INVALID_UTF8, failure.protocolDetail)
        }
    }
    @Test fun cancellationAndTransportFailuresArePreserved() {
        assertThrows(CancellationException::class.java) {
            ModelCatalog.read("{}".byteInputStream()) { throw CancellationException("Synthetic") }
        }
        assertThrows(IOException::class.java) {
            ModelCatalog.read(object : InputStream() { override fun read(): Int = throw IOException("Synthetic") })
        }
    }
}
