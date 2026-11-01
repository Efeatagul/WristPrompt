package io.github.grassified.wristprompt.storage

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.images.*
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.ByteArrayOutputStream

/** Isolated native JPEG/SQLite/VM/session gate. Synthetic provider only; no credentials or live HTTP.
 * Run seed and verify as separate instrumentation processes to test inert restart/reopen.
 */
class ImageInferenceInstrumentation : Instrumentation() {
    private var checkpoint = "seed"
    private var phase = "initialization"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); checkpoint = arguments?.getString("checkpoint") ?: "seed"; start() }
    private val isolated: Context get() = object : ContextWrapper(targetContext.createDeviceProtectedStorageContext()) {
        override fun getDatabasePath(name: String) = super.getDatabasePath("d7d-image-inference.db")
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            super.openOrCreateDatabase("d7d-image-inference.db", mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            super.openOrCreateDatabase("d7d-image-inference.db", mode, factory, errorHandler)
    }
    override fun onStart() {
        val outcome = Bundle()
        try {
            check(context.packageName.endsWith(".test"))
            runBlocking { verify() }
            outcome.putString("stream", "PASS: D7-D $checkpoint native JPEG, isolated SQLite, image Send/replay ownership, errors/Stop, zero automatic inference; synthetic provider only\n")
            finish(Activity.RESULT_OK, outcome)
        } catch (failure: Exception) {
            outcome.putString("stream", "FAIL: D7-D $checkpoint $phase (${failure.javaClass.simpleName}); payloads redacted\n")
            finish(Activity.RESULT_CANCELED, outcome)
        }
    }
    private class Provider : AiProvider {
        var count = 0
        val inputs = mutableListOf<List<JsonObject>>()
        var stop = false
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("gpt-6.1-sol", "Synthetic supported"), AiModel("unknown-model", "Synthetic unsupported"))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) { error("Chat only") }
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            // Exercise the exact public HTTP serializer without a network request.
            ResponsesProtocol.request(model, input, mode = credential.mode)
            count++; inputs += input
            onEvent(ResponseEvent.Delta("synthetic partial"))
            if (stop) awaitCancellation()
            onEvent(ResponseEvent.Completed("synthetic answer"))
        }
    }
    private suspend fun verify() {
        val ctx = isolated
        val directory = File(ctx.noBackupFilesDir, "d7d-image-inference-fixture")
        check(checkpoint in setOf("seed", "verify"))
        if (checkpoint == "seed") {
            // Dedicated synthetic namespace only, never the production library/database.
            ctx.deleteDatabase("d7d-image-inference.db")
            directory.deleteRecursively()
        }
        val library = ImageFileStore(File(directory, "library"))
        val images = ConversationImages(File(directory, "replay"), library)
        val store = SqliteConversationStore(ctx)
        val provider = Provider()
        val lease = CredentialLease("synthetic-client", 0, "synthetic-token", Long.MAX_VALUE)
        val vm = withContext(Dispatchers.Main) {
            InferenceViewModel(MutableStateFlow(InferenceAccessState(client = "synthetic-client", usable = true)),
                InferenceSession({ lease }, provider) { true }, conversationStore = store,
                conversationIdentity = { "d".repeat(64) }, conversationImages = images)
        }
        suspend fun main(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
        suspend fun done() = withTimeout(10000) { vm.chat.first { it.stage == ChatStage.COMPLETED } }
        suspend fun problem(value: ApiProblem) = withTimeout(10000) { vm.chat.first { it.problem == value } }
        suspend fun select(ids: List<String>) = main { check(vm.imageSelection.replace(vm.imageSelection.selected.value, ids, ids.toSet())) }
        suspend fun open(id: String) {
            val opened = CompletableDeferred<Unit>()
            main { vm.openChat(id) { opened.complete(Unit) } }; withTimeout(10000) { opened.await() }
        }
        try {
            withTimeout(10000) { vm.state.first { it.stage == ProofStage.READY } }
            check(provider.count == 0)
            val owner = store.owner("d".repeat(64))
            if (checkpoint == "verify") {
                phase = "restart-reopen"
                check(vm.chat.value.messages.first().attachments.size == 2)
                val saved = store.list(owner).single()
                open(saved.id); check(provider.count == 0)
                check(library.load().records.all { it.deleted })
                main { vm.draft("explicit after restart"); vm.send() }; done()
                check(provider.count == 1 && provider.inputs.single().size == 3)
                check(provider.inputs.single().first()["content"]!!.jsonArray.size == 3)
                return
            }
            phase = "native-fixtures"
            val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            bitmap.recycle()
            val ids = library.importBatch(3) { AndroidImages.normalize(bytes) }.records.map { it.id }
            phase = "one-image-send"
            select(ids.take(1)); check(provider.count == 0)
            main { vm.draft("one image"); vm.send(); vm.send() }; done(); check(provider.count == 1)
            check(provider.inputs.last().single()["content"]!!.jsonArray.size == 2)
            main { vm.newChat() }
            phase = "unsupported-route"
            main { vm.select("unknown-model") }
            withTimeout(10000) { vm.state.first { it.selectedSlug == "unknown-model" } }
            select(ids.take(2)); main { vm.draft("preserve unsupported draft"); vm.send() }; problem(ApiProblem.IMAGES_UNSUPPORTED)
            check(provider.count == 1 && vm.imageSelection.selected.value == ids.take(2))
            main { vm.select("gpt-6.1-sol") }
            withTimeout(10000) { vm.state.first { it.selectedSlug == "gpt-6.1-sol" } }
            phase = "missing-corrupt-validation"
            val file = File(directory, "library/${ids.last()}-1-image.jpg")
            val original = file.readBytes()
            select(ids.takeLast(1)); file.delete()
            main { vm.draft("preserve missing draft"); vm.send() }; problem(ApiProblem.IMAGE_UNAVAILABLE)
            check(provider.count == 1 && vm.chat.value.draft == "preserve missing draft" && vm.imageSelection.selected.value == ids.takeLast(1))
            file.writeBytes(ByteArray(original.size))
            main { vm.draft("preserve corrupt draft"); vm.send() }; problem(ApiProblem.IMAGE_UNAVAILABLE)
            check(provider.count == 1 && vm.chat.value.draft == "preserve corrupt draft")
            file.writeBytes(original)
            phase = "image-stop"
            select(ids.take(2)); provider.stop = true
            main { vm.draft("stop images"); vm.send() }
            withTimeout(10000) { vm.chat.first { it.stage == ChatStage.RECEIVING } }
            main { vm.stop() }; check(provider.count == 2)
            check(vm.chat.value.messages.single().status == ChatMessageStatus.EXCLUDED)
            main { vm.newChat() }; provider.stop = false
            phase = "multi-image-send-and-ownership"
            select(ids.take(2)); main { vm.draft("two images"); vm.send(); vm.send() }; done()
            check(provider.count == 3 && provider.inputs.last().single()["content"]!!.jsonArray.size == 3)
            val latest = store.list(owner).first()
            // Keep only the successful multi-image chat in this isolated namespace.
            store.list(owner).filter { it.id != latest.id }.forEach { store.delete(it.id, owner, it.revision); images.delete(it.id) }
            ids.forEach { library.delete(it) }
            open(latest.id); check(provider.count == 3)
            check(vm.chat.value.messages.first().attachments == ids.take(2))
        } finally {
            withContext(Dispatchers.Main) { vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
            store.close()
        }
    }
}
