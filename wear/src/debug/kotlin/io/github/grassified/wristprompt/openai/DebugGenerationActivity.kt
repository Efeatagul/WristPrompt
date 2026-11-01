package io.github.grassified.wristprompt.openai

import android.content.*
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.*
import io.github.grassified.wristprompt.ui.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** Dedicated synthetic namespace. Production credentials/database/library are never opened here. */
internal class GenerationAcceptanceOwner(context: Context) : ViewModel() {
    private val ctx = object : ContextWrapper(context.createDeviceProtectedStorageContext()) {
        override fun getDatabasePath(name: String) = super.getDatabasePath("d7f-generation-acceptance.db")
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
            super.openOrCreateDatabase("d7f-generation-acceptance.db", mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
            super.openOrCreateDatabase("d7f-generation-acceptance.db", mode, factory, errorHandler)
    }
    private val countFile = File(ctx.filesDir, "d7f-synthetic-request-count")
    var requests = countFile.takeIf { it.isFile }?.readText()?.toIntOrNull() ?: 0; private set
    var behavior = "COMPLETE"
    val store = SqliteConversationStore(ctx)
    val images = GeneratedImages(File(ctx.filesDir, "d7f-generated-acceptance"))
    private var release = CompletableDeferred<Unit>()
    private var commitRelease = CompletableDeferred<Unit>()
    @Volatile var owned = false; private set
    var commitEntered = CompletableDeferred<Unit>(); private set
    val database = object : ConversationStore by store {
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (entry.snapshot.terminal == "COMPLETED" && behavior == "HOLD_COMMIT") {
                owned = true; commitEntered.complete(Unit); report(); commitRelease.await(); owned = false
            }
            return store.save(entry)
        }
    }
    private val mode = CredentialMode.OPENAI_API_KEY
    private val lease = CredentialLease("synthetic-generation", 0, "", Long.MAX_VALUE, mode)
    private val provider = object : AiProvider {
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("gpt-6.1-sol", "Synthetic reviewed model",
            imageGeneration = OpenAiGeneration.capability("gpt-6.1-sol", mode)))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Not proof")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            requests++; countFile.writeText(requests.toString()); onEvent(ResponseEvent.Completed("Synthetic text"))
        }
        override suspend fun generateImage(credential: CredentialLease, model: AiModel, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            OpenAiGeneration.request(model, credential.mode, input)
            requests++; countFile.writeText(requests.toString())
            onEvent(ResponseEvent.Delta("")); report()
            if (behavior == "HOLD_PROVIDER") { release = CompletableDeferred(); release.await() }
            val bitmap = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.rgb(12, 35, 78))
                drawCircle(170f, 58f, 32f, Paint().apply { color = Color.rgb(255, 210, 80) })
                drawRect(0f, 120f, 256f, 192f, Paint().apply { color = Color.rgb(30, 140, 160) })
                drawRect(34f, 85f, 83f, 149f, Paint().apply { color = Color.rgb(65, 180, 100) })
            }
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
            bitmap.recycle()
            val call = buildJsonObject {
                put("id", "ig_synthetic_$requests"); put("type", "image_generation_call"); put("status", "completed")
                put("output_format", "jpeg"); put("action", "generate"); put("result", Base64.getEncoder().encodeToString(bytes))
            }
            val terminal = buildJsonObject { put("type", "response.completed"); put("response", buildJsonObject {
                put("status", "completed"); put("output", buildJsonArray { add(call) })
            }) }
            onEvent(ResponseEvents.decode(null, terminal.toString(), true))
        }
    }
    val vm = InferenceViewModel(MutableStateFlow(InferenceAccessState(client = lease.clientId, usable = true, mode = mode)),
        InferenceSession({ lease }, provider) { true }, conversationStore = database,
        conversationIdentity = { "f".repeat(64) }, generatedImages = images)
    private val children = ViewModelStore().also {
        ViewModelProvider.create(it, viewModelFactory { initializer { vm } })[InferenceViewModel::class]
    }
    init {
        viewModelScope.launch { images.recover(store) }
        viewModelScope.launch { combine(vm.chat, vm.state) { _, _ -> Unit }.collect { report() } }
    }
    fun report() {
        val chat = vm.chat.value
        File(contextFiles(), "debug-generation.txt").writeText("stage=${chat.stage} requests=$requests messages=${chat.messages.size} generated=${chat.messages.count { it.generated != null }} problem=${chat.problem ?: "NONE"} owned=$owned ready=${vm.state.value.stage == ProofStage.READY}\n")
    }
    private fun contextFiles() = ctx.filesDir
    fun command(value: String) {
        when (value) {
            "DRAFT" -> vm.draft("Draw a synthetic lake beneath the moon")
            "HOLD_PROVIDER", "HOLD_COMMIT", "COMPLETE" -> { behavior = value; commitRelease = CompletableDeferred(); commitEntered = CompletableDeferred() }
            "RELEASE" -> { release.complete(Unit); commitRelease.complete(Unit) }
            "STOP" -> vm.stop()
            "NEW" -> vm.newChat()
        }
        report()
    }
    override fun onCleared() { children.clear(); viewModelScope.launch { store.close() } }
}

/** Explicit emulator entry only; no automatic inference, and no production service injection. */
class DebugGenerationActivity : ComponentActivity() {
    private var owner: GenerationAcceptanceOwner? = null
    internal fun acceptanceOwner() = checkNotNull(owner)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra("command") == "RECREATE") recreate()
            else owner?.command(intent?.getStringExtra("command").orEmpty())
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTurnScreenOn(true)
        ContextCompat.registerReceiver(this, receiver, IntentFilter("$packageName.DEBUG_GENERATION"), ContextCompat.RECEIVER_EXPORTED)
        setContent {
            val fixture: GenerationAcceptanceOwner = viewModel(factory = viewModelFactory {
                initializer { GenerationAcceptanceOwner(applicationContext) }
            })
            owner = fixture
            WristPromptTheme {
                WearShellContent(WatchAccountState(connected = true), WatchAuthState.IDLE,
                    ApiKeyState(loaded = true, mode = CredentialMode.OPENAI_API_KEY, configured = true), KeySetupStatus.IDLE, false,
                    fixture.vm, ConnectionActions({}, {}, {}, {}, {}, {}, {}), Surface.COMPOSER)
            }
        }
    }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}
