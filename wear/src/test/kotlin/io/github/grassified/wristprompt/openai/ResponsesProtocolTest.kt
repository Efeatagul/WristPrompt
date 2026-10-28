package io.github.grassified.wristprompt.openai

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ResponsesProtocolTest {
    @Test fun catalogFiltersVisibilityAndPreservesServerOrderingAndNames() {
        val models = ResponsesProtocol.models("""{"models":[{"slug":"z","display_name":"Last alphabetically","visibility":"list","extra":true},{"slug":"hidden","visibility":"hidden"},{"slug":"a","display_name":"First alphabetically","visibility":"list"},{"slug":"not-listed"}]}""")
        assertEquals(listOf("z", "a"), models.map { it.slug })
        assertEquals(listOf("Last alphabetically", "First alphabetically"), models.map { it.displayName })
    }
    @Test fun emptyCatalogIsRepresentableButWrongShapeAndMalformedVisibleModelsFail() {
        assertTrue(ResponsesProtocol.models("""{"models":[]}""").isEmpty())
        for (body in listOf("""{"data":[]}""", """{"models":[{"visibility":"list","slug":"a"}]}""",
            """{"models":[{"visibility":"list","slug":"a","display_name":"A"},{"visibility":"list","slug":"a","display_name":"A"}]}""")) {
            assertThrows(ApiFailure::class.java) { ResponsesProtocol.models(body) }
        }
    }
    @Test fun catalogFailuresExposeOnlyStaticCategories() {
        for ((body, detail) in listOf(
            "synthetic-sensitive-body" to ProtocolDetail.CATALOG_JSON,
            """{"data":[]}""" to ProtocolDetail.CATALOG_MODELS,
            """{"models":[{"visibility":"list","slug":"a"}]}""" to ProtocolDetail.CATALOG_MODEL,
            """{"models":[{"visibility":"list","slug":"a","display_name":"A"},{"visibility":"list","slug":"a","display_name":"A"}]}""" to ProtocolDetail.CATALOG_DUPLICATE,
        )) {
            val failure = assertThrows(ApiFailure::class.java) { ResponsesProtocol.models(body) }
            assertEquals(detail, failure.protocolDetail)
            assertNull(failure.cause)
            assertFalse(failure.message.orEmpty().contains("synthetic-sensitive-body"))
            assertEquals("[REDACTED]", failure.toString())
        }
    }
    @Test fun requestHasOnlySupportedFieldsAndRequiredArrayInput() {
        val body = Json.parseToJsonElement(ResponsesProtocol.request("account-model")).jsonObject
        assertEquals(setOf("model", "input", "store", "stream"), body.keys)
        assertEquals("account-model", body.getValue("model").jsonPrimitive.content)
        assertFalse(body.getValue("store").jsonPrimitive.boolean)
        assertTrue(body.getValue("stream").jsonPrimitive.boolean)
        val input = body.getValue("input").jsonArray.single().jsonObject
        assertEquals(setOf("role", "content"), input.keys)
        assertEquals("user", input.getValue("role").jsonPrimitive.content)
        assertEquals("Reply with exactly: WristPrompt works.", input.getValue("content").jsonPrimitive.content)
    }
}
