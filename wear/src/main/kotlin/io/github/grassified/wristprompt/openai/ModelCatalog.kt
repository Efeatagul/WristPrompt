package io.github.grassified.wristprompt.openai

import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/** Decode only picker fields; discard other service metadata without buffering the body. */
object ModelCatalog {
    const val MAX_BYTES = 8_388_608L
    private const val MAX_MODELS = 1000
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun read(input: InputStream, checkActive: () -> Unit = {}): List<AiModel> {
        var detail = ProtocolDetail.CATALOG_JSON
        val bounded = LimitedInput(input, checkActive)
        return try {
            val catalog = json.decodeFromStream<Catalog>(bounded)
            detail = ProtocolDetail.CATALOG_MODELS
            val entries = catalog.models ?: throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = detail)
            require(entries.size <= MAX_MODELS)
            detail = ProtocolDetail.CATALOG_MODEL
            val models = entries.mapNotNull { entry ->
                if (entry.visibility != "list") null else {
                    val slug = entry.slug ?: error("Missing slug")
                    val name = entry.displayName ?: error("Missing name")
                    require(slug.isNotBlank() && slug.length <= 256 && slug.none(Char::isISOControl))
                    require(name.isNotBlank() && name.length <= 256 && name.none(Char::isISOControl))
                    AiModel(slug, name)
                }
            }
            detail = ProtocolDetail.CATALOG_DUPLICATE
            require(models.map(AiModel::slug).distinct().size == models.size)
            models
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: ApiFailure) { throw failure
        } catch (failure: IOException) { throw failure
        } catch (_: Exception) { throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = bounded.failure ?: detail) }
    }

    private class LimitedInput(input: InputStream, private val checkActive: () -> Unit) : FilterInputStream(input) {
        private var count = 0L
        var failure: ProtocolDetail? = null; private set
        private var continuation = 0
        private var lower = 0x80
        private var upper = 0xbf
        private fun reject(detail: ProtocolDetail): Nothing {
            failure = detail
            throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = detail)
        }
        // Validate UTF-8 even inside skipped fields: the library stream decoder can
        // replace malformed sequences, whereas buffered responses reject them.
        private fun validate(value: Int) {
            if (continuation > 0) {
                if (value !in lower..upper) reject(ProtocolDetail.INVALID_UTF8)
                continuation--; lower = 0x80; upper = 0xbf
            } else when (value) {
                in 0..0x7f -> Unit
                in 0xc2..0xdf -> continuation = 1
                in 0xe0..0xef -> {
                    continuation = 2
                    if (value == 0xe0) lower = 0xa0
                    if (value == 0xed) upper = 0x9f
                }
                in 0xf0..0xf4 -> {
                    continuation = 3
                    if (value == 0xf0) lower = 0x90
                    if (value == 0xf4) upper = 0x8f
                }
                else -> reject(ProtocolDetail.INVALID_UTF8)
            }
        }
        private fun counted(size: Int): Int {
            if (size > 0) count += size
            if (count > MAX_BYTES) reject(ProtocolDetail.BODY_TOO_LARGE)
            if (size == -1 && continuation != 0) reject(ProtocolDetail.INVALID_UTF8)
            return size
        }
        override fun read(): Int {
            checkActive()
            val value = `in`.read()
            counted(if (value < 0) -1 else 1)
            if (value >= 0) validate(value)
            return value
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            checkActive()
            val size = counted(`in`.read(bytes, offset, minOf(length.toLong(), MAX_BYTES - count + 1).toInt()))
            for (index in offset until offset + size.coerceAtLeast(0)) validate(bytes[index].toInt() and 0xff)
            return size
        }
    }

    @Serializable private data class Catalog(val models: List<Entry>? = null)
    @Serializable private data class Entry(
        val slug: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        val visibility: String? = null,
    )
}
