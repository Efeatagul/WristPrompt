package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.GcmRecord
import io.github.grassified.wristprompt.storage.SecretRecord
import io.github.grassified.wristprompt.ui.shellPresentation
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
import org.junit.Test

/** Real repositories/router/session/ViewModel and catalog decoder; only disk-key/HTTP boundaries are synthetic. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelRestorationIntegrationTest {
    private val time = 1_000_000L
    private val host = BridgeProtocol.newHostId()
    private val terra = "gpt-6.1-terra"
    private val sol = "gpt-6.1-sol"
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var directory: File
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
    private inner class Disk(name: String) {
        private val file = File(directory, name)
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        private val aad = name.encodeToByteArray()
        val writes = AtomicInteger()
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
    private fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, time)
    private suspend fun addPlan(p: WatchProfiles, client: String = "client_one") {
        val r = request()
        p.prepare("phone", r)
        p.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, r.requestId, BridgeProtocol.newRequestId(),
            CredentialProfile(RegistrationIdentity(client, OpenAiAuth.ISSUER, "subject_$client"), host,
                "Synthetic", null, false, "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer",
                3600, time, OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)))))
    }
    private suspend fun seed(oauth: Disk, api: Disk, saved: String? = terra) {
        val p = WatchProfiles(oauth.record(), main) { time }
        val k = ApiKeyRepository(api.record()) { time }
        addPlan(p)
        k.load()
        if (saved != null) p.selectModel("client_one", p.state.value.generation, saved)
    }

    private inner class Catalog {
        @Volatile var order = listOf(sol, terra)
        val calls = AtomicInteger()
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
                calls.incrementAndGet()
                before(credential)
                return provider.models(credential)
            }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) =
                error("Restoration must not send inference")
        }
    }
    private inner class Runtime(oauth: Disk, api: Disk, val catalog: Catalog = Catalog(),
        beforeRead: suspend (CredentialLease) -> Unit = {}) {
        val p = WatchProfiles(oauth.record(), main) { time }
        val k = ApiKeyRepository(api.record()) { time }
        private val scope = CoroutineScope(SupervisorJob() + main)
        val router = CredentialRouter(p, k, { error("Fresh synthetic credentials must not refresh") }, scope)
        private val session = InferenceSession(router, catalog.transport, router::isCurrent)
        val vm = InferenceViewModel(router.state, session, { beforeRead(it); router.selectedModel(it) }, router::selectModel)
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
            job.cancelAndJoin()
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
    private fun runtime(oauth: Disk, api: Disk, catalog: Catalog = Catalog(),
        beforeRead: suspend (CredentialLease) -> Unit = {}) = Runtime(oauth, api, catalog, beforeRead).also { runtimes += it }

    @Test fun explicitTerraSelectionSurvivesFullReconstruction(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api, saved = null)
        val first = runtime(oauth, api); first.start(); first.ready(sol); first.select(terra)
        assertEquals(terra, first.p.selectedModel("client_one"))
        first.close()
        val writes = oauth.writes.get()
        val restored = runtime(oauth, api); restored.start(); restored.ready(terra)
        assertEquals(writes, oauth.writes.get())
        assertEquals("Label $terra", shellPresentation(restored.p.state.value, restored.k.state.value,
            restored.vm.state.value, WatchAuthState.IDLE, false).model)
    }
    @Test fun eitherFreshCatalogOrderRestoresTerraWithoutWriteback(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val writes = oauth.writes.get()
        for (order in listOf(listOf(terra, sol), listOf(sol, terra))) {
            val r = runtime(oauth, api, Catalog().apply { this.order = order })
            r.start(); r.ready(terra); r.close()
        }
        assertEquals(writes, oauth.writes.get())
    }
    @Test fun repeatedReloadsDoNotSelectOrPersistFirstEntry(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val r = runtime(oauth, api); r.start(); r.ready(terra)
        val writes = oauth.writes.get()
        repeat(3) {
            withContext(main) { r.vm.loadModels(); r.vm.loadModels() }
            r.ready(terra)
        }
        assertEquals(writes, oauth.writes.get()); assertEquals(terra, r.p.selectedModel("client_one"))
        assertEquals(4, r.catalog.calls.get())
    }
    @Test fun delayedProfileRestorationDoesNotDiscoverOrOverwriteTerra(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val r = runtime(oauth, api); r.k.load()
        withContext(main) { assertFalse(r.router.state.value.usable); assertEquals(0, r.catalog.calls.get()) }
        r.p.load(); r.ready(terra)
    }
    @Test fun delayedBillingRestorationDoesNotDiscoverUsingDefaultMode(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val r = runtime(oauth, api); r.p.load()
        withContext(main) { assertFalse(r.router.state.value.usable); assertEquals(0, r.catalog.calls.get()) }
        r.k.load(); r.ready(terra)
    }
    @Test fun delayedDiscoveryAndRepeatedReloadCannotWriteDefault(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val r = runtime(oauth, api, Catalog().apply { before = { entered.complete(Unit); release.await() } })
        val writes = oauth.writes.get()
        r.start(); withTimeout(5000) { entered.await() }
        withContext(main) { r.vm.loadModels(); r.vm.loadModels(); assertNull(r.vm.state.value.selectedSlug) }
        assertEquals(writes, oauth.writes.get())
        release.complete(Unit); r.ready(terra); assertEquals(writes, oauth.writes.get())
    }
    @Test fun multipleStartupLoadsAndDelayedPreferenceReadPreserveTerra(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val r = runtime(oauth, api, beforeRead = { entered.complete(Unit); release.await() })
        val writes = oauth.writes.get()
        r.start(); withTimeout(5000) { entered.await() }
        r.p.load(); r.k.load(); r.p.load(); r.k.snapshot()
        withContext(main) { r.vm.loadModels(); assertNull(r.vm.state.value.selectedSlug) }
        release.complete(Unit); r.ready(terra)
        assertEquals(writes, oauth.writes.get()); assertEquals(terra, r.p.selectedModel("client_one"))
    }
    @Test fun absentSavedModelUsesFirstServiceEntryWithoutErasingPreference(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api, "removed-model")
        val r = runtime(oauth, api); val writes = oauth.writes.get(); r.start(); r.ready(sol)
        assertEquals("removed-model", r.p.selectedModel("client_one")); assertEquals(writes, oauth.writes.get())
        r.catalog.order = listOf(sol, "removed-model")
        withContext(main) { r.vm.loadModels() }; r.ready("removed-model")
    }
    @Test fun noSavedModelUsesFirstServiceEntryWithoutPersistingDefault(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api, null)
        val r = runtime(oauth, api); val writes = oauth.writes.get(); r.start(); r.ready(sol)
        assertNull(r.p.selectedModel("client_one")); assertEquals(writes, oauth.writes.get())
    }
    @Test fun differentClientDoesNotInheritTerraAndReturningClientRestoresIt(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val r = runtime(oauth, api); r.start(); r.ready(terra)
        r.p.select(null); addPlan(r.p, "client_two"); r.ready(sol)
        assertNull(r.p.selectedModel("client_two")); assertEquals(terra, r.p.selectedModel("client_one"))
        r.p.select("client_one"); r.ready(terra)
    }
    @Test fun apiModeRestoresBeforeProfileLoadAndKeepsPlanPreferenceSeparate(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val k = ApiKeyRepository(api.record()) { time }; val req = request(); k.prepare("phone", req)
        k.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, req.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
        k.selectMode(CredentialMode.OPENAI_API_KEY); k.selectModel(k.credential(), "api-model")
        val r = runtime(oauth, api); r.k.load(); r.ready("api-model")
        assertEquals(CredentialMode.OPENAI_API_KEY, r.vm.state.value.mode)
        r.p.load(); r.k.selectMode(CredentialMode.CHATGPT_PLAN); r.ready(terra)
        r.k.selectMode(CredentialMode.OPENAI_API_KEY); r.ready("api-model")
        assertEquals(terra, r.p.selectedModel("client_one"))
    }
    @Test fun stalePreferenceCallbackCannotReplaceCurrentAccountSelection(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        val r = runtime(oauth, api, beforeRead = { lease ->
            if (lease.clientId == "client_one") withContext(NonCancellable) {
                entered.complete(Unit); withTimeout(5000) { release.await() }; finished.complete(Unit)
            }
        })
        r.start(); withTimeout(5000) { entered.await() }
        r.p.select(null); addPlan(r.p, "client_two"); r.ready(sol)
        release.complete(Unit); withTimeout(5000) { finished.await() }
        // Wait for the cancelled old operation to finish on the UI dispatcher.
        withContext(main) { assertEquals(sol, r.vm.state.value.selectedSlug) }
        assertEquals(terra, r.p.selectedModel("client_one")); assertNull(r.p.selectedModel("client_two"))
    }
    @Test fun identicalLabelsAndCaseDistinctSlugsRestoreExactPersistedIdentity(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val catalog = Catalog().apply {
            order = listOf(terra.uppercase(), sol, terra)
            label = { "Same display label" }
        }
        val r = runtime(oauth, api, catalog); r.start(); r.ready(terra)
        assertEquals(terra, r.vm.state.value.selectedSlug)
        assertEquals(terra, r.p.selectedModel("client_one"))
    }
    @Test fun lateDiscoveryCannotReplaceNewAccountSelection(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        val catalog = Catalog().apply {
            before = { lease ->
                if (lease.clientId == "client_one") withContext(NonCancellable) {
                    entered.complete(Unit); withTimeout(5000) { release.await() }; finished.complete(Unit)
                }
            }
        }
        val r = runtime(oauth, api, catalog); r.start(); withTimeout(5000) { entered.await() }
        r.p.select(null); addPlan(r.p, "client_two"); r.ready(sol)
        release.complete(Unit); withTimeout(5000) { finished.await() }
        withContext(main) { assertEquals(sol, r.vm.state.value.selectedSlug) }
        assertEquals(terra, r.p.selectedModel("client_one")); assertNull(r.p.selectedModel("client_two"))
    }
    @Test fun sameClientReauthorizationPreservesPreference(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val r = runtime(oauth, api); r.start(); r.ready(terra)
        r.p.signOut { RevocationResult.CONFIRMED }
        addPlan(r.p); r.ready(terra)
        assertEquals(terra, r.p.selectedModel("client_one"))
    }
}
