package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.GcmRecord
import io.github.grassified.wristprompt.storage.SecretRecord
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before

/** Real repositories/router/session/ViewModel and catalog decoder; only disk-key/HTTP boundaries are synthetic. */
@OptIn(ExperimentalCoroutinesApi::class)
open class ReconstructionFixture {
    protected var time = 1_000_000L
    protected val host = BridgeProtocol.newHostId()
    protected val terra = "gpt-6.1-terra"
    protected val sol = "gpt-6.1-sol"
    protected lateinit var main: ExecutorCoroutineDispatcher
    protected lateinit var directory: File
    private val runtimes = mutableListOf<Runtime>()

    @Before fun setup() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        directory = Files.createTempDirectory("synthetic-model-restoration").toFile()
    }
    @After fun cleanup(): Unit = runBlocking {
        runtimes.forEach { it.close() }
        Dispatchers.resetMain()
        main.close()
        directory.deleteRecursively()
    }

    // New SecretRecord wrappers decrypt a real file on every read. No plaintext vault or cached repository survives restart.
    // This exercises production GCM/serialization, not Android Keystore or AtomicFile/device durability.
    protected inner class Disk(name: String) {
        private val file = File(directory, name)
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        private val aad = name.encodeToByteArray()
        val writes = AtomicInteger()
        fun snapshot(): ByteArray = record().read()!!
        fun corrupt() { file.writeBytes(byteArrayOf(1, 2, 3)) }
        fun record() = object : SecretRecord {
            override fun read(): ByteArray? = synchronized(this@Disk) {
                if (file.exists()) GcmRecord.decrypt(file.readBytes(), key, aad) else null
            }
            override fun write(plaintext: ByteArray) = synchronized(this@Disk) {
                file.writeBytes(GcmRecord.encrypt(plaintext, key, aad))
                writes.incrementAndGet()
                Unit
            }
        }
    }
    protected fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, time)
    protected suspend fun addPlan(p: WatchProfiles, client: String = "client_one", seconds: Long = 3600) {
        val r = request()
        p.prepare("phone", r)
        p.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, r.requestId, BridgeProtocol.newRequestId(),
            CredentialProfile(RegistrationIdentity(client, OpenAiAuth.ISSUER, "subject_$client"), host,
                "Synthetic", null, false, "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer",
                seconds, time, OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)))))
    }
    protected suspend fun seed(oauth: Disk, api: Disk, saved: String? = terra) {
        val p = WatchProfiles(oauth.record(), main) { time }
        val k = ApiKeyRepository(api.record()) { time }
        addPlan(p)
        k.load()
        if (saved != null) p.selectModel("client_one", p.state.value.generation, saved)
    }

    protected inner class Catalog {
        @Volatile var order = listOf(sol, terra)
        val calls = AtomicInteger()
        val generations = AtomicInteger()
        val inputs = java.util.concurrent.CopyOnWriteArrayList<List<kotlinx.serialization.json.JsonObject>>()
        val leases = java.util.concurrent.CopyOnWriteArrayList<CredentialLease>()
        var answer: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { emit -> emit(ResponseEvent.Completed("Synthetic answer")) }
        var label: (String) -> String = { "Label $it" }
        var before: suspend (CredentialLease) -> Unit = {}
        private val provider = OpenAiProvider(OpenAiHttp(OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(ResponsesProtocol.MODELS_URL, chain.request().url.toString())
            val api = chain.request().header("Authorization") == "Bearer synthetic-api-key"
            val body = if (api) """{"data":[{"id":"api-model"}]}""" else
                order.joinToString(prefix = "{\"models\":[", postfix = "]}") {
                    """{"slug":"$it","display_name":"${label(it)}","visibility":"list"}"""
                }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()))
        val transport = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> {
                calls.incrementAndGet(); leases += credential
                before(credential)
                return provider.models(credential)
            }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) =
                error("Chat only")
            override suspend fun stream(credential: CredentialLease, model: String,
                input: List<kotlinx.serialization.json.JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
                generations.incrementAndGet(); inputs += input; leases += credential; answer(onEvent)
            }
        }
    }
    protected inner class Runtime(oauth: Disk, api: Disk, val catalog: Catalog = Catalog(),
        beforeRead: suspend (CredentialLease) -> Unit = {},
        refresh: suspend (CredentialProfile) -> CredentialProfile = { error("Fresh synthetic credentials must not refresh") }) {
        val p = WatchProfiles(oauth.record(), main) { time }
        val k = ApiKeyRepository(api.record()) { time }
        private val scope = CoroutineScope(SupervisorJob() + main)
        val router = CredentialRouter(p, k, refresh, scope)
        val session = InferenceSession(router, catalog.transport, router::isCurrent)
        val vm = InferenceViewModel(router.state, session, { beforeRead(it); router.selectedModel(it) }, router::selectModel)
        private val store = androidx.lifecycle.ViewModelStore().also {
            androidx.lifecycle.ViewModelProvider.create(it, viewModelFactory {
                initializer { vm }
            })[InferenceViewModel::class]
        }
        suspend fun clearOwner() = withContext(main) { store.clear() }
        suspend fun start() { p.load(); k.load() }
        suspend fun ready(slug: String) = withTimeout(5000) {
            vm.state.first { it.stage == ProofStage.READY && it.selectedSlug == slug }
        }
        suspend fun select(slug: String) {
            withContext(main) { vm.select(slug) }
            ready(slug)
        }
        suspend fun close() {
            val job = vm.viewModelScope.coroutineContext[Job]!!
            clearOwner()
            job.join()
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
    protected fun runtime(oauth: Disk, api: Disk, catalog: Catalog = Catalog(),
        beforeRead: suspend (CredentialLease) -> Unit = {},
        refresh: suspend (CredentialProfile) -> CredentialProfile = { error("Fresh synthetic credentials must not refresh") }) = Runtime(oauth, api, catalog, beforeRead, refresh).also { runtimes += it }

}
