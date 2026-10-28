package io.github.grassified.wristprompt.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** API catalog IDs carry no invented Responses/text capability flags. */
object ApiModelCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    fun read(body: String): List<AiModel> = try {
        val data = json.decodeFromString<Catalog>(body).data
        require(data.size <= 1000)
        val models = data.map {
            require(it.id.isNotBlank() && it.id.length <= 256 && it.id.none(Char::isISOControl))
            AiModel(it.id, it.id)
        }
        require(models.map { it.slug }.distinct().size == models.size)
        models
    } catch (_: Exception) { throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = ProtocolDetail.CATALOG_MODELS) }
    @Serializable private data class Catalog(val data: List<Model>)
    @Serializable private data class Model(val id: String)
}
