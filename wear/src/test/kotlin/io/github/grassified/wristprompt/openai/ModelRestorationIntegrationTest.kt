package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.ui.shellPresentation
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ModelRestorationIntegrationTest : ReconstructionFixture() {
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
