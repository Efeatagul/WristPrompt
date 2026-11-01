package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.images.ImageLimits
import io.github.grassified.wristprompt.openai.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GeneratedImageProjectionTest {
    @Test fun generatedAttachmentSurvivesPlainAndMarkdownProjectionWithoutFakeText() {
        val reference = GeneratedAttachment(ImageLimits.newId(), 16, 12, 3, "a".repeat(64), 2, "b".repeat(64))
        val call = buildJsonObject { put("type", "image_generation_call"); put("id", "ig_synthetic") }
        val result = StreamResult().apply { accept(ResponseEvent.Completed("", listOf(call), GeneratedOutput(byteArrayOf(1)))) }
        val chat = ChatConversation().apply { draft("draw"); begin(); complete(result, reference) }
        val plain = PlainTextProjection().project(chat.state)
        val rich = MarkdownProjection().project(chat.state, plain)
        for (presentation in listOf(plain, rich)) {
            assertEquals(reference, presentation.rows.single { it.type == TranscriptType.IMAGE }.generated)
            assertTrue(presentation.rows.none { it.text.contains("ig_synthetic") || it.text.contains(reference.id) })
        }
        assertEquals(listOf("user", null), chat.snapshot().replay.map { it["role"]?.jsonPrimitive?.content })
    }
}
