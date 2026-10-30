package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.AiModel
import io.github.grassified.wristprompt.openai.ApiModelCatalog
import org.junit.Assert.*
import org.junit.Test

class ModelItemsTest {
    @Test fun authoritativeNameIsUnmodifiedWithoutAddedMetadata() {
        val model = AiModel("opaque-slug", "Service supplied name")
        assertEquals("Service supplied name", modelRowName(model))
    }
    @Test fun apiCatalogShowsRawId() {
        val model = ApiModelCatalog.read("""{"data":[{"id":"raw-model-id"}]}""").single()
        assertEquals("raw-model-id", modelRowName(model))
        assertEquals("raw-model-id", modelRowName(AiModel("raw-model-id", "")))
    }
    @Test fun keysDependOnSlugNotNameSelectionOrPosition() {
        val model = AiModel("stable", "Name")
        assertEquals(modelRowKey(model), modelRowKey(model.copy(displayName = "Renamed")))
        assertNotEquals(modelRowKey(model), modelRowKey(AiModel("other", "Name")))
    }
    @Test fun thousandModelsKeepServiceOrderAndUniqueIdentity() {
        val models = (1000 downTo 1).map { AiModel("slug-$it", "Name $it") }
        assertEquals(1000, models.map(::modelRowKey).toSet().size)
        assertEquals((1000 downTo 1).map { "Name $it" }, models.map(::modelRowName))
    }
}
